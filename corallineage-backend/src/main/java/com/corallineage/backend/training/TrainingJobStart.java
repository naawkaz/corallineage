package com.corallineage.backend.training;

/**
 * Snapshot of the fields executeTrainingAsync needs after a job is marked RUNNING, captured
 * inside TrainingJobStatusService.markRunning's own transaction so the caller never touches a
 * lazy association (TrainingJob.datasetVersion) outside a session.
 */
public record TrainingJobStart(Long datasetVersionId, String architecture, String hyperparametersJson) {
}
