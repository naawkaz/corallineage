package com.corallineage.backend.projects;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectMemberSourceAccessRepository extends JpaRepository<ProjectMemberSourceAccess, Long> {

    List<ProjectMemberSourceAccess> findByProjectMemberId(Long projectMemberId);

    void deleteByProjectMemberId(Long projectMemberId);
}
