package com.corallineage.backend.pointannotation;

import org.springframework.stereotype.Component;

@Component
public class PointAnnotationMapper {

    public PointAnnotationJobResponse toResponse(PointAnnotationJob job, int totalImages,
                                                  long seedTotalPoints, long seedLabeledPoints) {
        PointAnnotationJobResponse response = new PointAnnotationJobResponse();
        response.setJobId(job.getId());
        response.setDatasetVersionId(job.getDatasetVersion().getId());
        response.setVersionTag(job.getDatasetVersion().getVersionTag());
        response.setGridRows(job.getGridRows());
        response.setGridCols(job.getGridCols());
        response.setConfidenceThreshold(job.getConfidenceThreshold());
        response.setSeedImageCount(job.getSeedImageCount());
        response.setStatus(job.getStatus());
        response.setTotalImages(totalImages);
        response.setSeedTotalPoints(seedTotalPoints);
        response.setSeedLabeledPoints(seedLabeledPoints);
        if (job.getTrainingJob() != null) {
            response.setTrainingJobId(job.getTrainingJob().getId());
        }
        if (job.getAnnotationJob() != null) {
            response.setAnnotationJobId(job.getAnnotationJob().getId());
        }
        if (job.getModelVersion() != null) {
            response.setModelVersionId(job.getModelVersion().getId());
            response.setModelTag(job.getModelVersion().getModelTag());
        }
        response.setInitiatedBy(job.getInitiatedBy());
        response.setCreatedAt(job.getCreatedAt());
        response.setErrorMessage(job.getErrorMessage());
        response.setAutoAnnotationProcessedCount(job.getAutoAnnotationProcessedCount());
        response.setAutoAnnotationTotalCount(job.getAutoAnnotationTotalCount());
        response.setAutoAnnotationCurrentPointId(job.getAutoAnnotationCurrentPointId());
        return response;
    }

    public GeneratedPointResponse toResponse(GeneratedPoint point) {
        GeneratedPointResponse response = new GeneratedPointResponse();
        response.setId(point.getId());
        response.setImageId(point.getImage().getId());
        response.setFilename(point.getImage().getFilename());
        response.setPointIndex(point.getPointIndex());
        response.setRowPosition(point.getRowPosition());
        response.setColPosition(point.getColPosition());
        response.setSeed(point.isSeed());
        response.setStatus(point.getStatus());
        if (point.getPredictedLabelDefinition() != null) {
            response.setPredictedLabelCode(point.getPredictedLabelDefinition().getLabelCode());
        }
        response.setConfidenceScore(point.getConfidenceScore());
        if (point.getFinalLabelDefinition() != null) {
            response.setFinalLabelCode(point.getFinalLabelDefinition().getLabelCode());
        }
        return response;
    }
}
