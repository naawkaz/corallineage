package com.corallineage.backend.annotation;

import com.corallineage.backend.datasets.DatasetService;
import com.corallineage.backend.registry.model.ModelVersionService;
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

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/annotation")
public class AnnotationJobController {

    private final AnnotationJobService annotationJobService;
    private final DatasetService datasetService;
    private final ModelVersionService modelVersionService;

    public AnnotationJobController(AnnotationJobService annotationJobService, DatasetService datasetService,
                                    ModelVersionService modelVersionService) {
        this.annotationJobService = annotationJobService;
        this.datasetService = datasetService;
        this.modelVersionService = modelVersionService;
    }

    @PostMapping("/jobs")
    public ResponseEntity<AnnotationJobResponse> submitAnnotationJob(@Valid @RequestBody InitiateAnnotationJobRequest request,
                                                                       Authentication authentication) {
        AnnotationJobResponse response = annotationJobService.submitAnnotationJob(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /** Rechecks a model's accuracy against human labeled points */
    @PostMapping("/jobs/validate")
    public ResponseEntity<AnnotationJobResponse> submitValidationJob(@Valid @RequestBody SubmitValidationJobRequest request,
                                                                       Authentication authentication) {
        AnnotationJobResponse response = annotationJobService.submitValidationJob(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /** Bulk labels freshly uploaded images */
    @PostMapping("/jobs/new-images")
    public ResponseEntity<AnnotationJobResponse> submitNewImagesJob(
            @RequestParam Long modelVersionId,
            @RequestParam Long datasetId,
            @RequestParam String initiatedBy,
            @RequestParam(defaultValue = "3") int gridRows,
            @RequestParam(defaultValue = "4") int gridCols,
            @RequestParam(required = false) BigDecimal confidenceThreshold,
            @RequestParam MultipartFile[] files,
            Authentication authentication) {
        datasetService.requireAccess(datasetId, authentication.getName());
        AnnotationJobResponse response = annotationJobService.submitNewImagesJob(
                modelVersionId, datasetId, initiatedBy, gridRows, gridCols, confidenceThreshold, files);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /** Creates a dataset version from a reviewed job's images and labels */
    @PostMapping("/jobs/{jobId}/add-to-project")
    public AnnotationJobResponse addToProject(@PathVariable Long jobId, @Valid @RequestBody AddToProjectRequest request,
                                               Authentication authentication) {
        Long datasetId = annotationJobService.getDatasetIdForJob(jobId);
        datasetService.requireAccess(datasetId, authentication.getName());
        return annotationJobService.addToProject(jobId, request);
    }

    /** Annotates an existing dataset version's images with a chosen model */
    @PostMapping("/jobs/existing-version")
    public ResponseEntity<AnnotationJobResponse> submitExistingVersionJob(
            @Valid @RequestBody SubmitExistingVersionJobRequest request, Authentication authentication) {
        AnnotationJobResponse response = annotationJobService.submitExistingVersionJob(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /** Applies a reviewed job's predictions onto its dataset version */
    @PostMapping("/jobs/{jobId}/apply-to-version")
    public AnnotationJobResponse applyToExistingVersion(@PathVariable Long jobId, Authentication authentication) {
        Long datasetId = annotationJobService.getDatasetIdForJob(jobId);
        datasetService.requireAccess(datasetId, authentication.getName());
        return annotationJobService.applyToExistingVersion(jobId, authentication.getName());
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

    /** Lists jobs for one model */
    @GetMapping("/jobs/by-model")
    public Page<AnnotationJobResponse> getJobsByModel(
            @RequestParam Long modelVersionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        datasetService.requireAccess(modelVersionService.getDatasetIdForModel(modelVersionId), authentication.getName());
        return annotationJobService.getJobsByModel(modelVersionId, PageRequest.of(page, size));
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

    /** Accepts every pending prediction on a job */
    @PostMapping("/jobs/{jobId}/predictions/accept-all")
    public AnnotationJobResponse acceptAllPredictions(
            @PathVariable Long jobId,
            @Valid @RequestBody AcceptAllPredictionsRequest request,
            Authentication authentication) {
        Long datasetId = annotationJobService.getDatasetIdForJob(jobId);
        datasetService.requireAccess(datasetId, authentication.getName());
        return annotationJobService.acceptAllPredictions(jobId, request.getReviewedBy());
    }
}
