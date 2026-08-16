package com.corallineage.backend.datasets;

import org.springframework.stereotype.Component;

@Component
public class DatasetMapper {

    public DatasetResponse toResponse(Dataset dataset) {
        DatasetResponse response = new DatasetResponse();
        response.setId(dataset.getId());
        response.setProjectId(dataset.getProject() != null ? dataset.getProject().getId() : null);
        response.setDatasetName(dataset.getDatasetName());
        response.setDescription(dataset.getDescription());
        response.setCreatedBy(dataset.getCreatedBy());
        response.setCreatedAt(dataset.getCreatedAt());
        return response;
    }

    public DatasetResponse toSummaryResponse(Dataset dataset, long versionCount, String latestVersionTag) {
        DatasetResponse response = toResponse(dataset);
        response.setVersionCount(versionCount);
        response.setLatestVersionTag(latestVersionTag);
        return response;
    }

    public DatasetResponse toDetailResponse(Dataset dataset, long versionCount, String latestVersionTag,
                                            Integer imageCount, Integer annotationCount) {
        DatasetResponse response = toSummaryResponse(dataset, versionCount, latestVersionTag);
        response.setImageCount(imageCount);
        response.setAnnotationCount(annotationCount);
        return response;
    }
}
