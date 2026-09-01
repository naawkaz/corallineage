package com.corallineage.backend.versioning.dataset;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "annotation_points",
       uniqueConstraints = @UniqueConstraint(columnNames = {"image_id", "point_index"}),
       indexes = {
           @Index(name = "idx_annotation_points_image", columnList = "image_id"),
           @Index(name = "idx_annotation_points_label", columnList = "label_definition_id")
       })
public class AnnotationPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_id", nullable = false)
    private Image image;

    @Column(name = "point_index", nullable = false)
    private Integer pointIndex;

    @Column(name = "row_position", nullable = false)
    private Integer rowPosition;

    @Column(name = "col_position", nullable = false)
    private Integer colPosition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "label_definition_id", nullable = false)
    private LabelDefinition labelDefinition;

    // one of HUMAN MODEL_PREDICTED MODEL_CORRECTED
    @Column(name = "annotation_source", nullable = false, length = 20)
    private String annotationSource;

    @Column(name = "confidence_score", precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @Column(name = "annotated_by", nullable = false, length = 100)
    private String annotatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }


    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Image getImage() { return image; }
    public void setImage(Image image) { this.image = image; }

    public Integer getPointIndex() { return pointIndex; }
    public void setPointIndex(Integer pointIndex) { this.pointIndex = pointIndex; }

    public Integer getRowPosition() { return rowPosition; }
    public void setRowPosition(Integer rowPosition) { this.rowPosition = rowPosition; }

    public Integer getColPosition() { return colPosition; }
    public void setColPosition(Integer colPosition) { this.colPosition = colPosition; }

    public LabelDefinition getLabelDefinition() { return labelDefinition; }
    public void setLabelDefinition(LabelDefinition labelDefinition) { this.labelDefinition = labelDefinition; }

    public String getAnnotationSource() { return annotationSource; }
    public void setAnnotationSource(String annotationSource) { this.annotationSource = annotationSource; }

    public BigDecimal getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(BigDecimal confidenceScore) { this.confidenceScore = confidenceScore; }

    public String getAnnotatedBy() { return annotatedBy; }
    public void setAnnotatedBy(String annotatedBy) { this.annotatedBy = annotatedBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
