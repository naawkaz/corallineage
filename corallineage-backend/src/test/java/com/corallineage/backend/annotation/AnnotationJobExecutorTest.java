package com.corallineage.backend.annotation;

import com.corallineage.backend.datasets.DatasetRepository;
import com.corallineage.backend.logging.AnnotationEventLoggerService;
import com.corallineage.backend.registry.model.ModelVersion;
import com.corallineage.backend.training.AnnotateResult;
import com.corallineage.backend.training.MlServiceException;
import com.corallineage.backend.training.PythonMlServiceClient;
import com.corallineage.backend.versioning.dataset.AnnotationPointRepository;
import com.corallineage.backend.versioning.dataset.DatasetVersionImageRepository;
import com.corallineage.backend.versioning.dataset.DatasetVersionRepository;
import com.corallineage.backend.versioning.dataset.DatasetVersionService;
import com.corallineage.backend.versioning.dataset.Image;
import com.corallineage.backend.versioning.dataset.ImageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Calls executeAnnotationAsync directly (bypassing the real @Async proxy), same synchronous-test-double
 * pattern already used for TrainingJobExecutorTest/DatasetImportExecutorTest.
 */
@ExtendWith(MockitoExtension.class)
class AnnotationJobExecutorTest {

    @Mock private AnnotationJobRepository annotationJobRepository;
    @Mock private AnnotationPredictionRepository annotationPredictionRepository;
    @Mock private ImageRepository imageRepository;
    @Mock private AnnotationPointRepository annotationPointRepository;
    @Mock private DatasetRepository datasetRepository;
    @Mock private DatasetVersionRepository datasetVersionRepository;
    @Mock private DatasetVersionImageRepository datasetVersionImageRepository;
    @Mock private DatasetVersionService datasetVersionService;
    @Mock private PythonMlServiceClient pythonMlServiceClient;
    @Mock private AnnotationEventLoggerService annotationEventLoggerService;

    private AnnotationJobExecutor newExecutor(Path workingDirectory) {
        return new AnnotationJobExecutor(annotationJobRepository, annotationPredictionRepository,
                imageRepository, annotationPointRepository, datasetRepository, datasetVersionRepository,
                datasetVersionImageRepository, datasetVersionService, pythonMlServiceClient,
                annotationEventLoggerService, workingDirectory, "dataset", 64);
    }

    private ModelVersion model() {
        ModelVersion m = new ModelVersion();
        m.setId(1L);
        m.setModelTag("model-v1");
        m.setArchitecture("efficientnet_b0");
        m.setWeightsPath("weights.pt");
        m.setClassMappings("{\"0\":\"ACROPORA\",\"1\":\"SAND\"}");
        m.setStatus("ACTIVE");
        return m;
    }

    private Image image(Long id, String filePath) {
        Image img = new Image();
        img.setId(id);
        img.setFilename("img" + id + ".jpg");
        img.setFilePath(filePath);
        return img;
    }

    private AnnotationJob job(Long id, ModelVersion model, List<Long> imageIds) {
        AnnotationJob job = new AnnotationJob();
        job.setId(id);
        job.setModelVersion(model);
        job.setInitiatedBy("researcher_01");
        job.setStatus("RUNNING");
        job.setReviewStatus("PENDING");
        job.setImageIds(imageIds);
        return job;
    }

    @Test
    void executeAnnotationAsync_success_persistsPredictionsAndCompletesJob(@TempDir Path workingDir) {
        ModelVersion model = model();
        AnnotationJob job = job(7L, model, List.of(1L, 2L));
        when(annotationJobRepository.findById(7L)).thenReturn(Optional.of(job));
        when(imageRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(image(1L, "img1.jpg"), image(2L, "img2.jpg")));

        AnnotateResult result = new AnnotateResult(List.of(
                new AnnotateResult.AnnotatedImage(1L, List.of(
                        new AnnotateResult.AnnotatedPoint(0, "ACROPORA", new BigDecimal("0.92")))),
                new AnnotateResult.AnnotatedImage(2L, List.of(
                        new AnnotateResult.AnnotatedPoint(0, "SAND", new BigDecimal("0.61"))))));
        when(pythonMlServiceClient.annotate(eq("efficientnet_b0"), eq("weights.pt"), any(), any())).thenReturn(result);

        newExecutor(workingDir).executeAnnotationAsync(7L);

        assertThat(job.getStatus()).isEqualTo("COMPLETED");
        assertThat(job.getCompletedAt()).isNotNull();
        verify(annotationPredictionRepository).saveAll(argThat((List<AnnotationPrediction> list) -> list.size() == 2));
    }

    @Test
    void executeAnnotationAsync_mlServiceUnreachable_marksJobFailed(@TempDir Path workingDir) {
        ModelVersion model = model();
        AnnotationJob job = job(8L, model, List.of(1L));
        when(annotationJobRepository.findById(8L)).thenReturn(Optional.of(job));
        when(imageRepository.findAllById(List.of(1L))).thenReturn(List.of(image(1L, "img1.jpg")));
        when(pythonMlServiceClient.annotate(any(), any(), any(), any()))
                .thenThrow(new MlServiceException("Python ML service is unreachable"));

        newExecutor(workingDir).executeAnnotationAsync(8L);

        assertThat(job.getStatus()).isEqualTo("FAILED");
        assertThat(job.getErrorMessage()).contains("unreachable");
        assertThat(job.getCompletedAt()).isNotNull();
    }
}
