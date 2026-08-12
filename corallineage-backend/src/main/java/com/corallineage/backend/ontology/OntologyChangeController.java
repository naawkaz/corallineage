package com.corallineage.backend.ontology;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ontology")
public class OntologyChangeController {

    private final OntologyChangeService ontologyChangeService;

    public OntologyChangeController(OntologyChangeService ontologyChangeService) {
        this.ontologyChangeService = ontologyChangeService;
    }

    @GetMapping("/changes")
    public Page<OntologyChangeResponse> getChanges(
            @RequestParam(required = false) Long datasetVersionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ontologyChangeService.getChanges(datasetVersionId, PageRequest.of(page, size));
    }

    @PostMapping("/changes")
    public ResponseEntity<OntologyChangeResponse> recordChange(@Valid @RequestBody RecordOntologyChangeRequest request) {
        OntologyChangeResponse response = ontologyChangeService.recordChange(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/dirty")
    public Page<DirtyAnnotationResponse> getDirtyAnnotations(
            @RequestParam(required = false) Long ontologyChangeId,
            @RequestParam(required = false) Long imageId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ontologyChangeService.getDirtyAnnotations(ontologyChangeId, imageId, pageable);
    }

    @PatchMapping("/dirty/{id}/resolve")
    public DirtyAnnotationResponse resolveDirtyAnnotation(
            @PathVariable Long id,
            @Valid @RequestBody ResolveDirtyAnnotationRequest request) {
        return ontologyChangeService.resolveDirtyAnnotation(id, request);
    }
}
