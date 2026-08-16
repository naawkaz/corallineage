package com.corallineage.backend.training;

import com.corallineage.backend.datasets.Dataset;
import com.corallineage.backend.logging.AnnotationEventLoggerService;
import com.corallineage.backend.logging.EventType;
import com.corallineage.backend.registry.model.ModelVersion;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Calls executeTrainingAsync directly (bypassing the real @Async proxy, same synchronous-test-double
 * pattern already used for DatasetImportExecutorTest). All job-status persistence has moved to
 * TrainingJobStatusService (mocked here), which is what actually fixes the UnexpectedRollbackException
 * bug this file's status-mocking cannot itself reproduce: that bug depended on Spring's real transactional
 * proxy behavior across nested beans (see TrainingJobExecutorIntegrationTest for a real-transaction
 * reproduction of the original failure and confirmation of the fix). This file still covers the
 * orchestration logic: which collaborator is called with which arguments on each path.
 */
@ExtendWith(MockitoExtension.class)
class TrainingJobExecutorTest {

    @Mock private TrainingJobStatusService trainingJobStatusService;
    @Mock private DatasetPreparationService datasetPreparationService;
    @Mock private PythonMlServiceClient pythonMlServiceClient;
    @Mock private AnnotationEventLoggerService annotationEventLoggerService;

    private TrainingJobExecutor newExecutor() {
        return new TrainingJobExecutor(trainingJobStatusService, datasetPreparationService,
                pythonMlServiceClient, annotationEventLoggerService);
    }

    private TrainingJob completedJob(Long id, ModelVersion model) {
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
        job.setStatus("COMPLETED");
        job.setHyperparameters("{\"learningRate\":0.001,\"batchSize\":32,\"epochs\":10}");
        job.setModelVersion(model);
        return job;
    }

    @Test
    void executeTrainingAsync_success_registersModelAndLogsEvent(@TempDir Path preparedDir) {
        TrainingJobStart start = new TrainingJobStart(3L, "efficientnet_b0",
                "{\"learningRate\":0.001,\"batchSize\":32,\"epochs\":10}");
        when(trainingJobStatusService.markRunning(7L)).thenReturn(start);
        when(datasetPreparationService.prepareImageFolderDataset(3L, 7L)).thenReturn(preparedDir);

        TrainResult result = new TrainResult("run-123", "weights.pt", new BigDecimal("0.85"),
                Map.of("perClass", List.of()), Map.of("0", "ACROPORA", "1", "SAND"));
        when(pythonMlServiceClient.train(eq("efficientnet_b0"), eq(preparedDir.toString()), any())).thenReturn(result);

        ModelVersion registered = new ModelVersion();
        registered.setId(9L);
        registered.setModelTag("model-v1");
        TrainingJob completed = completedJob(7L, registered);
        when(trainingJobStatusService.markCompleted(eq(7L), eq("run-123"), eq("weights.pt"),
                eq(new BigDecimal("0.85")), any(), any())).thenReturn(completed);

        newExecutor().executeTrainingAsync(7L);

        verify(trainingJobStatusService).markCompleted(eq(7L), eq("run-123"), eq("weights.pt"),
                eq(new BigDecimal("0.85")), any(), any());
        verify(trainingJobStatusService, never()).markFailed(anyLong(), any());
        verify(annotationEventLoggerService).log(eq(EventType.MODEL_TRAINED), eq(completed.getDatasetVersion()),
                eq(registered), eq("researcher_01"), any());
    }

    @Test
    void executeTrainingAsync_datasetPreparationFailure_marksJobFailedWithRealMessage() {
        TrainingJobStart start = new TrainingJobStart(3L, "efficientnet_b0",
                "{\"learningRate\":0.001,\"batchSize\":32,\"epochs\":10}");
        when(trainingJobStatusService.markRunning(8L)).thenReturn(start);
        when(datasetPreparationService.prepareImageFolderDataset(anyLong(), anyLong()))
                .thenThrow(new DatasetPreparationException("Need at least 2 distinct label classes to train, found 1"));

        newExecutor().executeTrainingAsync(8L);

        verify(trainingJobStatusService).markFailed(eq(8L),
                eq("Need at least 2 distinct label classes to train, found 1"));
        verify(trainingJobStatusService, never()).markCompleted(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void executeTrainingAsync_noAnnotationPoints_marksJobFailedWithRealMessage() {
        TrainingJobStart start = new TrainingJobStart(25L, "efficientnet_b0",
                "{\"learningRate\":0.001,\"batchSize\":32,\"epochs\":10}");
        when(trainingJobStatusService.markRunning(6L)).thenReturn(start);
        when(datasetPreparationService.prepareImageFolderDataset(25L, 6L))
                .thenThrow(new DatasetPreparationException("Dataset version has no annotation points to train on"));

        newExecutor().executeTrainingAsync(6L);

        verify(trainingJobStatusService).markFailed(6L, "Dataset version has no annotation points to train on");
    }

    @Test
    void executeTrainingAsync_mlServiceUnreachable_marksJobFailed(@TempDir Path preparedDir) {
        TrainingJobStart start = new TrainingJobStart(3L, "efficientnet_b0",
                "{\"learningRate\":0.001,\"batchSize\":32,\"epochs\":10}");
        when(trainingJobStatusService.markRunning(9L)).thenReturn(start);
        when(datasetPreparationService.prepareImageFolderDataset(3L, 9L)).thenReturn(preparedDir);
        when(pythonMlServiceClient.train(any(), any(), any()))
                .thenThrow(new MlServiceException("Python ML service is unreachable"));

        newExecutor().executeTrainingAsync(9L);

        verify(trainingJobStatusService).markFailed(9L, "Python ML service is unreachable");
        verify(trainingJobStatusService, never()).markCompleted(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void executeTrainingAsync_exceptionWithNullMessage_marksJobFailedWithClassName(@TempDir Path preparedDir) {
        TrainingJobStart start = new TrainingJobStart(3L, "efficientnet_b0",
                "{\"learningRate\":0.001,\"batchSize\":32,\"epochs\":10}");
        when(trainingJobStatusService.markRunning(10L)).thenReturn(start);
        when(datasetPreparationService.prepareImageFolderDataset(3L, 10L)).thenReturn(preparedDir);
        when(pythonMlServiceClient.train(any(), any(), any())).thenThrow(new NullPointerException());

        newExecutor().executeTrainingAsync(10L);

        verify(trainingJobStatusService).markFailed(10L, "NullPointerException");
    }
}
