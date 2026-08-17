package com.corallineage.backend.projects;

import java.time.Instant;

public class ProjectMemberResponse {

    private Long userId;
    private String username;
    private String displayName;
    private ProjectRole role;
    private Instant addedAt;
    private boolean sourceAccessRestricted;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public ProjectRole getRole() { return role; }
    public void setRole(ProjectRole role) { this.role = role; }

    public Instant getAddedAt() { return addedAt; }
    public void setAddedAt(Instant addedAt) { this.addedAt = addedAt; }

    public boolean isSourceAccessRestricted() { return sourceAccessRestricted; }
    public void setSourceAccessRestricted(boolean sourceAccessRestricted) { this.sourceAccessRestricted = sourceAccessRestricted; }
}
