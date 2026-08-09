package com.corallineage.backend.versioning.dataset;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageRepository extends JpaRepository<Image, Long> {

    long countByDatasetVersionDatasetId(Long datasetId);
}
