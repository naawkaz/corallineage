package com.corallineage.backend.registry.model;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/models")
public class ModelVersionController {

    private final ModelVersionService modelVersionService;

    public ModelVersionController(ModelVersionService modelVersionService) {
        this.modelVersionService = modelVersionService;
    }

    @GetMapping
    public Page<ModelVersionResponse> getModels(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        Pageable pageable = PageRequest.of(page, size);
        return modelVersionService.getAllModels(pageable, status);
    }

    @GetMapping("/{id}")
    public ModelVersionResponse getModel(@PathVariable Long id) {
        return modelVersionService.getModelById(id);
    }

    @GetMapping("/{id}/mlflow-details")
    public MlflowDetailsResponse getMlflowDetails(@PathVariable Long id) {
        return modelVersionService.getMlflowDetails(id);
    }

    @PatchMapping("/{id}/status")
    public ModelVersionResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateModelStatusRequest request) {
        return modelVersionService.updateStatus(id, request.getStatus(), request.getChangedBy());
    }
}
