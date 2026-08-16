package com.corallineage.backend.datasets;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/datasets")
public class DatasetController {

    private final DatasetService datasetService;
    private final ProjectMemberService projectMemberService;

    public DatasetController(DatasetService datasetService, ProjectMemberService projectMemberService) {
        this.datasetService = datasetService;
        this.projectMemberService = projectMemberService;
    }

    @GetMapping
    public List<DatasetResponse> getDatasets(Authentication authentication) {
        return datasetService.getAllDatasets(authentication.getName());
    }

    @GetMapping("/{id}")
    public DatasetResponse getDataset(@PathVariable Long id) {
        return datasetService.getDatasetById(id);
    }

    @PostMapping
    public ResponseEntity<DatasetResponse> createDataset(@Valid @RequestBody CreateDatasetRequest request,
                                                           Authentication authentication) {
        DatasetResponse response = datasetService.createDataset(request, authentication.getName());
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
