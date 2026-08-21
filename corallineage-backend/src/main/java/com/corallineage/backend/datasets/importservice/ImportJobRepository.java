package com.corallineage.backend.datasets.importservice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ImportJobRepository extends JpaRepository<ImportJob, Long> {

    Optional<ImportJob> findByIdAndDatasetId(Long id, Long datasetId);

    Optional<ImportJob> findTopByCreatedDatasetVersionIdOrderBySubmittedAtDesc(Long createdDatasetVersionId);
}
