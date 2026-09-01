package com.corallineage.backend.versioning.dataset;

import com.corallineage.backend.datasets.DatasetService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/datasets/{datasetId}/versions")
public class DatasetVersionController {

    private final DatasetVersionService datasetVersionService;
    private final DatasetService datasetService;

    public DatasetVersionController(DatasetVersionService datasetVersionService, DatasetService datasetService) {
        this.datasetVersionService = datasetVersionService;
        this.datasetService = datasetService;
    }

    @GetMapping
    public Page<DatasetVersionResponse> getVersions(
            @PathVariable Long datasetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        datasetService.requireAccess(datasetId, authentication.getName());
        Pageable pageable = PageRequest.of(page, size);
        return datasetVersionService.getVersionHistory(datasetId, pageable);
    }

    @GetMapping("/{id}")
    public DatasetVersionResponse getVersion(@PathVariable Long datasetId, @PathVariable Long id,
            Authentication authentication) {
        datasetService.requireAccess(datasetId, authentication.getName());
        return datasetVersionService.getVersionById(datasetId, id);
    }

    @PostMapping
    public ResponseEntity<DatasetVersionResponse> createVersion(
            @PathVariable Long datasetId,
            @Valid @RequestBody CreateVersionRequest request,
            Authentication authentication) {
        datasetService.requireAccess(datasetId, authentication.getName());
        DatasetVersionResponse response = datasetVersionService.createSnapshot(datasetId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{versionId}/labels")
    public ResponseEntity<LabelDefinitionResponse> addLabelDefinition(
            @PathVariable Long datasetId,
            @PathVariable Long versionId,
            @Valid @RequestBody CreateLabelDefinitionRequest request,
            Authentication authentication) {
        datasetService.requireAccess(datasetId, authentication.getName());
        LabelDefinitionResponse response = datasetVersionService.addLabelDefinition(datasetId, versionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
