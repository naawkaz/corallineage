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

    /**
     * Excludes staging versions - see {@link DatasetVersion#getIsStaging()}'s javadoc. Also excludes
     * by the pre-existing {@code "staging-annotation-job-"} version-tag convention (AnnotationJobExecutor's
     * literal tag prefix), not just the {@code isStaging} column: rows created before that column
     * existed have {@code isStaging=null} and would otherwise still leak through, and did in practice
     * against a dev DB carrying pre-session staging rows.
     */
    @Query("SELECT v FROM DatasetVersion v WHERE v.dataset.id = :datasetId "
            + "AND (v.isStaging IS NULL OR v.isStaging = false) "
            + "AND v.versionTag NOT LIKE 'staging-annotation-job-%' ORDER BY v.createdAt DESC")
    Page<DatasetVersion> findAllByDatasetIdAndIsStagingNotTrueOrderByCreatedAtDesc(@Param("datasetId") Long datasetId, Pageable pageable);

    /**
     * Same filter as the paginated overload above, unpaginated - used by
     * {@code DatasetVersionService.getVersionHistory} to compute each version's resolve status
     * (an entity-level enrichment step, not expressible in this query) before filtering out
     * UNRESOLVED rows and paginating what's left, so pagination totals stay correct after the
     * filter runs.
     */
    @Query("SELECT v FROM DatasetVersion v WHERE v.dataset.id = :datasetId "
            + "AND (v.isStaging IS NULL OR v.isStaging = false) "
            + "AND v.versionTag NOT LIKE 'staging-annotation-job-%' ORDER BY v.createdAt DESC")
    List<DatasetVersion> findAllByDatasetIdAndIsStagingNotTrueOrderByCreatedAtDesc(@Param("datasetId") Long datasetId);

    Optional<DatasetVersion> findTopByDatasetIdOrderByCreatedAtDesc(Long datasetId);

    long countByDatasetId(Long datasetId);

    List<DatasetVersion> findByCreatedAtBetween(Instant from, Instant to);
}
