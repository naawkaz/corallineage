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
    private boolean complete;
    private long unresolvedDirtyCount;
    // Only set when this version was created by an import that's still PROCESSING (or FAILED) -
    // null once the import completed or if this version wasn't created by an import at all.
    private String importStatus;
    // The originating ImportJob's format (e.g. "BLANK"), null if not import-created. Used by the
    // frontend to decide whether to offer "Resolve" (start blank import) on a completed import.
    private String importFormat;
    // Set once a PointAnnotationJob already exists for this version, so the frontend knows blank
    // import has already been started here and shouldn't offer "Resolve" again.
    private boolean blankImportStarted;

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

    public boolean isComplete() { return complete; }
    public void setComplete(boolean complete) { this.complete = complete; }

    public long getUnresolvedDirtyCount() { return unresolvedDirtyCount; }
    public void setUnresolvedDirtyCount(long unresolvedDirtyCount) { this.unresolvedDirtyCount = unresolvedDirtyCount; }

    public String getImportStatus() { return importStatus; }
    public void setImportStatus(String importStatus) { this.importStatus = importStatus; }

    public String getImportFormat() { return importFormat; }
    public void setImportFormat(String importFormat) { this.importFormat = importFormat; }

    public boolean isBlankImportStarted() { return blankImportStarted; }
    public void setBlankImportStarted(boolean blankImportStarted) { this.blankImportStarted = blankImportStarted; }
}
