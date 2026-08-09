package com.corallineage.backend.repository;

import com.corallineage.backend.entity.Dataset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DatasetRepository extends JpaRepository<Dataset, Long> {

    Optional<Dataset> findByDatasetName(String datasetName);

    List<Dataset> findAllByOrderByCreatedAtDesc();
}
