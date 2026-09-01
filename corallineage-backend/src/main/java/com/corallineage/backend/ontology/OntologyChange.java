package com.corallineage.backend.ontology;

import com.corallineage.backend.logging.AnnotationEvent;
import com.corallineage.backend.versioning.dataset.DatasetVersion;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "ontology_changes",
       indexes = {
           @Index(name = "idx_ontology_changes_type", columnList = "change_type"),
           @Index(name = "idx_ontology_changes_dataset_version", columnList = "affected_dataset_version_id"),
           @Index(name = "idx_ontology_changes_old_label", columnList = "old_label_code")
       })
public class OntologyChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Values: LABEL_ADDED, LABEL_DEPRECATED, LABEL_SPLIT, LABEL_RENAMED
    @Column(name = "change_type", nullable = false, length = 50)
    private String changeType;

    @Column(name = "old_label_code", length = 100)
    private String oldLabelCode;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "new_label_codes")
    private List<String> newLabelCodes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "affected_dataset_version_id", nullable = false)
    private DatasetVersion affectedDatasetVersion;

    @Column(name = "changed_by", nullable = false, length = 100)
    private String changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "annotation_event_id", nullable = false)
    private AnnotationEvent annotationEvent;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /** Replacement label offered when deprecating a label */
    @Column(name = "replacement_label_code", length = 100)
    private String replacementLabelCode;

    @PrePersist
    protected void onCreate() {
        if (changedAt == null) changedAt = Instant.now();
    }


    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getChangeType() { return changeType; }
    public void setChangeType(String changeType) { this.changeType = changeType; }

    public String getOldLabelCode() { return oldLabelCode; }
    public void setOldLabelCode(String oldLabelCode) { this.oldLabelCode = oldLabelCode; }

    public List<String> getNewLabelCodes() { return newLabelCodes; }
    public void setNewLabelCodes(List<String> newLabelCodes) { this.newLabelCodes = newLabelCodes; }

    public DatasetVersion getAffectedDatasetVersion() { return affectedDatasetVersion; }
    public void setAffectedDatasetVersion(DatasetVersion affectedDatasetVersion) { this.affectedDatasetVersion = affectedDatasetVersion; }

    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }

    public Instant getChangedAt() { return changedAt; }
    public void setChangedAt(Instant changedAt) { this.changedAt = changedAt; }

    public AnnotationEvent getAnnotationEvent() { return annotationEvent; }
    public void setAnnotationEvent(AnnotationEvent annotationEvent) { this.annotationEvent = annotationEvent; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getReplacementLabelCode() { return replacementLabelCode; }
    public void setReplacementLabelCode(String replacementLabelCode) { this.replacementLabelCode = replacementLabelCode; }
}
