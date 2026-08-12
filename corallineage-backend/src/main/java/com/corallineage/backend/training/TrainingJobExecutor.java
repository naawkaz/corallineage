package com.corallineage.backend.training;

import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.logging.AnnotationEventLoggerService;
import com.corallineage.backend.logging.EventType;
import com.corallineage.backend.registry.model.ModelVersion;
import com.corallineage.backend.registry.model.ModelVersionService;
import com.corallineage.backend.versioning.dataset.DatasetVersion;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;

@Service
public class TrainingJobExecutor {

    private final TrainingJobRepository trainingJobRepository;
    private final DatasetPreparationService datasetPreparationService;
    private final PythonMlServiceClient pythonMlServiceClient;
    private final ModelVersionService modelVersionService;
    private final AnnotationEventLoggerService annotationEventLoggerService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TrainingJobExecutor(TrainingJobRepository trainingJobRepository,
                               DatasetPreparationService datasetPreparationService,
                               PythonMlServiceClient pythonMlServiceClient,
                               ModelVersionService modelVersionService,
                               AnnotationEventLoggerService annotationEventLoggerService) {
        this.trainingJobRepository = trainingJobRepository;
        this.datasetPreparationService = datasetPreparationService;
        this.pythonMlServiceClient = pythonMlServiceClient;
        this.modelVersionService = modelVersionService;
        this.annotationEventLoggerService = annotationEventLoggerService;
    }

    @Async
    @Transactional
    public void executeTrainingAsync(Long jobId) {
        TrainingJob job = trainingJobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Training job not found: " + jobId));
        Path preparedDir = null;
        try {
            job.setStatus("RUNNING");
            trainingJobRepository.save(job);

            preparedDir = datasetPreparationService.prepareImageFolderDataset(job.getDatasetVersion().getId(), jobId);

            @SuppressWarnings("unchecked")
            Map<String, Object> hyperparameters = objectMapper.readValue(job.getHyperparameters(), Map.class);
            TrainResult result = pythonMlServiceClient.train(job.getArchitecture(), preparedDir.toString(), hyperparameters);

            String performanceMetricsJson = objectMapper.writeValueAsString(result.performanceMetrics());
            String classMappingsJson = objectMapper.writeValueAsString(result.classMappings());
            ModelVersion model = modelVersionService.registerFromTrainingResult(
                    job, result.mlflowRunId(), result.weightsPath(), result.accuracy(),
                    performanceMetricsJson, classMappingsJson);

            job.setModelVersion(model);
            job.setStatus("COMPLETED");
            job.setCompletedAt(Instant.now());
            trainingJobRepository.save(job);

            logModelTrainedAfterCommit(job, model, "Training completed, registered " + model.getModelTag());
        } catch (Exception ex) {
            job.setStatus("FAILED");
            job.setErrorMessage(ex.getMessage());
            job.setCompletedAt(Instant.now());
            trainingJobRepository.save(job);
        } finally {
            if (preparedDir != null) {
                deleteRecursivelyQuietly(preparedDir);
            }
        }
    }

    /**
     * Mirrors DatasetImportExecutor's logImportCompletedAfterCommit: AnnotationEventLoggerService.log()
     * runs in REQUIRES_NEW on a separate connection, so it cannot see this method's own uncommitted
     * writes (the new ModelVersion row in particular) until the outer transaction commits.
     */
    private void logModelTrainedAfterCommit(TrainingJob job, ModelVersion modelVersion, String description) {
        DatasetVersion datasetVersion = job.getDatasetVersion();
        String performedBy = job.getInitiatedBy();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    annotationEventLoggerService.log(EventType.MODEL_TRAINED, datasetVersion, modelVersion, performedBy, description);
                }
            });
        } else {
            annotationEventLoggerService.log(EventType.MODEL_TRAINED, datasetVersion, modelVersion, performedBy, description);
        }
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
