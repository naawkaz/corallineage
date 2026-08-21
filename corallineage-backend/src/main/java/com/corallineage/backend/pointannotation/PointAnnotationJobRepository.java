package com.corallineage.backend.pointannotation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointAnnotationJobRepository extends JpaRepository<PointAnnotationJob, Long> {

    Page<PointAnnotationJob> findByDatasetVersion_Dataset_IdOrderByCreatedAtDesc(Long datasetId, Pageable pageable);

    boolean existsByDatasetVersion_Id(Long datasetVersionId);
}
