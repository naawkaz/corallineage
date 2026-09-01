package com.corallineage.backend.datasets.importservice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ImportJobRepository extends JpaRepository<ImportJob, Long> {

    Optional<ImportJob> findByIdAndDatasetId(Long id, Long datasetId);

    Optional<ImportJob> findTopByCreatedDatasetVersionIdOrderBySubmittedAtDesc(Long createdDatasetVersionId);

    // BLANK format import jobs still processing or failed before producing a version
    List<ImportJob> findByDataset_IdAndFormatAndCreatedDatasetVersionIdIsNullOrderBySubmittedAtDesc(
            Long datasetId, String format);
}
