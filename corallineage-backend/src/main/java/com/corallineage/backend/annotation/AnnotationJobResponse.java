package com.corallineage.backend.annotation;

import java.math.BigDecimal;
import java.time.Instant;

public class AnnotationJobResponse {

    private Long jobId;
    private String status;
    private String reviewStatus;
    private Long modelVersionId;
    private String modelTag;
    private Integer imageCount;
    private String initiatedBy;
    private Instant initiatedAt;
    private Instant completedAt;
    private Long totalPredictions;
    private Long reviewedPredictions;
    private String errorMessage;
    private String jobType;
    private Long datasetVersionId;
    private Long correctPredictions;
    private Double accuracyPercent;
    private Long addedToProjectVersionId;
    private BigDecimal confidenceThreshold;
    private boolean commitInPlace;

    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }

    public Long getModelVersionId() { return modelVersionId; }
    public void setModelVersionId(Long modelVersionId) { this.modelVersionId = modelVersionId; }

    public String getModelTag() { return modelTag; }
    public void setModelTag(String modelTag) { this.modelTag = modelTag; }

    public Integer getImageCount() { return imageCount; }
    public void setImageCount(Integer imageCount) { this.imageCount = imageCount; }

    public String getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(String initiatedBy) { this.initiatedBy = initiatedBy; }

    public Instant getInitiatedAt() { return initiatedAt; }
    public void setInitiatedAt(Instant initiatedAt) { this.initiatedAt = initiatedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Long getTotalPredictions() { return totalPredictions; }
    public void setTotalPredictions(Long totalPredictions) { this.totalPredictions = totalPredictions; }

    public Long getReviewedPredictions() { return reviewedPredictions; }
    public void setReviewedPredictions(Long reviewedPredictions) { this.reviewedPredictions = reviewedPredictions; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getJobType() { return jobType; }
    public void setJobType(String jobType) { this.jobType = jobType; }

    public Long getDatasetVersionId() { return datasetVersionId; }
    public void setDatasetVersionId(Long datasetVersionId) { this.datasetVersionId = datasetVersionId; }

    public Long getCorrectPredictions() { return correctPredictions; }
    public void setCorrectPredictions(Long correctPredictions) { this.correctPredictions = correctPredictions; }

    public Double getAccuracyPercent() { return accuracyPercent; }
    public void setAccuracyPercent(Double accuracyPercent) { this.accuracyPercent = accuracyPercent; }

    public Long getAddedToProjectVersionId() { return addedToProjectVersionId; }
    public void setAddedToProjectVersionId(Long addedToProjectVersionId) { this.addedToProjectVersionId = addedToProjectVersionId; }

    public BigDecimal getConfidenceThreshold() { return confidenceThreshold; }
    public void setConfidenceThreshold(BigDecimal confidenceThreshold) { this.confidenceThreshold = confidenceThreshold; }

    public boolean isCommitInPlace() { return commitInPlace; }
    public void setCommitInPlace(boolean commitInPlace) { this.commitInPlace = commitInPlace; }
}
