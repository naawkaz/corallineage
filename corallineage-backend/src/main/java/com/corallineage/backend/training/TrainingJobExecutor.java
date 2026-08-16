package com.corallineage.backend.training;

import com.corallineage.backend.logging.AnnotationEventLoggerService;
import com.corallineage.backend.logging.EventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;

/**
 * Orchestrates a training job's background run. Deliberately holds no @Transactional boundary
 * of its own: every DB write is delegated to TrainingJobStatusService, each call opening its own
 * fresh Propagation.REQUIRES_NEW transaction. See TrainingJobStatusService's class comment for
 * the full history of why (a shared-transaction design here previously caused a real
 * UnexpectedRollbackException bug that left failed jobs stuck at QUEUED with no error surfaced).
 */
@Service
public class TrainingJobExecutor {

    private final TrainingJobStatusService trainingJobStatusService;
    private final DatasetPreparationService datasetPreparationService;
    private final PythonMlServiceClient pythonMlServiceClient;
    private final AnnotationEventLoggerService annotationEventLoggerService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TrainingJobExecutor(TrainingJobStatusService trainingJobStatusService,
                               DatasetPreparationService datasetPreparationService,
                               PythonMlServiceClient pythonMlServiceClient,
                               AnnotationEventLoggerService annotationEventLoggerService) {
        this.trainingJobStatusService = trainingJobStatusService;
        this.datasetPreparationService = datasetPreparationService;
        this.pythonMlServiceClient = pythonMlServiceClient;
        this.annotationEventLoggerService = annotationEventLoggerService;
    }

    @Async
    public void executeTrainingAsync(Long jobId) {
        Path preparedDir = null;
        try {
            TrainingJobStart start = trainingJobStatusService.markRunning(jobId);

            preparedDir = datasetPreparationService.prepareImageFolderDataset(start.datasetVersionId(), jobId);

            @SuppressWarnings("unchecked")
            Map<String, Object> hyperparameters = objectMapper.readValue(start.hyperparametersJson(), Map.class);
            TrainResult result = pythonMlServiceClient.train(start.architecture(), preparedDir.toString(), hyperparameters);

            String performanceMetricsJson = objectMapper.writeValueAsString(result.performanceMetrics());
            String classMappingsJson = objectMapper.writeValueAsString(result.classMappings());

            TrainingJob completed = trainingJobStatusService.markCompleted(jobId, result.mlflowRunId(),
                    result.weightsPath(), result.accuracy(), performanceMetricsJson, classMappingsJson);

            annotationEventLoggerService.log(EventType.MODEL_TRAINED, completed.getDatasetVersion(),
                    completed.getModelVersion(), completed.getInitiatedBy(),
                    "Training completed, registered " + completed.getModelVersion().getModelTag());
        } catch (Exception ex) {
            trainingJobStatusService.markFailed(jobId, describeFailure(ex));
        } finally {
            if (preparedDir != null) {
                deleteRecursivelyQuietly(preparedDir);
            }
        }
    }

    /**
     * Falls back to the exception's class name when the message is null (a bare NullPointerException,
     * for example), so a training job never ends up FAILED with an empty errorMessage that gives the
     * user nothing to act on.
     */
    private String describeFailure(Exception ex) {
        String message = ex.getMessage();
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
