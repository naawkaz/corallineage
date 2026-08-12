package com.corallineage.backend.datasets.importservice;

import java.time.Instant;

public class ImportJobResponse {

    private String importJobId;
    private Long datasetId;
    private String format;
    private String versionTag;
    private String status;
    private Instant submittedAt;
    private Instant completedAt;
    private Integer imagesImported;
    private Integer annotationPointsImported;
    private Integer labelsResolved;
    private Integer labelsUnresolved;
    private Long createdDatasetVersionId;
    private String createdVersionTag;
    private String errorMessage;

    public String getImportJobId() { return importJobId; }
    public void setImportJobId(String importJobId) { this.importJobId = importJobId; }

    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public String getVersionTag() { return versionTag; }
    public void setVersionTag(String versionTag) { this.versionTag = versionTag; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Integer getImagesImported() { return imagesImported; }
    public void setImagesImported(Integer imagesImported) { this.imagesImported = imagesImported; }

    public Integer getAnnotationPointsImported() { return annotationPointsImported; }
    public void setAnnotationPointsImported(Integer annotationPointsImported) { this.annotationPointsImported = annotationPointsImported; }

    public Integer getLabelsResolved() { return labelsResolved; }
    public void setLabelsResolved(Integer labelsResolved) { this.labelsResolved = labelsResolved; }

    public Integer getLabelsUnresolved() { return labelsUnresolved; }
    public void setLabelsUnresolved(Integer labelsUnresolved) { this.labelsUnresolved = labelsUnresolved; }

    public Long getCreatedDatasetVersionId() { return createdDatasetVersionId; }
    public void setCreatedDatasetVersionId(Long createdDatasetVersionId) { this.createdDatasetVersionId = createdDatasetVersionId; }

    public String getCreatedVersionTag() { return createdVersionTag; }
    public void setCreatedVersionTag(String createdVersionTag) { this.createdVersionTag = createdVersionTag; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
