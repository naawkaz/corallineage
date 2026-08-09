package com.corallineage.backend.repository;

import com.corallineage.backend.entity.Image;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageRepository extends JpaRepository<Image, Long> {

    long countByDatasetVersionDatasetId(Long datasetId);
}
