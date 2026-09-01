package com.corallineage.backend.pointannotation;

import com.corallineage.backend.datasets.DatasetService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

@RestController
@RequestMapping("/api/v1/point-annotation")
public class PointAnnotationJobController {

    private final PointAnnotationJobService pointAnnotationJobService;
    private final DatasetService datasetService;

    public PointAnnotationJobController(PointAnnotationJobService pointAnnotationJobService,
            DatasetService datasetService) {
        this.pointAnnotationJobService = pointAnnotationJobService;
        this.datasetService = datasetService;
    }

    @PostMapping("/jobs")
    public ResponseEntity<PointAnnotationJobResponse> startJob(
            @Valid @RequestBody StartPointAnnotationJobRequest request,
            Authentication authentication) {
        PointAnnotationJobResponse response = pointAnnotationJobService.submitJob(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/jobs/{id}")
    public PointAnnotationJobResponse getJob(@PathVariable Long id, Authentication authentication) {
        datasetService.requireAccess(pointAnnotationJobService.getDatasetIdForJob(id), authentication.getName());
        return pointAnnotationJobService.getJobById(id);
    }

    @GetMapping("/jobs")
    public Page<PointAnnotationJobResponse> getJobs(
            @RequestParam Long datasetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        datasetService.requireAccess(datasetId, authentication.getName());
        return pointAnnotationJobService.getJobs(datasetId, PageRequest.of(page, size));
    }

    @GetMapping("/unlabeled-queue")
    public Page<UnlabeledImportQueueEntryResponse> getUnlabeledImportQueue(
            @RequestParam Long datasetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        datasetService.requireAccess(datasetId, authentication.getName());
        return pointAnnotationJobService.getUnlabeledImportQueue(datasetId, PageRequest.of(page, size));
    }

    @GetMapping("/jobs/{id}/points")
    public Page<GeneratedPointResponse> getPoints(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") boolean seed,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "500") int size,
            Authentication authentication) {
        datasetService.requireAccess(pointAnnotationJobService.getDatasetIdForJob(id), authentication.getName());
        return pointAnnotationJobService.getPoints(id, seed, PageRequest.of(page, size));
    }

    @PatchMapping("/points/{pointId}/label")
    public GeneratedPointResponse labelPoint(@PathVariable Long pointId, @Valid @RequestBody LabelPointRequest request,
            Authentication authentication) {
        datasetService.requireAccess(pointAnnotationJobService.getDatasetIdForPoint(pointId), authentication.getName());
        return pointAnnotationJobService.labelPoint(pointId, request);
    }

    @PostMapping("/jobs/{id}/train")
    public PointAnnotationJobResponse train(@PathVariable Long id,
            @RequestBody(required = false) TrainPointAnnotationJobRequest request,
            Authentication authentication) {
        datasetService.requireAccess(pointAnnotationJobService.getDatasetIdForJob(id), authentication.getName());
        return pointAnnotationJobService.trainModel(id, request);
    }

    @PostMapping("/jobs/{id}/auto-annotate")
    public PointAnnotationJobResponse autoAnnotate(@PathVariable Long id, Authentication authentication) {
        datasetService.requireAccess(pointAnnotationJobService.getDatasetIdForJob(id), authentication.getName());
        return pointAnnotationJobService.startAutoAnnotate(id);
    }
}
