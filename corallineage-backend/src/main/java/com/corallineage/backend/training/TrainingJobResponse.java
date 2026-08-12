package com.corallineage.backend.training;

import java.time.Instant;

public class TrainingJobResponse {

    private Long jobId;
    private String status;
    private String architecture;
    private String hyperparameters;
    private Long datasetVersionId;
    private String datasetVersionTag;
    private String initiatedBy;
    private Instant initiatedAt;
    private Instant completedAt;
    private Long durationSeconds;
    private Long modelVersionId;
    private String modelTag;
    private String errorMessage;

    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getArchitecture() { return architecture; }
    public void setArchitecture(String architecture) { this.architecture = architecture; }

    public String getHyperparameters() { return hyperparameters; }
    public void setHyperparameters(String hyperparameters) { this.hyperparameters = hyperparameters; }

    public Long getDatasetVersionId() { return datasetVersionId; }
    public void setDatasetVersionId(Long datasetVersionId) { this.datasetVersionId = datasetVersionId; }

    public String getDatasetVersionTag() { return datasetVersionTag; }
    public void setDatasetVersionTag(String datasetVersionTag) { this.datasetVersionTag = datasetVersionTag; }

    public String getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(String initiatedBy) { this.initiatedBy = initiatedBy; }

    public Instant getInitiatedAt() { return initiatedAt; }
    public void setInitiatedAt(Instant initiatedAt) { this.initiatedAt = initiatedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Long getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Long durationSeconds) { this.durationSeconds = durationSeconds; }

    public Long getModelVersionId() { return modelVersionId; }
    public void setModelVersionId(Long modelVersionId) { this.modelVersionId = modelVersionId; }

    public String getModelTag() { return modelTag; }
    public void setModelTag(String modelTag) { this.modelTag = modelTag; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
