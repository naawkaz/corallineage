package com.corallineage.backend.training;

/** Fields needed once a job is marked running */
public record TrainingJobStart(Long datasetVersionId, String architecture, String hyperparametersJson) {
}
