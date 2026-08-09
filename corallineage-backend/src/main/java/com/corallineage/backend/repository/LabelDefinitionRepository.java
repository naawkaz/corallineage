package com.corallineage.backend.repository;

import com.corallineage.backend.entity.LabelDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LabelDefinitionRepository extends JpaRepository<LabelDefinition, Long> {

    List<LabelDefinition> findByDatasetVersionId(Long datasetVersionId);
}
