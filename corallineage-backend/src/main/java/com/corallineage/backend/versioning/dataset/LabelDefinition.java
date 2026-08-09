package com.corallineage.backend.versioning.dataset;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "label_definitions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"dataset_version_id", "label_code"}),
       indexes = {
           @Index(name = "idx_label_definitions_dataset_version", columnList = "dataset_version_id")
       })
public class LabelDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_version_id", nullable = false)
    private DatasetVersion datasetVersion;

    @Column(name = "label_code", nullable = false, length = 100)
    private String labelCode;

    @Column(name = "label_name", nullable = false, length = 255)
    private String labelName;

    @Column(name = "label_category", length = 100)
    private String labelCategory;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deprecated_at")
    private Instant deprecatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    // --- getters and setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public DatasetVersion getDatasetVersion() { return datasetVersion; }
    public void setDatasetVersion(DatasetVersion datasetVersion) { this.datasetVersion = datasetVersion; }

    public String getLabelCode() { return labelCode; }
    public void setLabelCode(String labelCode) { this.labelCode = labelCode; }

    public String getLabelName() { return labelName; }
    public void setLabelName(String labelName) { this.labelName = labelName; }

    public String getLabelCategory() { return labelCategory; }
    public void setLabelCategory(String labelCategory) { this.labelCategory = labelCategory; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getDeprecatedAt() { return deprecatedAt; }
    public void setDeprecatedAt(Instant deprecatedAt) { this.deprecatedAt = deprecatedAt; }
}
