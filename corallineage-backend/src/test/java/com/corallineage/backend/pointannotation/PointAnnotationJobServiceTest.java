package com.corallineage.backend.pointannotation;

import com.corallineage.backend.datasets.Dataset;
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
    @Mock private PointInferenceService pointInferenceService;

    private final PointAnnotationMapper mapper = new PointAnnotationMapper();

    private PointAnnotationJobService newService(Path workingDirectory) {
        PointGenerationService pointGenerationService = new PointGenerationService(workingDirectory);
        AnnotationPointBridge annotationPointBridge = new AnnotationPointBridge(annotationPointRepository);
        return new PointAnnotationJobService(datasetVersionRepository, datasetVersionImageRepository,
                imageRepository, pointAnnotationJobRepository, generatedPointRepository,
                pointGenerationService, mapper, annotationEventLoggerService,
                labelDefinitionRepository, annotationPointRepository, annotationPointBridge,
                trainingJobService, trainingJobRepository, pointInferenceService);
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

        assertThatThrownBy(() -> newService(workingDirectory).submitJob(request(1L, 5)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void submitJob_noImages_throwsInvalidRequest(@org.junit.jupiter.api.io.TempDir Path workingDirectory) {
        when(datasetVersionRepository.findById(1L)).thenReturn(Optional.of(version(1L)));
        when(datasetVersionImageRepository.findImageIdsByDatasetVersionId(1L)).thenReturn(Set.of());

        assertThatThrownBy(() -> newService(workingDirectory).submitJob(request(1L, 5)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("no images");
    }

    @Test
    void submitJob_seedCountExceedsImageCount_throwsInvalidRequest(@org.junit.jupiter.api.io.TempDir Path workingDirectory) {
        when(datasetVersionRepository.findById(1L)).thenReturn(Optional.of(version(1L)));
        when(datasetVersionImageRepository.findImageIdsByDatasetVersionId(1L)).thenReturn(Set.of(1L, 2L));
        when(imageRepository.findAllById(Set.of(1L, 2L)))
                .thenReturn(List.of(image(1L, "dataset/1.jpg"), image(2L, "dataset/2.jpg")));

        assertThatThrownBy(() -> newService(workingDirectory).submitJob(request(1L, 5)))
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
        req.setPointsPerImage(50);
        req.setConfidenceThreshold(new BigDecimal("0.9"));

        PointAnnotationJobResponse response = newService(workingDirectory).submitJob(req);

        assertThat(response.getJobId()).isEqualTo(7L);
        assertThat(response.getStatus()).isEqualTo("SEED_LABELING");
        assertThat(response.getTotalImages()).isEqualTo(3);
        assertThat(response.getPointsPerImage()).isEqualTo(50);
        assertThat(response.getSeedImageCount()).isEqualTo(2);

        org.mockito.ArgumentCaptor<List<GeneratedPoint>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(generatedPointRepository).saveAll(captor.capture());
        List<GeneratedPoint> saved = captor.getValue();
        assertThat(saved).hasSize(150); // 3 images * 50 points
        assertThat(saved.stream().filter(GeneratedPoint::isSeed).count()).isEqualTo(100); // first 2 images
        assertThat(saved.stream().filter(p -> !p.isSeed()).count()).isEqualTo(50); // 3rd image

        verify(annotationEventLoggerService).log(eq(EventType.POINTS_GENERATED), eq(version), eq("researcher_01"), any());
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

        assertThatThrownBy(() -> newService(workingDirectory).trainModel(1L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void trainModel_wrongStatus_throwsInvalidRequest(@org.junit.jupiter.api.io.TempDir Path workingDirectory) {
        PointAnnotationJob job = job(1L, "REVIEW", version(1L));
        when(pointAnnotationJobRepository.findById(1L)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> newService(workingDirectory).trainModel(1L)).isInstanceOf(InvalidRequestException.class);
        verify(trainingJobService, never()).initiateTrainingJob(any());
    }

    @Test
    void trainModel_seedNotFullyLabeled_throwsInvalidRequest(@org.junit.jupiter.api.io.TempDir Path workingDirectory) {
        PointAnnotationJob job = job(1L, "SEED_LABELING", version(1L));
        when(pointAnnotationJobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(generatedPointRepository.countByJobIdAndSeedTrue(1L)).thenReturn(20L);
        when(generatedPointRepository.countByJobIdAndSeedTrueAndStatus(1L, "HUMAN_LABELED")).thenReturn(19L);

        assertThatThrownBy(() -> newService(workingDirectory).trainModel(1L)).isInstanceOf(InvalidRequestException.class);
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

        PointAnnotationJobResponse response = newService(workingDirectory).trainModel(1L);

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
        verify(pointInferenceService, never()).runInference(any());
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
        verify(pointInferenceService, never()).runInference(any());
    }

    @Test
    void startAutoAnnotate_success_pendingPointsRemain_setsReviewStatus(@org.junit.jupiter.api.io.TempDir Path workingDirectory) {
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
        when(generatedPointRepository.countByJobIdAndSeedFalseAndStatus(1L, "MODEL_PREDICTED")).thenReturn(3L);

        PointAnnotationJobResponse response = newService(workingDirectory).startAutoAnnotate(1L);

        assertThat(response.getStatus()).isEqualTo("REVIEW");
        assertThat(job.getModelVersion()).isSameAs(model);
        verify(pointInferenceService).runInference(job);
        verify(annotationEventLoggerService).log(eq(EventType.AUTO_ANNOTATION_COMPLETED), any(), eq("researcher_01"), any());
    }

    @Test
    void startAutoAnnotate_success_noPendingPoints_setsCompleteStatus(@org.junit.jupiter.api.io.TempDir Path workingDirectory) {
        PointAnnotationJob job = job(1L, "TRAINING", version(1L));
        TrainingJob trainingJob = new TrainingJob();
        trainingJob.setId(50L);
        trainingJob.setStatus("COMPLETED");
        ModelVersion model = new ModelVersion();
        model.setId(9L);
        trainingJob.setModelVersion(model);
        job.setTrainingJob(trainingJob);

        when(pointAnnotationJobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(pointAnnotationJobRepository.save(any(PointAnnotationJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(generatedPointRepository.countByJobIdAndSeedFalseAndStatus(1L, "MODEL_PREDICTED")).thenReturn(0L);

        PointAnnotationJobResponse response = newService(workingDirectory).startAutoAnnotate(1L);

        assertThat(response.getStatus()).isEqualTo("COMPLETE");
    }

    private GeneratedPoint pendingReviewPoint(Long id, PointAnnotationJob job, LabelDefinition predicted) {
        GeneratedPoint point = new GeneratedPoint();
        point.setId(id);
        point.setJob(job);
        point.setImage(image(1L, "dataset/1.jpg"));
        point.setPointIndex(200);
        point.setRowPosition(10);
        point.setColPosition(20);
        point.setSeed(false);
        point.setStatus("MODEL_PREDICTED");
        point.setPredictedLabelDefinition(predicted);
        point.setConfidenceScore(new BigDecimal("0.55"));
        return point;
    }

    private ReviewGeneratedPointRequest reviewRequest(Boolean accepted, String correctedLabelCode) {
        ReviewGeneratedPointRequest request = new ReviewGeneratedPointRequest();
        request.setAccepted(accepted);
        request.setCorrectedLabelCode(correctedLabelCode);
        request.setReviewedBy("researcher_01");
        return request;
    }

    @Test
    void reviewPoint_pointNotFound_throwsNotFound(@org.junit.jupiter.api.io.TempDir Path workingDirectory) {
        when(generatedPointRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService(workingDirectory).reviewPoint(9L, reviewRequest(true, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void reviewPoint_notPendingReview_throwsInvalidRequest(@org.junit.jupiter.api.io.TempDir Path workingDirectory) {
        PointAnnotationJob job = job(1L, "REVIEW", version(1L));
        LabelDefinition predicted = new LabelDefinition();
        predicted.setLabelCode("SAND");
        GeneratedPoint point = pendingReviewPoint(9L, job, predicted);
        point.setStatus("CONFIRMED");
        when(generatedPointRepository.findById(9L)).thenReturn(Optional.of(point));

        assertThatThrownBy(() -> newService(workingDirectory).reviewPoint(9L, reviewRequest(true, null)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void reviewPoint_override_requiresCorrectedLabelCode(@org.junit.jupiter.api.io.TempDir Path workingDirectory) {
        PointAnnotationJob job = job(1L, "REVIEW", version(1L));
        LabelDefinition predicted = new LabelDefinition();
        predicted.setLabelCode("SAND");
        GeneratedPoint point = pendingReviewPoint(9L, job, predicted);
        when(generatedPointRepository.findById(9L)).thenReturn(Optional.of(point));

        assertThatThrownBy(() -> newService(workingDirectory).reviewPoint(9L, reviewRequest(false, null)))
                .isInstanceOf(InvalidRequestException.class);
        verify(annotationPointRepository, never()).save(any());
    }

    @Test
    void reviewPoint_accept_confirmsPredictedLabelAndBridges(@org.junit.jupiter.api.io.TempDir Path workingDirectory) {
        PointAnnotationJob job = job(1L, "REVIEW", version(1L));
        LabelDefinition predicted = new LabelDefinition();
        predicted.setLabelCode("SAND");
        GeneratedPoint point = pendingReviewPoint(9L, job, predicted);
        when(generatedPointRepository.findById(9L)).thenReturn(Optional.of(point));
        when(generatedPointRepository.save(any(GeneratedPoint.class))).thenAnswer(inv -> inv.getArgument(0));
        when(annotationPointRepository.findByImageIdAndPointIndex(1L, 200)).thenReturn(Optional.empty());
        when(annotationPointRepository.save(any(AnnotationPoint.class))).thenAnswer(inv -> inv.getArgument(0));
        when(generatedPointRepository.countByJobIdAndSeedFalseAndStatus(1L, "MODEL_PREDICTED")).thenReturn(0L);
        when(pointAnnotationJobRepository.save(any(PointAnnotationJob.class))).thenAnswer(inv -> inv.getArgument(0));

        GeneratedPointResponse response = newService(workingDirectory).reviewPoint(9L, reviewRequest(true, null));

        assertThat(response.getStatus()).isEqualTo("CONFIRMED");
        assertThat(response.getFinalLabelCode()).isEqualTo("SAND");
        assertThat(job.getStatus()).isEqualTo("COMPLETE"); // last pending point resolved

        org.mockito.ArgumentCaptor<AnnotationPoint> captor = org.mockito.ArgumentCaptor.forClass(AnnotationPoint.class);
        verify(annotationPointRepository).save(captor.capture());
        assertThat(captor.getValue().getAnnotationSource()).isEqualTo("MODEL_PREDICTED");
        assertThat(captor.getValue().getAnnotatedBy()).isEqualTo("researcher_01");
    }

    @Test
    void reviewPoint_override_setsCorrectedLabelAndLogsHumanCorrection(@org.junit.jupiter.api.io.TempDir Path workingDirectory) {
        PointAnnotationJob job = job(1L, "REVIEW", version(1L));
        LabelDefinition predicted = new LabelDefinition();
        predicted.setLabelCode("SAND");
        LabelDefinition corrected = new LabelDefinition();
        corrected.setLabelCode("CCA");
        GeneratedPoint point = pendingReviewPoint(9L, job, predicted);
        when(generatedPointRepository.findById(9L)).thenReturn(Optional.of(point));
        when(labelDefinitionRepository.findByDatasetVersionIdAndLabelCode(1L, "CCA")).thenReturn(Optional.of(corrected));
        when(generatedPointRepository.save(any(GeneratedPoint.class))).thenAnswer(inv -> inv.getArgument(0));
        when(annotationPointRepository.findByImageIdAndPointIndex(1L, 200)).thenReturn(Optional.empty());
        when(annotationPointRepository.save(any(AnnotationPoint.class))).thenAnswer(inv -> inv.getArgument(0));
        when(generatedPointRepository.countByJobIdAndSeedFalseAndStatus(1L, "MODEL_PREDICTED")).thenReturn(2L);

        GeneratedPointResponse response = newService(workingDirectory).reviewPoint(9L, reviewRequest(false, "CCA"));

        assertThat(response.getFinalLabelCode()).isEqualTo("CCA");
        assertThat(job.getStatus()).isEqualTo("REVIEW"); // still points pending, stays REVIEW

        org.mockito.ArgumentCaptor<AnnotationPoint> captor = org.mockito.ArgumentCaptor.forClass(AnnotationPoint.class);
        verify(annotationPointRepository).save(captor.capture());
        assertThat(captor.getValue().getAnnotationSource()).isEqualTo("MODEL_CORRECTED");
        assertThat(captor.getValue().getConfidenceScore()).isNull();
        verify(annotationEventLoggerService).log(eq(EventType.HUMAN_CORRECTION), any(), eq("researcher_01"), any());
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
