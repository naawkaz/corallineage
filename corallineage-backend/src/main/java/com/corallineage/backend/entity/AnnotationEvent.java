package com.corallineage.backend.entity;

import com.corallineage.backend.entity.enums.EventType;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "annotation_events",
       indexes = {
           @Index(name = "idx_events_type", columnList = "event_type"),
           @Index(name = "idx_events_dataset_version", columnList = "dataset_version_id"),
           @Index(name = "idx_events_model_version", columnList = "model_version_id"),
           @Index(name = "idx_events_occurred_at", columnList = "occurred_at")
       })
public class AnnotationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 100)
    private EventType eventType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_version_id")
    private DatasetVersion datasetVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_version_id")
    private ModelVersion modelVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_id")
    private Image image;

    @Column(name = "performed_by", nullable = false, length = 100)
    private String performedBy;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata")
    private String metadata;

    @PrePersist
    protected void onCreate() {
        if (occurredAt == null) occurredAt = Instant.now();
    }

    // --- getters and setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }

    public DatasetVersion getDatasetVersion() { return datasetVersion; }
    public void setDatasetVersion(DatasetVersion datasetVersion) { this.datasetVersion = datasetVersion; }

    public ModelVersion getModelVersion() { return modelVersion; }
    public void setModelVersion(ModelVersion modelVersion) { this.modelVersion = modelVersion; }

    public Image getImage() { return image; }
    public void setImage(Image image) { this.image = image; }

    public String getPerformedBy() { return performedBy; }
    public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
}
