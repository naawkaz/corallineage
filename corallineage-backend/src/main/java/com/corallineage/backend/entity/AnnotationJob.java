package com.corallineage.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "annotation_jobs",
       indexes = {
           @Index(name = "idx_annotation_jobs_model_version", columnList = "model_version_id"),
           @Index(name = "idx_annotation_jobs_status", columnList = "status"),
           @Index(name = "idx_annotation_jobs_initiated_at", columnList = "initiated_at")
       })
public class AnnotationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_version_id", nullable = false)
    private ModelVersion modelVersion;

    @Column(name = "initiated_by", nullable = false, length = 100)
    private String initiatedBy;

    @Column(name = "initiated_at", nullable = false, updatable = false)
    private Instant initiatedAt;

    // Values: RUNNING, COMPLETED, FAILED
    @Column(name = "status", nullable = false, length = 50)
    private String status = "RUNNING";

    @Column(name = "completed_at")
    private Instant completedAt;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "image_ids", nullable = false)
    private List<Long> imageIds;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // Values: PENDING, IN_PROGRESS, COMPLETED
    @Column(name = "review_status", nullable = false, length = 50)
    private String reviewStatus = "PENDING";

    @PrePersist
    protected void onCreate() {
        if (initiatedAt == null) initiatedAt = Instant.now();
    }

    // --- getters and setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ModelVersion getModelVersion() { return modelVersion; }
    public void setModelVersion(ModelVersion modelVersion) { this.modelVersion = modelVersion; }

    public String getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(String initiatedBy) { this.initiatedBy = initiatedBy; }

    public Instant getInitiatedAt() { return initiatedAt; }
    public void setInitiatedAt(Instant initiatedAt) { this.initiatedAt = initiatedAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public List<Long> getImageIds() { return imageIds; }
    public void setImageIds(List<Long> imageIds) { this.imageIds = imageIds; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }
}
