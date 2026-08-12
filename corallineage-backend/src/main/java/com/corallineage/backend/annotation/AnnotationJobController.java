package com.corallineage.backend.annotation;

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
@RequestMapping("/api/v1/annotation")
public class AnnotationJobController {

    private final AnnotationJobService annotationJobService;

    public AnnotationJobController(AnnotationJobService annotationJobService) {
        this.annotationJobService = annotationJobService;
    }

    @PostMapping("/jobs")
    public ResponseEntity<AnnotationJobResponse> submitAnnotationJob(@Valid @RequestBody InitiateAnnotationJobRequest request) {
        AnnotationJobResponse response = annotationJobService.submitAnnotationJob(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/jobs")
    public Page<AnnotationJobResponse> getJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return annotationJobService.getJobs(PageRequest.of(page, size));
    }

    @GetMapping("/jobs/{id}")
    public AnnotationJobResponse getJob(@PathVariable Long id) {
        return annotationJobService.getJobById(id);
    }

    @GetMapping("/jobs/{id}/predictions")
    public Page<AnnotationPredictionResponse> getPredictions(
            @PathVariable Long id,
            @RequestParam(required = false) Long imageId,
            @RequestParam(required = false) Boolean accepted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return annotationJobService.getPredictions(id, imageId, accepted, pageable);
    }

    @PatchMapping("/jobs/{jobId}/predictions/{predictionId}/review")
    public AnnotationPredictionResponse reviewPrediction(
            @PathVariable Long jobId,
            @PathVariable Long predictionId,
            @Valid @RequestBody ReviewPredictionRequest request) {
        return annotationJobService.reviewPrediction(jobId, predictionId, request);
    }
}
