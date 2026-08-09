package com.corallineage.backend.versioning.dataset;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DatasetVersionMapper {

    public DatasetVersionResponse toResponse(DatasetVersion version) {
        DatasetVersionResponse response = new DatasetVersionResponse();
        response.setId(version.getId());
        response.setDatasetId(version.getDataset() != null ? version.getDataset().getId() : null);
        response.setVersionTag(version.getVersionTag());
        response.setCreatedAt(version.getCreatedAt());
        response.setCreatedBy(version.getCreatedBy());
        response.setTriggerReason(version.getTriggerReason());
        response.setImageCount(version.getImageCount());
        response.setAnnotationCount(version.getAnnotationCount());
        response.setDvcCommitHash(version.getDvcCommitHash());
        response.setNotes(version.getNotes());
        return response;
    }

    public DatasetVersionResponse toResponseWithLabels(DatasetVersion version, List<LabelDefinition> labels) {
        DatasetVersionResponse response = toResponse(version);
        if (labels != null) {
            response.setLabelDefinitions(labels.stream().map(this::toLabelResponse).toList());
        }
        return response;
    }

    public LabelDefinitionResponse toLabelResponse(LabelDefinition label) {
        LabelDefinitionResponse response = new LabelDefinitionResponse();
        response.setLabelCode(label.getLabelCode());
        response.setLabelName(label.getLabelName());
        response.setLabelCategory(label.getLabelCategory());
        response.setDeprecated(label.getDeprecatedAt() != null);
        return response;
    }
}
