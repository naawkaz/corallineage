package com.corallineage.backend.pointannotation;

import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.versioning.dataset.DatasetVersion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists every AUTO_ANNOTATING-phase status transition in its own short-lived
 * Propagation.REQUIRES_NEW transaction, on a separate bean from AutoAnnotationExecutor, mirroring
 * training.TrainingJobStatusService (see that class's javadoc for the full history of the real
 * UnexpectedRollbackException bug that pattern fixes). The same shared-transaction risk applies
 * here: AutoAnnotationExecutor runs a poll loop lasting anywhere from seconds to minutes, and
 * PointAnnotationJobService's other methods (submitJob, labelPoint, reviewPoint, trainModel,
 * startAutoAnnotate) are all plain @Transactional, so a single shared transaction spanning the
 * whole poll loop would hold a DB connection open for that entire duration and would let a late
 * failure poison an earlier successful write's ability to commit.
 */
@Service
public class PointAnnotationJobStatusService {

    private final PointAnnotationJobRepository pointAnnotationJobRepository;
    private final GeneratedPointRepository generatedPointRepository;

    public PointAnnotationJobStatusService(PointAnnotationJobRepository pointAnnotationJobRepository,
                                           GeneratedPointRepository generatedPointRepository) {
        this.pointAnnotationJobRepository = pointAnnotationJobRepository;
        this.generatedPointRepository = generatedPointRepository;
    }

    /**
     * Persists a live per-image (really per-point) progress reading from the poller in
     * AutoAnnotationExecutor. Silently no-ops if the job can't be found, mirroring
     * TrainingJobStatusService.updateProgress: expected under a real race, not a genuine error.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProgress(Long jobId, Integer processedCount, Integer totalCount, Long currentPointId) {
        pointAnnotationJobRepository.findById(jobId).ifPresent(job -> {
            job.setAutoAnnotationProcessedCount(processedCount);
            job.setAutoAnnotationTotalCount(totalCount);
            job.setAutoAnnotationCurrentPointId(currentPointId);
            pointAnnotationJobRepository.save(job);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long jobId, String errorMessage) {
        pointAnnotationJobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus("FAILED");
            job.setErrorMessage(errorMessage);
            pointAnnotationJobRepository.save(job);
        });
    }

    /**
     * Same completion logic PointAnnotationJobService.startAutoAnnotate used to run inline right
     * after its (now removed) blocking runInference call: REVIEW if any point is still pending
     * human review, COMPLETE otherwise. Returns the lazy fields the caller needs to log the
     * completion event (datasetVersion, modelTag, initiatedBy), captured here while the entity is
     * still attached to this method's own transaction, since AutoAnnotationExecutor logs the event
     * only after this transaction has already committed and closed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompletionResult completeAutoAnnotation(Long jobId) {
        PointAnnotationJob job = pointAnnotationJobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Point annotation job not found: " + jobId));
        long pendingReview = generatedPointRepository.countByJobIdAndSeedFalseAndStatus(jobId, "MODEL_PREDICTED");
        job.setStatus(pendingReview > 0 ? "REVIEW" : "COMPLETE");
        PointAnnotationJob saved = pointAnnotationJobRepository.save(job);
        String modelTag = saved.getModelVersion() != null ? saved.getModelVersion().getModelTag() : null;
        return new CompletionResult(saved.getDatasetVersion(), modelTag, saved.getInitiatedBy(), pendingReview);
    }

    public record CompletionResult(DatasetVersion datasetVersion, String modelTag, String initiatedBy,
                                    long pendingReviewCount) {
    }
}
