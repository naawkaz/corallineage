package com.corallineage.backend.versioning.dataset;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageRepository extends JpaRepository<Image, Long> {

    long countByOriginDatasetVersionDatasetId(Long datasetId);

    /** Images whose origin (first-imported-under) version is {@code datasetVersionId}. Origin-only, not current membership. */
    Page<Image> findByOriginDatasetVersionId(Long datasetVersionId, Pageable pageable);
}
