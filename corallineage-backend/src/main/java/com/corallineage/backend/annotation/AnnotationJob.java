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
@Table(name = "annotation_jobs",
       indexes = {
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

    // Nullable at the JPA level even though it's conceptually required post-backfill:
    // the dev DB already has rows created before this FK existed (see the one-time
    // backfill run during this session's implementation).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id")
    private Dataset dataset;

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

    // Values: VALIDATION (recheck model accuracy against existing human labels, no review step),
    // NEW_IMAGES (bulk-label freshly uploaded images, Accept/Override review then Add to Project).
    // Nullable for backward compatibility: rows created by the legacy whole-image submit path
    // (still reachable internally, no longer exposed by the frontend) leave this null.
    @Column(name = "job_type", length = 20)
    private String jobType;

    // VALIDATION: the dataset version the selected images/points were checked against.
    // NEW_IMAGES: the staging dataset version created to hold the freshly uploaded images until
    // reviewed and added to the project (see AnnotationJobExecutor.executeNewImagesAsync).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_version_id")
    private DatasetVersion datasetVersion;

    // Set once a NEW_IMAGES job's reviewed points have been committed into a real project
    // dataset version via "Add to Project", null until then, and permanently sentinel-guards
    // against adding the same job's images twice.
    @Column(name = "added_to_project_version_id")
    private Long addedToProjectVersionId;

    // NEW_IMAGES only: a prediction at or above this confidence auto-confirms (accepted=true) at
    // inference time, same pattern as pointannotation.PointAnnotationJob.confidenceThreshold.
    // Nullable at the JPA level (existing rows predate this column; same ddl-auto=update
    // NOT-NULL-on-a-populated-table gotcha as gridRows/gridCols elsewhere in this codebase).
    @Column(name = "confidence_threshold", precision = 5, scale = 4)
    private BigDecimal confidenceThreshold;

    // True for a job created via submitExistingVersionJob (warm-start blank-import): its
    // datasetVersion is a real, already-existing version, so review commits in place via
    // applyToExistingVersion rather than branching a new version via addToProject. False/null
    // for a genuine Mode B (freshly uploaded images, staging version) NEW_IMAGES job.
    @Column(name = "commit_in_place")
    private Boolean commitInPlace;

    @PrePersist
    protected void onCreate() {
        if (initiatedAt == null) initiatedAt = Instant.now();
    }

    // --- getters and setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ModelVersion getModelVersion() { return modelVersion; }
    public void setModelVersion(ModelVersion modelVersion) { this.modelVersion = modelVersion; }

    public Dataset getDataset() { return dataset; }
    public void setDataset(Dataset dataset) { this.dataset = dataset; }

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

    public String getJobType() { return jobType; }
    public void setJobType(String jobType) { this.jobType = jobType; }

    public DatasetVersion getDatasetVersion() { return datasetVersion; }
    public void setDatasetVersion(DatasetVersion datasetVersion) { this.datasetVersion = datasetVersion; }

    public Long getAddedToProjectVersionId() { return addedToProjectVersionId; }
    public void setAddedToProjectVersionId(Long addedToProjectVersionId) { this.addedToProjectVersionId = addedToProjectVersionId; }

    public BigDecimal getConfidenceThreshold() { return confidenceThreshold; }
    public void setConfidenceThreshold(BigDecimal confidenceThreshold) { this.confidenceThreshold = confidenceThreshold; }

    public Boolean getCommitInPlace() { return commitInPlace; }
    public void setCommitInPlace(Boolean commitInPlace) { this.commitInPlace = commitInPlace; }
}
