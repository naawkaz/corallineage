package com.corallineage.backend.versioning.dataset;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DatasetVersionRepository extends JpaRepository<DatasetVersion, Long> {

    Optional<DatasetVersion> findByDatasetIdAndVersionTag(Long datasetId, String versionTag);

    Page<DatasetVersion> findAllByDatasetIdOrderByCreatedAtDesc(Long datasetId, Pageable pageable);

    /** Excludes staging versions */
    @Query("SELECT v FROM DatasetVersion v WHERE v.dataset.id = :datasetId "
            + "AND (v.isStaging IS NULL OR v.isStaging = false) "
            + "AND v.versionTag NOT LIKE 'staging-annotation-job-%' ORDER BY v.createdAt DESC")
    Page<DatasetVersion> findAllByDatasetIdAndIsStagingNotTrueOrderByCreatedAtDesc(@Param("datasetId") Long datasetId, Pageable pageable);

    /** Same filter as above without pagination */
    @Query("SELECT v FROM DatasetVersion v WHERE v.dataset.id = :datasetId "
            + "AND (v.isStaging IS NULL OR v.isStaging = false) "
            + "AND v.versionTag NOT LIKE 'staging-annotation-job-%' ORDER BY v.createdAt DESC")
    List<DatasetVersion> findAllByDatasetIdAndIsStagingNotTrueOrderByCreatedAtDesc(@Param("datasetId") Long datasetId);

    Optional<DatasetVersion> findTopByDatasetIdOrderByCreatedAtDesc(Long datasetId);

    long countByDatasetId(Long datasetId);

    /** Counts non staging versions up to this one by creation order */
    @Query("SELECT COUNT(v) FROM DatasetVersion v WHERE v.dataset.id = :datasetId "
            + "AND (v.isStaging IS NULL OR v.isStaging = false) "
            + "AND v.versionTag NOT LIKE 'staging-annotation-job-%' AND v.id <= :versionId")
    long countByDatasetIdAndIsStagingNotTrueAndIdLessThanEqual(@Param("datasetId") Long datasetId, @Param("versionId") Long versionId);

    List<DatasetVersion> findByCreatedAtBetween(Instant from, Instant to);
}
