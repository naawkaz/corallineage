package com.corallineage.backend.pointannotation;

import jakarta.validation.constraints.NotBlank;

public class LabelPointRequest {

    @NotBlank
    private String labelCode;

    @NotBlank
    private String annotatedBy;

    public String getLabelCode() { return labelCode; }
    public void setLabelCode(String labelCode) { this.labelCode = labelCode; }

    public String getAnnotatedBy() { return annotatedBy; }
    public void setAnnotatedBy(String annotatedBy) { this.annotatedBy = annotatedBy; }
}
