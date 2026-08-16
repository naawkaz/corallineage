package com.corallineage.backend.datasets;

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
    private final DatasetRepository datasetRepository;
    private final UserRepository userRepository;
    private final ProjectMemberMapper projectMemberMapper;

    public ProjectMemberService(ProjectMemberRepository projectMemberRepository,
                                 DatasetRepository datasetRepository,
                                 UserRepository userRepository,
                                 ProjectMemberMapper projectMemberMapper) {
        this.projectMemberRepository = projectMemberRepository;
        this.datasetRepository = datasetRepository;
        this.userRepository = userRepository;
        this.projectMemberMapper = projectMemberMapper;
    }

    @Transactional
    public ProjectMember addOwner(Dataset dataset, User user) {
        ProjectMember member = new ProjectMember();
        member.setDataset(dataset);
        member.setUser(user);
        member.setRole(ProjectRole.OWNER);
        return projectMemberRepository.save(member);
    }

    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> listMembers(Long datasetId, String actingUsername) {
        requireDataset(datasetId);
        requireMember(datasetId, actingUsername);

        return projectMemberRepository.findByDatasetId(datasetId).stream()
                .map(projectMemberMapper::toResponse)
                .toList();
    }

    @Transactional
    public ProjectMemberResponse addMember(Long datasetId, AddMemberRequest request, String actingUsername) {
        Dataset dataset = requireDataset(datasetId);
        requireOwner(datasetId, actingUsername);

        User target = userRepository.findByUsername(request.getUsername().trim())
                .orElseThrow(() -> new NotFoundException("User not found: " + request.getUsername()));

        if (projectMemberRepository.existsByDatasetIdAndUserId(datasetId, target.getId())) {
            throw new ConflictException(target.getUsername() + " is already a member of this project");
        }

        ProjectMember member = new ProjectMember();
        member.setDataset(dataset);
        member.setUser(target);
        member.setRole(request.getRole());
        ProjectMember saved = projectMemberRepository.save(member);

        return projectMemberMapper.toResponse(saved);
    }

    @Transactional
    public void removeMember(Long datasetId, Long userId, String actingUsername) {
        requireDataset(datasetId);
        requireOwner(datasetId, actingUsername);

        ProjectMember member = projectMemberRepository.findByDatasetIdAndUserId(datasetId, userId)
                .orElseThrow(() -> new NotFoundException("This user is not a member of this project"));

        if (member.getRole() == ProjectRole.OWNER
                && projectMemberRepository.countByDatasetIdAndRole(datasetId, ProjectRole.OWNER) <= 1) {
            throw new ConflictException("Cannot remove the last remaining owner of a project");
        }

        projectMemberRepository.delete(member);
    }

    private Dataset requireDataset(Long datasetId) {
        return datasetRepository.findById(datasetId)
                .orElseThrow(() -> new NotFoundException("Dataset not found: " + datasetId));
    }

    private ProjectMember requireMember(Long datasetId, String actingUsername) {
        User actingUser = userRepository.findByUsername(actingUsername)
                .orElseThrow(() -> new UnauthorizedException("Unknown user"));
        return projectMemberRepository.findByDatasetIdAndUserId(datasetId, actingUser.getId())
                .orElseThrow(() -> new UnauthorizedException("You are not a member of this project"));
    }

    private void requireOwner(Long datasetId, String actingUsername) {
        ProjectMember member = requireMember(datasetId, actingUsername);
        if (member.getRole() != ProjectRole.OWNER) {
            throw new ForbiddenException("Only a project owner can manage collaborators");
        }
    }
}
