package com.corallineage.backend.logging;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;

public interface AnnotationEventRepository extends JpaRepository<AnnotationEvent, Long> {

    // Native query with explicit ::text/::timestamptz casts: Postgres' extended query
    // protocol cannot infer a bind parameter's type from an "x IS NULL OR ..." predicate
    // alone (no surrounding type context), which raised "could not determine data type
    // of parameter" for the nullable filters when using an equivalent JPQL query.
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

    // Used only when the caller supplies no *dataset-scoping* filter (datasetId/datasetVersionId/
    // modelVersionId all absent — see AnnotationEventController); eventType/from/to still apply
    // normally. Rather than blocking the call, results are filtered down to rows whose derivable
    // dataset id (via dataset_version_id directly, via the triggering model's trained-on version,
    // or via the logged image's origin version) is in the requester's accessible set. A row with
    // no derivable dataset id on any of the three (a genuinely cross-cutting event) stays visible
    // to everyone - there is no ownership anchor to hide it behind.
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
