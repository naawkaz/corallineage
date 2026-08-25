package com.corallineage.backend.pointannotation;

import com.corallineage.backend.annotation.AnnotationJob;
import com.corallineage.backend.annotation.AnnotationJobRepository;
import com.corallineage.backend.datasets.Dataset;
import com.corallineage.backend.datasets.DatasetService;
import com.corallineage.backend.exception.ForbiddenException;
import com.corallineage.backend.exception.InvalidRequestException;
import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.logging.AnnotationEventLoggerService;
import com.corallineage.backend.logging.EventType;
import com.corallineage.backend.registry.model.ModelVersion;
import com.corallineage.backend.training.InitiateTrainingRequest;
import com.corallineage.backend.training.TrainingJob;
import com.corallineage.backend.training.TrainingJobRepository;
import com.corallineage.backend.training.TrainingJobResponse;
import com.corallineage.backend.training.TrainingJobService;
import com.corallineage.backend.versioning.dataset.AnnotationPoint;
import com.corallineage.backend.versioning.dataset.AnnotationPointRepository;
import com.corallineage.backend.versioning.dataset.DatasetVersion;
import com.corallineage.backend.versioning.dataset.DatasetVersionImageRepository;
import com.corallineage.backend.versioning.dataset.DatasetVersionRepository;
import com.corallineage.backend.versioning.dataset.Image;
import com.corallineage.backend.versioning.dataset.ImageRepository;
import com.corallineage.backend.versioning.dataset.LabelDefinition;
import com.corallineage.backend.versioning.dataset.LabelDefinitionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PointAnnotationJobServiceTest {

    @Mock private DatasetVersionRepository datasetVersionRepository;
    @Mock private DatasetVersionImageRepository datasetVersionImageRepository;
    @Mock private ImageRepository imageRepository;
    @Mock private PointAnnotationJobRepository pointAnnotationJobRepository;
    @Mock private GeneratedPointRepository generatedPointRepository;
    @Mock private AnnotationEventLoggerService annotationEventLoggerService;
    @Mock private LabelDefinitionRepository labelDefinitionRepository;
    @Mock private AnnotationPointRepository annotationPointRepository;
    @Mock private TrainingJobService trainingJobService;
    @Mock private TrainingJobRepository trainingJobRepository;
    @Mock private AutoAnnotationExecutor autoAnnotationExecutor;
    @Mock private DatasetService datasetService;
    @Mock private AnnotationJobRepository annotationJobRepository;

    private final PointAnnotationMapper mapper = new PointAnnotationMapper();

    private PointAnnotationJobService newService(Path workingDirectory) {
        PointGenerationService pointGenerationService = new PointGenerationService(workingDirectory);
        AnnotationPointBridge annotationPointBridge = new AnnotationPointBridge(annotationPointRepository);
        return new PointAnnotationJobService(datasetVersionRepository, datasetVersionImageRepository,
                imageRepository, pointAnnotationJobRepository, generatedPointRepository,
                pointGenerationService, mapper, annotationEventLoggerService,
                labelDefinitionRepository, annotationPointRepository, annotationPointBridge,
                trainingJobService, trainingJobRepository, autoAnnotationExecutor, datasetService,
                annotationJobRepository);
    }

    private void writeTestImage(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        ImageIO.write(new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB), "jpg", file.toFile());
    }

    private DatasetVersion version(Long id) {
        DatasetVersion version = new DatasetVersion();
        version.setId(id);
        version.setVersionTag("v1.0");
        Dataset dataset = new Dataset();
        dataset.setId(99L);
        version.setDataset(dataset);
        return version;
    }

    private Image image(Long id, String relativePath) {
        Image image = new Image();
        image.setId(id);
        image.setFilename(id + ".jpg");
        image.setFilePath(relativePath);
        return image;
    }

    private StartPointAnnotationJobRequest request(Long versionId, Integer seedCount) {
        StartPointAnnotationJobRequest request = new StartPointAnnotationJobRequest();
        request.setDatasetVersionId(versionId);
        request.setSeedImageCount(seedCount);
        request.setInitiatedBy("researcher_01");
        return request;
    }

    @Test
    void submitJob_versionNotFound_throwsNotFound(@org.junit.jupiter.api.io.TempDir Path workingDirectory) {
        when(datasetVersionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService(workingDirectory).submitJob(request(1L, 5), "researcher_01"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void submitJob_noImages_throwsInvalidRequest(@org.junit.jupiter.api.io.TempDir Path workingDirectory) {
        when(datasetVersionRepository.findById(1L)).thenReturn(Optional.of(version(1L)));
        when(datasetVersionImageRepository.findImageIdsByDatasetVersionId(1L)).thenReturn(Set.of());

        assertThatThrownBy(() -> newService(workingDirectory).submitJob(request(1L, 5), "researcher_01"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("no images");
    }

    @Test
    void submitJob_seedCountExceedsImageCount_throwsInvalidRequest(@org.junit.jupiter.api.io.TempDir Path workingDirectory) {
        when(datasetVersionRepository.findById(1L)).thenReturn(Optional.of(version(1L)));
        when(datasetVersionImageRepository.findImageIdsByDatasetVersionId(1L)).thenReturn(Set.of(1L, 2L));
        when(imageRepository.findAllById(Set.of(1L, 2L)))
                .thenReturn(List.of(image(1L, "dataset/1.jpg"), image(2L, "dataset/2.jpg")));

        assertThatThrownBy(() -> newService(workingDirectory).submitJob(request(1L, 5), "researcher_01"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("exceeds");
    }

    @Test
    void submitJob_success_generatesPointsAndLogsEvent(@org.junit.jupiter.api.io.TempDir Path workingDirectory) throws IOException {
        writeTestImage(workingDirectory.resolve("dataset/1.jpg"));
        writeTestImage(workingDirectory.resolve("dataset/2.jpg"));
        writeTestImage(workingDirectory.resolve("dataset/3.jpg"));

        DatasetVersion version = version(1L);
        when(datasetVersionRepository.findById(1L)).thenReturn(Optional.of(version));
        when(datasetVersionImageRepository.findImageIdsByDatasetVersionId(1L)).thenReturn(Set.of(1L, 2L, 3L));
        when(imageRepository.findAllById(Set.of(1L, 2L, 3L))).thenReturn(List.of(
                image(1L, "dataset/1.jpg"), image(2L, "dataset/2.jpg"), image(3L, "dataset/3.jpg")));
        when(pointAnnotationJobRepository.save(any(PointAnnotationJob.class))).thenAnswer(inv -> {
            PointAnnotationJob job = inv.getArgument(0);
            if (job.getId() == null) job.setId(7L);
            return job;
        });
        when(generatedPointRepository.countByJobIdAndSeedTrue(7L)).thenReturn(100L);
        when(generatedPointRepository.countByJobIdAndSeedTrueAndStatus(7L, "HUMAN_LABELED")).thenReturn(0L);

        StartPointAnnotationJobRequest req = request(1L, 2);
        req.setGridRows(7);
        req.setGridCols(8);
        req.setConfidenceThreshold(new BigDecimal("0.9"));

        PointAnnotationJobResponse response = newService(workingDirectory).submitJob(req, "researcher_01");

        assertThat(response.getJobId()).isEqualTo(7L);
        assertThat(response.getStatus()).isEqualTo("SEED_LABELING");
        assertThat(response.getTotalImages()).isEqualTo(3);
        assertThat(response.getGridRows()).isEqualTo(7);
        assertThat(response.getGridCols()).isEqualTo(8);
        assertThat(response.getSeedImageCount()).isEqualTo(2);

        org.mockito.ArgumentCaptor<List<GeneratedPoint>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(generatedPointRepository).saveAll(captor.capture());
        List<GeneratedPoint> saved = captor.getValue();
        // requested 7x8 = 56-point grid per image, 3 images * 56 points.
        assertThat(saved).hasSize(168);
        assertThat(saved.stream().filter(GeneratedPoint::isSeed).count()).isEqualTo(112); // first 2 images
        assertThat(saved.stream().filter(p -> !p.isSeed()).count()).isEqualTo(56); // 3rd image

        verify(annotationEventLoggerService).log(eq(EventType.POINTS_GENERATED), eq(version), eq("researcher_01"), any());
    }

    @Test
    void submitJob_actingUserNotProjectMember_throwsForbidden(@org.junit.jupiter.api.io.TempDir Path workingDirectory) {
        when(datasetVersionRepository.findById(1L)).thenReturn(Optional.of(version(1L)));
        org.mockito.Mockito.doThrow(new ForbiddenException("You are not a member of the project that owns this dataset"))
                .when(datasetService).requireAccess(99L, "outsider");

        assertThatThrownBy(() -> newService(workingDirectory).submitJob(request(1L, 5), "outsider"))
                .isInstanceOf(ForbiddenException.class);
        verify(pointAnnotationJobRepository, never()).save(any());
    }

    @Test
    void getPoints_jobNotFound_throwsNotFound(@org.junit.jupiter.api.io.TempDir Path workingDirectory) {
        when(pointAnnotationJobRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService(workingDirectory).getPoints(99L, true,
                org.springframework.data.domain.PageRequest.of(0, 10)))
                .isInstanceOf(NotFoundException.class);
        verify(generatedPointRepository, never()).findByJobIdAndSeed(any(), anyBoolean(), any());
    }

    private PointAnnotationJob job(Long id, String status, DatasetVersion version) {
        PointAnnotationJob job = new PointAnnotationJob();
        job.setId(id);
        job.setDatasetVersion(version);
        job.setStatus(status);
        job.setInitiatedBy("researcher_01");
        job.setConfidenceThreshold(new BigDecimal("0.85"));
        return job;
    }

    @Test
    void trainModel_jobNotFound_throwsNotFound(@org.junit.jupiter.api.io.TempDir Path workingDirectory) {
        when(pointAnnotationJobRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService(workingDirectory).trainModel(1L, null)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void trainModel_wrongStatus_throwsInvalidRequest(@org.junit.jupiter.api.io.TempDir Path workingDirectory) {
        PointAnnotationJob job = job(1L, "REVIEW", version(1L));
        when(pointAnnotationJobRepository.findById(1L)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> newService(workingDirectory).trainModel(1L, null)).isInstanceOf(InvalidRequestException.class);
        verify(trainingJobService, never()).initiateTrainingJob(any());
    }

    @Test
    void trainModel_seedNotFullyLabeled_throwsInvalidRequest(@org.junit.jupiter.api.io.TempDir Path workingDirectory) {
        PointAnnotationJob job = job(1L, "SEED_LABELING", version(1L));
        when(pointAnnotationJobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(generatedPointRepository.countByJobIdAndSeedTrue(1L)).thenReturn(20L);
        when(generatedPointRepository.countByJobIdAndSeedTrueAndStatus(1L, "HUMAN_LABELED")).thenReturn(19L);

        assertThatThrownBy(() -> newService(workingDirectory).trainModel(1L, null)).isInstanceOf(InvalidRequestException.class);
        verify(trainingJobService, never()).initiateTrainingJob(any());
    }

    @Test
    void trainModel_success_dispatchesTrainingAndSetsTrainingStatus(@org.junit.jupiter.api.io.TempDir Path workingDirectory) {
        PointAnnotationJob job = job(1L, "SEED_LABELING", version(1L));
        when(pointAnnotationJobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(generatedPointRepository.countByJobIdAndSeedTrue(1L)).thenReturn(20L);
        when(generatedPointRepository.countByJobIdAndSeedTrueAndStatus(1L, "HUMAN_LABELED")).thenReturn(20L);

        TrainingJobResponse trainingResponse = new TrainingJobResponse();
        trainingResponse.setJobId(50L);
        when(trainingJobService.initiateTrainingJob(any(InitiateTrainingRequest.class))).thenReturn(trainingResponse);

        TrainingJob trainingJob = new TrainingJob();
        trainingJob.setId(50L);
        when(trainingJobRepository.findById(50L)).thenReturn(Optional.of(trainingJob));
        when(pointAnnotationJobRepository.save(any(PointAnnotationJob.class))).thenAnswer(inv -> inv.getArgument(0));

        PointAnnotationJobResponse response = newService(workingDirectory).trainModel(1L, null);

        assertThat(response.getStatus()).isEqualTo("TRAINING");
        assertThat(response.getTrainingJobId()).isEqualTo(50L);
        assertThat(job.getTrainingJob()).isSameAs(trainingJob);

        org.mockito.ArgumentCaptor<InitiateTrainingRequest> captor =
                org.mockito.ArgumentCaptor.forClass(InitiateTrainingRequest.class);
        verify(trainingJobService).initiateTrainingJob(captor.capture());
        assertThat(captor.getValue().getDatasetVersionId()).isEqualTo(1L);
        assertThat(captor.getValue().getInitiatedBy()).isEqualTo("researcher_01");
    }

    @Test
    void startAutoAnnotate_wrongStatus_throwsInvalidRequest(@org.junit.jupiter.api.io.TempDir Path workingDirectory) {
        PointAnnotationJob job = job(1L, "SEED_LABELING", version(1L));
        when(pointAnnotationJobRepository.findById(1L)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> newService(workingDirectory).startAutoAnnotate(1L)).isInstanceOf(InvalidRequestException.class);
        verify(autoAnnotationExecutor, never()).executeAutoAnnotationAsync(any());
    }

    @Test
    void startAutoAnnotate_trainingNotCompleted_throwsInvalidRequest(@org.junit.jupiter.api.io.TempDir Path workingDirectory) {
        PointAnnotationJob job = job(1L, "TRAINING", version(1L));
        TrainingJob trainingJob = new TrainingJob();
        trainingJob.setId(50L);
        trainingJob.setStatus("RUNNING");
        job.setTrainingJob(trainingJob);
        when(pointAnnotationJobRepository.findById(1L)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> newService(workingDirectory).startAutoAnnotate(1L)).isInstanceOf(InvalidRequestException.class);
        verify(autoAnnotationExecutor, never()).executeAutoAnnotationAsync(any());
    }

    /**
     * startAutoAnnotate now delegates into a real AnnotationJob (commitInPlace=true) the moment
     * training completes, marks this point-annotation job COMPLETE immediately (its own
     * responsibility ends at delegation - see PointAnnotationJob.annotationJob's javadoc), and
     * still dispatches the same background executor as before (no real Spring transaction/
     * synchronization is active under Mockito, so dispatchAutoAnnotateAfterCommit's
     * TransactionSynchronizationManager.isSynchronizationActive() check is false and the dispatch
     * happens immediately, same as TrainingJobServiceTest's equivalent dispatch-after-commit
     * coverage). The actual REVIEW/COMPLETE-equivalent completion logic for the delegated job now
     * lives in PointAnnotationJobStatusService.completeAutoAnnotation, exercised by
     * AutoAnnotationExecutorTest instead.
     */
    @Test
    void startAutoAnnotate_success_delegatesToAnnotationJobAndDispatchesExecutor(@org.junit.jupiter.api.io.TempDir Path workingDirectory) {
        PointAnnotationJob job = job(1L, "TRAINING", version(1L));
        TrainingJob trainingJob = new TrainingJob();
        trainingJob.setId(50L);
        trainingJob.setStatus("COMPLETED");
        ModelVersion model = new ModelVersion();
        model.setId(9L);
        model.setModelTag("model-v9");
        trainingJob.setModelVersion(model);
        job.setTrainingJob(trainingJob);

        when(pointAnnotationJobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(pointAnnotationJobRepository.save(any(PointAnnotationJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(generatedPointRepository.findByJobIdAndSeedFalseAndStatus(1L, "UNLABELED")).thenReturn(List.of());
        when(annotationJobRepository.save(any(AnnotationJob.class))).thenAnswer(inv -> {
            AnnotationJob delegated = inv.getArgument(0);
            if (delegated.getId() == null) delegated.setId(500L);
            return delegated;
        });

        PointAnnotationJobResponse response = newService(workingDirectory).startAutoAnnotate(1L);

        assertThat(response.getStatus()).isEqualTo("COMPLETE");
        assertThat(job.getModelVersion()).isSameAs(model);
        assertThat(job.getAnnotationJob()).isNotNull();
        assertThat(job.getAnnotationJob().getCommitInPlace()).isTrue();
        assertThat(job.getAnnotationJob().getModelVersion()).isSameAs(model);
        verify(autoAnnotationExecutor).executeAutoAnnotationAsync(1L);
        verify(annotationEventLoggerService, never()).log(eq(EventType.AUTO_ANNOTATION_COMPLETED), any(), any(), any());
    }

    private GeneratedPoint generatedPoint(Long id, Long imageId, int pointIndex, DatasetVersion version) {
        PointAnnotationJob job = new PointAnnotationJob();
        job.setDatasetVersion(version);
        Image image = image(imageId, "dataset/" + imageId + ".jpg");
        GeneratedPoint point = new GeneratedPoint();
        point.setId(id);
        point.setJob(job);
        point.setImage(image);
        point.setPointIndex(pointIndex);
        point.setRowPosition(10);
        point.setColPosition(20);
        point.setSeed(true);
        point.setStatus("UNLABELED");
        return point;
    }

    private LabelPointRequest labelRequest(String labelCode) {
        LabelPointRequest request = new LabelPointRequest();
        request.setLabelCode(labelCode);
        request.setAnnotatedBy("researcher_01");
        return request;
    }

    @Test
    void labelPoint_pointNotFound_throwsNotFound(@org.junit.jupiter.api.io.TempDir Path workingDirectory) {
        when(generatedPointRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService(workingDirectory).labelPoint(5L, labelRequest("CCA")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void labelPoint_unknownLabelCode_throwsInvalidRequest(@org.junit.jupiter.api.io.TempDir Path workingDirectory) {
        DatasetVersion version = version(1L);
        GeneratedPoint point = generatedPoint(5L, 1L, 0, version);
        when(generatedPointRepository.findById(5L)).thenReturn(Optional.of(point));
        when(labelDefinitionRepository.findByDatasetVersionIdAndLabelCode(1L, "NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService(workingDirectory).labelPoint(5L, labelRequest("NOPE")))
                .isInstanceOf(InvalidRequestException.class);
        verify(annotationPointRepository, never()).save(any());
    }

    @Test
    void labelPoint_success_setsFinalLabelAndUpsertsAnnotationPoint(@org.junit.jupiter.api.io.TempDir Path workingDirectory) {
        DatasetVersion version = version(1L);
        GeneratedPoint point = generatedPoint(5L, 1L, 3, version);
        LabelDefinition label = new LabelDefinition();
        label.setLabelCode("CCA");
        label.setLabelName("Crustose Coralline Algae");

        when(generatedPointRepository.findById(5L)).thenReturn(Optional.of(point));
        when(labelDefinitionRepository.findByDatasetVersionIdAndLabelCode(1L, "CCA")).thenReturn(Optional.of(label));
        when(generatedPointRepository.save(any(GeneratedPoint.class))).thenAnswer(inv -> inv.getArgument(0));
        when(annotationPointRepository.findByImageIdAndPointIndex(1L, 3)).thenReturn(Optional.empty());
        when(annotationPointRepository.save(any(AnnotationPoint.class))).thenAnswer(inv -> inv.getArgument(0));

        GeneratedPointResponse response = newService(workingDirectory).labelPoint(5L, labelRequest("CCA"));

        assertThat(response.getStatus()).isEqualTo("HUMAN_LABELED");
        assertThat(response.getFinalLabelCode()).isEqualTo("CCA");

        org.mockito.ArgumentCaptor<AnnotationPoint> captor = org.mockito.ArgumentCaptor.forClass(AnnotationPoint.class);
        verify(annotationPointRepository).save(captor.capture());
        AnnotationPoint saved = captor.getValue();
        assertThat(saved.getAnnotationSource()).isEqualTo("HUMAN");
        assertThat(saved.getAnnotatedBy()).isEqualTo("researcher_01");
        assertThat(saved.getLabelDefinition()).isEqualTo(label);
        assertThat(saved.getPointIndex()).isEqualTo(3);
        assertThat(saved.getConfidenceScore()).isNull();
    }

    @Test
    void labelPoint_existingAnnotationPoint_updatesInPlaceRatherThanDuplicating(@org.junit.jupiter.api.io.TempDir Path workingDirectory) {
        DatasetVersion version = version(1L);
        GeneratedPoint point = generatedPoint(5L, 1L, 3, version);
        LabelDefinition label = new LabelDefinition();
        label.setLabelCode("SAND");

        AnnotationPoint existing = new AnnotationPoint();
        existing.setId(42L);

        when(generatedPointRepository.findById(5L)).thenReturn(Optional.of(point));
        when(labelDefinitionRepository.findByDatasetVersionIdAndLabelCode(1L, "SAND")).thenReturn(Optional.of(label));
        when(generatedPointRepository.save(any(GeneratedPoint.class))).thenAnswer(inv -> inv.getArgument(0));
        when(annotationPointRepository.findByImageIdAndPointIndex(1L, 3)).thenReturn(Optional.of(existing));
        when(annotationPointRepository.save(any(AnnotationPoint.class))).thenAnswer(inv -> inv.getArgument(0));

        newService(workingDirectory).labelPoint(5L, labelRequest("SAND"));

        org.mockito.ArgumentCaptor<AnnotationPoint> captor = org.mockito.ArgumentCaptor.forClass(AnnotationPoint.class);
        verify(annotationPointRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(42L);
    }
}
