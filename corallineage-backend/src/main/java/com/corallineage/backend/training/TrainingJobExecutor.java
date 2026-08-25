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

/**
 * Orchestrates a training job's background run. Deliberately holds no @Transactional boundary
 * of its own: every DB write is delegated to TrainingJobStatusService, each call opening its own
 * fresh Propagation.REQUIRES_NEW transaction. See TrainingJobStatusService's class comment for
 * the full history of why (a shared-transaction design here previously caused a real
 * UnexpectedRollbackException bug that left failed jobs stuck at QUEUED with no error surfaced).
 *
 * Training itself now runs on the Python side in a background thread (see app.py), so
 * pythonMlServiceClient.startTraining just kicks it off and returns immediately; this method
 * polls pollTrainingStatus in its own loop until the job reaches a terminal state. While RUNNING,
 * as soon as an MLflow run id is known, it also polls MlflowIntegrationService.getEpochMetrics
 * directly (MLflow's autolog writes metrics live as each epoch finishes, so this is a genuine
 * live progress reading, not a guess) to derive currentEpoch/totalEpochs for the UI. This
 * replaces last session's custom Flask progress dict, which never actually worked live because
 * Flask's dev server ran single-threaded: the poll requests queued behind the blocking /train
 * call and were never served until training finished.
 */
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
                trainingJobStatusService.markFailed(jobId, describeFailure(status.error()));
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
                // Training itself already succeeded and is persisted - a failure delegating into
                // auto-annotate must never be reported back as a training failure. Worst case, the
                // point-annotation job is left at TRAINING with no forward progress, visible in the
                // Unlabeled import queue for manual follow-up.
                log.error("Failed to auto-delegate into auto-annotate after training job {} completed", jobId, ex);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            trainingJobStatusService.markFailed(jobId, "Interrupted while waiting for training to complete");
        } catch (Exception ex) {
            trainingJobStatusService.markFailed(jobId, describeFailure(ex.getMessage(), ex));
        } finally {
            if (preparedDir != null) {
                deleteRecursivelyQuietly(preparedDir);
            }
        }
    }

    /**
     * Reads the live epoch metric history straight from MLflow (autolog logs a metric point as
     * soon as each epoch finishes, even while the run is still in progress) and, if any points
     * exist yet, persists the latest one as the job's current epoch. MLflow's step numbers are
     * 0-based (mlflow.log_metric(..., step=epoch) in app.py's epoch loop starts at epoch 0), so
     * +1 here converts to the 1-based "epoch X of N" a human expects to see.
     */
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

    /**
     * Falls back to a generic message when the Python-reported error is null/blank (a status poll
     * caught a FAILED job before Flask finished writing a detailed error, for example), so a
     * training job never ends up FAILED with an empty errorMessage that gives the user nothing to
     * act on.
     */
    private String describeFailure(String pythonError) {
        return (pythonError == null || pythonError.isBlank()) ? "Training failed" : pythonError;
    }

    /**
     * Falls back to the exception's class name when the message is null (a bare NullPointerException,
     * for example), so a training job never ends up FAILED with an empty errorMessage that gives the
     * user nothing to act on.
     */
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
