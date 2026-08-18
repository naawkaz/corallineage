package com.corallineage.backend.annotation;

import com.corallineage.backend.datasets.DatasetService;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/annotation")
public class AnnotationJobController {

    private final AnnotationJobService annotationJobService;
    private final DatasetService datasetService;

    public AnnotationJobController(AnnotationJobService annotationJobService, DatasetService datasetService) {
        this.annotationJobService = annotationJobService;
        this.datasetService = datasetService;
    }

    @PostMapping("/jobs")
    public ResponseEntity<AnnotationJobResponse> submitAnnotationJob(@Valid @RequestBody InitiateAnnotationJobRequest request,
                                                                       Authentication authentication) {
        AnnotationJobResponse response = annotationJobService.submitAnnotationJob(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /** Mode A: "Validate against human labels" - recheck a model's accuracy against real human-given points. */
    @PostMapping("/jobs/validate")
    public ResponseEntity<AnnotationJobResponse> submitValidationJob(@Valid @RequestBody SubmitValidationJobRequest request,
                                                                       Authentication authentication) {
        AnnotationJobResponse response = annotationJobService.submitValidationJob(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /** Mode B: "Annotate new images" - bulk-label freshly uploaded images not yet part of any dataset version. */
    @PostMapping("/jobs/new-images")
    public ResponseEntity<AnnotationJobResponse> submitNewImagesJob(
            @RequestParam Long modelVersionId,
            @RequestParam Long datasetId,
            @RequestParam String initiatedBy,
            @RequestParam(defaultValue = "12") int pointsPerImage,
            @RequestParam MultipartFile[] files,
            Authentication authentication) {
        datasetService.requireAccess(datasetId, authentication.getName());
        AnnotationJobResponse response = annotationJobService.submitNewImagesJob(
                modelVersionId, datasetId, initiatedBy, pointsPerImage, files);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /** Commits a fully-reviewed Mode B job's images and final point labels into a new, real dataset version. */
    @PostMapping("/jobs/{jobId}/add-to-project")
    public AnnotationJobResponse addToProject(@PathVariable Long jobId, @Valid @RequestBody AddToProjectRequest request,
                                               Authentication authentication) {
        Long datasetId = annotationJobService.getDatasetIdForJob(jobId);
        datasetService.requireAccess(datasetId, authentication.getName());
        return annotationJobService.addToProject(jobId, request);
    }

    @GetMapping("/jobs")
    public Page<AnnotationJobResponse> getJobs(
            @RequestParam Long datasetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        datasetService.requireAccess(datasetId, authentication.getName());
        return annotationJobService.getJobs(datasetId, PageRequest.of(page, size));
    }

    @GetMapping("/jobs/{id}")
    public AnnotationJobResponse getJob(@PathVariable Long id, Authentication authentication) {
        Long datasetId = annotationJobService.getDatasetIdForJob(id);
        datasetService.requireAccess(datasetId, authentication.getName());
        return annotationJobService.getJobById(id);
    }

    @GetMapping("/jobs/{id}/predictions")
    public Page<AnnotationPredictionResponse> getPredictions(
            @PathVariable Long id,
            @RequestParam(required = false) Long imageId,
            @RequestParam(required = false) Boolean accepted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        Long datasetId = annotationJobService.getDatasetIdForJob(id);
        datasetService.requireAccess(datasetId, authentication.getName());
        Pageable pageable = PageRequest.of(page, size);
        return annotationJobService.getPredictions(id, imageId, accepted, pageable);
    }

    @PatchMapping("/jobs/{jobId}/predictions/{predictionId}/review")
    public AnnotationPredictionResponse reviewPrediction(
            @PathVariable Long jobId,
            @PathVariable Long predictionId,
            @Valid @RequestBody ReviewPredictionRequest request,
            Authentication authentication) {
        Long datasetId = annotationJobService.getDatasetIdForJob(jobId);
        datasetService.requireAccess(datasetId, authentication.getName());
        return annotationJobService.reviewPrediction(jobId, predictionId, request);
    }
}
