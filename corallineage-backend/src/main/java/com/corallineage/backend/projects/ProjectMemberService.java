package com.corallineage.backend.projects;

import com.corallineage.backend.auth.user.User;
import com.corallineage.backend.auth.user.UserRepository;
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

    /** Returns a collaborator's current source access scope */
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

    /** Replaces a collaborator's source access scope */
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

    /** Leaves a member unrestricted, or persists a restricted subset of sources */
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

    /** Returns the source ids a user may access in a project, empty optional means unrestricted */
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

    /** Unions accessible source ids across every project a user belongs to */
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
