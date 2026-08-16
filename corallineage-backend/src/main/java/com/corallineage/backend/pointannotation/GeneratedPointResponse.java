package com.corallineage.backend.pointannotation;

import java.math.BigDecimal;

public class GeneratedPointResponse {

    private Long id;
    private Long imageId;
    private String filename;
    private Integer pointIndex;
    private Integer rowPosition;
    private Integer colPosition;
    private boolean seed;
    private String status;
    private String predictedLabelCode;
    private BigDecimal confidenceScore;
    private String finalLabelCode;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getImageId() { return imageId; }
    public void setImageId(Long imageId) { this.imageId = imageId; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public Integer getPointIndex() { return pointIndex; }
    public void setPointIndex(Integer pointIndex) { this.pointIndex = pointIndex; }

    public Integer getRowPosition() { return rowPosition; }
    public void setRowPosition(Integer rowPosition) { this.rowPosition = rowPosition; }

    public Integer getColPosition() { return colPosition; }
    public void setColPosition(Integer colPosition) { this.colPosition = colPosition; }

    public boolean isSeed() { return seed; }
    public void setSeed(boolean seed) { this.seed = seed; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPredictedLabelCode() { return predictedLabelCode; }
    public void setPredictedLabelCode(String predictedLabelCode) { this.predictedLabelCode = predictedLabelCode; }

    public BigDecimal getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(BigDecimal confidenceScore) { this.confidenceScore = confidenceScore; }

    public String getFinalLabelCode() { return finalLabelCode; }
    public void setFinalLabelCode(String finalLabelCode) { this.finalLabelCode = finalLabelCode; }
}
