package com.corallineage.backend.pointannotation;

import com.corallineage.backend.versioning.dataset.AnnotationPoint;
import com.corallineage.backend.versioning.dataset.AnnotationPointRepository;
import com.corallineage.backend.versioning.dataset.LabelDefinition;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Upserts a confirmed {@link GeneratedPoint} (human-labeled during seed labeling, or auto-confirmed
 * above the confidence threshold during auto-annotate) into the shared {@link AnnotationPoint} table
 * — the bridge back into the existing, unmodified training/diff pipeline.
 */
@Component
class AnnotationPointBridge {

    private final AnnotationPointRepository annotationPointRepository;

    AnnotationPointBridge(AnnotationPointRepository annotationPointRepository) {
        this.annotationPointRepository = annotationPointRepository;
    }

    void upsert(GeneratedPoint point, LabelDefinition label, String annotationSource, String annotatedBy,
                BigDecimal confidenceScore) {
        AnnotationPoint annotationPoint = annotationPointRepository
                .findByImageIdAndPointIndex(point.getImage().getId(), point.getPointIndex())
                .orElseGet(AnnotationPoint::new);
        annotationPoint.setImage(point.getImage());
        annotationPoint.setPointIndex(point.getPointIndex());
        annotationPoint.setRowPosition(point.getRowPosition());
        annotationPoint.setColPosition(point.getColPosition());
        annotationPoint.setLabelDefinition(label);
        annotationPoint.setAnnotationSource(annotationSource);
        annotationPoint.setConfidenceScore(confidenceScore);
        annotationPoint.setAnnotatedBy(annotatedBy);
        annotationPointRepository.save(annotationPoint);
    }
}
