package com.corallineage.backend.projects;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateProjectRequest {

    @NotBlank(message = "projectName is required")
    @Size(max = 255, message = "projectName must be at most 255 characters")
    private String projectName;

    private String description;

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
