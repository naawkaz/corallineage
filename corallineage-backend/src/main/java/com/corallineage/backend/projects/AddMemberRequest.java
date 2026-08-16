package com.corallineage.backend.projects;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class AddMemberRequest {

    @NotBlank(message = "username is required")
    private String username;

    @NotNull(message = "role is required")
    private ProjectRole role;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public ProjectRole getRole() { return role; }
    public void setRole(ProjectRole role) { this.role = role; }
}
