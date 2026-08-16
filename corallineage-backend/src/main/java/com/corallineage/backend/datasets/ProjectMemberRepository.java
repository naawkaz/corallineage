package com.corallineage.backend.datasets;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    List<ProjectMember> findByDatasetId(Long datasetId);

    List<ProjectMember> findByUserId(Long userId);

    Optional<ProjectMember> findByDatasetIdAndUserId(Long datasetId, Long userId);

    boolean existsByDatasetIdAndUserId(Long datasetId, Long userId);

    long countByDatasetIdAndRole(Long datasetId, ProjectRole role);
}
