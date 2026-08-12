package com.corallineage.backend.annotation;

import java.math.BigDecimal;
import java.time.Instant;

public class AnnotationPredictionResponse {

    private Long predictionId;
    private Long imageId;
    private String filename;
    private Integer pointIndex;
    private String predictedLabelCode;
    private BigDecimal confidenceScore;
    private Boolean accepted;
    private String correctedLabelCode;
    private String reviewedBy;
    private Instant reviewedAt;

    public Long getPredictionId() { return predictionId; }
    public void setPredictionId(Long predictionId) { this.predictionId = predictionId; }

    public Long getImageId() { return imageId; }
    public void setImageId(Long imageId) { this.imageId = imageId; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public Integer getPointIndex() { return pointIndex; }
    public void setPointIndex(Integer pointIndex) { this.pointIndex = pointIndex; }

    public String getPredictedLabelCode() { return predictedLabelCode; }
    public void setPredictedLabelCode(String predictedLabelCode) { this.predictedLabelCode = predictedLabelCode; }

    public BigDecimal getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(BigDecimal confidenceScore) { this.confidenceScore = confidenceScore; }

    public Boolean getAccepted() { return accepted; }
    public void setAccepted(Boolean accepted) { this.accepted = accepted; }

    public String getCorrectedLabelCode() { return correctedLabelCode; }
    public void setCorrectedLabelCode(String correctedLabelCode) { this.correctedLabelCode = correctedLabelCode; }

    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }

    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
}
