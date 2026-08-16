package com.corallineage.backend.pointannotation;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
@RequestMapping("/api/v1/point-annotation")
public class PointAnnotationJobController {

    private final PointAnnotationJobService pointAnnotationJobService;

    public PointAnnotationJobController(PointAnnotationJobService pointAnnotationJobService) {
        this.pointAnnotationJobService = pointAnnotationJobService;
    }

    @PostMapping("/jobs")
    public ResponseEntity<PointAnnotationJobResponse> startJob(@Valid @RequestBody StartPointAnnotationJobRequest request) {
        PointAnnotationJobResponse response = pointAnnotationJobService.submitJob(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/jobs/{id}")
    public PointAnnotationJobResponse getJob(@PathVariable Long id) {
        return pointAnnotationJobService.getJobById(id);
    }

    @GetMapping("/jobs")
    public Page<PointAnnotationJobResponse> getJobs(
            @RequestParam Long datasetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return pointAnnotationJobService.getJobs(datasetId, PageRequest.of(page, size));
    }

    @GetMapping("/jobs/{id}/points")
    public Page<GeneratedPointResponse> getPoints(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") boolean seed,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "500") int size) {
        return pointAnnotationJobService.getPoints(id, seed, PageRequest.of(page, size));
    }

    @PatchMapping("/points/{pointId}/label")
    public GeneratedPointResponse labelPoint(@PathVariable Long pointId, @Valid @RequestBody LabelPointRequest request) {
        return pointAnnotationJobService.labelPoint(pointId, request);
    }

    @PatchMapping("/points/{pointId}/review")
    public GeneratedPointResponse reviewPoint(@PathVariable Long pointId, @Valid @RequestBody ReviewGeneratedPointRequest request) {
        return pointAnnotationJobService.reviewPoint(pointId, request);
    }

    @PostMapping("/jobs/{id}/train")
    public PointAnnotationJobResponse train(@PathVariable Long id) {
        return pointAnnotationJobService.trainModel(id);
    }

    @PostMapping("/jobs/{id}/auto-annotate")
    public PointAnnotationJobResponse autoAnnotate(@PathVariable Long id) {
        return pointAnnotationJobService.startAutoAnnotate(id);
    }
}
