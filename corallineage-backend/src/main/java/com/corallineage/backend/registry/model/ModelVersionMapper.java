package com.corallineage.backend.registry.model;

import com.corallineage.backend.versioning.dataset.DatasetVersion;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ModelVersionMapper {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ModelVersionResponse toResponse(ModelVersion model) {
        ModelVersionResponse response = new ModelVersionResponse();
        response.setId(model.getId());
        response.setModelTag(model.getModelTag());
        response.setTrainedAt(model.getTrainedAt());
        response.setTrainedBy(model.getTrainedBy());
        response.setArchitecture(model.getArchitecture());
        response.setHyperparameters(parseJson(model.getHyperparameters()));
        response.setClassMappings(parseJson(model.getClassMappings()));
        response.setAccuracy(model.getAccuracy());
        response.setPerformanceMetrics(parseJson(model.getPerformanceMetrics()));
        response.setMlflowRunId(model.getMlflowRunId());
        response.setWeightsPath(model.getWeightsPath());
        response.setStatus(model.getStatus());

        DatasetVersion version = model.getTrainedOnDatasetVersion();
        if (version != null) {
            String datasetName = version.getDataset() != null ? version.getDataset().getDatasetName() : null;
            response.setTrainedOnDatasetVersion(new DatasetVersionSummary(version.getId(), version.getVersionTag(), datasetName));
        }
        return response;
    }

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            return null;
        }
    }
}
