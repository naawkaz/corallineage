package com.corallineage.backend.registry.model;

import java.util.List;

public class MlflowDetailsResponse {

    public record Environment(String pythonVersion, String pytorchVersion, String cudaVersion,
                               String randomSeed, Long totalDurationSeconds) {
    }

    /**
     * A single loss/accuracy line, not the spec's separate trainLoss/valLoss pair — this training
     * loop has no held-out validation split (consistent with the Feature 6 simplification, C-07:
     * "model accuracy is not an evaluation criterion" for this prototype), so there is no second
     * series to plot honestly.
     */
    public record EpochMetricPoint(int epoch, Double loss, Double accuracy) {
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
