package com.corallineage.backend.pointannotation;

import com.corallineage.backend.annotation.AnnotationJob;
import com.corallineage.backend.registry.model.ModelVersion;
import com.corallineage.backend.training.TrainingJob;
import com.corallineage.backend.versioning.dataset.DatasetVersion;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** One point annotation workflow run against a dataset version */
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

    // Nullable at the JPA level though every new row sets both
    @Column(name = "grid_rows")
    private Integer gridRows;

    @Column(name = "grid_cols")
    private Integer gridCols;

    @Column(name = "confidence_threshold", nullable = false, precision = 5, scale = 4)
    private BigDecimal confidenceThreshold;

    @Column(name = "seed_image_count", nullable = false)
    private Integer seedImageCount;

    // Values GENERATING SEED_LABELING TRAINING COMPLETE
    @Column(name = "status", nullable = false, length = 50)
    private String status = "GENERATING";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_job_id")
    private TrainingJob trainingJob;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_version_id")
    private ModelVersion modelVersion;

    // The delegated annotation job this job's review lives on, null until auto annotate starts
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "annotation_job_id")
    private AnnotationJob annotationJob;

    @Column(name = "initiated_by", nullable = false, length = 100)
    private String initiatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // Live progress while auto annotating, null before it starts
    @Column(name = "auto_annotation_processed_count")
    private Integer autoAnnotationProcessedCount;

    @Column(name = "auto_annotation_total_count")
    private Integer autoAnnotationTotalCount;

    // A GeneratedPoint id, not a real image id
    @Column(name = "auto_annotation_current_point_id")
    private Long autoAnnotationCurrentPointId;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }


    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public DatasetVersion getDatasetVersion() { return datasetVersion; }
    public void setDatasetVersion(DatasetVersion datasetVersion) { this.datasetVersion = datasetVersion; }

    public Integer getGridRows() { return gridRows; }
    public void setGridRows(Integer gridRows) { this.gridRows = gridRows; }

    public Integer getGridCols() { return gridCols; }
    public void setGridCols(Integer gridCols) { this.gridCols = gridCols; }

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

    public AnnotationJob getAnnotationJob() { return annotationJob; }
    public void setAnnotationJob(AnnotationJob annotationJob) { this.annotationJob = annotationJob; }

    public String getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(String initiatedBy) { this.initiatedBy = initiatedBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Integer getAutoAnnotationProcessedCount() { return autoAnnotationProcessedCount; }
    public void setAutoAnnotationProcessedCount(Integer autoAnnotationProcessedCount) { this.autoAnnotationProcessedCount = autoAnnotationProcessedCount; }

    public Integer getAutoAnnotationTotalCount() { return autoAnnotationTotalCount; }
    public void setAutoAnnotationTotalCount(Integer autoAnnotationTotalCount) { this.autoAnnotationTotalCount = autoAnnotationTotalCount; }

    public Long getAutoAnnotationCurrentPointId() { return autoAnnotationCurrentPointId; }
    public void setAutoAnnotationCurrentPointId(Long autoAnnotationCurrentPointId) { this.autoAnnotationCurrentPointId = autoAnnotationCurrentPointId; }
}
