package com.corallineage.backend.ontology;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface OntologyChangeRepository extends JpaRepository<OntologyChange, Long> {

    Page<OntologyChange> findAllByOrderByChangedAtDesc(Pageable pageable);

    Page<OntologyChange> findByAffectedDatasetVersionIdOrderByChangedAtDesc(Long affectedDatasetVersionId, Pageable pageable);

    Page<OntologyChange> findByAffectedDatasetVersion_Dataset_IdOrderByChangedAtDesc(Long datasetId, Pageable pageable);

    List<OntologyChange> findByChangedAtBetween(Instant from, Instant to);
}
