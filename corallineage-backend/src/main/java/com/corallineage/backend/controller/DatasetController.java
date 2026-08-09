package com.corallineage.backend.controller;

import com.corallineage.backend.dto.CreateDatasetRequest;
import com.corallineage.backend.dto.DatasetResponse;
import com.corallineage.backend.service.DatasetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/datasets")
public class DatasetController {

    private final DatasetService datasetService;

    public DatasetController(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    @GetMapping
    public List<DatasetResponse> getDatasets() {
        return datasetService.getAllDatasets();
    }

    @GetMapping("/{id}")
    public DatasetResponse getDataset(@PathVariable Long id) {
        return datasetService.getDatasetById(id);
    }

    @PostMapping
    public ResponseEntity<DatasetResponse> createDataset(@Valid @RequestBody CreateDatasetRequest request) {
        DatasetResponse response = datasetService.createDataset(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
