package com.corallineage.backend.ontology;

import java.time.Instant;

public class DirtyAnnotationResponse {

    private Long id;
    private Long imageId;
    private String filename;
    private Integer pointIndex;
    private String currentLabelCode;
    private Long ontologyChangeId;
    private Instant flaggedAt;
    private boolean resolved;
    private Instant resolvedAt;
    private String resolvedBy;
    private String newLabelCode;
    private Integer rowPosition;
    private Integer colPosition;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getImageId() {
        return imageId;
    }

    public void setImageId(Long imageId) {
        this.imageId = imageId;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public Integer getPointIndex() {
        return pointIndex;
    }

    public void setPointIndex(Integer pointIndex) {
        this.pointIndex = pointIndex;
    }

    public String getCurrentLabelCode() {
        return currentLabelCode;
    }

    public void setCurrentLabelCode(String currentLabelCode) {
        this.currentLabelCode = currentLabelCode;
    }

    public Long getOntologyChangeId() {
        return ontologyChangeId;
    }

    public void setOntologyChangeId(Long ontologyChangeId) {
        this.ontologyChangeId = ontologyChangeId;
    }

    public Instant getFlaggedAt() {
        return flaggedAt;
    }

    public void setFlaggedAt(Instant flaggedAt) {
        this.flaggedAt = flaggedAt;
    }

    public boolean isResolved() {
        return resolved;
    }

    public void setResolved(boolean resolved) {
        this.resolved = resolved;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(String resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public String getNewLabelCode() {
        return newLabelCode;
    }

    public void setNewLabelCode(String newLabelCode) {
        this.newLabelCode = newLabelCode;
    }

    public Integer getRowPosition() {
        return rowPosition;
    }

    public void setRowPosition(Integer rowPosition) {
        this.rowPosition = rowPosition;
    }

    public Integer getColPosition() {
        return colPosition;
    }

    public void setColPosition(Integer colPosition) {
        this.colPosition = colPosition;
    }
}
