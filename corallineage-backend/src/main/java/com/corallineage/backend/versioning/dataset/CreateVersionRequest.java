package com.corallineage.backend.versioning.dataset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreateVersionRequest {

    @NotBlank(message = "versionTag is required")
    @Size(max = 50, message = "versionTag must be at most 50 characters")
    private String versionTag;

    @NotNull(message = "triggerReason is required")
    private TriggerReason triggerReason;

    @NotBlank(message = "createdBy is required")
    @Size(max = 100, message = "createdBy must be at most 100 characters")
    private String createdBy;

    private String notes;

    public String getVersionTag() {
        return versionTag;
    }

    public void setVersionTag(String versionTag) {
        this.versionTag = versionTag;
    }

    public TriggerReason getTriggerReason() {
        return triggerReason;
    }

    public void setTriggerReason(TriggerReason triggerReason) {
        this.triggerReason = triggerReason;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
