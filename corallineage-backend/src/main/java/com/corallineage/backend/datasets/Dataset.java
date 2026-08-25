package com.corallineage.backend.datasets;

import com.corallineage.backend.projects.Project;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "datasets")
public class Dataset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dataset_name", nullable = false, unique = true, length = 255)
    private String datasetName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = true)
    private Project project;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // The next fallback number a model registered on this dataset without an explicit tag will
    // get ("model-v{N}"), incremented only at the moment it's actually used - see
    // ModelVersionService.registerFromTrainingResult. Nullable at the JPA level even though every
    // row conceptually starts at 1 (Hibernate's ddl-auto=update can't add a NOT NULL column to a
    // table that already has rows, same gotcha as several other columns in this codebase - see
    // decisions.md 2026-08-10); application code treats null as 1. Scoped per dataset rather than
    // a live COUNT(*) across every dataset (the previous approach) so a deleted model can never
    // cause a fallback number to be reused/collide later.
    @Column(name = "next_model_fallback_number")
    private Integer nextModelFallbackNumber;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    // --- getters and setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDatasetName() { return datasetName; }
    public void setDatasetName(String datasetName) { this.datasetName = datasetName; }

    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Integer getNextModelFallbackNumber() { return nextModelFallbackNumber; }
    public void setNextModelFallbackNumber(Integer nextModelFallbackNumber) { this.nextModelFallbackNumber = nextModelFallbackNumber; }
}
