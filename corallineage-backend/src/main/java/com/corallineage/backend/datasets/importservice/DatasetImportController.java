package com.corallineage.backend.datasets.importservice;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/datasets/{datasetId}/import")
public class DatasetImportController {

    private final DatasetImportService datasetImportService;

    public DatasetImportController(DatasetImportService datasetImportService) {
        this.datasetImportService = datasetImportService;
    }

    @PostMapping
    public ResponseEntity<ImportJobResponse> submitImport(
            @PathVariable Long datasetId,
            @RequestParam MultipartFile[] files,
            @RequestParam String[] relativePaths,
            @RequestParam ImportFormat format,
            @RequestParam @NotBlank String versionTag,
            @RequestParam @NotBlank String importedBy,
            @RequestParam(required = false) String notes) {
        ImportJobResponse response = datasetImportService.submitImport(
                datasetId, files, relativePaths, format, versionTag, importedBy, notes);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{importJobId}")
    public ImportJobResponse getImportJob(@PathVariable Long datasetId, @PathVariable Long importJobId) {
        return datasetImportService.getImportJob(datasetId, importJobId);
    }
}
