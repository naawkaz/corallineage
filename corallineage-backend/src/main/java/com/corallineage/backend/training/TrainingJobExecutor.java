package com.corallineage.backend.training;

import com.corallineage.backend.logging.AnnotationEventLoggerService;
import com.corallineage.backend.logging.EventType;
import com.corallineage.backend.pointannotation.PointAnnotationJobService;
import com.corallineage.backend.registry.model.MlflowDetailsResponse;
import com.corallineage.backend.registry.model.MlflowIntegrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Runs a training job's background execution and polls MLflow for live epoch progress */
@Service
public class TrainingJobExecutor {

    private static final long POLL_INTERVAL_MILLIS = 2000;
    private static final Logger log = LoggerFactory.getLogger(TrainingJobExecutor.class);

    private final TrainingJobStatusService trainingJobStatusService;
    private final DatasetPreparationService datasetPreparationService;
    private final PythonMlServiceClient pythonMlServiceClient;
    private final MlflowIntegrationService mlflowIntegrationService;
    private final AnnotationEventLoggerService annotationEventLoggerService;
    private final PointAnnotationJobService pointAnnotationJobService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TrainingJobExecutor(TrainingJobStatusService trainingJobStatusService,
                               DatasetPreparationService datasetPreparationService,
                               PythonMlServiceClient pythonMlServiceClient,
                               MlflowIntegrationService mlflowIntegrationService,
                               AnnotationEventLoggerService annotationEventLoggerService,
                               @Lazy PointAnnotationJobService pointAnnotationJobService) {
        this.trainingJobStatusService = trainingJobStatusService;
        this.datasetPreparationService = datasetPreparationService;
        this.pythonMlServiceClient = pythonMlServiceClient;
        this.mlflowIntegrationService = mlflowIntegrationService;
        this.annotationEventLoggerService = annotationEventLoggerService;
        this.pointAnnotationJobService = pointAnnotationJobService;
    }

    @Async
    public void executeTrainingAsync(Long jobId) {
        Path preparedDir = null;
        try {
            TrainingJobStart start = trainingJobStatusService.markRunning(jobId);

            preparedDir = datasetPreparationService.prepareImageFolderDataset(start.datasetVersionId(), jobId);

            @SuppressWarnings("unchecked")
            Map<String, Object> hyperparameters = objectMapper.readValue(start.hyperparametersJson(), Map.class);
            int totalEpochs = readEpochs(hyperparameters);

            pythonMlServiceClient.startTraining(start.architecture(), preparedDir.toString(), hyperparameters, jobId);

            String seenMlflowRunId = null;
            PythonMlServiceClient.TrainingStatus status;
            while (true) {
                status = pythonMlServiceClient.pollTrainingStatus(jobId);

                if (status.mlflowRunId() != null && !status.mlflowRunId().equals(seenMlflowRunId)) {
                    seenMlflowRunId = status.mlflowRunId();
                    trainingJobStatusService.updateMlflowRunId(jobId, seenMlflowRunId);
                }
                if (seenMlflowRunId != null) {
                    updateEpochProgressFromMlflow(jobId, seenMlflowRunId, totalEpochs);
                }

                if ("COMPLETED".equals(status.status()) || "FAILED".equals(status.status())) {
                    break;
                }
                Thread.sleep(POLL_INTERVAL_MILLIS);
            }

            if ("FAILED".equals(status.status())) {
                markFailedAndRevertPointJob(jobId, describeFailure(status.error()));
                return;
            }

            TrainResult result = status.result();
            String performanceMetricsJson = objectMapper.writeValueAsString(result.performanceMetrics());
            String classMappingsJson = objectMapper.writeValueAsString(result.classMappings());

            TrainingJob completed = trainingJobStatusService.markCompleted(jobId, result.mlflowRunId(),
                    result.weightsPath(), result.accuracy(), performanceMetricsJson, classMappingsJson);

            annotationEventLoggerService.log(EventType.MODEL_TRAINED, completed.getDatasetVersion(),
                    completed.getModelVersion(), completed.getInitiatedBy(),
                    "Training completed, registered " + completed.getModelVersion().getModelTag());

            try {
                pointAnnotationJobService.autoAnnotateAfterTraining(jobId);
            } catch (Exception ex) {
                // Training already succeeded, a failure delegating into auto annotate is not a training failure
                log.error("Failed to auto-delegate into auto-annotate after training job {} completed", jobId, ex);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            markFailedAndRevertPointJob(jobId, "Interrupted while waiting for training to complete");
        } catch (Exception ex) {
            markFailedAndRevertPointJob(jobId, describeFailure(ex.getMessage(), ex));
        } finally {
            if (preparedDir != null) {
                deleteRecursivelyQuietly(preparedDir);
            }
        }
    }

    /** Marks the job failed and reverts a linked point annotation job to seed labeling */
    private void markFailedAndRevertPointJob(Long jobId, String message) {
        trainingJobStatusService.markFailed(jobId, message);
        try {
            pointAnnotationJobService.revertAfterTrainingFailure(jobId);
        } catch (Exception ex) {
            log.error("Failed to revert point-annotation job to SEED_LABELING after training job {} failed", jobId, ex);
        }
    }

    /** Reads live epoch metrics from MLflow and persists the current epoch */
    private void updateEpochProgressFromMlflow(Long jobId, String mlflowRunId, int totalEpochs) {
        try {
            List<MlflowDetailsResponse.EpochMetricPoint> points = mlflowIntegrationService.getEpochMetrics(mlflowRunId);
            if (points == null || points.isEmpty()) {
                return;
            }
            int currentEpoch = points.get(points.size() - 1).epoch() + 1;
            trainingJobStatusService.updateProgress(jobId, currentEpoch, totalEpochs);
        } catch (Exception ignored) {
            // best-effort progress polling, never allowed to interrupt the training job
        }
    }

    private int readEpochs(Map<String, Object> hyperparameters) {
        Object epochs = hyperparameters.get("epochs");
        return epochs instanceof Number number ? number.intValue() : 10;
    }

    /** Falls back to a generic error message when none is reported */
    private String describeFailure(String pythonError) {
        return (pythonError == null || pythonError.isBlank()) ? "Training failed" : pythonError;
    }

    /** Falls back to the exception class name when no message exists */
    private String describeFailure(String message, Exception ex) {
        return (message == null || message.isBlank()) ? ex.getClass().getSimpleName() : message;
    }

    private void deleteRecursivelyQuietly(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort temp directory cleanup
                }
            });
        } catch (IOException ignored) {
            // best-effort temp directory cleanup
        }
    }
}
