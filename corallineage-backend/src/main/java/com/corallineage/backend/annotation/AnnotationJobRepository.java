package com.corallineage.backend.annotation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnotationJobRepository extends JpaRepository<AnnotationJob, Long> {

    Page<AnnotationJob> findAllByOrderByInitiatedAtDesc(Pageable pageable);

    Page<AnnotationJob> findByDataset_IdOrderByInitiatedAtDesc(Long datasetId, Pageable pageable);
}
