package com.corallineage.backend.versioning.dataset;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ImageRepository extends JpaRepository<Image, Long> {

    long countByDatasetVersionDatasetId(Long datasetId);

    Page<Image> findByDatasetVersionId(Long datasetVersionId, Pageable pageable);

    /** All images belonging to the dataset whose owning version was created at or before {@code cutoff}. */
    List<Image> findByDatasetVersionDatasetIdAndDatasetVersionCreatedAtLessThanEqual(Long datasetId, Instant cutoff);
}
