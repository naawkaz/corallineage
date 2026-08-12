package com.corallineage.backend.versioning.dataset;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnnotationPointRepository extends JpaRepository<AnnotationPoint, Long> {

    long countByImageDatasetVersionDatasetId(Long datasetId);

    List<AnnotationPoint> findByImageDatasetVersionId(Long datasetVersionId);

    List<AnnotationPoint> findByImageDatasetVersionIdAndLabelDefinitionLabelCode(Long datasetVersionId, String labelCode);
}
