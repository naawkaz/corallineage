package com.corallineage.backend.annotation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnnotationJobRepository extends JpaRepository<AnnotationJob, Long> {

    Page<AnnotationJob> findAllByOrderByInitiatedAtDesc(Pageable pageable);

    Page<AnnotationJob> findByDataset_IdOrderByInitiatedAtDesc(Long datasetId, Pageable pageable);

    Page<AnnotationJob> findByModelVersion_IdOrderByInitiatedAtDesc(Long modelVersionId, Pageable pageable);

    /** The in place annotation job targeting this dataset version if any */
    Optional<AnnotationJob> findFirstByDatasetVersion_IdAndCommitInPlaceTrueOrderByInitiatedAtDesc(Long datasetVersionId);

    /** Every in place job for this dataset not yet applied */
    List<AnnotationJob> findByDataset_IdAndCommitInPlaceTrueAndAddedToProjectVersionIdIsNullOrderByInitiatedAtDesc(Long datasetId);
}
