package com.corallineage.backend.pointannotation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GeneratedPointRepository extends JpaRepository<GeneratedPoint, Long> {

    // Explicit order (not just "whatever the query planner returns") - without it, Postgres can
    // return a different row order on each call once rows have been UPDATEd (e.g. the frontend's
    // seed-labeling list visibly reordering itself as points get labeled), since an unsorted query
    // has no guaranteed order at all. Ordering by id matches point-generation order.
    Page<GeneratedPoint> findByJobIdAndSeedOrderByIdAsc(Long jobId, boolean seed, Pageable pageable);

    long countByJobIdAndSeedTrue(Long jobId);

    long countByJobIdAndSeedTrueAndStatus(Long jobId, String status);

    long countByJobIdAndSeedFalseAndStatus(Long jobId, String status);

    List<GeneratedPoint> findByJobIdAndSeedFalseAndStatus(Long jobId, String status);

    Optional<GeneratedPoint> findByIdAndJobId(Long id, Long jobId);
}
