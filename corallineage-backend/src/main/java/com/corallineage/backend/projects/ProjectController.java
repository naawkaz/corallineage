package com.corallineage.backend.projects;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectMemberService projectMemberService;

    public ProjectController(ProjectService projectService, ProjectMemberService projectMemberService) {
        this.projectService = projectService;
        this.projectMemberService = projectMemberService;
    }

    @GetMapping
    public List<ProjectResponse> getProjects(Authentication authentication) {
        return projectService.getAllProjects(authentication.getName());
    }

    @GetMapping("/{id}")
    public ProjectResponse getProject(@PathVariable Long id, Authentication authentication) {
        return projectService.getProjectById(id, authentication.getName());
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(@Valid @RequestBody CreateProjectRequest request,
                                                           Authentication authentication) {
        ProjectResponse response = projectService.createProject(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/members")
    public List<ProjectMemberResponse> getMembers(@PathVariable Long id, Authentication authentication) {
        return projectMemberService.listMembers(id, authentication.getName());
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<ProjectMemberResponse> addMember(@PathVariable Long id,
                                                             @Valid @RequestBody AddMemberRequest request,
                                                             Authentication authentication) {
        ProjectMemberResponse response = projectMemberService.addMember(id, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable Long id, @PathVariable Long userId,
                                              Authentication authentication) {
        projectMemberService.removeMember(id, userId, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
