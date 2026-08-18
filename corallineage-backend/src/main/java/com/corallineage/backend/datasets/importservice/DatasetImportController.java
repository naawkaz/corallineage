package com.corallineage.backend.datasets.importservice;

import com.corallineage.backend.datasets.DatasetService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/datasets/{datasetId}/import")
public class DatasetImportController {

    private final DatasetImportService datasetImportService;
    private final DatasetService datasetService;

    public DatasetImportController(DatasetImportService datasetImportService, DatasetService datasetService) {
        this.datasetImportService = datasetImportService;
        this.datasetService = datasetService;
    }

    @PostMapping
    public ResponseEntity<ImportJobResponse> submitImport(
            @PathVariable Long datasetId,
            @RequestParam MultipartFile[] files,
            @RequestParam String[] relativePaths,
            @RequestParam ImportFormat format,
            @RequestParam @NotBlank String versionTag,
            @RequestParam @NotBlank String importedBy,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) String[] labelCodes,
            @RequestParam(required = false) String[] labelNames,
            @RequestParam(required = false) String[] labelCategories,
            Authentication authentication) {
        datasetService.requireAccess(datasetId, authentication.getName());
        List<LabelDeclarationRequest> labels = toLabelDeclarations(labelCodes, labelNames, labelCategories);
        ImportJobResponse response = datasetImportService.submitImport(
                datasetId, files, relativePaths, format, versionTag, importedBy, notes, labels);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    // labelCodes/labelNames/labelCategories are parallel multipart form arrays (one entry per
    // declared label, category left as an empty string rather than omitted so array indices line
    // up) rather than a JSON body, since this endpoint already accepts files as multipart form
    // data. Each label requires a code and a name; an empty-string category is treated as absent.
    private List<LabelDeclarationRequest> toLabelDeclarations(String[] labelCodes, String[] labelNames,
                                                               String[] labelCategories) {
        if (labelCodes == null || labelCodes.length == 0) {
            return List.of();
        }
        List<String> codes = Arrays.asList(labelCodes);
        List<String> names = labelNames != null ? Arrays.asList(labelNames) : List.of();
        List<String> categories = labelCategories != null ? Arrays.asList(labelCategories) : List.of();
        List<LabelDeclarationRequest> declarations = new ArrayList<>();
        for (int i = 0; i < codes.size(); i++) {
            String name = i < names.size() ? names.get(i) : null;
            String category = i < categories.size() ? categories.get(i) : null;
            declarations.add(new LabelDeclarationRequest(
                    codes.get(i), name, (category == null || category.isBlank()) ? null : category));
        }
        return declarations;
    }

    @GetMapping("/{importJobId}")
    public ImportJobResponse getImportJob(@PathVariable Long datasetId, @PathVariable Long importJobId, Authentication authentication) {
        datasetService.requireAccess(datasetId, authentication.getName());
        return datasetImportService.getImportJob(datasetId, importJobId);
    }
}
