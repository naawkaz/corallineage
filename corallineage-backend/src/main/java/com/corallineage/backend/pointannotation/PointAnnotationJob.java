package com.corallineage.backend.pointannotation;

import com.corallineage.backend.registry.model.ModelVersion;
import com.corallineage.backend.training.TrainingJob;
import com.corallineage.backend.versioning.dataset.DatasetVersion;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One raw-upload -> point-generation -> seed-labeling -> auto-annotate workflow run against a
 * dataset version. Settings (points/image, confidence threshold, seed count) are fixed at
 * creation and tied to this job/version rather than floating global config, so a later session
 * can see exactly what settings produced a given version's confirmed points.
 */
@Entity
@Table(name = "point_annotation_jobs",
       indexes = {
           @Index(name = "idx_point_annotation_jobs_dataset_version", columnList = "dataset_version_id"),
           @Index(name = "idx_point_annotation_jobs_status", columnList = "status")
       })
public class PointAnnotationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_version_id", nullable = false)
    private DatasetVersion datasetVersion;

    @Column(name = "points_per_image", nullable = false)
    private Integer pointsPerImage;

    @Column(name = "confidence_threshold", nullable = false, precision = 5, scale = 4)
    private BigDecimal confidenceThreshold;

    @Column(name = "seed_image_count", nullable = false)
    private Integer seedImageCount;

    // Values: GENERATING, SEED_LABELING, TRAINING, AUTO_ANNOTATING, REVIEW, COMPLETE, FAILED
    @Column(name = "status", nullable = false, length = 50)
    private String status = "GENERATING";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_job_id")
    private TrainingJob trainingJob;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_version_id")
    private ModelVersion modelVersion;

    @Column(name = "initiated_by", nullable = false, length = 100)
    private String initiatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    // --- getters and setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public DatasetVersion getDatasetVersion() { return datasetVersion; }
    public void setDatasetVersion(DatasetVersion datasetVersion) { this.datasetVersion = datasetVersion; }

    public Integer getPointsPerImage() { return pointsPerImage; }
    public void setPointsPerImage(Integer pointsPerImage) { this.pointsPerImage = pointsPerImage; }

    public BigDecimal getConfidenceThreshold() { return confidenceThreshold; }
    public void setConfidenceThreshold(BigDecimal confidenceThreshold) { this.confidenceThreshold = confidenceThreshold; }

    public Integer getSeedImageCount() { return seedImageCount; }
    public void setSeedImageCount(Integer seedImageCount) { this.seedImageCount = seedImageCount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public TrainingJob getTrainingJob() { return trainingJob; }
    public void setTrainingJob(TrainingJob trainingJob) { this.trainingJob = trainingJob; }

    public ModelVersion getModelVersion() { return modelVersion; }
    public void setModelVersion(ModelVersion modelVersion) { this.modelVersion = modelVersion; }

    public String getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(String initiatedBy) { this.initiatedBy = initiatedBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
