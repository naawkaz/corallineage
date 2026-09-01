package com.corallineage.backend.datasets.importservice;

/** One label declared upfront for a blank import, category is optional */
public class LabelDeclarationRequest {

    private String labelCode;
    private String labelName;
    private String labelCategory;

    public LabelDeclarationRequest() {
    }

    public LabelDeclarationRequest(String labelCode, String labelName, String labelCategory) {
        this.labelCode = labelCode;
        this.labelName = labelName;
        this.labelCategory = labelCategory;
    }

    public String getLabelCode() {
        return labelCode;
    }

    public void setLabelCode(String labelCode) {
        this.labelCode = labelCode;
    }

    public String getLabelName() {
        return labelName;
    }

    public void setLabelName(String labelName) {
        this.labelName = labelName;
    }

    public String getLabelCategory() {
        return labelCategory;
    }

    public void setLabelCategory(String labelCategory) {
        this.labelCategory = labelCategory;
    }
}
