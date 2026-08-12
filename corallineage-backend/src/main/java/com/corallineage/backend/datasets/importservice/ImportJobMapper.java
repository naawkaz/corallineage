package com.corallineage.backend.datasets.importservice;

import org.springframework.stereotype.Component;

@Component
public class ImportJobMapper {

    public ImportJobResponse toResponse(ImportJob job) {
        ImportJobResponse response = new ImportJobResponse();
        response.setImportJobId("IMP-" + job.getId());
        response.setDatasetId(job.getDataset().getId());
        response.setFormat(job.getFormat());
        response.setVersionTag(job.getVersionTag());
        response.setStatus(job.getStatus());
        response.setSubmittedAt(job.getSubmittedAt());
        response.setCompletedAt(job.getCompletedAt());
        response.setImagesImported(job.getImagesImported());
        response.setAnnotationPointsImported(job.getAnnotationPointsImported());
        response.setLabelsResolved(job.getLabelsResolved());
        response.setLabelsUnresolved(job.getLabelsUnresolved());
        response.setCreatedDatasetVersionId(job.getCreatedDatasetVersionId());
        if ("COMPLETED".equals(job.getStatus())) {
            response.setCreatedVersionTag(job.getVersionTag());
        }
        response.setErrorMessage(job.getErrorMessage());
        return response;
    }
}
