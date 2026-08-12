package com.corallineage.backend.versioning.dataset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AnnotationPointRepository extends JpaRepository<AnnotationPoint, Long> {

    long countByImageDatasetVersionDatasetId(Long datasetId);

    List<AnnotationPoint> findByImageDatasetVersionId(Long datasetVersionId);

    List<AnnotationPoint> findByImageDatasetVersionIdAndLabelDefinitionLabelCode(Long datasetVersionId, String labelCode);

    /**
     * Per-label annotation point counts across every image in the dataset whose owning version
     * was created at or before {@code cutoff} — the "class distribution as of version X" query
     * used by Dataset Diffing. Grouped by labelCode/labelName (strings) rather than the label
     * definition's row ID, since each dataset version carries its own copy of every label
     * (see DatasetVersionService.snapshotCurrentLabelDefinitions) and points reference whichever
     * copy existed at import time.
     */
    @Query("select ld.labelCode as labelCode, ld.labelName as labelName, count(ap) as pointCount "
            + "from AnnotationPoint ap join ap.labelDefinition ld join ap.image img join img.datasetVersion dv "
            + "where dv.dataset.id = :datasetId and dv.createdAt <= :cutoff "
            + "group by ld.labelCode, ld.labelName")
    List<LabelCountRow> countByLabelUpToVersion(@Param("datasetId") Long datasetId, @Param("cutoff") Instant cutoff);

    interface LabelCountRow {
        String getLabelCode();
        String getLabelName();
        long getPointCount();
    }
}
