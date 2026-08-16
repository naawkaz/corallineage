package com.corallineage.backend.projects;

import com.corallineage.backend.auth.User;
import com.corallineage.backend.auth.UserRepository;
import com.corallineage.backend.exception.ConflictException;
import com.corallineage.backend.exception.ForbiddenException;
import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.exception.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProjectMemberService {

    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectMemberMapper projectMemberMapper;

    public ProjectMemberService(ProjectMemberRepository projectMemberRepository,
                                 ProjectRepository projectRepository,
                                 UserRepository userRepository,
                                 ProjectMemberMapper projectMemberMapper) {
        this.projectMemberRepository = projectMemberRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.projectMemberMapper = projectMemberMapper;
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

        return projectMemberMapper.toResponse(saved);
    }

    @Transactional
    public void removeMember(Long projectId, Long userId, String actingUsername) {
        requireProject(projectId);
        requireOwner(projectId, actingUsername);

        ProjectMember member = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new NotFoundException("This user is not a member of this project"));

        if (member.getRole() == ProjectRole.OWNER
                && projectMemberRepository.countByProjectIdAndRole(projectId, ProjectRole.OWNER) <= 1) {
            throw new ConflictException("Cannot remove the last remaining owner of a project");
        }

        projectMemberRepository.delete(member);
    }

    public boolean isMember(Long projectId, String actingUsername) {
        User actingUser = userRepository.findByUsername(actingUsername).orElse(null);
        if (actingUser == null) return false;
        return projectMemberRepository.existsByProjectIdAndUserId(projectId, actingUser.getId());
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
