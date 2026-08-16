package com.corallineage.backend.datasets.importservice;

/**
 * One label declared upfront on the "Blank / Unlabeled Dataset" import path: code and name are
 * both required (mirrors LabelDefinition's own NOT NULL columns), category is optional. Built by
 * DatasetImportController from parallel multipart form fields, either typed manually or copied
 * from another project's version via the frontend's "Reuse labels from" flow.
 */
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

    public String getLabelCode() { return labelCode; }
    public void setLabelCode(String labelCode) { this.labelCode = labelCode; }

    public String getLabelName() { return labelName; }
    public void setLabelName(String labelName) { this.labelName = labelName; }

    public String getLabelCategory() { return labelCategory; }
    public void setLabelCategory(String labelCategory) { this.labelCategory = labelCategory; }
}
