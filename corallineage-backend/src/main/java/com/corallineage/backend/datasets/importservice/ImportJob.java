package com.corallineage.backend.datasets.importservice;

import com.corallineage.backend.datasets.Dataset;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "import_jobs",
       indexes = {
           @Index(name = "idx_import_jobs_dataset", columnList = "dataset_id"),
           @Index(name = "idx_import_jobs_status", columnList = "status")
       })
public class ImportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id", nullable = false)
    private Dataset dataset;

    @Column(name = "format", nullable = false, length = 20)
    private String format;

    @Column(name = "version_tag", nullable = false, length = 50)
    private String versionTag;

    @Column(name = "imported_by", nullable = false, length = 100)
    private String importedBy;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // Values: PROCESSING, COMPLETED, FAILED
    @Column(name = "status", nullable = false, length = 20)
    private String status = "PROCESSING";

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "images_imported")
    private Integer imagesImported;

    @Column(name = "annotation_points_imported")
    private Integer annotationPointsImported;

    @Column(name = "labels_resolved")
    private Integer labelsResolved;

    @Column(name = "labels_unresolved")
    private Integer labelsUnresolved;

    @Column(name = "created_dataset_version_id")
    private Long createdDatasetVersionId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @PrePersist
    protected void onCreate() {
        if (submittedAt == null) submittedAt = Instant.now();
    }

    // --- getters and setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Dataset getDataset() { return dataset; }
    public void setDataset(Dataset dataset) { this.dataset = dataset; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public String getVersionTag() { return versionTag; }
    public void setVersionTag(String versionTag) { this.versionTag = versionTag; }

    public String getImportedBy() { return importedBy; }
    public void setImportedBy(String importedBy) { this.importedBy = importedBy; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Integer getImagesImported() { return imagesImported; }
    public void setImagesImported(Integer imagesImported) { this.imagesImported = imagesImported; }

    public Integer getAnnotationPointsImported() { return annotationPointsImported; }
    public void setAnnotationPointsImported(Integer annotationPointsImported) { this.annotationPointsImported = annotationPointsImported; }

    public Integer getLabelsResolved() { return labelsResolved; }
    public void setLabelsResolved(Integer labelsResolved) { this.labelsResolved = labelsResolved; }

    public Integer getLabelsUnresolved() { return labelsUnresolved; }
    public void setLabelsUnresolved(Integer labelsUnresolved) { this.labelsUnresolved = labelsUnresolved; }

    public Long getCreatedDatasetVersionId() { return createdDatasetVersionId; }
    public void setCreatedDatasetVersionId(Long createdDatasetVersionId) { this.createdDatasetVersionId = createdDatasetVersionId; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
