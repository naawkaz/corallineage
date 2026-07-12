package com.corallineage.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "dataset_versions",
       indexes = {
           @Index(name = "idx_dataset_versions_created_at", columnList = "createdAt")
       })
public class DatasetVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_tag", nullable = false, unique = true, length = 50)
    private String versionTag;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    // Enum values: IMAGES_ADDED, IMAGES_REMOVED, ANNOTATIONS_MODIFIED, LABEL_DEFINITION_CHANGED, MANUAL_SNAPSHOT
    @Column(name = "trigger_reason", nullable = false, length = 50)
    private String triggerReason;

    @Column(name = "image_count", nullable = false)
    private Integer imageCount;

    @Column(name = "annotation_count", nullable = false)
    private Integer annotationCount;

    @Column(name = "dvc_commit_hash", nullable = false, length = 64)
    private String dvcCommitHash;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    // --- getters and setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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
}
