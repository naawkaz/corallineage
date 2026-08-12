package com.corallineage.backend.registry.model;

import jakarta.validation.constraints.NotBlank;

public class UpdateModelStatusRequest {

    @NotBlank
    private String status;

    @NotBlank
    private String changedBy;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }
}
