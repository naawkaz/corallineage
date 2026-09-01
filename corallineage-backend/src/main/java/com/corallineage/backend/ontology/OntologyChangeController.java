package com.corallineage.backend.ontology;

import com.corallineage.backend.datasets.DatasetService;
import com.corallineage.backend.projects.ProjectMemberService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/ontology")
public class OntologyChangeController {

    private final OntologyChangeService ontologyChangeService;
    private final DatasetService datasetService;
    private final ProjectMemberService projectMemberService;

    public OntologyChangeController(OntologyChangeService ontologyChangeService,
            DatasetService datasetService,
            ProjectMemberService projectMemberService) {
        this.ontologyChangeService = ontologyChangeService;
        this.datasetService = datasetService;
        this.projectMemberService = projectMemberService;
    }

    @GetMapping("/changes")
    public Page<OntologyChangeResponse> getChanges(
            @RequestParam Long datasetId,
            @RequestParam(required = false) Long datasetVersionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        datasetService.requireAccess(datasetId, authentication.getName());
        return ontologyChangeService.getChanges(datasetId, datasetVersionId, PageRequest.of(page, size));
    }

    @PostMapping("/changes")
    public ResponseEntity<OntologyChangeResponse> recordChange(@Valid @RequestBody RecordOntologyChangeRequest request,
            Authentication authentication) {
        OntologyChangeResponse response = ontologyChangeService.recordChange(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/dirty")
    public Page<DirtyAnnotationResponse> getDirtyAnnotations(
            @RequestParam(required = false) Long ontologyChangeId,
            @RequestParam(required = false) Long imageId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        Pageable pageable = PageRequest.of(page, size);
        Long scopedDatasetId = ontologyChangeService.resolveDatasetIdForDirtyFilter(ontologyChangeId, imageId);
        if (scopedDatasetId != null) {
            datasetService.requireAccess(scopedDatasetId, authentication.getName());
            return ontologyChangeService.getDirtyAnnotations(ontologyChangeId, imageId, pageable);
        }
        // No scoping filter, so filter to the requester's accessible datasets
        Set<Long> accessibleDatasetIds = projectMemberService.getAllAccessibleDatasetIds(authentication.getName());
        return ontologyChangeService.getDirtyAnnotationsAccessibleWithNoFilter(accessibleDatasetIds, pageable);
    }

    @PatchMapping("/dirty/{id}/resolve")
    public DirtyAnnotationResponse resolveDirtyAnnotation(
            @PathVariable Long id,
            @Valid @RequestBody ResolveDirtyAnnotationRequest request,
            Authentication authentication) {
        return ontologyChangeService.resolveDirtyAnnotation(id, request, authentication.getName());
    }
}
