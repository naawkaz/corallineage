package com.corallineage.backend.projects;

import org.springframework.stereotype.Component;

@Component
public class ProjectMemberMapper {

    public ProjectMemberResponse toResponse(ProjectMember member) {
        ProjectMemberResponse response = new ProjectMemberResponse();
        response.setUserId(member.getUser().getId());
        response.setUsername(member.getUser().getUsername());
        response.setDisplayName(member.getUser().getDisplayName());
        response.setRole(member.getRole());
        response.setAddedAt(member.getCreatedAt());
        response.setSourceAccessRestricted(member.isSourceAccessRestricted());
        return response;
    }
}
