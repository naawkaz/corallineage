package com.corallineage.backend.annotation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Mode A ("Validate against human labels") submit request: an existing dataset version's
 * real, already-labeled images/points are checked against a model's predictions, purely
 * as an accuracy recheck, not a labeling workflow.
 */
public class SubmitValidationJobRequest {

    @NotNull
    private Long modelVersionId;

    @NotNull
    private Long datasetVersionId;

    @NotEmpty
    private List<Long> imageIds;

    @NotBlank
    private String initiatedBy;

    public Long getModelVersionId() { return modelVersionId; }
    public void setModelVersionId(Long modelVersionId) { this.modelVersionId = modelVersionId; }

    public Long getDatasetVersionId() { return datasetVersionId; }
    public void setDatasetVersionId(Long datasetVersionId) { this.datasetVersionId = datasetVersionId; }

    public List<Long> getImageIds() { return imageIds; }
    public void setImageIds(List<Long> imageIds) { this.imageIds = imageIds; }

    public String getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(String initiatedBy) { this.initiatedBy = initiatedBy; }
}
