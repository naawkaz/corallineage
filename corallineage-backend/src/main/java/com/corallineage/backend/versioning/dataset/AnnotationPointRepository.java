package com.corallineage.backend.versioning.dataset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AnnotationPointRepository extends JpaRepository<AnnotationPoint, Long> {

        long countByImageOriginDatasetVersionDatasetId(Long datasetId);

        /** A point's coordinates for dot placement */
        Optional<AnnotationPoint> findByImageIdAndPointIndex(Long imageId, Integer pointIndex);

        /** Every point on an image with a given source */
        List<AnnotationPoint> findByImageIdAndAnnotationSource(Long imageId, String annotationSource);

        /** Image ids with at least one point in the given sources */
        @Query("select distinct dvi.image.id from DatasetVersionImage dvi "
                        + "where dvi.datasetVersion.id = :datasetVersionId "
                        + "and exists (select 1 from AnnotationPoint ap where ap.image = dvi.image "
                        + "and ap.annotationSource in :sources)")
        List<Long> findDistinctImageIdsByDatasetVersionAndAnnotationSourceIn(
                        @Param("datasetVersionId") Long datasetVersionId,
                        @Param("sources") List<String> sources);

        /** Highest point index used on an image */
        @Query("select coalesce(max(ap.pointIndex), -1) from AnnotationPoint ap where ap.image.id = :imageId")
        int findMaxPointIndexByImageId(@Param("imageId") Long imageId);

        /** Points with a label code on images whose origin version matches */
        List<AnnotationPoint> findByImageOriginDatasetVersionIdAndLabelDefinitionLabelCode(Long datasetVersionId,
                        String labelCode);

        /** Points with a label code on every image currently a member of a version */
        @Query("select ap from AnnotationPoint ap "
                        + "join DatasetVersionImage dvi on dvi.image = ap.image "
                        + "where dvi.datasetVersion.id = :datasetVersionId and ap.labelDefinition.labelCode = :labelCode")
        List<AnnotationPoint> findByLabelCodeAndDatasetVersionMembership(
                        @Param("datasetVersionId") Long datasetVersionId,
                        @Param("labelCode") String labelCode);

        /** Every point on every image currently a member of a version */
        @Query("select ap from AnnotationPoint ap "
                        + "join DatasetVersionImage dvi on dvi.image = ap.image "
                        + "where dvi.datasetVersion.id = :datasetVersionId")
        List<AnnotationPoint> findByDatasetVersionMembership(@Param("datasetVersionId") Long datasetVersionId);

        /** Per label point counts across a version's member images */
        @Query("select ld.labelCode as labelCode, ld.labelName as labelName, count(ap) as pointCount "
                        + "from AnnotationPoint ap join ap.labelDefinition ld "
                        + "join DatasetVersionImage dvi on dvi.image = ap.image "
                        + "where dvi.datasetVersion.id = :datasetVersionId "
                        + "group by ld.labelCode, ld.labelName")
        List<LabelCountRow> countByLabelForVersionMembership(@Param("datasetVersionId") Long datasetVersionId);

        /** Per label point counts within a date range across the project */
        @Query("select ld.labelCode as labelCode, max(ld.labelName) as labelName, count(ap) as pointCount "
                        + "from AnnotationPoint ap join ap.labelDefinition ld "
                        + "where ap.createdAt between :from and :to "
                        + "group by ld.labelCode")
        List<LabelCountRow> countByLabelBetween(@Param("from") Instant from, @Param("to") Instant to);

        /** Per source point counts within a date range across the project */
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
