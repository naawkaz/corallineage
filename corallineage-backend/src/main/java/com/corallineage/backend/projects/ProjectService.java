package com.corallineage.backend.projects;

import com.corallineage.backend.auth.user.User;
import com.corallineage.backend.auth.user.UserRepository;
import com.corallineage.backend.datasets.DatasetRepository;
import com.corallineage.backend.exception.ConflictException;
import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.exception.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;
    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectMemberService projectMemberService;
    private final DatasetRepository datasetRepository;

    public ProjectService(ProjectRepository projectRepository,
            ProjectMapper projectMapper,
            UserRepository userRepository,
            ProjectMemberRepository projectMemberRepository,
            ProjectMemberService projectMemberService,
            DatasetRepository datasetRepository) {
        this.projectRepository = projectRepository;
        this.projectMapper = projectMapper;
        this.userRepository = userRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.projectMemberService = projectMemberService;
        this.datasetRepository = datasetRepository;
    }

    @Transactional
    public ProjectResponse createProject(CreateProjectRequest request, String actingUsername) {
        if (projectRepository.findByProjectName(request.getProjectName()).isPresent()) {
            throw new ConflictException("Project name already exists: " + request.getProjectName());
        }

        User owner = userRepository.findByUsername(actingUsername)
                .orElseThrow(() -> new UnauthorizedException("Unknown user"));

        Project project = new Project();
        project.setProjectName(request.getProjectName());
        project.setDescription(request.getDescription());
        project.setCreatedBy(actingUsername);
        Project saved = projectRepository.save(project);

        projectMemberService.addOwner(saved, owner);

        return projectMapper.toSummaryResponse(saved, 0, ProjectRole.OWNER);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getAllProjects(String actingUsername) {
        User user = userRepository.findByUsername(actingUsername)
                .orElseThrow(() -> new UnauthorizedException("Unknown user"));

        List<ProjectMember> memberships = projectMemberRepository.findByUserId(user.getId());
        Map<Long, ProjectRole> roleByProjectId = memberships.stream()
                .collect(java.util.stream.Collectors.toMap(m -> m.getProject().getId(), ProjectMember::getRole));

        if (roleByProjectId.isEmpty()) {
            return List.of();
        }

        return projectRepository.findAllByIdInOrderByCreatedAtDesc(List.copyOf(roleByProjectId.keySet())).stream()
                .map(project -> projectMapper.toSummaryResponse(project,
                        datasetRepository.countByProjectId(project.getId()),
                        roleByProjectId.get(project.getId()),
                        ownerDisplayName(project.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProjectById(Long id, String actingUsername) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Project not found: " + id));

        ProjectRole callerRole = null;
        if (actingUsername != null) {
            User user = userRepository.findByUsername(actingUsername).orElse(null);
            if (user != null) {
                callerRole = projectMemberRepository.findByProjectIdAndUserId(id, user.getId())
                        .map(ProjectMember::getRole)
                        .orElse(null);
            }
        }

        long sourceCount = datasetRepository.countByProjectId(id);
        return projectMapper.toSummaryResponse(project, sourceCount, callerRole, ownerDisplayName(id));
    }

    private String ownerDisplayName(Long projectId) {
        return projectMemberRepository.findByProjectIdAndRole(projectId, ProjectRole.OWNER).stream()
                .findFirst()
                .map(member -> member.getUser().getDisplayName())
                .orElse(null);
    }
}
