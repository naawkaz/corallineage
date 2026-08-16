package com.corallineage.backend.pointannotation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GeneratedPointRepository extends JpaRepository<GeneratedPoint, Long> {

    Page<GeneratedPoint> findByJobIdAndSeed(Long jobId, boolean seed, Pageable pageable);

    long countByJobIdAndSeedTrue(Long jobId);

    long countByJobIdAndSeedTrueAndStatus(Long jobId, String status);

    long countByJobIdAndSeedFalseAndStatus(Long jobId, String status);

    List<GeneratedPoint> findByJobIdAndSeedFalseAndStatus(Long jobId, String status);

    Optional<GeneratedPoint> findByIdAndJobId(Long id, Long jobId);
}
