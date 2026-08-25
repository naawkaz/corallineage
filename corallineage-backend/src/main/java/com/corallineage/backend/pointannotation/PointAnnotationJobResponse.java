package com.corallineage.backend.pointannotation;

import java.math.BigDecimal;
import java.time.Instant;

public class PointAnnotationJobResponse {

    private Long jobId;
    private Long datasetVersionId;
    private String versionTag;
    private Integer gridRows;
    private Integer gridCols;
    private BigDecimal confidenceThreshold;
    private Integer seedImageCount;
    private String status;
    private int totalImages;
    private long seedTotalPoints;
    private long seedLabeledPoints;
    private Long trainingJobId;
    private Long annotationJobId;
    private Long modelVersionId;
    private String modelTag;
    private String initiatedBy;
    private Instant createdAt;
    private String errorMessage;
    private Integer autoAnnotationProcessedCount;
    private Integer autoAnnotationTotalCount;
    private Long autoAnnotationCurrentPointId;

    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }

    public Long getDatasetVersionId() { return datasetVersionId; }
    public void setDatasetVersionId(Long datasetVersionId) { this.datasetVersionId = datasetVersionId; }

    public String getVersionTag() { return versionTag; }
    public void setVersionTag(String versionTag) { this.versionTag = versionTag; }

    public Integer getGridRows() { return gridRows; }
    public void setGridRows(Integer gridRows) { this.gridRows = gridRows; }

    public Integer getGridCols() { return gridCols; }
    public void setGridCols(Integer gridCols) { this.gridCols = gridCols; }

    public BigDecimal getConfidenceThreshold() { return confidenceThreshold; }
    public void setConfidenceThreshold(BigDecimal confidenceThreshold) { this.confidenceThreshold = confidenceThreshold; }

    public Integer getSeedImageCount() { return seedImageCount; }
    public void setSeedImageCount(Integer seedImageCount) { this.seedImageCount = seedImageCount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getTotalImages() { return totalImages; }
    public void setTotalImages(int totalImages) { this.totalImages = totalImages; }

    public long getSeedTotalPoints() { return seedTotalPoints; }
    public void setSeedTotalPoints(long seedTotalPoints) { this.seedTotalPoints = seedTotalPoints; }

    public long getSeedLabeledPoints() { return seedLabeledPoints; }
    public void setSeedLabeledPoints(long seedLabeledPoints) { this.seedLabeledPoints = seedLabeledPoints; }

    public Long getTrainingJobId() { return trainingJobId; }
    public void setTrainingJobId(Long trainingJobId) { this.trainingJobId = trainingJobId; }

    public Long getAnnotationJobId() { return annotationJobId; }
    public void setAnnotationJobId(Long annotationJobId) { this.annotationJobId = annotationJobId; }

    public Long getModelVersionId() { return modelVersionId; }
    public void setModelVersionId(Long modelVersionId) { this.modelVersionId = modelVersionId; }

    public String getModelTag() { return modelTag; }
    public void setModelTag(String modelTag) { this.modelTag = modelTag; }

    public String getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(String initiatedBy) { this.initiatedBy = initiatedBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Integer getAutoAnnotationProcessedCount() { return autoAnnotationProcessedCount; }
    public void setAutoAnnotationProcessedCount(Integer autoAnnotationProcessedCount) { this.autoAnnotationProcessedCount = autoAnnotationProcessedCount; }

    public Integer getAutoAnnotationTotalCount() { return autoAnnotationTotalCount; }
    public void setAutoAnnotationTotalCount(Integer autoAnnotationTotalCount) { this.autoAnnotationTotalCount = autoAnnotationTotalCount; }

    public Long getAutoAnnotationCurrentPointId() { return autoAnnotationCurrentPointId; }
    public void setAutoAnnotationCurrentPointId(Long autoAnnotationCurrentPointId) { this.autoAnnotationCurrentPointId = autoAnnotationCurrentPointId; }
}
