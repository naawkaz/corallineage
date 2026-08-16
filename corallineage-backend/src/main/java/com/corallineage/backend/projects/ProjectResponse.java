package com.corallineage.backend.projects;

import java.time.Instant;

public class ProjectResponse {

    private Long id;
    private String projectName;
    private String description;
    private String createdBy;
    private Instant createdAt;
    private long sourceCount;
    private ProjectRole callerRole;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public long getSourceCount() { return sourceCount; }
    public void setSourceCount(long sourceCount) { this.sourceCount = sourceCount; }

    public ProjectRole getCallerRole() { return callerRole; }
    public void setCallerRole(ProjectRole callerRole) { this.callerRole = callerRole; }
}
