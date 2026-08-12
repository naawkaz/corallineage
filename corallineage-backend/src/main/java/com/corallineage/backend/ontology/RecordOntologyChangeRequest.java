package com.corallineage.backend.ontology;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class RecordOntologyChangeRequest {

    @NotNull(message = "changeType is required")
    private ChangeType changeType;

    private String oldLabelCode;

    private List<String> newLabelCodes;

    @NotNull(message = "affectedDatasetVersionId is required")
    private Long affectedDatasetVersionId;

    @NotBlank(message = "changedBy is required")
    private String changedBy;

    private String notes;

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

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
