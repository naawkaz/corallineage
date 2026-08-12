package com.corallineage.backend.training;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class InitiateTrainingRequest {

    @NotNull
    private Long datasetId;

    private Long datasetVersionId;

    private String architecture;

    private Double learningRate;

    private Integer batchSize;

    private Integer epochs;

    @NotBlank
    private String initiatedBy;

    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }

    public Long getDatasetVersionId() { return datasetVersionId; }
    public void setDatasetVersionId(Long datasetVersionId) { this.datasetVersionId = datasetVersionId; }

    public String getArchitecture() { return architecture; }
    public void setArchitecture(String architecture) { this.architecture = architecture; }

    public Double getLearningRate() { return learningRate; }
    public void setLearningRate(Double learningRate) { this.learningRate = learningRate; }

    public Integer getBatchSize() { return batchSize; }
    public void setBatchSize(Integer batchSize) { this.batchSize = batchSize; }

    public Integer getEpochs() { return epochs; }
    public void setEpochs(Integer epochs) { this.epochs = epochs; }

    public String getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(String initiatedBy) { this.initiatedBy = initiatedBy; }
}
