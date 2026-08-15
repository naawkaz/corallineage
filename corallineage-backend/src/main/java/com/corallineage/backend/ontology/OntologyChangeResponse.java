package com.corallineage.backend.ontology;

import java.time.Instant;
import java.util.List;

public class OntologyChangeResponse {

    private Long id;
    private ChangeType changeType;
    private String oldLabelCode;
    private List<String> newLabelCodes;
    private Long affectedDatasetVersionId;
    private String changedBy;
    private Instant changedAt;
    private String notes;
    private String replacementLabelCode;
    private long dirtyAnnotationsFlagged;
    private long resolvedCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ChangeType getChangeType() { return changeType; }
    public void setChangeType(ChangeType changeType) { this.changeType = changeType; }

    public String getOldLabelCode() { return oldLabelCode; }
    public void setOldLabelCode(String oldLabelCode) { this.oldLabelCode = oldLabelCode; }

    public List<String> getNewLabelCodes() { return newLabelCodes; }
    public void setNewLabelCodes(List<String> newLabelCodes) { this.newLabelCodes = newLabelCodes; }

    public Long getAffectedDatasetVersionId() { return affectedDatasetVersionId; }
    public void setAffectedDatasetVersionId(Long affectedDatasetVersionId) { this.affectedDatasetVersionId = affectedDatasetVersionId; }

    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }

    public Instant getChangedAt() { return changedAt; }
    public void setChangedAt(Instant changedAt) { this.changedAt = changedAt; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getReplacementLabelCode() { return replacementLabelCode; }
    public void setReplacementLabelCode(String replacementLabelCode) { this.replacementLabelCode = replacementLabelCode; }

    public long getDirtyAnnotationsFlagged() { return dirtyAnnotationsFlagged; }
    public void setDirtyAnnotationsFlagged(long dirtyAnnotationsFlagged) { this.dirtyAnnotationsFlagged = dirtyAnnotationsFlagged; }

    public long getResolvedCount() { return resolvedCount; }
    public void setResolvedCount(long resolvedCount) { this.resolvedCount = resolvedCount; }
}
