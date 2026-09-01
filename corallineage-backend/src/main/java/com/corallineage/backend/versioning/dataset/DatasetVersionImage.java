package com.corallineage.backend.versioning.dataset;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Explicit many-to-many membership row: "image X is a member of dataset version Y".
 * Populated by copying forward the previous version's membership (see
 * DatasetVersionService.snapshotCurrentImageMembership) plus any newly imported images -
 * never by inference over Image.originDatasetVersion, which only records where an image
 * was first imported, not which versions currently include it.
 */
@Entity
@Table(name = "dataset_version_images",
       uniqueConstraints = @UniqueConstraint(columnNames = {"dataset_version_id", "image_id"}),
       indexes = {
           @Index(name = "idx_dvi_dataset_version", columnList = "dataset_version_id"),
           @Index(name = "idx_dvi_image", columnList = "image_id")
       })
public class DatasetVersionImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_version_id", nullable = false)
    private DatasetVersion datasetVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_id", nullable = false)
    private Image image;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    @PrePersist
    protected void onCreate() {
        if (addedAt == null) addedAt = Instant.now();
    }

    // --- getters and setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public DatasetVersion getDatasetVersion() { return datasetVersion; }
    public void setDatasetVersion(DatasetVersion datasetVersion) { this.datasetVersion = datasetVersion; }

    public Image getImage() { return image; }
    public void setImage(Image image) { this.image = image; }

    public Instant getAddedAt() { return addedAt; }
    public void setAddedAt(Instant addedAt) { this.addedAt = addedAt; }
}
