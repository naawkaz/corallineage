package com.corallineage.backend.pointannotation;

import com.corallineage.backend.versioning.dataset.Image;
import com.corallineage.backend.versioning.dataset.LabelDefinition;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** A grid point before it has a confirmed label */
@Entity
@Table(name = "generated_points",
       uniqueConstraints = @UniqueConstraint(columnNames = {"job_id", "image_id", "point_index"}),
       indexes = {
           @Index(name = "idx_generated_points_job", columnList = "job_id"),
           @Index(name = "idx_generated_points_image", columnList = "image_id"),
           @Index(name = "idx_generated_points_status", columnList = "status")
       })
public class GeneratedPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_id", nullable = false)
    private Image image;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private PointAnnotationJob job;

    @Column(name = "point_index", nullable = false)
    private Integer pointIndex;

    @Column(name = "row_position", nullable = false)
    private Integer rowPosition;

    @Column(name = "col_position", nullable = false)
    private Integer colPosition;

    @Column(name = "seed", nullable = false)
    private boolean seed;

    // Values: UNLABELED, HUMAN_LABELED, MODEL_PREDICTED, CONFIRMED
    @Column(name = "status", nullable = false, length = 20)
    private String status = "UNLABELED";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "predicted_label_definition_id")
    private LabelDefinition predictedLabelDefinition;

    @Column(name = "confidence_score", precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "final_label_definition_id")
    private LabelDefinition finalLabelDefinition;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }


    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Image getImage() { return image; }
    public void setImage(Image image) { this.image = image; }

    public PointAnnotationJob getJob() { return job; }
    public void setJob(PointAnnotationJob job) { this.job = job; }

    public Integer getPointIndex() { return pointIndex; }
    public void setPointIndex(Integer pointIndex) { this.pointIndex = pointIndex; }

    public Integer getRowPosition() { return rowPosition; }
    public void setRowPosition(Integer rowPosition) { this.rowPosition = rowPosition; }

    public Integer getColPosition() { return colPosition; }
    public void setColPosition(Integer colPosition) { this.colPosition = colPosition; }

    public boolean isSeed() { return seed; }
    public void setSeed(boolean seed) { this.seed = seed; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LabelDefinition getPredictedLabelDefinition() { return predictedLabelDefinition; }
    public void setPredictedLabelDefinition(LabelDefinition predictedLabelDefinition) { this.predictedLabelDefinition = predictedLabelDefinition; }

    public BigDecimal getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(BigDecimal confidenceScore) { this.confidenceScore = confidenceScore; }

    public LabelDefinition getFinalLabelDefinition() { return finalLabelDefinition; }
    public void setFinalLabelDefinition(LabelDefinition finalLabelDefinition) { this.finalLabelDefinition = finalLabelDefinition; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
