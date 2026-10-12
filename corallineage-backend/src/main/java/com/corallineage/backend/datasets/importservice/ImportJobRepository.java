package com.corallineage.backend.datasets.importservice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ImportJobRepository extends JpaRepository<ImportJob, Long> {

    Optional<ImportJob> findByIdAndDatasetId(Long id, Long datasetId);

    Optional<ImportJob> findTopByCreatedDatasetVersionIdOrderBySubmittedAtDesc(Long createdDatasetVersionId);

    @Query("select ij.createdDatasetVersionId from ImportJob ij where ij.dataset.id = :datasetId "
            + "and ij.status = 'FAILED' and ij.createdDatasetVersionId is not null")
    List<Long> findFailedCreatedDatasetVersionIds(@Param("datasetId") Long datasetId);

    // BLANK format import jobs still processing or failed before producing a
    // version
    List<ImportJob> findByDataset_IdAndFormatAndCreatedDatasetVersionIdIsNullOrderBySubmittedAtDesc(
            Long datasetId, String format);
}
