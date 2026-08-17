package com.corallineage.backend.training;

import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.registry.model.ModelVersion;
import com.corallineage.backend.registry.model.ModelVersionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Persists every TrainingJob status transition in its own short-lived Propagation.REQUIRES_NEW
 * transaction, on a separate bean from TrainingJobExecutor (a same-bean self-invocation would
 * bypass the Spring proxy and REQUIRES_NEW would silently do nothing, the same self-invocation
 * gotcha documented on TrainingJobService.dispatchAfterCommit).
 *
 * This split exists to fix a real bug: the previous code ran the entire training flow (the
 * RUNNING status write, dataset preparation, the long-running Python training HTTP call, and
 * model registration) inside a single shared transaction opened by executeTrainingAsync. When
 * DatasetPreparationService.prepareImageFolderDataset (itself @Transactional, participating in
 * that same shared transaction) threw because a dataset version had no annotation points, Spring
 * correctly marked the shared transaction rollback-only. The catch block then set the job to
 * FAILED and saved it, but that save happened inside the already-doomed transaction, and
 * returning normally made the surrounding transactional proxy try to commit anyway, throwing
 * UnexpectedRollbackException from outside the try/catch. Since executeTrainingAsync is @Async,
 * Spring's default uncaught-exception handler just logged that and the job was left stuck at
 * QUEUED forever with no errorMessage ever persisted.
 *
 * Giving every status write (RUNNING, COMPLETED, FAILED) its own fresh transaction means a
 * failure in dataset preparation, the ML service call, or model registration can never poison a
 * later write's ability to commit. It also stops holding a database connection open for the
 * full duration of the training call, which was an unrelated but real problem with the old
 * single-transaction design.
 */
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

    /**
     * Persists a live epoch-progress reading from the poller in TrainingJobExecutor. Silently
     * no-ops if the job can't be found: this is expected under a real race (the poller's last
     * in-flight request lands right after the job already reached a terminal state and, in tests,
     * after cleanup deleted the row), not a genuine error to surface.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProgress(Long jobId, Integer currentEpoch, Integer totalEpochs) {
        trainingJobRepository.findById(jobId).ifPresent(job -> {
            job.setCurrentEpoch(currentEpoch);
            job.setTotalEpochs(totalEpochs);
            trainingJobRepository.save(job);
        });
    }

    /**
     * Persists the MLflow run id as soon as the poller learns it, which may be well before the
     * job reaches COMPLETED now that the Python side starts its MLflow run before the epoch loop.
     * Kept as its own small method rather than folded into updateProgress's signature: the run id
     * becomes known on an earlier poll tick than epoch/total (before any metric history exists to
     * compute a current epoch from), so the two are genuinely written on different poll ticks.
     * Same silent-no-op-if-missing behavior as updateProgress, for the same reason.
     */
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
