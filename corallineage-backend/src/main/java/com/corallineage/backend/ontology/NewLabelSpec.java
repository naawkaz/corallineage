package com.corallineage.backend.ontology;

import jakarta.validation.constraints.NotBlank;

/** One new LabelDefinition to create as part of an ontology change (ADDED/RENAMED/SPLIT). */
public class NewLabelSpec {

    @NotBlank(message = "code is required")
    private String code;

    @NotBlank(message = "name is required")
    private String name;

    private String category;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
