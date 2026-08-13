package com.corallineage.backend.logging;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

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
}
