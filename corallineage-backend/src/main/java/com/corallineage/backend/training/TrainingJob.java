package com.corallineage.backend.training;

import com.corallineage.backend.registry.model.ModelVersion;
import com.corallineage.backend.versioning.dataset.DatasetVersion;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "training_jobs",
       indexes = {
           @Index(name = "idx_training_jobs_status", columnList = "status"),
           @Index(name = "idx_training_jobs_initiated_at", columnList = "initiated_at"),
           @Index(name = "idx_training_jobs_dataset_version", columnList = "dataset_version_id")
       })
public class TrainingJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_version_id", nullable = false)
    private DatasetVersion datasetVersion;

    @Column(name = "architecture", nullable = false, length = 100,
            columnDefinition = "VARCHAR(100) DEFAULT 'efficientnet_b0'")
    private String architecture = "efficientnet_b0";

    @Column(name = "initiated_by", nullable = false, length = 100)
    private String initiatedBy;

    @Column(name = "initiated_at", nullable = false, updatable = false)
    private Instant initiatedAt;

    // Values: QUEUED, RUNNING, COMPLETED, FAILED
    @Column(name = "status", nullable = false, length = 50)
    private String status = "QUEUED";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "hyperparameters", nullable = false)
    private String hyperparameters;

    @Column(name = "completed_at")
    private Instant completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_version_id")
    private ModelVersion modelVersion;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "current_epoch")
    private Integer currentEpoch;

    @Column(name = "total_epochs")
    private Integer totalEpochs;

    // Set as soon as the Python side's MLflow run starts, well before the job reaches COMPLETED,
    // so the frontend's live loss-curve chart can start polling MlflowIntegrationService.getEpochMetrics
    // while training is still RUNNING instead of waiting for the final registerFromTrainingResult write.
    @Column(name = "mlflow_run_id", length = 100)
    private String mlflowRunId;

    @PrePersist
    protected void onCreate() {
        if (initiatedAt == null) initiatedAt = Instant.now();
    }

    // --- getters and setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getArchitecture() { return architecture; }
    public void setArchitecture(String architecture) { this.architecture = architecture; }

    public DatasetVersion getDatasetVersion() { return datasetVersion; }
    public void setDatasetVersion(DatasetVersion datasetVersion) { this.datasetVersion = datasetVersion; }

    public String getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(String initiatedBy) { this.initiatedBy = initiatedBy; }

    public Instant getInitiatedAt() { return initiatedAt; }
    public void setInitiatedAt(Instant initiatedAt) { this.initiatedAt = initiatedAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getHyperparameters() { return hyperparameters; }
    public void setHyperparameters(String hyperparameters) { this.hyperparameters = hyperparameters; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public ModelVersion getModelVersion() { return modelVersion; }
    public void setModelVersion(ModelVersion modelVersion) { this.modelVersion = modelVersion; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Integer getCurrentEpoch() { return currentEpoch; }
    public void setCurrentEpoch(Integer currentEpoch) { this.currentEpoch = currentEpoch; }

    public Integer getTotalEpochs() { return totalEpochs; }
    public void setTotalEpochs(Integer totalEpochs) { this.totalEpochs = totalEpochs; }

    public String getMlflowRunId() { return mlflowRunId; }
    public void setMlflowRunId(String mlflowRunId) { this.mlflowRunId = mlflowRunId; }
}
