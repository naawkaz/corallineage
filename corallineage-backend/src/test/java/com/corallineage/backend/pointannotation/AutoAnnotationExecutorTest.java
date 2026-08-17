package com.corallineage.backend.pointannotation;

import com.corallineage.backend.logging.AnnotationEventLoggerService;
import com.corallineage.backend.logging.EventType;
import com.corallineage.backend.training.AnnotateResult;
import com.corallineage.backend.training.PythonMlServiceClient;
import com.corallineage.backend.versioning.dataset.DatasetVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Calls executeAutoAnnotationAsync directly (bypassing the real @Async proxy), same
 * synchronous-test-double pattern TrainingJobExecutorTest already uses. PointInferenceService and
 * PythonMlServiceClient are mocked here rather than exercised for real, so this file covers the
 * orchestration logic: which collaborator is called with which arguments on each path, and that a
 * RUNNING/COMPLETED/FAILED poll sequence drives the right status-service calls.
 */
@ExtendWith(MockitoExtension.class)
class AutoAnnotationExecutorTest {

    @Mock private PointInferenceService pointInferenceService;
    @Mock private PythonMlServiceClient pythonMlServiceClient;
    @Mock private PointAnnotationJobStatusService pointAnnotationJobStatusService;
    @Mock private AnnotationEventLoggerService annotationEventLoggerService;

    private AutoAnnotationExecutor newExecutor() {
        return new AutoAnnotationExecutor(pointInferenceService, pythonMlServiceClient,
                pointAnnotationJobStatusService, annotationEventLoggerService);
    }

    @Test
    void executeAutoAnnotationAsync_noPendingWork_skipsPollingAndCompletesDirectly() {
        when(pointInferenceService.prepareAndStartAnnotate(1L))
                .thenReturn(new PointInferenceService.AnnotationKickoff(false, null));

        DatasetVersion version = new DatasetVersion();
        version.setId(10L);
        when(pointAnnotationJobStatusService.completeAutoAnnotation(1L))
                .thenReturn(new PointAnnotationJobStatusService.CompletionResult(version, "model-v9", "researcher_01", 0L));

        newExecutor().executeAutoAnnotationAsync(1L);

        verify(pythonMlServiceClient, never()).pollAnnotateStatus(any());
        verify(pointInferenceService, never()).applyPredictions(any(), any());
        verify(annotationEventLoggerService).log(eq(EventType.AUTO_ANNOTATION_COMPLETED), eq(version),
                eq("researcher_01"), any());
    }

    @Test
    void executeAutoAnnotationAsync_success_pollsUntilCompletedThenAppliesPredictionsAndCleansUpPatchDir(@TempDir Path patchDir) {
        when(pointInferenceService.prepareAndStartAnnotate(2L))
                .thenReturn(new PointInferenceService.AnnotationKickoff(true, patchDir));

        AnnotateResult result = new AnnotateResult(List.of(
                new AnnotateResult.AnnotatedImage(100L, List.of(
                        new AnnotateResult.AnnotatedPoint(0, "CCA", new BigDecimal("0.9"))))));
        when(pythonMlServiceClient.pollAnnotateStatus(2L))
                .thenReturn(new PythonMlServiceClient.AnnotateStatus("RUNNING", 1, 2, 100L, null, null))
                .thenReturn(new PythonMlServiceClient.AnnotateStatus("COMPLETED", 2, 2, null, result, null));

        DatasetVersion version = new DatasetVersion();
        version.setId(10L);
        when(pointAnnotationJobStatusService.completeAutoAnnotation(2L))
                .thenReturn(new PointAnnotationJobStatusService.CompletionResult(version, "model-v9", "researcher_01", 1L));

        newExecutor().executeAutoAnnotationAsync(2L);

        verify(pointAnnotationJobStatusService).updateProgress(2L, 1, 2, 100L);
        verify(pointInferenceService).applyPredictions(2L, result);
        verify(pointAnnotationJobStatusService, never()).markFailed(any(), any());
        assertThat(Files.exists(patchDir)).isFalse();
    }

    @Test
    void executeAutoAnnotationAsync_pythonJobFails_marksJobFailedWithPythonErrorAndCleansUpPatchDir(@TempDir Path patchDir) {
        when(pointInferenceService.prepareAndStartAnnotate(3L))
                .thenReturn(new PointInferenceService.AnnotationKickoff(true, patchDir));
        when(pythonMlServiceClient.pollAnnotateStatus(3L))
                .thenReturn(new PythonMlServiceClient.AnnotateStatus("FAILED", 0, 2, null, null, "Weights file not found"));

        newExecutor().executeAutoAnnotationAsync(3L);

        verify(pointAnnotationJobStatusService).markFailed(3L, "Weights file not found");
        verify(pointInferenceService, never()).applyPredictions(any(), any());
        verify(pointAnnotationJobStatusService, never()).completeAutoAnnotation(any());
        assertThat(Files.exists(patchDir)).isFalse();
    }

    @Test
    void executeAutoAnnotationAsync_prepareThrows_marksJobFailedWithExceptionMessage() {
        when(pointInferenceService.prepareAndStartAnnotate(4L))
                .thenThrow(new RuntimeException("weights path missing"));

        newExecutor().executeAutoAnnotationAsync(4L);

        verify(pointAnnotationJobStatusService).markFailed(4L, "weights path missing");
    }
}
