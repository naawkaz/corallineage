package com.corallineage.backend.pointannotation;

import com.corallineage.backend.logging.AnnotationEventLoggerService;
import com.corallineage.backend.logging.EventType;
import com.corallineage.backend.training.AnnotateResult;
import com.corallineage.backend.training.PythonMlServiceClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Orchestrates a point-annotation job's AUTO_ANNOTATING-phase background run, mirroring
 * training.TrainingJobExecutor's shape. Deliberately holds no @Transactional boundary of its own:
 * every DB write is delegated to PointAnnotationJobStatusService (each call its own fresh
 * Propagation.REQUIRES_NEW transaction) or PointInferenceService (each call its own plain
 * @Transactional method). See PointAnnotationJobStatusService's class comment for why.
 *
 * Inference itself now runs on the Python side in a background thread (see app.py), so
 * pythonMlServiceClient (via PointInferenceService.prepareAndStartAnnotate) just kicks off the
 * batch and returns immediately; this method polls pollAnnotateStatus in its own loop until the
 * batch reaches a terminal state, persisting processedCount/totalCount/currentPointId on every
 * RUNNING tick so the frontend can show live "processed X of Y" progress instead of a single
 * blocking spinner for the whole batch.
 */
@Service
public class AutoAnnotationExecutor {

    private static final long POLL_INTERVAL_MILLIS = 1500;

    private final PointInferenceService pointInferenceService;
    private final PythonMlServiceClient pythonMlServiceClient;
    private final PointAnnotationJobStatusService pointAnnotationJobStatusService;
    private final AnnotationEventLoggerService annotationEventLoggerService;

    public AutoAnnotationExecutor(PointInferenceService pointInferenceService,
                                  PythonMlServiceClient pythonMlServiceClient,
                                  PointAnnotationJobStatusService pointAnnotationJobStatusService,
                                  AnnotationEventLoggerService annotationEventLoggerService) {
        this.pointInferenceService = pointInferenceService;
        this.pythonMlServiceClient = pythonMlServiceClient;
        this.pointAnnotationJobStatusService = pointAnnotationJobStatusService;
        this.annotationEventLoggerService = annotationEventLoggerService;
    }

    @Async
    public void executeAutoAnnotationAsync(Long jobId) {
        Path patchDir = null;
        try {
            PointInferenceService.AnnotationKickoff kickoff = pointInferenceService.prepareAndStartAnnotate(jobId);
            patchDir = kickoff.patchDir();

            if (kickoff.hasPendingWork()) {
                PythonMlServiceClient.AnnotateStatus status;
                while (true) {
                    status = pythonMlServiceClient.pollAnnotateStatus(jobId);

                    if ("RUNNING".equals(status.status())) {
                        pointAnnotationJobStatusService.updateProgress(
                                jobId, status.processedCount(), status.totalCount(), status.currentPointId());
                    }
                    if ("COMPLETED".equals(status.status()) || "FAILED".equals(status.status())) {
                        break;
                    }
                    Thread.sleep(POLL_INTERVAL_MILLIS);
                }

                if ("FAILED".equals(status.status())) {
                    pointAnnotationJobStatusService.markFailed(jobId, describeFailure(status.error()));
                    return;
                }

                AnnotateResult result = status.result();
                pointInferenceService.applyPredictions(jobId, result);
            }

            PointAnnotationJobStatusService.CompletionResult completion =
                    pointAnnotationJobStatusService.completeAutoAnnotation(jobId);

            annotationEventLoggerService.log(EventType.AUTO_ANNOTATION_COMPLETED, completion.datasetVersion(),
                    completion.initiatedBy(), "Auto-annotation completed with model " + completion.modelTag()
                            + ", " + completion.pendingReviewCount() + " point(s) pending review");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            pointAnnotationJobStatusService.markFailed(jobId, "Interrupted while waiting for auto-annotation to complete");
        } catch (Exception ex) {
            pointAnnotationJobStatusService.markFailed(jobId, describeFailure(ex));
        } finally {
            if (patchDir != null) {
                deleteRecursivelyQuietly(patchDir);
            }
        }
    }

    /**
     * Falls back to a generic message when the Python-reported error is null/blank (a status poll
     * caught a FAILED batch before Flask finished writing a detailed error, for example), so a
     * point annotation job never ends up FAILED with an empty errorMessage that gives the user
     * nothing to act on.
     */
    private String describeFailure(String pythonError) {
        return (pythonError == null || pythonError.isBlank()) ? "Auto-annotation failed" : pythonError;
    }

    /**
     * Falls back to the exception's class name when the message is null (a bare NullPointerException,
     * for example), mirroring TrainingJobExecutor.describeFailure.
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
