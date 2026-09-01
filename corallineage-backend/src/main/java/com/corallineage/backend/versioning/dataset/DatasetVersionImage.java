package com.corallineage.backend.versioning.dataset;

import jakarta.persistence.*;
import java.time.Instant;

/** Membership row marking an image as part of a dataset version */
@Entity
@Table(name = "dataset_version_images", uniqueConstraints = @UniqueConstraint(columnNames = { "dataset_version_id",
        "image_id" }), indexes = {
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
        if (addedAt == null)
            addedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public DatasetVersion getDatasetVersion() {
        return datasetVersion;
    }

    public void setDatasetVersion(DatasetVersion datasetVersion) {
        this.datasetVersion = datasetVersion;
    }

    public Image getImage() {
        return image;
    }

    public void setImage(Image image) {
        this.image = image;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    public void setAddedAt(Instant addedAt) {
        this.addedAt = addedAt;
    }
}
