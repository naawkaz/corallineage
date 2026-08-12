package com.corallineage.backend.annotation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnotationPredictionRepository extends JpaRepository<AnnotationPrediction, Long> {

    Page<AnnotationPrediction> findByAnnotationJobId(Long annotationJobId, Pageable pageable);

    Page<AnnotationPrediction> findByAnnotationJobIdAndImageId(Long annotationJobId, Long imageId, Pageable pageable);

    Page<AnnotationPrediction> findByAnnotationJobIdAndAccepted(Long annotationJobId, Boolean accepted, Pageable pageable);

    Page<AnnotationPrediction> findByAnnotationJobIdAndImageIdAndAccepted(Long annotationJobId, Long imageId, Boolean accepted, Pageable pageable);

    long countByAnnotationJobId(Long annotationJobId);

    long countByAnnotationJobIdAndAcceptedIsNotNull(Long annotationJobId);
}
