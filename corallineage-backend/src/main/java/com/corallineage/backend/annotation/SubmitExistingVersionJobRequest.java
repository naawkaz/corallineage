package com.corallineage.backend.annotation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * Warm-start blank-import submit request: annotates an existing dataset version's already-present
 * images (no upload, no new staging version) with a chosen model, structurally between Mode A
 * ("Validate") and Mode B ("New Images") — see AnnotationJobService.submitExistingVersionJob.
 */
public class SubmitExistingVersionJobRequest {

    @NotNull
    private Long modelVersionId;

    @NotNull
    private Long datasetVersionId;

    @NotEmpty
    private List<Long> imageIds;

    private Integer gridRows;

    private Integer gridCols;

    private BigDecimal confidenceThreshold;

    @NotBlank
    private String initiatedBy;

    public Long getModelVersionId() { return modelVersionId; }
    public void setModelVersionId(Long modelVersionId) { this.modelVersionId = modelVersionId; }

    public Long getDatasetVersionId() { return datasetVersionId; }
    public void setDatasetVersionId(Long datasetVersionId) { this.datasetVersionId = datasetVersionId; }

    public List<Long> getImageIds() { return imageIds; }
    public void setImageIds(List<Long> imageIds) { this.imageIds = imageIds; }

    public Integer getGridRows() { return gridRows; }
    public void setGridRows(Integer gridRows) { this.gridRows = gridRows; }

    public Integer getGridCols() { return gridCols; }
    public void setGridCols(Integer gridCols) { this.gridCols = gridCols; }

    public BigDecimal getConfidenceThreshold() { return confidenceThreshold; }
    public void setConfidenceThreshold(BigDecimal confidenceThreshold) { this.confidenceThreshold = confidenceThreshold; }

    public String getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(String initiatedBy) { this.initiatedBy = initiatedBy; }
}
