package com.corallineage.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "model_versions",
       indexes = {
           @Index(name = "idx_model_versions_dataset_version", columnList = "trained_on_dataset_version_id"),
           @Index(name = "idx_model_versions_trained_at", columnList = "trained_at")
       })
public class ModelVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_tag", nullable = false, unique = true, length = 50)
    private String modelTag;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trained_on_dataset_version_id", nullable = false)
    private DatasetVersion trainedOnDatasetVersion;

    @Column(name = "trained_at", nullable = false)
    private Instant trainedAt;

    @Column(name = "trained_by", nullable = false, length = 100)
    private String trainedBy;

    @Column(name = "architecture", nullable = false, length = 100)
    private String architecture = "EfficientNet";

    // Stored as JSON text; Hibernate 6 native JSON mapping (Spring Boot 3+)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "hyperparameters")
    private String hyperparameters;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "class_mappings", nullable = false)
    private String classMappings;

    @Column(name = "accuracy", precision = 5, scale = 4)
    private BigDecimal accuracy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "performance_metrics")
    private String performanceMetrics;

    @Column(name = "mlflow_run_id", length = 100)
    private String mlflowRunId;

    @Column(name = "weights_path", columnDefinition = "TEXT")
    private String weightsPath;

    // Values: ACTIVE, ARCHIVED, DEPRECATED
    @Column(name = "status", nullable = false, length = 50)
    private String status = "ACTIVE";

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // --- getters and setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getModelTag() { return modelTag; }
    public void setModelTag(String modelTag) { this.modelTag = modelTag; }

    public DatasetVersion getTrainedOnDatasetVersion() { return trainedOnDatasetVersion; }
    public void setTrainedOnDatasetVersion(DatasetVersion trainedOnDatasetVersion) { this.trainedOnDatasetVersion = trainedOnDatasetVersion; }

    public Instant getTrainedAt() { return trainedAt; }
    public void setTrainedAt(Instant trainedAt) { this.trainedAt = trainedAt; }

    public String getTrainedBy() { return trainedBy; }
    public void setTrainedBy(String trainedBy) { this.trainedBy = trainedBy; }

    public String getArchitecture() { return architecture; }
    public void setArchitecture(String architecture) { this.architecture = architecture; }

    public String getHyperparameters() { return hyperparameters; }
    public void setHyperparameters(String hyperparameters) { this.hyperparameters = hyperparameters; }

    public String getClassMappings() { return classMappings; }
    public void setClassMappings(String classMappings) { this.classMappings = classMappings; }

    public BigDecimal getAccuracy() { return accuracy; }
    public void setAccuracy(BigDecimal accuracy) { this.accuracy = accuracy; }

    public String getPerformanceMetrics() { return performanceMetrics; }
    public void setPerformanceMetrics(String performanceMetrics) { this.performanceMetrics = performanceMetrics; }

    public String getMlflowRunId() { return mlflowRunId; }
    public void setMlflowRunId(String mlflowRunId) { this.mlflowRunId = mlflowRunId; }

    public String getWeightsPath() { return weightsPath; }
    public void setWeightsPath(String weightsPath) { this.weightsPath = weightsPath; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
