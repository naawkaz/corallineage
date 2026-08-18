package com.corallineage.backend.projects;

import com.corallineage.backend.auth.User;
import com.corallineage.backend.auth.UserRepository;
import com.corallineage.backend.datasets.Dataset;
import com.corallineage.backend.datasets.DatasetRepository;
import com.corallineage.backend.exception.ConflictException;
import com.corallineage.backend.exception.ForbiddenException;
import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.exception.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProjectMemberService {

    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectMemberMapper projectMemberMapper;
    private final ProjectMemberSourceAccessRepository sourceAccessRepository;
    private final DatasetRepository datasetRepository;

    public ProjectMemberService(ProjectMemberRepository projectMemberRepository,
                                 ProjectRepository projectRepository,
                                 UserRepository userRepository,
                                 ProjectMemberMapper projectMemberMapper,
                                 ProjectMemberSourceAccessRepository sourceAccessRepository,
                                 DatasetRepository datasetRepository) {
        this.projectMemberRepository = projectMemberRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.projectMemberMapper = projectMemberMapper;
        this.sourceAccessRepository = sourceAccessRepository;
        this.datasetRepository = datasetRepository;
    }

    @Transactional
    public ProjectMember addOwner(Project project, User user) {
        ProjectMember member = new ProjectMember();
        member.setProject(project);
        member.setUser(user);
        member.setRole(ProjectRole.OWNER);
        return projectMemberRepository.save(member);
    }

    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> listMembers(Long projectId, String actingUsername) {
        requireProject(projectId);
        requireMember(projectId, actingUsername);

        return projectMemberRepository.findByProjectId(projectId).stream()
                .map(projectMemberMapper::toResponse)
                .toList();
    }

    @Transactional
    public ProjectMemberResponse addMember(Long projectId, AddMemberRequest request, String actingUsername) {
        Project project = requireProject(projectId);
        requireOwner(projectId, actingUsername);

        User target = userRepository.findByUsername(request.getUsername().trim())
                .orElseThrow(() -> new NotFoundException("User not found: " + request.getUsername()));

        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, target.getId())) {
            throw new ConflictException(target.getUsername() + " is already a member of this project");
        }

        ProjectMember member = new ProjectMember();
        member.setProject(project);
        member.setUser(target);
        member.setRole(request.getRole());
        ProjectMember saved = projectMemberRepository.save(member);

        applySourceAccess(project, saved, request.getSourceIds());
        touchProject(project);

        return projectMemberMapper.toResponse(saved);
    }

    @Transactional
    public void removeMember(Long projectId, Long userId, String actingUsername) {
        Project project = requireProject(projectId);
        requireOwner(projectId, actingUsername);

        ProjectMember member = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new NotFoundException("This user is not a member of this project"));

        if (member.getRole() == ProjectRole.OWNER
                && projectMemberRepository.countByProjectIdAndRole(projectId, ProjectRole.OWNER) <= 1) {
            throw new ConflictException("Cannot remove the last remaining owner of a project");
        }

        sourceAccessRepository.deleteByProjectMemberId(member.getId());
        projectMemberRepository.delete(member);
        touchProject(project);
    }

    /**
     * Returns the collaborator's current Source-access scope for the "Manage access" dialog.
     */
    @Transactional(readOnly = true)
    public SourceAccessResponse getSourceAccess(Long projectId, Long userId, String actingUsername) {
        requireProject(projectId);
        requireMember(projectId, actingUsername);

        ProjectMember member = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new NotFoundException("This user is not a member of this project"));

        SourceAccessResponse response = new SourceAccessResponse();
        if (member.getRole() == ProjectRole.OWNER || !member.isSourceAccessRestricted()) {
            response.setRestricted(false);
            response.setSourceIds(datasetRepository.findAllByProjectIdOrderByCreatedAtDesc(projectId).stream()
                    .map(Dataset::getId).toList());
        } else {
            response.setRestricted(true);
            response.setSourceIds(sourceAccessRepository.findByProjectMemberId(member.getId()).stream()
                    .map(a -> a.getDataset().getId()).toList());
        }
        return response;
    }

    /**
     * Replaces a collaborator's Source-access scope. Owners cannot be restricted; only
     * COLLABORATOR members can be scoped.
     */
    @Transactional
    public SourceAccessResponse setSourceAccess(Long projectId, Long userId, SourceAccessRequest request, String actingUsername) {
        Project project = requireProject(projectId);
        requireOwner(projectId, actingUsername);

        ProjectMember member = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new NotFoundException("This user is not a member of this project"));

        if (member.getRole() == ProjectRole.OWNER) {
            throw new ConflictException("The project owner always has access to every Source");
        }

        applySourceAccess(project, member, request.getSourceIds());
        touchProject(project);

        return getSourceAccess(projectId, userId, actingUsername);
    }

    /**
     * Encodes the "insert full-access rows, or narrow to a subset" default-access decision
     * (see ProjectMember). A null/omitted sourceIds list, or one that names every current
     * Source in the project, leaves the member unrestricted with no join rows persisted -
     * this also means Sources added to the project later are automatically visible to them.
     * Any proper subset marks the member restricted and persists exactly those rows.
     */
    private void applySourceAccess(Project project, ProjectMember member, List<Long> requestedSourceIds) {
        List<Dataset> allSources = datasetRepository.findAllByProjectIdOrderByCreatedAtDesc(project.getId());
        Set<Long> allSourceIds = allSources.stream().map(Dataset::getId).collect(Collectors.toSet());

        sourceAccessRepository.deleteByProjectMemberId(member.getId());

        boolean unrestricted = requestedSourceIds == null || Set.copyOf(requestedSourceIds).equals(allSourceIds);

        if (unrestricted) {
            member.setSourceAccessRestricted(false);
            projectMemberRepository.save(member);
            return;
        }

        member.setSourceAccessRestricted(true);
        projectMemberRepository.save(member);

        Set<Long> requestedSet = Set.copyOf(requestedSourceIds);
        List<ProjectMemberSourceAccess> rows = allSources.stream()
                .filter(d -> requestedSet.contains(d.getId()))
                .map(d -> {
                    ProjectMemberSourceAccess access = new ProjectMemberSourceAccess();
                    access.setProjectMember(member);
                    access.setDataset(d);
                    return access;
                })
                .toList();
        sourceAccessRepository.saveAll(rows);
    }

    private void touchProject(Project project) {
        project.setUpdatedAt(Instant.now());
        projectRepository.save(project);
    }

    public boolean isMember(Long projectId, String actingUsername) {
        User actingUser = userRepository.findByUsername(actingUsername).orElse(null);
        if (actingUser == null) return false;
        return projectMemberRepository.existsByProjectIdAndUserId(projectId, actingUser.getId());
    }

    /**
     * Returns the set of Source (dataset) ids the acting user may see within this project,
     * or empty Optional to mean "unrestricted, sees all" (owners, and unrestricted
     * collaborators). Returns an empty (present) Set for a restricted collaborator with
     * zero granted Sources.
     */
    @Transactional(readOnly = true)
    public java.util.Optional<Set<Long>> getAccessibleSourceIds(Long projectId, String actingUsername) {
        User actingUser = userRepository.findByUsername(actingUsername).orElse(null);
        if (actingUser == null) return java.util.Optional.of(Set.of());

        ProjectMember member = projectMemberRepository.findByProjectIdAndUserId(projectId, actingUser.getId())
                .orElse(null);
        if (member == null) return java.util.Optional.of(Set.of());

        if (member.getRole() == ProjectRole.OWNER || !member.isSourceAccessRestricted()) {
            return java.util.Optional.empty();
        }

        return java.util.Optional.of(sourceAccessRepository.findByProjectMemberId(member.getId()).stream()
                .map(a -> a.getDataset().getId())
                .collect(Collectors.toSet()));
    }

    /**
     * Unions accessible Source (dataset) ids across every project the user is a member of.
     * Used by unscoped listing endpoints (the Global Event Log, the ontology dirty queue) to
     * filter results down to what the requester can see, rather than blocking the call outright
     * when it's called with no scoping filter. For each membership: an unrestricted member
     * (owner, or a collaborator with sourceAccessRestricted=false) contributes every dataset id
     * in that project; a restricted collaborator contributes only their explicit allowlist.
     */
    @Transactional(readOnly = true)
    public Set<Long> getAllAccessibleDatasetIds(String actingUsername) {
        User actingUser = userRepository.findByUsername(actingUsername).orElse(null);
        if (actingUser == null) return Set.of();

        List<ProjectMember> memberships = projectMemberRepository.findByUserId(actingUser.getId());
        Set<Long> accessible = new java.util.HashSet<>();
        for (ProjectMember member : memberships) {
            Long projectId = member.getProject().getId();
            if (member.getRole() == ProjectRole.OWNER || !member.isSourceAccessRestricted()) {
                datasetRepository.findAllByProjectIdOrderByCreatedAtDesc(projectId)
                        .forEach(d -> accessible.add(d.getId()));
            } else {
                sourceAccessRepository.findByProjectMemberId(member.getId())
                        .forEach(a -> accessible.add(a.getDataset().getId()));
            }
        }
        return accessible;
    }

    private Project requireProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));
    }

    private ProjectMember requireMember(Long projectId, String actingUsername) {
        User actingUser = userRepository.findByUsername(actingUsername)
                .orElseThrow(() -> new UnauthorizedException("Unknown user"));
        return projectMemberRepository.findByProjectIdAndUserId(projectId, actingUser.getId())
                .orElseThrow(() -> new UnauthorizedException("You are not a member of this project"));
    }

    private void requireOwner(Long projectId, String actingUsername) {
        ProjectMember member = requireMember(projectId, actingUsername);
        if (member.getRole() != ProjectRole.OWNER) {
            throw new ForbiddenException("Only a project owner can manage collaborators");
        }
    }
}
