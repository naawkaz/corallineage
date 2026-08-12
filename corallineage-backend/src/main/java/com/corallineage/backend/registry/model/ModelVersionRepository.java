package com.corallineage.backend.registry.model;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelVersionRepository extends JpaRepository<ModelVersion, Long> {

    Page<ModelVersion> findByStatus(String status, Pageable pageable);
}
