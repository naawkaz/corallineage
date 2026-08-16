package com.corallineage.backend.projects;

import org.springframework.stereotype.Component;

@Component
public class ProjectMapper {

    public ProjectResponse toResponse(Project project) {
        ProjectResponse response = new ProjectResponse();
        response.setId(project.getId());
        response.setProjectName(project.getProjectName());
        response.setDescription(project.getDescription());
        response.setCreatedBy(project.getCreatedBy());
        response.setCreatedAt(project.getCreatedAt());
        return response;
    }

    public ProjectResponse toSummaryResponse(Project project, long sourceCount, ProjectRole callerRole) {
        ProjectResponse response = toResponse(project);
        response.setSourceCount(sourceCount);
        response.setCallerRole(callerRole);
        return response;
    }
}
