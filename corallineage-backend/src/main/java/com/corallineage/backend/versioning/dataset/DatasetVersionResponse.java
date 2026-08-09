package com.corallineage.backend.versioning.dataset;

import java.time.Instant;
import java.util.List;

public class DatasetVersionResponse {

    private Long id;
    private Long datasetId;
    private String versionTag;
    private Instant createdAt;
    private String createdBy;
    private String triggerReason;
    private Integer imageCount;
    private Integer annotationCount;
    private String dvcCommitHash;
    private String notes;
    private List<LabelDefinitionResponse> labelDefinitions;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }

    public String getVersionTag() { return versionTag; }
    public void setVersionTag(String versionTag) { this.versionTag = versionTag; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getTriggerReason() { return triggerReason; }
    public void setTriggerReason(String triggerReason) { this.triggerReason = triggerReason; }

    public Integer getImageCount() { return imageCount; }
    public void setImageCount(Integer imageCount) { this.imageCount = imageCount; }

    public Integer getAnnotationCount() { return annotationCount; }
    public void setAnnotationCount(Integer annotationCount) { this.annotationCount = annotationCount; }

    public String getDvcCommitHash() { return dvcCommitHash; }
    public void setDvcCommitHash(String dvcCommitHash) { this.dvcCommitHash = dvcCommitHash; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public List<LabelDefinitionResponse> getLabelDefinitions() { return labelDefinitions; }
    public void setLabelDefinitions(List<LabelDefinitionResponse> labelDefinitions) { this.labelDefinitions = labelDefinitions; }
}
