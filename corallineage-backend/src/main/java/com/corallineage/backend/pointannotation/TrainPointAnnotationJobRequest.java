package com.corallineage.backend.pointannotation;

/**
 * Optional hyperparameter overrides for {@link PointAnnotationJobService#trainModel}, mirroring
 * {@code InitiateTrainingRequest}'s own architecture/learningRate/batchSize/epochs fields - lets
 * the frontend reuse the exact same "Train New Model" dialog (and its submit shape) for a
 * cold-start point-annotation job's training step, not just the standalone Model Registry flow.
 * All fields optional; {@code TrainingJobService.initiateTrainingJob} already defaults any left null.
 */
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
