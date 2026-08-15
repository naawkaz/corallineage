package com.corallineage.backend.versioning.dataset;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LabelDefinitionRepository extends JpaRepository<LabelDefinition, Long> {

    List<LabelDefinition> findByDatasetVersionId(Long datasetVersionId);

    boolean existsByDatasetVersionIdAndLabelCode(Long datasetVersionId, String labelCode);

    Optional<LabelDefinition> findByDatasetVersionIdAndLabelCode(Long datasetVersionId, String labelCode);
}
