package com.corallineage.backend.pointannotation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class StartPointAnnotationJobRequest {

    @NotNull
    private Long datasetVersionId;

    private Integer pointsPerImage;

    private BigDecimal confidenceThreshold;

    private Integer seedImageCount;

    @NotBlank
    private String initiatedBy;

    public Long getDatasetVersionId() { return datasetVersionId; }
    public void setDatasetVersionId(Long datasetVersionId) { this.datasetVersionId = datasetVersionId; }

    public Integer getPointsPerImage() { return pointsPerImage; }
    public void setPointsPerImage(Integer pointsPerImage) { this.pointsPerImage = pointsPerImage; }

    public BigDecimal getConfidenceThreshold() { return confidenceThreshold; }
    public void setConfidenceThreshold(BigDecimal confidenceThreshold) { this.confidenceThreshold = confidenceThreshold; }

    public Integer getSeedImageCount() { return seedImageCount; }
    public void setSeedImageCount(Integer seedImageCount) { this.seedImageCount = seedImageCount; }

    public String getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(String initiatedBy) { this.initiatedBy = initiatedBy; }
}
