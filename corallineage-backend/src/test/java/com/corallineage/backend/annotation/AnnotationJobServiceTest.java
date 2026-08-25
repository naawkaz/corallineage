package com.corallineage.backend.annotation;

import com.corallineage.backend.datasets.Dataset;
import com.corallineage.backend.datasets.DatasetService;
import com.corallineage.backend.exception.ForbiddenException;
import com.corallineage.backend.exception.InvalidRequestException;
import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.logging.AnnotationEventLoggerService;
import com.corallineage.backend.logging.EventType;
import com.corallineage.backend.registry.model.ModelVersion;
import com.corallineage.backend.registry.model.ModelVersionRepository;
import com.corallineage.backend.versioning.dataset.AnnotationPointRepository;
import com.corallineage.backend.versioning.dataset.DatasetVersion;
import com.corallineage.backend.versioning.dataset.DatasetVersionImageRepository;
import com.corallineage.backend.versioning.dataset.DatasetVersionRepository;
import com.corallineage.backend.versioning.dataset.DatasetVersionService;
import com.corallineage.backend.versioning.dataset.Image;
import com.corallineage.backend.versioning.dataset.ImageRepository;
import com.corallineage.backend.versioning.dataset.LabelDefinitionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnotationJobServiceTest {

    @Mock private AnnotationJobRepository annotationJobRepository;
    @Mock private AnnotationPredictionRepository annotationPredictionRepository;
    @Mock private ModelVersionRepository modelVersionRepository;
    @Mock private ImageRepository imageRepository;
    @Mock private AnnotationJobExecutor annotationJobExecutor;
    @Mock private AnnotationEventLoggerService annotationEventLoggerService;
    @Mock private DatasetVersionRepository datasetVersionRepository;
    @Mock private DatasetVersionImageRepository datasetVersionImageRepository;
    @Mock private AnnotationPointRepository annotationPointRepository;
    @Mock private LabelDefinitionRepository labelDefinitionRepository;
    @Mock private DatasetVersionService datasetVersionService;
    @Mock private DatasetService datasetService;

    private final AnnotationJobMapper annotationJobMapper = new AnnotationJobMapper();

    private AnnotationJobService newService() {
        return new AnnotationJobService(annotationJobRepository, annotationPredictionRepository,
                modelVersionRepository, imageRepository, annotationJobMapper, annotationJobExecutor,
                annotationEventLoggerService, datasetVersionRepository, datasetVersionImageRepository,
                annotationPointRepository, labelDefinitionRepository, datasetVersionService, datasetService);
    }

    private ModelVersion model(Long id, String status) {
        ModelVersion m = new ModelVersion();
        m.setId(id);
        m.setModelTag("model-v" + id);
        m.setStatus(status);
        return m;
    }

    private Image image(Long id) {
        return image(id, 1L);
    }

    private Image image(Long id, Long datasetId) {
        Image img = new Image();
        img.setId(id);
        img.setFilename("img" + id + ".jpg");
        Dataset dataset = new Dataset();
        dataset.setId(datasetId);
        DatasetVersion version = new DatasetVersion();
        version.setId(datasetId);
        version.setDataset(dataset);
        img.setOriginDatasetVersion(version);
        return img;
    }

    private InitiateAnnotationJobRequest request(Long modelVersionId, List<Long> imageIds) {
        InitiateAnnotationJobRequest req = new InitiateAnnotationJobRequest();
        req.setModelVersionId(modelVersionId);
        req.setImageIds(imageIds);
        req.setInitiatedBy("researcher_01");
        return req;
    }

    @Test
    void submitAnnotationJob_modelNotFound_throwsNotFound() {
        when(modelVersionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().submitAnnotationJob(request(99L, List.of(1L)), "researcher_01"))
                .isInstanceOf(NotFoundException.class);
        verify(annotationJobExecutor, never()).executeAnnotationAsync(any());
    }

    @Test
    void submitAnnotationJob_modelNotActive_throwsInvalidRequest() {
        when(modelVersionRepository.findById(1L)).thenReturn(Optional.of(model(1L, "ARCHIVED")));

        assertThatThrownBy(() -> newService().submitAnnotationJob(request(1L, List.of(1L)), "researcher_01"))
                .isInstanceOf(InvalidRequestException.class);
        verify(annotationJobExecutor, never()).executeAnnotationAsync(any());
    }

    @Test
    void submitAnnotationJob_someImagesNotFound_throwsNotFound() {
        when(modelVersionRepository.findById(1L)).thenReturn(Optional.of(model(1L, "ACTIVE")));
        when(imageRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(image(1L)));

        assertThatThrownBy(() -> newService().submitAnnotationJob(request(1L, List.of(1L, 2L)), "researcher_01"))
                .isInstanceOf(NotFoundException.class);
        verify(annotationJobExecutor, never()).executeAnnotationAsync(any());
    }

    @Test
    void submitAnnotationJob_success_dispatchesAndLogsEvent() {
        ModelVersion model = model(1L, "ACTIVE");
        when(modelVersionRepository.findById(1L)).thenReturn(Optional.of(model));
        when(imageRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(image(1L), image(2L)));
        when(annotationJobRepository.save(any(AnnotationJob.class))).thenAnswer(inv -> {
            AnnotationJob job = inv.getArgument(0);
            job.setId(5L);
            return job;
        });

        AnnotationJobResponse response = newService().submitAnnotationJob(request(1L, List.of(1L, 2L)), "researcher_01");

        assertThat(response.getJobId()).isEqualTo(5L);
        assertThat(response.getStatus()).isEqualTo("RUNNING");
        assertThat(response.getModelTag()).isEqualTo("model-v1");
        assertThat(response.getImageCount()).isEqualTo(2);
        verify(annotationJobExecutor).executeAnnotationAsync(5L);
        verify(annotationEventLoggerService).log(eq(EventType.ANNOTATION_JOB_STARTED), any(), eq(model), any(), any());
    }

    @Test
    void submitAnnotationJob_success_derivesDatasetFromFirstImage() {
        ModelVersion model = model(1L, "ACTIVE");
        when(modelVersionRepository.findById(1L)).thenReturn(Optional.of(model));
        when(imageRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(image(1L, 42L), image(2L, 42L)));
        when(annotationJobRepository.save(any(AnnotationJob.class))).thenAnswer(inv -> {
            AnnotationJob job = inv.getArgument(0);
            job.setId(5L);
            return job;
        });

        newService().submitAnnotationJob(request(1L, List.of(1L, 2L)), "researcher_01");

        org.mockito.ArgumentCaptor<AnnotationJob> captor = org.mockito.ArgumentCaptor.forClass(AnnotationJob.class);
        verify(annotationJobRepository).save(captor.capture());
        assertThat(captor.getValue().getDataset().getId()).isEqualTo(42L);
    }

    @Test
    void submitAnnotationJob_actingUserNotProjectMember_throwsForbidden() {
        ModelVersion model = model(1L, "ACTIVE");
        when(modelVersionRepository.findById(1L)).thenReturn(Optional.of(model));
        when(imageRepository.findAllById(List.of(1L))).thenReturn(List.of(image(1L, 42L)));
        org.mockito.Mockito.doThrow(new ForbiddenException("You are not a member of the project that owns this dataset"))
                .when(datasetService).requireAccess(42L, "outsider");

        assertThatThrownBy(() -> newService().submitAnnotationJob(request(1L, List.of(1L)), "outsider"))
                .isInstanceOf(ForbiddenException.class);
        verify(annotationJobRepository, never()).save(any());
        verify(annotationJobExecutor, never()).executeAnnotationAsync(any());
    }

    @Test
    void submitValidationJob_actingUserNotProjectMember_throwsForbidden() {
        when(modelVersionRepository.findById(1L)).thenReturn(Optional.of(model(1L, "ACTIVE")));
        DatasetVersion version = new DatasetVersion();
        version.setId(10L);
        Dataset dataset = new Dataset();
        dataset.setId(42L);
        version.setDataset(dataset);
        when(datasetVersionRepository.findById(10L)).thenReturn(Optional.of(version));
        org.mockito.Mockito.doThrow(new ForbiddenException("You are not a member of the project that owns this dataset"))
                .when(datasetService).requireAccess(42L, "outsider");

        assertThatThrownBy(() -> newService().submitValidationJob(validationRequest(1L, 10L, List.of(1L)), "outsider"))
                .isInstanceOf(ForbiddenException.class);
        verify(annotationJobRepository, never()).save(any());
    }

    @Test
    void submitAnnotationJob_imagesFromDifferentDatasets_throwsInvalidRequest() {
        ModelVersion model = model(1L, "ACTIVE");
        when(modelVersionRepository.findById(1L)).thenReturn(Optional.of(model));
        when(imageRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(image(1L, 42L), image(2L, 43L)));

        assertThatThrownBy(() -> newService().submitAnnotationJob(request(1L, List.of(1L, 2L)), "researcher_01"))
                .isInstanceOf(InvalidRequestException.class);
        verify(annotationJobRepository, never()).save(any());
        verify(annotationJobExecutor, never()).executeAnnotationAsync(any());
    }

    @Test
    void getJobs_scopesToDataset() {
        Pageable pageable = PageRequest.of(0, 20);
        when(annotationJobRepository.findByDataset_IdOrderByInitiatedAtDesc(42L, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        Page<AnnotationJobResponse> result = newService().getJobs(42L, pageable);

        assertThat(result.getContent()).isEmpty();
        verify(annotationJobRepository).findByDataset_IdOrderByInitiatedAtDesc(42L, pageable);
    }

    @Test
    void getJobById_notFound_throwsNotFound() {
        when(annotationJobRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().getJobById(99L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void reviewPrediction_override_requiresCorrectedLabelCode() {
        AnnotationJob job = new AnnotationJob();
        job.setId(1L);
        job.setModelVersion(model(1L, "ACTIVE"));
        when(annotationJobRepository.findById(1L)).thenReturn(Optional.of(job));

        AnnotationPrediction prediction = new AnnotationPrediction();
        prediction.setId(10L);
        prediction.setAnnotationJob(job);
        prediction.setImage(image(1L));
        prediction.setPointIndex(0);
        prediction.setPredictedLabelCode("SAND");
        prediction.setConfidenceScore(new BigDecimal("0.5"));
        when(annotationPredictionRepository.findById(10L)).thenReturn(Optional.of(prediction));

        ReviewPredictionRequest request = new ReviewPredictionRequest();
        request.setAccepted(false);
        request.setReviewedBy("researcher_01");

        assertThatThrownBy(() -> newService().reviewPrediction(1L, 10L, request))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void reviewPrediction_override_logsHumanCorrectionAndUpdatesReviewStatus() {
        AnnotationJob job = new AnnotationJob();
        job.setId(1L);
        job.setModelVersion(model(1L, "ACTIVE"));
        job.setReviewStatus("PENDING");
        when(annotationJobRepository.findById(1L)).thenReturn(Optional.of(job));

        AnnotationPrediction prediction = new AnnotationPrediction();
        prediction.setId(10L);
        prediction.setAnnotationJob(job);
        prediction.setImage(image(1L));
        prediction.setPointIndex(0);
        prediction.setPredictedLabelCode("SAND");
        prediction.setConfidenceScore(new BigDecimal("0.5"));
        when(annotationPredictionRepository.findById(10L)).thenReturn(Optional.of(prediction));
        when(annotationPredictionRepository.save(any(AnnotationPrediction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(annotationPredictionRepository.countByAnnotationJobId(1L)).thenReturn(1L);
        when(annotationPredictionRepository.countByAnnotationJobIdAndAcceptedIsNotNull(1L)).thenReturn(1L);

        ReviewPredictionRequest request = new ReviewPredictionRequest();
        request.setAccepted(false);
        request.setCorrectedLabelCode("CCA");
        request.setReviewedBy("researcher_01");

        AnnotationPredictionResponse response = newService().reviewPrediction(1L, 10L, request);

        assertThat(response.getCorrectedLabelCode()).isEqualTo("CCA");
        assertThat(response.getAccepted()).isFalse();
        assertThat(job.getReviewStatus()).isEqualTo("COMPLETED");
        verify(annotationEventLoggerService).log(eq(EventType.HUMAN_CORRECTION), any(), eq(job.getModelVersion()), any(), any());
    }

    @Test
    void acceptAllPredictions_validationJob_throwsInvalidRequest() {
        AnnotationJob job = new AnnotationJob();
        job.setId(1L);
        job.setJobType("VALIDATION");
        when(annotationJobRepository.findById(1L)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> newService().acceptAllPredictions(1L, "researcher_01"))
                .isInstanceOf(InvalidRequestException.class);
        verify(annotationPredictionRepository, never()).saveAll(any());
    }

    @Test
    void acceptAllPredictions_bulkAcceptsEveryPendingPredictionAndUpdatesReviewStatus() {
        AnnotationJob job = new AnnotationJob();
        job.setId(1L);
        job.setJobType("NEW_IMAGES");
        job.setReviewStatus("PENDING");
        when(annotationJobRepository.findById(1L)).thenReturn(Optional.of(job));

        AnnotationPrediction pending1 = new AnnotationPrediction();
        pending1.setId(10L);
        pending1.setPredictedLabelCode("SAND");
        AnnotationPrediction pending2 = new AnnotationPrediction();
        pending2.setId(11L);
        pending2.setPredictedLabelCode("CCA");
        when(annotationPredictionRepository.findByAnnotationJobIdAndAcceptedIsNull(1L))
                .thenReturn(List.of(pending1, pending2));
        when(annotationPredictionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(annotationPredictionRepository.countByAnnotationJobId(1L)).thenReturn(2L);
        when(annotationPredictionRepository.countByAnnotationJobIdAndAcceptedIsNotNull(1L)).thenReturn(2L);

        AnnotationJobResponse response = newService().acceptAllPredictions(1L, "researcher_01");

        assertThat(pending1.getAccepted()).isTrue();
        assertThat(pending1.getReviewedBy()).isEqualTo("researcher_01");
        assertThat(pending1.getReviewedAt()).isNotNull();
        assertThat(pending2.getAccepted()).isTrue();
        assertThat(job.getReviewStatus()).isEqualTo("COMPLETED");
        assertThat(response.getReviewedPredictions()).isEqualTo(2);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<AnnotationPrediction>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(annotationPredictionRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).containsExactly(pending1, pending2);
    }

    private SubmitValidationJobRequest validationRequest(Long modelVersionId, Long datasetVersionId, List<Long> imageIds) {
        SubmitValidationJobRequest req = new SubmitValidationJobRequest();
        req.setModelVersionId(modelVersionId);
        req.setDatasetVersionId(datasetVersionId);
        req.setImageIds(imageIds);
        req.setInitiatedBy("researcher_01");
        return req;
    }

    @Test
    void submitValidationJob_datasetVersionNotFound_throwsNotFound() {
        when(modelVersionRepository.findById(1L)).thenReturn(Optional.of(model(1L, "ACTIVE")));
        when(datasetVersionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().submitValidationJob(validationRequest(1L, 99L, List.of(1L)), "researcher_01"))
                .isInstanceOf(NotFoundException.class);
        verify(annotationJobExecutor, never()).executeValidationAsync(any());
    }

    @Test
    void submitValidationJob_imageNotMemberOfVersion_throwsInvalidRequest() {
        when(modelVersionRepository.findById(1L)).thenReturn(Optional.of(model(1L, "ACTIVE")));
        DatasetVersion version = new DatasetVersion();
        version.setId(10L);
        Dataset dataset = new Dataset();
        dataset.setId(42L);
        version.setDataset(dataset);
        when(datasetVersionRepository.findById(10L)).thenReturn(Optional.of(version));
        when(imageRepository.findAllById(List.of(1L))).thenReturn(List.of(image(1L)));
        when(datasetVersionImageRepository.existsByDatasetVersionIdAndImageId(10L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> newService().submitValidationJob(validationRequest(1L, 10L, List.of(1L)), "researcher_01"))
                .isInstanceOf(InvalidRequestException.class);
        verify(annotationJobRepository, never()).save(any());
    }

    @Test
    void submitValidationJob_success_dispatchesValidationExecutor() {
        ModelVersion model = model(1L, "ACTIVE");
        when(modelVersionRepository.findById(1L)).thenReturn(Optional.of(model));
        DatasetVersion version = new DatasetVersion();
        version.setId(10L);
        Dataset dataset = new Dataset();
        dataset.setId(42L);
        version.setDataset(dataset);
        when(datasetVersionRepository.findById(10L)).thenReturn(Optional.of(version));
        when(imageRepository.findAllById(List.of(1L))).thenReturn(List.of(image(1L)));
        when(datasetVersionImageRepository.existsByDatasetVersionIdAndImageId(10L, 1L)).thenReturn(true);
        when(annotationJobRepository.save(any(AnnotationJob.class))).thenAnswer(inv -> {
            AnnotationJob job = inv.getArgument(0);
            job.setId(7L);
            return job;
        });

        AnnotationJobResponse response = newService().submitValidationJob(validationRequest(1L, 10L, List.of(1L)), "researcher_01");

        assertThat(response.getJobId()).isEqualTo(7L);
        assertThat(response.getJobType()).isEqualTo("VALIDATION");
        assertThat(response.getDatasetVersionId()).isEqualTo(10L);
        verify(annotationJobExecutor).executeValidationAsync(7L);
    }

    @Test
    void reviewPrediction_validationJob_throwsInvalidRequest() {
        AnnotationJob job = new AnnotationJob();
        job.setId(1L);
        job.setJobType("VALIDATION");
        when(annotationJobRepository.findById(1L)).thenReturn(Optional.of(job));

        ReviewPredictionRequest request = new ReviewPredictionRequest();
        request.setAccepted(true);
        request.setReviewedBy("researcher_01");

        assertThatThrownBy(() -> newService().reviewPrediction(1L, 10L, request))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void addToProject_jobNotNewImages_throwsInvalidRequest() {
        AnnotationJob job = new AnnotationJob();
        job.setId(1L);
        job.setJobType("VALIDATION");
        when(annotationJobRepository.findById(1L)).thenReturn(Optional.of(job));

        AddToProjectRequest request = new AddToProjectRequest();
        request.setVersionTag("v2.0");
        request.setCreatedBy("researcher_01");

        assertThatThrownBy(() -> newService().addToProject(1L, request))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void addToProject_reviewNotComplete_throwsInvalidRequest() {
        AnnotationJob job = new AnnotationJob();
        job.setId(1L);
        job.setJobType("NEW_IMAGES");
        job.setReviewStatus("IN_PROGRESS");
        when(annotationJobRepository.findById(1L)).thenReturn(Optional.of(job));

        AddToProjectRequest request = new AddToProjectRequest();
        request.setVersionTag("v2.0");
        request.setCreatedBy("researcher_01");

        assertThatThrownBy(() -> newService().addToProject(1L, request))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void addToProject_alreadyAdded_throwsConflict() {
        AnnotationJob job = new AnnotationJob();
        job.setId(1L);
        job.setJobType("NEW_IMAGES");
        job.setReviewStatus("COMPLETED");
        job.setAddedToProjectVersionId(99L);
        when(annotationJobRepository.findById(1L)).thenReturn(Optional.of(job));

        AddToProjectRequest request = new AddToProjectRequest();
        request.setVersionTag("v2.0");
        request.setCreatedBy("researcher_01");

        assertThatThrownBy(() -> newService().addToProject(1L, request))
                .isInstanceOf(com.corallineage.backend.exception.ConflictException.class);
    }

    @Test
    void reviewPrediction_wrongJob_throwsNotFound() {
        AnnotationJob job = new AnnotationJob();
        job.setId(1L);
        when(annotationJobRepository.findById(1L)).thenReturn(Optional.of(job));

        AnnotationJob otherJob = new AnnotationJob();
        otherJob.setId(2L);
        AnnotationPrediction prediction = new AnnotationPrediction();
        prediction.setId(10L);
        prediction.setAnnotationJob(otherJob);
        when(annotationPredictionRepository.findById(10L)).thenReturn(Optional.of(prediction));

        ReviewPredictionRequest request = new ReviewPredictionRequest();
        request.setAccepted(true);
        request.setReviewedBy("researcher_01");

        assertThatThrownBy(() -> newService().reviewPrediction(1L, 10L, request))
                .isInstanceOf(NotFoundException.class);
    }
}
