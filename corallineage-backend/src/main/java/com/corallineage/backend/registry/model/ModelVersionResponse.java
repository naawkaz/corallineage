package com.corallineage.backend.registry.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public class ModelVersionResponse {

    private Long id;
    private String modelTag;
    private Instant trainedAt;
    private String trainedBy;
    private String architecture;
    private Map<String, Object> hyperparameters;
    private Map<String, Object> classMappings;
    private BigDecimal accuracy;
    private Map<String, Object> performanceMetrics;
    private String mlflowRunId;
    private String weightsPath;
    private String status;
    private DatasetVersionSummary trainedOnDatasetVersion;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getModelTag() {
        return modelTag;
    }

    public void setModelTag(String modelTag) {
        this.modelTag = modelTag;
    }

    public Instant getTrainedAt() {
        return trainedAt;
    }

    public void setTrainedAt(Instant trainedAt) {
        this.trainedAt = trainedAt;
    }

    public String getTrainedBy() {
        return trainedBy;
    }

    public void setTrainedBy(String trainedBy) {
        this.trainedBy = trainedBy;
    }

    public String getArchitecture() {
        return architecture;
    }

    public void setArchitecture(String architecture) {
        this.architecture = architecture;
    }

    public Map<String, Object> getHyperparameters() {
        return hyperparameters;
    }

    public void setHyperparameters(Map<String, Object> hyperparameters) {
        this.hyperparameters = hyperparameters;
    }

    public Map<String, Object> getClassMappings() {
        return classMappings;
    }

    public void setClassMappings(Map<String, Object> classMappings) {
        this.classMappings = classMappings;
    }

    public BigDecimal getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(BigDecimal accuracy) {
        this.accuracy = accuracy;
    }

    public Map<String, Object> getPerformanceMetrics() {
        return performanceMetrics;
    }

    public void setPerformanceMetrics(Map<String, Object> performanceMetrics) {
        this.performanceMetrics = performanceMetrics;
    }

    public String getMlflowRunId() {
        return mlflowRunId;
    }

    public void setMlflowRunId(String mlflowRunId) {
        this.mlflowRunId = mlflowRunId;
    }

    public String getWeightsPath() {
        return weightsPath;
    }

    public void setWeightsPath(String weightsPath) {
        this.weightsPath = weightsPath;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public DatasetVersionSummary getTrainedOnDatasetVersion() {
        return trainedOnDatasetVersion;
    }

    public void setTrainedOnDatasetVersion(DatasetVersionSummary trainedOnDatasetVersion) {
        this.trainedOnDatasetVersion = trainedOnDatasetVersion;
    }
}
