package com.corallineage.backend.training;

import com.corallineage.backend.datasets.Dataset;
import com.corallineage.backend.logging.AnnotationEventLoggerService;
import com.corallineage.backend.registry.model.ModelVersion;
import com.corallineage.backend.registry.model.ModelVersionService;
import com.corallineage.backend.versioning.dataset.DatasetVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Calls executeTrainingAsync directly (bypassing the real @Async proxy, same synchronous-test-double
 * pattern already used for DatasetImportExecutorTest) so results can be asserted without waiting on
 * real background execution.
 */
@ExtendWith(MockitoExtension.class)
class TrainingJobExecutorTest {

    @Mock private TrainingJobRepository trainingJobRepository;
    @Mock private DatasetPreparationService datasetPreparationService;
    @Mock private PythonMlServiceClient pythonMlServiceClient;
    @Mock private ModelVersionService modelVersionService;
    @Mock private AnnotationEventLoggerService annotationEventLoggerService;

    private TrainingJobExecutor newExecutor() {
        return new TrainingJobExecutor(trainingJobRepository, datasetPreparationService,
                pythonMlServiceClient, modelVersionService, annotationEventLoggerService);
    }

    private TrainingJob job(Long id) {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        DatasetVersion version = new DatasetVersion();
        version.setId(3L);
        version.setDataset(dataset);
        version.setVersionTag("v1.0");

        TrainingJob job = new TrainingJob();
        job.setId(id);
        job.setDatasetVersion(version);
        job.setArchitecture("efficientnet_b0");
        job.setInitiatedBy("researcher_01");
        job.setStatus("QUEUED");
        job.setHyperparameters("{\"learningRate\":0.001,\"batchSize\":32,\"epochs\":10}");
        return job;
    }

    @Test
    void executeTrainingAsync_success_registersModelAndCompletesJob(@TempDir Path preparedDir) {
        TrainingJob job = job(7L);
        when(trainingJobRepository.findById(7L)).thenReturn(Optional.of(job));
        when(datasetPreparationService.prepareImageFolderDataset(3L, 7L)).thenReturn(preparedDir);

        TrainResult result = new TrainResult("run-123", "weights.pt", new BigDecimal("0.85"),
                Map.of("perClass", List.of()), Map.of("0", "ACROPORA", "1", "SAND"));
        when(pythonMlServiceClient.train(eq("efficientnet_b0"), eq(preparedDir.toString()), any())).thenReturn(result);

        ModelVersion registered = new ModelVersion();
        registered.setId(9L);
        registered.setModelTag("model-v1");
        when(modelVersionService.registerFromTrainingResult(eq(job), eq("run-123"), eq("weights.pt"),
                eq(new BigDecimal("0.85")), any(), any())).thenReturn(registered);

        newExecutor().executeTrainingAsync(7L);

        assertThat(job.getStatus()).isEqualTo("COMPLETED");
        assertThat(job.getModelVersion()).isEqualTo(registered);
        assertThat(job.getCompletedAt()).isNotNull();
    }

    @Test
    void executeTrainingAsync_datasetPreparationFailure_marksJobFailed() {
        TrainingJob job = job(8L);
        when(trainingJobRepository.findById(8L)).thenReturn(Optional.of(job));
        when(datasetPreparationService.prepareImageFolderDataset(anyLong(), anyLong()))
                .thenThrow(new DatasetPreparationException("Need at least 2 distinct label classes to train, found 1"));

        newExecutor().executeTrainingAsync(8L);

        assertThat(job.getStatus()).isEqualTo("FAILED");
        assertThat(job.getErrorMessage()).contains("Need at least 2 distinct label classes");
        assertThat(job.getCompletedAt()).isNotNull();
    }

    @Test
    void executeTrainingAsync_mlServiceUnreachable_marksJobFailed(@TempDir Path preparedDir) {
        TrainingJob job = job(9L);
        when(trainingJobRepository.findById(9L)).thenReturn(Optional.of(job));
        when(datasetPreparationService.prepareImageFolderDataset(3L, 9L)).thenReturn(preparedDir);
        when(pythonMlServiceClient.train(any(), any(), any()))
                .thenThrow(new MlServiceException("Python ML service is unreachable"));

        newExecutor().executeTrainingAsync(9L);

        assertThat(job.getStatus()).isEqualTo("FAILED");
        assertThat(job.getErrorMessage()).contains("unreachable");
        assertThat(job.getModelVersion()).isNull();
    }
}
