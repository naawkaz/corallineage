package com.corallineage.backend.pointannotation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PointAnnotationJobRepository extends JpaRepository<PointAnnotationJob, Long> {

    Page<PointAnnotationJob> findByDatasetVersion_Dataset_IdOrderByCreatedAtDesc(Long datasetId, Pageable pageable);

    boolean existsByDatasetVersion_Id(Long datasetVersionId);

    /** One point annotation job per dataset version */
    Optional<PointAnnotationJob> findFirstByDatasetVersion_IdOrderByCreatedAtDesc(Long datasetVersionId);

    /** The point annotation job that triggered a training job */
    Optional<PointAnnotationJob> findByTrainingJob_Id(Long trainingJobId);
}
