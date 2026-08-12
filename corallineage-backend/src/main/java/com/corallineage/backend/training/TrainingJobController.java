package com.corallineage.backend.training;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/training")
public class TrainingJobController {

    private final TrainingJobService trainingJobService;

    public TrainingJobController(TrainingJobService trainingJobService) {
        this.trainingJobService = trainingJobService;
    }

    @PostMapping("/jobs")
    public ResponseEntity<TrainingJobResponse> initiateTrainingJob(@Valid @RequestBody InitiateTrainingRequest request) {
        TrainingJobResponse response = trainingJobService.initiateTrainingJob(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/jobs")
    public Page<TrainingJobResponse> getTrainingJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        Pageable pageable = PageRequest.of(page, size);
        return trainingJobService.getTrainingJobs(pageable, status);
    }

    @GetMapping("/jobs/{id}")
    public TrainingJobResponse getTrainingJob(@PathVariable Long id) {
        return trainingJobService.getTrainingJobById(id);
    }

    @GetMapping("/architectures")
    public Map<String, List<ArchitectureInfo>> getArchitectures() {
        return Map.of("architectures", trainingJobService.getArchitectures());
    }
}
