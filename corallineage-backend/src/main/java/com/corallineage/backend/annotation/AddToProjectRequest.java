package com.corallineage.backend.annotation;

import jakarta.validation.constraints.NotBlank;

/**
 * Mode B ("Annotate new images") completion request: commits a fully-reviewed job's images
 * and final (accepted/corrected) point labels into a new, real dataset version on the project.
 */
public class AddToProjectRequest {

    @NotBlank
    private String versionTag;

    private String notes;

    @NotBlank
    private String createdBy;

    public String getVersionTag() { return versionTag; }
    public void setVersionTag(String versionTag) { this.versionTag = versionTag; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
