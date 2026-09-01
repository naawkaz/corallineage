package com.corallineage.backend.projects;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class AddMemberRequest {

    @NotBlank(message = "username is required")
    private String username;

    @NotNull(message = "role is required")
    private ProjectRole role;

    /** Source ids this member can access, omitted means unrestricted */
    private List<Long> sourceIds;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public ProjectRole getRole() { return role; }
    public void setRole(ProjectRole role) { this.role = role; }

    public List<Long> getSourceIds() { return sourceIds; }
    public void setSourceIds(List<Long> sourceIds) { this.sourceIds = sourceIds; }
}
