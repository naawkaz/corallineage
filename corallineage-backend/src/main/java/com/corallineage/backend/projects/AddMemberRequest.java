package com.corallineage.backend.projects;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class AddMemberRequest {

    @NotBlank(message = "username is required")
    private String username;

    @NotNull(message = "role is required")
    private ProjectRole role;

    /**
     * Source (dataset) ids this member should have access to. Optional; when omitted or
     * when it names every current Source in the project, the member is unrestricted
     * (sees all current and future Sources). When it names a proper subset, the member
     * is restricted to exactly those Sources.
     */
    private List<Long> sourceIds;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public ProjectRole getRole() { return role; }
    public void setRole(ProjectRole role) { this.role = role; }

    public List<Long> getSourceIds() { return sourceIds; }
    public void setSourceIds(List<Long> sourceIds) { this.sourceIds = sourceIds; }
}
