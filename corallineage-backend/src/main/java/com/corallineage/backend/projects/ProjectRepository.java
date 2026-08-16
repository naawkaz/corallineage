package com.corallineage.backend.projects;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByProjectName(String projectName);

    List<Project> findAllByOrderByCreatedAtDesc();

    List<Project> findAllByIdInOrderByCreatedAtDesc(List<Long> ids);
}
