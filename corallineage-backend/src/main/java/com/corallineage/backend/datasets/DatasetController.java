package com.corallineage.backend.datasets;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class DatasetController {

    private final DatasetService datasetService;

    public DatasetController(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    @GetMapping("/api/v1/datasets/{id}")
    public DatasetResponse getDataset(@PathVariable Long id, Authentication authentication) {
        datasetService.requireAccess(id, authentication.getName());
        return datasetService.getDatasetById(id);
    }

    @GetMapping("/api/v1/projects/{projectId}/datasets")
    public List<DatasetResponse> getDatasets(@PathVariable Long projectId, Authentication authentication) {
        return datasetService.getAllDatasets(projectId, authentication.getName());
    }

    @PostMapping("/api/v1/projects/{projectId}/datasets")
    public ResponseEntity<DatasetResponse> createDataset(@PathVariable Long projectId,
            @Valid @RequestBody CreateDatasetRequest request,
            Authentication authentication) {
        DatasetResponse response = datasetService.createDataset(request, projectId, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
