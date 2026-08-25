package com.corallineage.backend.pointannotation;

import com.corallineage.backend.annotation.AnnotationJob;
import com.corallineage.backend.annotation.AnnotationJobRepository;
import com.corallineage.backend.annotation.AnnotationPredictionRepository;
import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.versioning.dataset.DatasetVersion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Persists every AUTO_ANNOTATING-phase status transition in its own short-lived
 * Propagation.REQUIRES_NEW transaction, on a separate bean from AutoAnnotationExecutor, mirroring
 * training.TrainingJobStatusService (see that class's javadoc for the full history of the real
 * UnexpectedRollbackException bug that pattern fixes). The same shared-transaction risk applies
 * here: AutoAnnotationExecutor runs a poll loop lasting anywhere from seconds to minutes, and
 * PointAnnotationJobService's other methods (submitJob, labelPoint, trainModel,
 * startAutoAnnotate) are all plain @Transactional, so a single shared transaction spanning the
 * whole poll loop would hold a DB connection open for that entire duration and would let a late
 * failure poison an earlier successful write's ability to commit.
 */
@Service
public class PointAnnotationJobStatusService {

    private final PointAnnotationJobRepository pointAnnotationJobRepository;
    private final AnnotationJobRepository annotationJobRepository;
    private final AnnotationPredictionRepository annotationPredictionRepository;

    public PointAnnotationJobStatusService(PointAnnotationJobRepository pointAnnotationJobRepository,
                                           AnnotationJobRepository annotationJobRepository,
                                           AnnotationPredictionRepository annotationPredictionRepository) {
        this.pointAnnotationJobRepository = pointAnnotationJobRepository;
        this.annotationJobRepository = annotationJobRepository;
        this.annotationPredictionRepository = annotationPredictionRepository;
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

    /**
     * A failure here is a failure of the delegated {@link AnnotationJob}'s inference batch, not of
     * the point-annotation job itself (which already reached its own terminal "COMPLETE" the moment
     * startAutoAnnotate created the delegation) - marks that AnnotationJob FAILED so the review
     * screen/resolve-route surface the real error, instead of resurrecting the point-annotation
     * job's own status.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long jobId, String errorMessage) {
        pointAnnotationJobRepository.findById(jobId).ifPresent(job -> {
            AnnotationJob delegatedJob = job.getAnnotationJob();
            if (delegatedJob == null) {
                return;
            }
            delegatedJob.setStatus("FAILED");
            delegatedJob.setErrorMessage(errorMessage);
            delegatedJob.setCompletedAt(Instant.now());
            annotationJobRepository.save(delegatedJob);
        });
    }

    /**
     * Marks the delegated {@link AnnotationJob} COMPLETED and derives its reviewStatus from the
     * AnnotationPrediction accept counts, mirroring AnnotationJobExecutor.executeExistingVersionAsync's
     * identical inline logic - the point-annotation job's own status is untouched here (already
     * COMPLETE since startAutoAnnotate). Returns the lazy fields the caller needs to log the
     * completion event (datasetVersion, modelTag, initiatedBy), captured here while the entities are
     * still attached to this method's own transaction, since AutoAnnotationExecutor logs the event
     * only after this transaction has already committed and closed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompletionResult completeAutoAnnotation(Long jobId) {
        PointAnnotationJob job = pointAnnotationJobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Point annotation job not found: " + jobId));
        AnnotationJob delegatedJob = job.getAnnotationJob();
        if (delegatedJob == null) {
            throw new IllegalStateException("Point annotation job " + jobId + " has no delegated annotation job");
        }
        long total = annotationPredictionRepository.countByAnnotationJobId(delegatedJob.getId());
        long autoAccepted = annotationPredictionRepository.countByAnnotationJobIdAndAcceptedTrue(delegatedJob.getId());
        delegatedJob.setStatus("COMPLETED");
        delegatedJob.setCompletedAt(Instant.now());
        delegatedJob.setReviewStatus(autoAccepted == 0 ? "PENDING" : (autoAccepted < total ? "IN_PROGRESS" : "COMPLETED"));
        AnnotationJob savedDelegated = annotationJobRepository.save(delegatedJob);
        long pendingReview = total - autoAccepted;
        String modelTag = savedDelegated.getModelVersion() != null ? savedDelegated.getModelVersion().getModelTag() : null;
        return new CompletionResult(job.getDatasetVersion(), modelTag, job.getInitiatedBy(), pendingReview);
    }

    public record CompletionResult(DatasetVersion datasetVersion, String modelTag, String initiatedBy,
                                    long pendingReviewCount) {
    }
}
