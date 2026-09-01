package com.corallineage.backend.logging;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;

public interface AnnotationEventRepository extends JpaRepository<AnnotationEvent, Long> {

    // Native query with explicit casts so Postgres can infer nullable bind parameter types
    @Query(value = """
            SELECT e.* FROM annotation_events e
            LEFT JOIN dataset_versions dv ON dv.id = e.dataset_version_id
            WHERE (CAST(:eventType AS varchar) IS NULL OR e.event_type = CAST(:eventType AS varchar))
              AND (:datasetVersionId IS NULL OR e.dataset_version_id = :datasetVersionId)
              AND (:modelVersionId IS NULL OR e.model_version_id = :modelVersionId)
              AND (:datasetId IS NULL OR dv.dataset_id = :datasetId)
              AND (CAST(:from AS timestamptz) IS NULL OR e.occurred_at >= CAST(:from AS timestamptz))
              AND (CAST(:to AS timestamptz) IS NULL OR e.occurred_at <= CAST(:to AS timestamptz))
            """,
            countQuery = """
            SELECT count(*) FROM annotation_events e
            LEFT JOIN dataset_versions dv ON dv.id = e.dataset_version_id
            WHERE (CAST(:eventType AS varchar) IS NULL OR e.event_type = CAST(:eventType AS varchar))
              AND (:datasetVersionId IS NULL OR e.dataset_version_id = :datasetVersionId)
              AND (:modelVersionId IS NULL OR e.model_version_id = :modelVersionId)
              AND (:datasetId IS NULL OR dv.dataset_id = :datasetId)
              AND (CAST(:from AS timestamptz) IS NULL OR e.occurred_at >= CAST(:from AS timestamptz))
              AND (CAST(:to AS timestamptz) IS NULL OR e.occurred_at <= CAST(:to AS timestamptz))
            """,
            nativeQuery = true)
    Page<AnnotationEvent> findByFilters(@Param("eventType") String eventType,
                                        @Param("datasetVersionId") Long datasetVersionId,
                                        @Param("modelVersionId") Long modelVersionId,
                                        @Param("datasetId") Long datasetId,
                                        @Param("from") Instant from,
                                        @Param("to") Instant to,
                                        Pageable pageable);

    // Used when no dataset scoping filter is supplied, filters rows to the requester's accessible datasets
    @Query(value = """
            SELECT e.* FROM annotation_events e
            LEFT JOIN dataset_versions dv ON dv.id = e.dataset_version_id
            LEFT JOIN model_versions mv ON mv.id = e.model_version_id
            LEFT JOIN dataset_versions mvdv ON mvdv.id = mv.trained_on_dataset_version_id
            LEFT JOIN images img ON img.id = e.image_id
            LEFT JOIN dataset_versions idv ON idv.id = img.origin_dataset_version_id
            WHERE (CAST(:eventType AS varchar) IS NULL OR e.event_type = CAST(:eventType AS varchar))
              AND (CAST(:from AS timestamptz) IS NULL OR e.occurred_at >= CAST(:from AS timestamptz))
              AND (CAST(:to AS timestamptz) IS NULL OR e.occurred_at <= CAST(:to AS timestamptz))
              AND (
                (dv.dataset_id IS NULL AND mvdv.dataset_id IS NULL AND idv.dataset_id IS NULL)
                OR dv.dataset_id IN (:accessibleDatasetIds)
                OR mvdv.dataset_id IN (:accessibleDatasetIds)
                OR idv.dataset_id IN (:accessibleDatasetIds)
              )
            ORDER BY e.occurred_at DESC
            """,
            countQuery = """
            SELECT count(*) FROM annotation_events e
            LEFT JOIN dataset_versions dv ON dv.id = e.dataset_version_id
            LEFT JOIN model_versions mv ON mv.id = e.model_version_id
            LEFT JOIN dataset_versions mvdv ON mvdv.id = mv.trained_on_dataset_version_id
            LEFT JOIN images img ON img.id = e.image_id
            LEFT JOIN dataset_versions idv ON idv.id = img.origin_dataset_version_id
            WHERE (CAST(:eventType AS varchar) IS NULL OR e.event_type = CAST(:eventType AS varchar))
              AND (CAST(:from AS timestamptz) IS NULL OR e.occurred_at >= CAST(:from AS timestamptz))
              AND (CAST(:to AS timestamptz) IS NULL OR e.occurred_at <= CAST(:to AS timestamptz))
              AND (
                (dv.dataset_id IS NULL AND mvdv.dataset_id IS NULL AND idv.dataset_id IS NULL)
                OR dv.dataset_id IN (:accessibleDatasetIds)
                OR mvdv.dataset_id IN (:accessibleDatasetIds)
                OR idv.dataset_id IN (:accessibleDatasetIds)
              )
            """,
            nativeQuery = true)
    Page<AnnotationEvent> findAccessibleWithNoDatasetFilter(@Param("eventType") String eventType,
                                                              @Param("from") Instant from,
                                                              @Param("to") Instant to,
                                                              @Param("accessibleDatasetIds") Collection<Long> accessibleDatasetIds,
                                                              Pageable pageable);
}
