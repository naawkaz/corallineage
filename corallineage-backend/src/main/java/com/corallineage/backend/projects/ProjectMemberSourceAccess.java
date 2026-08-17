package com.corallineage.backend.projects;

import com.corallineage.backend.datasets.Dataset;
import jakarta.persistence.*;

/**
 * Grants a COLLABORATOR ProjectMember visibility into one specific Source (Dataset)
 * within the project. Only meaningful when the owning ProjectMember has
 * sourceAccessRestricted = true; see ProjectMember for the full semantics.
 */
@Entity
@Table(name = "project_member_source_access",
       uniqueConstraints = @UniqueConstraint(columnNames = {"project_member_id", "dataset_id"}))
public class ProjectMemberSourceAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_member_id", nullable = false)
    private ProjectMember projectMember;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id", nullable = false)
    private Dataset dataset;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ProjectMember getProjectMember() { return projectMember; }
    public void setProjectMember(ProjectMember projectMember) { this.projectMember = projectMember; }

    public Dataset getDataset() { return dataset; }
    public void setDataset(Dataset dataset) { this.dataset = dataset; }
}
