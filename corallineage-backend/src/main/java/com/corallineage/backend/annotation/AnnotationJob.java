package com.corallineage.backend.annotation;

import com.corallineage.backend.datasets.Dataset;
import com.corallineage.backend.registry.model.ModelVersion;
import com.corallineage.backend.versioning.dataset.DatasetVersion;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "annotation_jobs", indexes = {
        @Index(name = "idx_annotation_jobs_model_version", columnList = "model_version_id"),
        @Index(name = "idx_annotation_jobs_status", columnList = "status"),
        @Index(name = "idx_annotation_jobs_initiated_at", columnList = "initiated_at"),
        @Index(name = "idx_annotation_jobs_dataset", columnList = "dataset_id")
})
public class AnnotationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_version_id", nullable = false)
    private ModelVersion modelVersion;

    // Nullable at the JPA level but conceptually required
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id")
    private Dataset dataset;

    @Column(name = "initiated_by", nullable = false, length = 100)
    private String initiatedBy;

    @Column(name = "initiated_at", nullable = false, updatable = false)
    private Instant initiatedAt;

    // one of RUNNING COMPLETED FAILED
    @Column(name = "status", nullable = false, length = 50)
    private String status = "RUNNING";

    @Column(name = "completed_at")
    private Instant completedAt;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "image_ids", nullable = false)
    private List<Long> imageIds;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // one of PENDING IN_PROGRESS COMPLETED
    @Column(name = "review_status", nullable = false, length = 50)
    private String reviewStatus = "PENDING";

    // VALIDATION or NEW_IMAGES, null for the internal whole image path
    @Column(name = "job_type", length = 20)
    private String jobType;

    // The version checked against for VALIDATION, or the staging version for
    // NEW_IMAGES
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_version_id")
    private DatasetVersion datasetVersion;

    // The version this job's reviewed points were committed into, null until then
    @Column(name = "added_to_project_version_id")
    private Long addedToProjectVersionId;

    // A prediction at or above this confidence auto confirms at inference time,
    // nullable
    @Column(name = "confidence_threshold", precision = 5, scale = 4)
    private BigDecimal confidenceThreshold;

    // True when review commits in place onto an existing version rather than
    // branching a new one
    @Column(name = "commit_in_place")
    private Boolean commitInPlace;

    @PrePersist
    protected void onCreate() {
        if (initiatedAt == null)
            initiatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ModelVersion getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(ModelVersion modelVersion) {
        this.modelVersion = modelVersion;
    }

    public Dataset getDataset() {
        return dataset;
    }

    public void setDataset(Dataset dataset) {
        this.dataset = dataset;
    }

    public String getInitiatedBy() {
        return initiatedBy;
    }

    public void setInitiatedBy(String initiatedBy) {
        this.initiatedBy = initiatedBy;
    }

    public Instant getInitiatedAt() {
        return initiatedAt;
    }

    public void setInitiatedAt(Instant initiatedAt) {
        this.initiatedAt = initiatedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public List<Long> getImageIds() {
        return imageIds;
    }

    public void setImageIds(List<Long> imageIds) {
        this.imageIds = imageIds;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(String reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public String getJobType() {
        return jobType;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    public DatasetVersion getDatasetVersion() {
        return datasetVersion;
    }

    public void setDatasetVersion(DatasetVersion datasetVersion) {
        this.datasetVersion = datasetVersion;
    }

    public Long getAddedToProjectVersionId() {
        return addedToProjectVersionId;
    }

    public void setAddedToProjectVersionId(Long addedToProjectVersionId) {
        this.addedToProjectVersionId = addedToProjectVersionId;
    }

    public BigDecimal getConfidenceThreshold() {
        return confidenceThreshold;
    }

    public void setConfidenceThreshold(BigDecimal confidenceThreshold) {
        this.confidenceThreshold = confidenceThreshold;
    }

    public Boolean getCommitInPlace() {
        return commitInPlace;
    }

    public void setCommitInPlace(Boolean commitInPlace) {
        this.commitInPlace = commitInPlace;
    }
}
