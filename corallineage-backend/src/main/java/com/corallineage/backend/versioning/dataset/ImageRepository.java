package com.corallineage.backend.versioning.dataset;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageRepository extends JpaRepository<Image, Long> {

    long countByDatasetVersionDatasetId(Long datasetId);

    Page<Image> findByDatasetVersionId(Long datasetVersionId, Pageable pageable);
}
