package com.corallineage.backend.versioning.dataset;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "images",
       indexes = {
           @Index(name = "idx_images_dataset_version", columnList = "origin_dataset_version_id"),
           @Index(name = "idx_images_filename", columnList = "filename")
       })
public class Image {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The version this image was first imported under — a permanent, one-time "origin" fact,
     * never updated after insert. Does NOT represent which version(s) currently include this
     * image; that's tracked separately by {@link DatasetVersionImage} membership rows.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_dataset_version_id", nullable = false)
    private DatasetVersion originDatasetVersion;

    @Column(name = "filename", nullable = false, length = 500)
    private String filename;

    @Column(name = "file_path", nullable = false, columnDefinition = "TEXT")
    private String filePath;

    @Column(name = "dvc_file_hash", length = 64)
    private String dvcFileHash;

    @Column(name = "site", length = 100)
    private String site;

    @Column(name = "survey_date")
    private LocalDate surveyDate;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    @PrePersist
    protected void onCreate() {
        if (addedAt == null) addedAt = Instant.now();
    }

    // --- getters and setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public DatasetVersion getOriginDatasetVersion() { return originDatasetVersion; }
    public void setOriginDatasetVersion(DatasetVersion originDatasetVersion) { this.originDatasetVersion = originDatasetVersion; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getDvcFileHash() { return dvcFileHash; }
    public void setDvcFileHash(String dvcFileHash) { this.dvcFileHash = dvcFileHash; }

    public String getSite() { return site; }
    public void setSite(String site) { this.site = site; }

    public LocalDate getSurveyDate() { return surveyDate; }
    public void setSurveyDate(LocalDate surveyDate) { this.surveyDate = surveyDate; }

    public Instant getAddedAt() { return addedAt; }
    public void setAddedAt(Instant addedAt) { this.addedAt = addedAt; }
}
