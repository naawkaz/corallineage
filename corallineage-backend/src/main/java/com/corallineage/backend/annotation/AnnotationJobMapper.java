package com.corallineage.backend.annotation;

import org.springframework.stereotype.Component;

@Component
public class AnnotationJobMapper {

    public AnnotationJobResponse toResponse(AnnotationJob job, long totalPredictions, long reviewedPredictions) {
        AnnotationJobResponse response = new AnnotationJobResponse();
        response.setJobId(job.getId());
        response.setStatus(job.getStatus());
        response.setReviewStatus(job.getReviewStatus());
        response.setModelVersionId(job.getModelVersion() != null ? job.getModelVersion().getId() : null);
        response.setModelTag(job.getModelVersion() != null ? job.getModelVersion().getModelTag() : null);
        response.setImageCount(job.getImageIds() != null ? job.getImageIds().size() : 0);
        response.setInitiatedBy(job.getInitiatedBy());
        response.setInitiatedAt(job.getInitiatedAt());
        response.setCompletedAt(job.getCompletedAt());
        response.setTotalPredictions(totalPredictions);
        response.setReviewedPredictions(reviewedPredictions);
        response.setErrorMessage(job.getErrorMessage());
        return response;
    }

    public AnnotationPredictionResponse toPredictionResponse(AnnotationPrediction prediction) {
        AnnotationPredictionResponse response = new AnnotationPredictionResponse();
        response.setPredictionId(prediction.getId());
        response.setImageId(prediction.getImage() != null ? prediction.getImage().getId() : null);
        response.setFilename(prediction.getImage() != null ? prediction.getImage().getFilename() : null);
        response.setPointIndex(prediction.getPointIndex());
        response.setPredictedLabelCode(prediction.getPredictedLabelCode());
        response.setConfidenceScore(prediction.getConfidenceScore());
        response.setAccepted(prediction.getAccepted());
        response.setCorrectedLabelCode(prediction.getCorrectedLabelCode());
        response.setReviewedBy(prediction.getReviewedBy());
        response.setReviewedAt(prediction.getReviewedAt());
        return response;
    }
}
