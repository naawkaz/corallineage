package com.corallineage.backend.versioning.dataset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateLabelDefinitionRequest {

    @NotBlank(message = "labelCode is required")
    @Size(max = 100, message = "labelCode must be at most 100 characters")
    private String labelCode;

    @NotBlank(message = "labelName is required")
    @Size(max = 255, message = "labelName must be at most 255 characters")
    private String labelName;

    private String labelCategory;

    public String getLabelCode() { return labelCode; }
    public void setLabelCode(String labelCode) { this.labelCode = labelCode; }

    public String getLabelName() { return labelName; }
    public void setLabelName(String labelName) { this.labelName = labelName; }

    public String getLabelCategory() { return labelCategory; }
    public void setLabelCategory(String labelCategory) { this.labelCategory = labelCategory; }
}
