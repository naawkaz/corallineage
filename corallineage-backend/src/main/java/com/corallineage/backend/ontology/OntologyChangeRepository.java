package com.corallineage.backend.ontology;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OntologyChangeRepository extends JpaRepository<OntologyChange, Long> {

    Page<OntologyChange> findAllByOrderByChangedAtDesc(Pageable pageable);

    Page<OntologyChange> findByAffectedDatasetVersionIdOrderByChangedAtDesc(Long affectedDatasetVersionId, Pageable pageable);
}
