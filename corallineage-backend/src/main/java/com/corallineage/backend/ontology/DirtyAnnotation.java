package com.corallineage.backend.ontology;

import com.corallineage.backend.versioning.dataset.Image;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "dirty_annotations", uniqueConstraints = @UniqueConstraint(columnNames = { "image_id", "point_index",
        "ontology_change_id" }), indexes = {
                @Index(name = "idx_dirty_annotations_image", columnList = "image_id"),
                @Index(name = "idx_dirty_annotations_ontology_change", columnList = "ontology_change_id"),
                @Index(name = "idx_dirty_annotations_resolved_at", columnList = "resolved_at")
        })
public class DirtyAnnotation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_id", nullable = false)
    private Image image;

    @Column(name = "point_index", nullable = false)
    private Integer pointIndex;

    @Column(name = "current_label_code", nullable = false, length = 100)
    private String currentLabelCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ontology_change_id", nullable = false)
    private OntologyChange ontologyChange;

    @Column(name = "flagged_at", nullable = false, updatable = false)
    private Instant flaggedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    @Column(name = "new_label_code", length = 100)
    private String newLabelCode;

    @PrePersist
    protected void onCreate() {
        if (flaggedAt == null)
            flaggedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Image getImage() {
        return image;
    }

    public void setImage(Image image) {
        this.image = image;
    }

    public Integer getPointIndex() {
        return pointIndex;
    }

    public void setPointIndex(Integer pointIndex) {
        this.pointIndex = pointIndex;
    }

    public String getCurrentLabelCode() {
        return currentLabelCode;
    }

    public void setCurrentLabelCode(String currentLabelCode) {
        this.currentLabelCode = currentLabelCode;
    }

    public OntologyChange getOntologyChange() {
        return ontologyChange;
    }

    public void setOntologyChange(OntologyChange ontologyChange) {
        this.ontologyChange = ontologyChange;
    }

    public Instant getFlaggedAt() {
        return flaggedAt;
    }

    public void setFlaggedAt(Instant flaggedAt) {
        this.flaggedAt = flaggedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(String resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public String getNewLabelCode() {
        return newLabelCode;
    }

    public void setNewLabelCode(String newLabelCode) {
        this.newLabelCode = newLabelCode;
    }
}
