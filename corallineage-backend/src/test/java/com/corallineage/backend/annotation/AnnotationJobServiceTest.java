package com.corallineage.backend.annotation;

import com.corallineage.backend.exception.InvalidRequestException;
import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.logging.AnnotationEventLoggerService;
import com.corallineage.backend.logging.EventType;
import com.corallineage.backend.registry.model.ModelVersion;
import com.corallineage.backend.registry.model.ModelVersionRepository;
import com.corallineage.backend.versioning.dataset.Image;
import com.corallineage.backend.versioning.dataset.ImageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    private final AnnotationJobMapper annotationJobMapper = new AnnotationJobMapper();

    private AnnotationJobService newService() {
        return new AnnotationJobService(annotationJobRepository, annotationPredictionRepository,
                modelVersionRepository, imageRepository, annotationJobMapper, annotationJobExecutor,
                annotationEventLoggerService);
    }

    private ModelVersion model(Long id, String status) {
        ModelVersion m = new ModelVersion();
        m.setId(id);
        m.setModelTag("model-v" + id);
        m.setStatus(status);
        return m;
    }

    private Image image(Long id) {
        Image img = new Image();
        img.setId(id);
        img.setFilename("img" + id + ".jpg");
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

        assertThatThrownBy(() -> newService().submitAnnotationJob(request(99L, List.of(1L))))
                .isInstanceOf(NotFoundException.class);
        verify(annotationJobExecutor, never()).executeAnnotationAsync(any());
    }

    @Test
    void submitAnnotationJob_modelNotActive_throwsInvalidRequest() {
        when(modelVersionRepository.findById(1L)).thenReturn(Optional.of(model(1L, "ARCHIVED")));

        assertThatThrownBy(() -> newService().submitAnnotationJob(request(1L, List.of(1L))))
                .isInstanceOf(InvalidRequestException.class);
        verify(annotationJobExecutor, never()).executeAnnotationAsync(any());
    }

    @Test
    void submitAnnotationJob_someImagesNotFound_throwsNotFound() {
        when(modelVersionRepository.findById(1L)).thenReturn(Optional.of(model(1L, "ACTIVE")));
        when(imageRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(image(1L)));

        assertThatThrownBy(() -> newService().submitAnnotationJob(request(1L, List.of(1L, 2L))))
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

        AnnotationJobResponse response = newService().submitAnnotationJob(request(1L, List.of(1L, 2L)));

        assertThat(response.getJobId()).isEqualTo(5L);
        assertThat(response.getStatus()).isEqualTo("RUNNING");
        assertThat(response.getModelTag()).isEqualTo("model-v1");
        assertThat(response.getImageCount()).isEqualTo(2);
        verify(annotationJobExecutor).executeAnnotationAsync(5L);
        verify(annotationEventLoggerService).log(eq(EventType.ANNOTATION_JOB_STARTED), any(), eq(model), any(), any());
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
