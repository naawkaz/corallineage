package com.corallineage.backend.projects;

import com.corallineage.backend.auth.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "project_memberships",
       uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "user_id"}))
public class ProjectMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private ProjectRole role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** False by default, unrestricted access to every source, true limits access to granted sources */
    @Column(name = "source_access_restricted", nullable = false)
    private boolean sourceAccessRestricted = false;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }


    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public ProjectRole getRole() { return role; }
    public void setRole(ProjectRole role) { this.role = role; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public boolean isSourceAccessRestricted() { return sourceAccessRestricted; }
    public void setSourceAccessRestricted(boolean sourceAccessRestricted) { this.sourceAccessRestricted = sourceAccessRestricted; }
}
