package com.corallineage.backend.annotation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnnotationPredictionRepository extends JpaRepository<AnnotationPrediction, Long> {

    // Explicit order - see GeneratedPointRepository's identical note: an unsorted query has no
    // guaranteed row order, so AnnotationReview.tsx's prediction list could visibly reorder itself
    // as predictions get reviewed (each review is an UPDATE) without this.
    Page<AnnotationPrediction> findByAnnotationJobIdOrderByIdAsc(Long annotationJobId, Pageable pageable);

    /** Non-paged, used by the executor (applying inference results) and Add to Project (reading every reviewed point). */
    List<AnnotationPrediction> findByAnnotationJobId(Long annotationJobId);

    long countByAnnotationJobIdAndAgreesWithHumanTrue(Long annotationJobId);

    Page<AnnotationPrediction> findByAnnotationJobIdAndImageId(Long annotationJobId, Long imageId, Pageable pageable);

    Page<AnnotationPrediction> findByAnnotationJobIdAndAccepted(Long annotationJobId, Boolean accepted, Pageable pageable);

    Page<AnnotationPrediction> findByAnnotationJobIdAndImageIdAndAccepted(Long annotationJobId, Long imageId, Boolean accepted, Pageable pageable);

    long countByAnnotationJobId(Long annotationJobId);

    long countByAnnotationJobIdAndAcceptedIsNotNull(Long annotationJobId);

    long countByAnnotationJobIdAndAcceptedTrue(Long annotationJobId);

    List<AnnotationPrediction> findByAnnotationJobIdAndAcceptedIsNull(Long annotationJobId);
}
