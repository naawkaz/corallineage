package com.corallineage.backend.versioning.dataset;

public class LabelDefinitionResponse {

    private String labelCode;
    private String labelName;
    private String labelCategory;
    private boolean deprecated;

    public String getLabelCode() { return labelCode; }
    public void setLabelCode(String labelCode) { this.labelCode = labelCode; }

    public String getLabelName() { return labelName; }
    public void setLabelName(String labelName) { this.labelName = labelName; }

    public String getLabelCategory() { return labelCategory; }
    public void setLabelCategory(String labelCategory) { this.labelCategory = labelCategory; }

    public boolean isDeprecated() { return deprecated; }
    public void setDeprecated(boolean deprecated) { this.deprecated = deprecated; }
}
