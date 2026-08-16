package com.corallineage.backend.datasets;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DatasetRepository extends JpaRepository<Dataset, Long> {

    Optional<Dataset> findByDatasetName(String datasetName);

    List<Dataset> findAllByOrderByCreatedAtDesc();

    List<Dataset> findAllByIdInOrderByCreatedAtDesc(List<Long> ids);

    List<Dataset> findAllByProjectIdOrderByCreatedAtDesc(Long projectId);

    long countByProjectId(Long projectId);
}
