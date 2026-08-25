package com.corallineage.backend.training;

import com.corallineage.backend.datasets.Dataset;
import com.corallineage.backend.logging.AnnotationEventLoggerService;
import com.corallineage.backend.logging.EventType;
import com.corallineage.backend.pointannotation.PointAnnotationJobService;
import com.corallineage.backend.registry.model.MlflowDetailsResponse;
import com.corallineage.backend.registry.model.MlflowIntegrationService;
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
 *
 * Training is now async on the Python side too (a background thread there, not a blocking HTTP call),
 * so pythonMlServiceClient.pollTrainingStatus is mocked to return RUNNING once and then a terminal
 * status, exercising the real poll loop (with its 2-second sleep) rather than stubbing it away, since
 * that loop is short-lived enough in these tests to run for real.
 */
@ExtendWith(MockitoExtension.class)
class TrainingJobExecutorTest {

    @Mock private TrainingJobStatusService trainingJobStatusService;
    @Mock private DatasetPreparationService datasetPreparationService;
    @Mock private PythonMlServiceClient pythonMlServiceClient;
    @Mock private MlflowIntegrationService mlflowIntegrationService;
    @Mock private AnnotationEventLoggerService annotationEventLoggerService;
    @Mock private PointAnnotationJobService pointAnnotationJobService;

    private TrainingJobExecutor newExecutor() {
        return new TrainingJobExecutor(trainingJobStatusService, datasetPreparationService,
                pythonMlServiceClient, mlflowIntegrationService, annotationEventLoggerService, pointAnnotationJobService);
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
        when(pythonMlServiceClient.pollTrainingStatus(7L))
                .thenReturn(new PythonMlServiceClient.TrainingStatus("RUNNING", "run-123", null, null))
                .thenReturn(new PythonMlServiceClient.TrainingStatus("COMPLETED", "run-123", result, null));
        when(mlflowIntegrationService.getEpochMetrics("run-123")).thenReturn(List.of());

        ModelVersion registered = new ModelVersion();
        registered.setId(9L);
        registered.setModelTag("model-v1");
        TrainingJob completed = completedJob(7L, registered);
        when(trainingJobStatusService.markCompleted(eq(7L), eq("run-123"), eq("weights.pt"),
                eq(new BigDecimal("0.85")), any(), any())).thenReturn(completed);

        newExecutor().executeTrainingAsync(7L);

        verify(pythonMlServiceClient).startTraining(eq("efficientnet_b0"), eq(preparedDir.toString()), any(), eq(7L));
        verify(trainingJobStatusService).updateMlflowRunId(7L, "run-123");
        verify(trainingJobStatusService).markCompleted(eq(7L), eq("run-123"), eq("weights.pt"),
                eq(new BigDecimal("0.85")), any(), any());
        verify(trainingJobStatusService, never()).markFailed(anyLong(), any());
        verify(annotationEventLoggerService).log(eq(EventType.MODEL_TRAINED), eq(completed.getDatasetVersion()),
                eq(registered), eq("researcher_01"), any());
    }

    @Test
    void executeTrainingAsync_success_updatesEpochProgressFromMlflow(@TempDir Path preparedDir) {
        TrainingJobStart start = new TrainingJobStart(3L, "efficientnet_b0",
                "{\"learningRate\":0.001,\"batchSize\":32,\"epochs\":10}");
        when(trainingJobStatusService.markRunning(11L)).thenReturn(start);
        when(datasetPreparationService.prepareImageFolderDataset(3L, 11L)).thenReturn(preparedDir);

        TrainResult result = new TrainResult("run-456", "weights.pt", new BigDecimal("0.9"),
                Map.of("perClass", List.of()), Map.of("0", "ACROPORA", "1", "SAND"));
        when(pythonMlServiceClient.pollTrainingStatus(11L))
                .thenReturn(new PythonMlServiceClient.TrainingStatus("RUNNING", "run-456", null, null))
                .thenReturn(new PythonMlServiceClient.TrainingStatus("COMPLETED", "run-456", result, null));
        // MLflow's step is 0-based (epoch 2 means "epoch 3 of 10" to a human).
        when(mlflowIntegrationService.getEpochMetrics("run-456"))
                .thenReturn(List.of(new MlflowDetailsResponse.EpochMetricPoint(2, 0.1, 0.2, 0.9, null, null)));

        ModelVersion registered = new ModelVersion();
        registered.setId(10L);
        registered.setModelTag("model-v2");
        when(trainingJobStatusService.markCompleted(eq(11L), any(), any(), any(), any(), any()))
                .thenReturn(completedJob(11L, registered));

        newExecutor().executeTrainingAsync(11L);

        // Called on every poll tick where the run id is known, including the terminal COMPLETED
        // tick, not just once, since MLflow's metric history is polled independently of job status.
        verify(trainingJobStatusService, org.mockito.Mockito.atLeastOnce()).updateProgress(11L, 3, 10);
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
        org.mockito.Mockito.doThrow(new MlServiceException("Python ML service is unreachable"))
                .when(pythonMlServiceClient).startTraining(any(), any(), any(), any());

        newExecutor().executeTrainingAsync(9L);

        verify(trainingJobStatusService).markFailed(9L, "Python ML service is unreachable");
        verify(trainingJobStatusService, never()).markCompleted(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void executeTrainingAsync_pythonJobFails_marksJobFailedWithPythonError(@TempDir Path preparedDir) {
        TrainingJobStart start = new TrainingJobStart(3L, "efficientnet_b0",
                "{\"learningRate\":0.001,\"batchSize\":32,\"epochs\":10}");
        when(trainingJobStatusService.markRunning(10L)).thenReturn(start);
        when(datasetPreparationService.prepareImageFolderDataset(3L, 10L)).thenReturn(preparedDir);
        when(pythonMlServiceClient.pollTrainingStatus(10L))
                .thenReturn(new PythonMlServiceClient.TrainingStatus("FAILED", null, null, "Need at least 2 classes to train, found 1"));

        newExecutor().executeTrainingAsync(10L);

        verify(trainingJobStatusService).markFailed(10L, "Need at least 2 classes to train, found 1");
        verify(trainingJobStatusService, never()).markCompleted(anyLong(), any(), any(), any(), any(), any());
    }
}
