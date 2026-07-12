package com.corallineage.backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "annotation_predictions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"annotation_job_id", "image_id", "point_index"}),
       indexes = {
           @Index(name = "idx_predictions_job", columnList = "annotation_job_id"),
           @Index(name = "idx_predictions_image", columnList = "image_id")
       })
public class AnnotationPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "annotation_job_id", nullable = false)
    private AnnotationJob annotationJob;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_id", nullable = false)
    private Image image;

    @Column(name = "point_index", nullable = false)
    private Integer pointIndex;

    @Column(name = "predicted_label_code", nullable = false, length = 100)
    private String predictedLabelCode;

    @Column(name = "confidence_score", nullable = false, precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    // NULL = not yet reviewed; TRUE = accepted; FALSE = overridden
    @Column(name = "accepted")
    private Boolean accepted;

    @Column(name = "corrected_label_code", length = 100)
    private String correctedLabelCode;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    // --- getters and setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public AnnotationJob getAnnotationJob() { return annotationJob; }
    public void setAnnotationJob(AnnotationJob annotationJob) { this.annotationJob = annotationJob; }

    public Image getImage() { return image; }
    public void setImage(Image image) { this.image = image; }

    public Integer getPointIndex() { return pointIndex; }
    public void setPointIndex(Integer pointIndex) { this.pointIndex = pointIndex; }

    public String getPredictedLabelCode() { return predictedLabelCode; }
    public void setPredictedLabelCode(String predictedLabelCode) { this.predictedLabelCode = predictedLabelCode; }

    public BigDecimal getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(BigDecimal confidenceScore) { this.confidenceScore = confidenceScore; }

    public Boolean getAccepted() { return accepted; }
    public void setAccepted(Boolean accepted) { this.accepted = accepted; }

    public String getCorrectedLabelCode() { return correctedLabelCode; }
    public void setCorrectedLabelCode(String correctedLabelCode) { this.correctedLabelCode = correctedLabelCode; }

    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }

    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
}
