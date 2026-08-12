package com.corallineage.backend.ontology;

import jakarta.validation.constraints.NotBlank;

public class ResolveDirtyAnnotationRequest {

    @NotBlank(message = "newLabelCode is required")
    private String newLabelCode;

    @NotBlank(message = "resolvedBy is required")
    private String resolvedBy;

    public String getNewLabelCode() { return newLabelCode; }
    public void setNewLabelCode(String newLabelCode) { this.newLabelCode = newLabelCode; }

    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }
}
