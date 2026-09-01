package com.corallineage.backend.registry.model;

import com.corallineage.backend.datasets.DatasetService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
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
    private final DatasetService datasetService;

    public ModelVersionController(ModelVersionService modelVersionService, DatasetService datasetService) {
        this.modelVersionService = modelVersionService;
        this.datasetService = datasetService;
    }

    @GetMapping
    public Page<ModelVersionResponse> getModels(
            @RequestParam Long datasetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            Authentication authentication) {
        datasetService.requireAccess(datasetId, authentication.getName());
        Pageable pageable = PageRequest.of(page, size);
        return modelVersionService.getAllModels(datasetId, pageable, status);
    }

    @GetMapping("/{id}")
    public ModelVersionResponse getModel(@PathVariable Long id, Authentication authentication) {
        datasetService.requireAccess(modelVersionService.getDatasetIdForModel(id), authentication.getName());
        return modelVersionService.getModelById(id);
    }

    @GetMapping("/{id}/mlflow-details")
    public MlflowDetailsResponse getMlflowDetails(@PathVariable Long id, Authentication authentication) {
        datasetService.requireAccess(modelVersionService.getDatasetIdForModel(id), authentication.getName());
        return modelVersionService.getMlflowDetails(id);
    }

    @PatchMapping("/{id}/status")
    public ModelVersionResponse updateStatus(@PathVariable Long id,
            @Valid @RequestBody UpdateModelStatusRequest request,
            Authentication authentication) {
        datasetService.requireAccess(modelVersionService.getDatasetIdForModel(id), authentication.getName());
        return modelVersionService.updateStatus(id, request.getStatus(), request.getChangedBy());
    }
}
