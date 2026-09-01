package com.corallineage.backend.pointannotation;

/** Optional hyperparameter overrides for training a point annotation job */
public class TrainPointAnnotationJobRequest {

    private String architecture;
    private Double learningRate;
    private Integer batchSize;
    private Integer epochs;

    public String getArchitecture() { return architecture; }
    public void setArchitecture(String architecture) { this.architecture = architecture; }

    public Double getLearningRate() { return learningRate; }
    public void setLearningRate(Double learningRate) { this.learningRate = learningRate; }

    public Integer getBatchSize() { return batchSize; }
    public void setBatchSize(Integer batchSize) { this.batchSize = batchSize; }

    public Integer getEpochs() { return epochs; }
    public void setEpochs(Integer epochs) { this.epochs = epochs; }
}
