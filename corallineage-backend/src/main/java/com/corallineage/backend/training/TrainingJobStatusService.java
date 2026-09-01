package com.corallineage.backend.training;

import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.registry.model.ModelVersion;
import com.corallineage.backend.registry.model.ModelVersionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/** Persists every training job status transition in its own transaction */
@Service
public class TrainingJobStatusService {

    private final TrainingJobRepository trainingJobRepository;
    private final ModelVersionService modelVersionService;

    public TrainingJobStatusService(TrainingJobRepository trainingJobRepository,
                                    ModelVersionService modelVersionService) {
        this.trainingJobRepository = trainingJobRepository;
        this.modelVersionService = modelVersionService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TrainingJobStart markRunning(Long jobId) {
        TrainingJob job = requireJob(jobId);
        job.setStatus("RUNNING");
        trainingJobRepository.save(job);
        return new TrainingJobStart(job.getDatasetVersion().getId(), job.getArchitecture(), job.getHyperparameters());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TrainingJob markCompleted(Long jobId, String mlflowRunId, String weightsPath, BigDecimal accuracy,
                                     String performanceMetricsJson, String classMappingsJson) {
        TrainingJob job = requireJob(jobId);
        ModelVersion model = modelVersionService.registerFromTrainingResult(
                job, mlflowRunId, weightsPath, accuracy, performanceMetricsJson, classMappingsJson);
        job.setModelVersion(model);
        job.setMlflowRunId(mlflowRunId);
        job.setStatus("COMPLETED");
        job.setCompletedAt(Instant.now());
        return trainingJobRepository.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long jobId, String errorMessage) {
        TrainingJob job = requireJob(jobId);
        job.setStatus("FAILED");
        job.setErrorMessage(errorMessage);
        job.setCompletedAt(Instant.now());
        trainingJobRepository.save(job);
    }

    /** Persists live epoch progress from the poller, does nothing if the job is gone */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProgress(Long jobId, Integer currentEpoch, Integer totalEpochs) {
        trainingJobRepository.findById(jobId).ifPresent(job -> {
            job.setCurrentEpoch(currentEpoch);
            job.setTotalEpochs(totalEpochs);
            trainingJobRepository.save(job);
        });
    }

    /** Persists the MLflow run id once known */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateMlflowRunId(Long jobId, String mlflowRunId) {
        trainingJobRepository.findById(jobId).ifPresent(job -> {
            job.setMlflowRunId(mlflowRunId);
            trainingJobRepository.save(job);
        });
    }

    private TrainingJob requireJob(Long jobId) {
        return trainingJobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Training job not found: " + jobId));
    }
}
