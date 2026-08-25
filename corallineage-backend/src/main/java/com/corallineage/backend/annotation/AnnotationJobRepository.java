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

    /** The warm-start blank-import job (commitInPlace=true) targeting this dataset version, if any. */
    Optional<AnnotationJob> findFirstByDatasetVersion_IdAndCommitInPlaceTrueOrderByInitiatedAtDesc(Long datasetVersionId);

    /**
     * Every commitInPlace job for this dataset not yet applied - the same condition
     * DatasetVersionService.attachResolveStatus's branch #2 checks - for the Unlabeled import
     * queue (see PointAnnotationJobService.getUnlabeledImportQueue). Covers both a warm-start
     * blank-import job's own AnnotationJob and the one a cold-start job's auto-annotate delegates
     * into once training completes.
     */
    List<AnnotationJob> findByDataset_IdAndCommitInPlaceTrueAndAddedToProjectVersionIdIsNullOrderByInitiatedAtDesc(Long datasetId);
}
