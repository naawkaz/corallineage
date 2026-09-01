package com.corallineage.backend.versioning.dataset;

/** Lightweight point and label projection for the image grid overlay */
public class AnnotationPointSummaryResponse {

    private Long id;
    private Long imageId;
    private Integer pointIndex;
    private Integer rowPosition;
    private Integer colPosition;
    private String labelCode;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getImageId() { return imageId; }
    public void setImageId(Long imageId) { this.imageId = imageId; }

    public Integer getPointIndex() { return pointIndex; }
    public void setPointIndex(Integer pointIndex) { this.pointIndex = pointIndex; }

    public Integer getRowPosition() { return rowPosition; }
    public void setRowPosition(Integer rowPosition) { this.rowPosition = rowPosition; }

    public Integer getColPosition() { return colPosition; }
    public void setColPosition(Integer colPosition) { this.colPosition = colPosition; }

    public String getLabelCode() { return labelCode; }
    public void setLabelCode(String labelCode) { this.labelCode = labelCode; }
}
