package com.corallineage.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateDatasetRequest {

    @NotBlank(message = "datasetName is required")
    @Size(max = 255, message = "datasetName must be at most 255 characters")
    private String datasetName;

    private String description;

    @NotBlank(message = "createdBy is required")
    @Size(max = 100, message = "createdBy must be at most 100 characters")
    private String createdBy;

    public String getDatasetName() { return datasetName; }
    public void setDatasetName(String datasetName) { this.datasetName = datasetName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
