package com.corallineage.backend.versioning.dataset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AnnotationPointRepository extends JpaRepository<AnnotationPoint, Long> {

    long countByImageOriginDatasetVersionDatasetId(Long datasetId);

    List<AnnotationPoint> findByImageOriginDatasetVersionId(Long datasetVersionId);

    /**
     * Points on images whose ORIGIN version is {@code datasetVersionId} with a given label code.
     * Kept for callers that specifically need origin-scoped lookups; ontology-change dirty-flagging
     * uses {@link #findByLabelCodeAndDatasetVersionMembership} instead, since membership (not origin)
     * is what determines whether a point belongs to the version being changed.
     */
    List<AnnotationPoint> findByImageOriginDatasetVersionIdAndLabelDefinitionLabelCode(Long datasetVersionId, String labelCode);

    /**
     * Points with a given label code on every image that is a current MEMBER of {@code datasetVersionId}
     * (via {@link DatasetVersionImage}), regardless of which version the image was originally imported
     * under. This is the correct scope for ontology-change dirty-flagging (SPLIT/DEPRECATE): a label-only
     * version copies forward images originally imported under earlier versions, and points on those
     * images must still be caught.
     */
    @Query("select ap from AnnotationPoint ap "
            + "join DatasetVersionImage dvi on dvi.image = ap.image "
            + "where dvi.datasetVersion.id = :datasetVersionId and ap.labelDefinition.labelCode = :labelCode")
    List<AnnotationPoint> findByLabelCodeAndDatasetVersionMembership(@Param("datasetVersionId") Long datasetVersionId,
                                                                      @Param("labelCode") String labelCode);

    /**
     * Per-label annotation point counts across every image that is a current member of
     * {@code datasetVersionId} (via {@link DatasetVersionImage}) — the "class distribution as of
     * version X" query used by Dataset Diffing. Grouped by labelCode/labelName (strings) rather than
     * the label definition's row ID, since each dataset version carries its own copy of every label
     * (see DatasetVersionService.snapshotCurrentLabelDefinitions) and points reference whichever
     * copy existed at import time.
     */
    @Query("select ld.labelCode as labelCode, ld.labelName as labelName, count(ap) as pointCount "
            + "from AnnotationPoint ap join ap.labelDefinition ld "
            + "join DatasetVersionImage dvi on dvi.image = ap.image "
            + "where dvi.datasetVersion.id = :datasetVersionId "
            + "group by ld.labelCode, ld.labelName")
    List<LabelCountRow> countByLabelForVersionMembership(@Param("datasetVersionId") Long datasetVersionId);

    /**
     * Project-wide per-label point counts within a date range — the reproducibility report's classDistribution.
     * Grouped by labelCode only (unlike {@link #countByLabelForVersionMembership}, which is scoped to one
     * dataset version): across different datasets the same code can legitimately carry different labelName
     * values (e.g. a hand-authored "Crustose Coralline Algae" in one dataset vs. an import's auto-bootstrapped
     * "CCA" in another — see DatasetImportExecutor.bootstrapLabelDefinitions), and grouping by the (code, name)
     * pair would otherwise split one logical class into multiple report rows. max(ld.labelName) just picks a
     * deterministic representative label for display; the point counts themselves are exact either way.
     */
    @Query("select ld.labelCode as labelCode, max(ld.labelName) as labelName, count(ap) as pointCount "
            + "from AnnotationPoint ap join ap.labelDefinition ld "
            + "where ap.createdAt between :from and :to "
            + "group by ld.labelCode")
    List<LabelCountRow> countByLabelBetween(@Param("from") Instant from, @Param("to") Instant to);

    /** Project-wide per-source point counts within a date range — the reproducibility report's annotationSourceSplit. */
    @Query("select ap.annotationSource as annotationSource, count(ap) as pointCount "
            + "from AnnotationPoint ap "
            + "where ap.createdAt between :from and :to "
            + "group by ap.annotationSource")
    List<SourceCountRow> countBySourceBetween(@Param("from") Instant from, @Param("to") Instant to);

    interface LabelCountRow {
        String getLabelCode();
        String getLabelName();
        long getPointCount();
    }

    interface SourceCountRow {
        String getAnnotationSource();
        long getPointCount();
    }
}
