package com.corallineage.backend.registry.model;

import java.util.List;

public class MlflowDetailsResponse {

    public record Environment(String pythonVersion, String pytorchVersion, String cudaVersion,
                               String randomSeed, Long totalDurationSeconds) {
    }

    /**
     * One epoch's worth of training metrics. trainLoss/valLoss/valAccuracy come from a real
     * held-out validation split added to the training loop (see MlflowIntegrationService and
     * app.py's run_training/split_train_val); they are null for runs trained before that change.
     * loss/accuracy are the pre-validation-split metric keys, kept so models trained before this
     * change still render a single-line loss chart instead of a blank one, see
     * MlflowIntegrationService.getEpochMetrics for how the two are merged.
     */
    public record EpochMetricPoint(int epoch, Double trainLoss, Double valLoss, Double valAccuracy,
                                    Double loss, Double accuracy) {
    }

    public record ArtifactUrls(String confusionMatrixPng, String weightsFile) {
    }

    private Long modelId;
    private String mlflowRunId;
    private Environment environment;
    private List<EpochMetricPoint> epochMetrics;
    private ArtifactUrls artifactUrls;
    private String mlflowUiUrl;

    public Long getModelId() { return modelId; }
    public void setModelId(Long modelId) { this.modelId = modelId; }

    public String getMlflowRunId() { return mlflowRunId; }
    public void setMlflowRunId(String mlflowRunId) { this.mlflowRunId = mlflowRunId; }

    public Environment getEnvironment() { return environment; }
    public void setEnvironment(Environment environment) { this.environment = environment; }

    public List<EpochMetricPoint> getEpochMetrics() { return epochMetrics; }
    public void setEpochMetrics(List<EpochMetricPoint> epochMetrics) { this.epochMetrics = epochMetrics; }

    public ArtifactUrls getArtifactUrls() { return artifactUrls; }
    public void setArtifactUrls(ArtifactUrls artifactUrls) { this.artifactUrls = artifactUrls; }

    public String getMlflowUiUrl() { return mlflowUiUrl; }
    public void setMlflowUiUrl(String mlflowUiUrl) { this.mlflowUiUrl = mlflowUiUrl; }
}
