package com.corallineage.backend.pointannotation;

import com.corallineage.backend.annotation.AnnotationCommitService;
import com.corallineage.backend.annotation.AnnotationJob;
import com.corallineage.backend.annotation.AnnotationJobRepository;
import com.corallineage.backend.annotation.AnnotationPredictionRepository;
import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.versioning.dataset.DatasetVersion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Persists auto annotating status transitions in their own transaction */
@Service
public class PointAnnotationJobStatusService {

    private final PointAnnotationJobRepository pointAnnotationJobRepository;
    private final AnnotationJobRepository annotationJobRepository;
    private final AnnotationPredictionRepository annotationPredictionRepository;
    private final AnnotationCommitService annotationCommitService;

    public PointAnnotationJobStatusService(PointAnnotationJobRepository pointAnnotationJobRepository,
                                           AnnotationJobRepository annotationJobRepository,
                                           AnnotationPredictionRepository annotationPredictionRepository,
                                           AnnotationCommitService annotationCommitService) {
        this.pointAnnotationJobRepository = pointAnnotationJobRepository;
        this.annotationJobRepository = annotationJobRepository;
        this.annotationPredictionRepository = annotationPredictionRepository;
        this.annotationCommitService = annotationCommitService;
    }

    /** Persists live progress from the poller, does nothing if the job is gone */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProgress(Long jobId, Integer processedCount, Integer totalCount, Long currentPointId) {
        pointAnnotationJobRepository.findById(jobId).ifPresent(job -> {
            job.setAutoAnnotationProcessedCount(processedCount);
            job.setAutoAnnotationTotalCount(totalCount);
            job.setAutoAnnotationCurrentPointId(currentPointId);
            pointAnnotationJobRepository.save(job);
        });
    }

    /** Marks the delegated annotation job failed */
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

    /** Marks the delegated annotation job completed and derives its review status */
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

        // Every predicted point cleared the confidence threshold, so register this dataset version
        // now rather than leaving it in the unlabeled import queue for a manual apply.
        boolean fullyAutoAccepted = total > 0 && autoAccepted == total;
        if (fullyAutoAccepted && Boolean.TRUE.equals(savedDelegated.getCommitInPlace())) {
            annotationCommitService.applyToExistingVersion(savedDelegated.getId(), job.getInitiatedBy());
        }

        long pendingReview = total - autoAccepted;
        String modelTag = savedDelegated.getModelVersion() != null ? savedDelegated.getModelVersion().getModelTag() : null;
        return new CompletionResult(job.getDatasetVersion(), modelTag, job.getInitiatedBy(), pendingReview);
    }

    public record CompletionResult(DatasetVersion datasetVersion, String modelTag, String initiatedBy,
                                    long pendingReviewCount) {
    }
}
