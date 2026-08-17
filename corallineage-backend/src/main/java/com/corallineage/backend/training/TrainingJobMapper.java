package com.corallineage.backend.training;

import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class TrainingJobMapper {

    public TrainingJobResponse toResponse(TrainingJob job) {
        TrainingJobResponse response = new TrainingJobResponse();
        response.setJobId(job.getId());
        response.setStatus(job.getStatus());
        response.setArchitecture(job.getArchitecture());
        response.setHyperparameters(job.getHyperparameters());
        response.setDatasetVersionId(job.getDatasetVersion() != null ? job.getDatasetVersion().getId() : null);
        response.setDatasetVersionTag(job.getDatasetVersion() != null ? job.getDatasetVersion().getVersionTag() : null);
        response.setInitiatedBy(job.getInitiatedBy());
        response.setInitiatedAt(job.getInitiatedAt());
        response.setCompletedAt(job.getCompletedAt());
        if (job.getInitiatedAt() != null && job.getCompletedAt() != null) {
            response.setDurationSeconds(Duration.between(job.getInitiatedAt(), job.getCompletedAt()).getSeconds());
        }
        response.setModelVersionId(job.getModelVersion() != null ? job.getModelVersion().getId() : null);
        response.setModelTag(job.getModelVersion() != null ? job.getModelVersion().getModelTag() : null);
        response.setErrorMessage(job.getErrorMessage());
        response.setCurrentEpoch(job.getCurrentEpoch());
        response.setTotalEpochs(job.getTotalEpochs());
        response.setMlflowRunId(job.getMlflowRunId());
        return response;
    }
}
