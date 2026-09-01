package com.corallineage.backend.annotation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnnotationPredictionRepository extends JpaRepository<AnnotationPrediction, Long> {

    // Explicit order keeps the review list stable as predictions get updated
    Page<AnnotationPrediction> findByAnnotationJobIdOrderByIdAsc(Long annotationJobId, Pageable pageable);

    /** Every prediction for a job, not paged */
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
