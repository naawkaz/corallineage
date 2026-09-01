package com.corallineage.backend.registry.model;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ModelVersionRepository extends JpaRepository<ModelVersion, Long> {

    Page<ModelVersion> findByStatus(String status, Pageable pageable);

    List<ModelVersion> findByTrainedAtBetween(Instant from, Instant to);

    Page<ModelVersion> findByTrainedOnDatasetVersion_Dataset_Id(Long datasetId, Pageable pageable);

    Page<ModelVersion> findByTrainedOnDatasetVersion_Dataset_IdAndStatus(Long datasetId, String status,
            Pageable pageable);

    boolean existsByModelTag(String modelTag);
}
