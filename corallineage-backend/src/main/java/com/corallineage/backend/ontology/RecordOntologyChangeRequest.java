package com.corallineage.backend.ontology;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public class RecordOntologyChangeRequest {

    @NotNull(message = "changeType is required")
    private ChangeType changeType;

    /** The version this change branches from */
    @NotNull(message = "basedOnDatasetVersionId is required")
    private Long basedOnDatasetVersionId;

    /** Tag for the new branched version */
    @NotBlank(message = "newVersionTag is required")
    @Size(max = 50, message = "newVersionTag must be at most 50 characters")
    private String newVersionTag;

    private String oldLabelCode;

    @Valid
    private List<NewLabelSpec> newLabels;

    /** Replacement label offered when deprecating a label */
    private String replacementLabelCode;

    @NotBlank(message = "changedBy is required")
    private String changedBy;

    private String notes;

    public ChangeType getChangeType() { return changeType; }
    public void setChangeType(ChangeType changeType) { this.changeType = changeType; }

    public Long getBasedOnDatasetVersionId() { return basedOnDatasetVersionId; }
    public void setBasedOnDatasetVersionId(Long basedOnDatasetVersionId) { this.basedOnDatasetVersionId = basedOnDatasetVersionId; }

    public String getNewVersionTag() { return newVersionTag; }
    public void setNewVersionTag(String newVersionTag) { this.newVersionTag = newVersionTag; }

    public String getOldLabelCode() { return oldLabelCode; }
    public void setOldLabelCode(String oldLabelCode) { this.oldLabelCode = oldLabelCode; }

    public List<NewLabelSpec> getNewLabels() { return newLabels; }
    public void setNewLabels(List<NewLabelSpec> newLabels) { this.newLabels = newLabels; }

    public String getReplacementLabelCode() { return replacementLabelCode; }
    public void setReplacementLabelCode(String replacementLabelCode) { this.replacementLabelCode = replacementLabelCode; }

    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
