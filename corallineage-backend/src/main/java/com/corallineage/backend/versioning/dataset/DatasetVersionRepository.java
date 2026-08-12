package com.corallineage.backend.versioning.dataset;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DatasetVersionRepository extends JpaRepository<DatasetVersion, Long> {

    Optional<DatasetVersion> findByDatasetIdAndVersionTag(Long datasetId, String versionTag);

    Page<DatasetVersion> findAllByDatasetIdOrderByCreatedAtDesc(Long datasetId, Pageable pageable);

    Optional<DatasetVersion> findTopByDatasetIdOrderByCreatedAtDesc(Long datasetId);

    long countByDatasetId(Long datasetId);

    List<DatasetVersion> findByCreatedAtBetween(Instant from, Instant to);
}
