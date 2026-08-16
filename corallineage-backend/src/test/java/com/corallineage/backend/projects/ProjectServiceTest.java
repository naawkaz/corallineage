package com.corallineage.backend.projects;

import com.corallineage.backend.auth.User;
import com.corallineage.backend.auth.UserRepository;
import com.corallineage.backend.datasets.DatasetRepository;
import com.corallineage.backend.exception.ConflictException;
import com.corallineage.backend.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private ProjectMemberService projectMemberService;
    @Mock
    private DatasetRepository datasetRepository;

    @InjectMocks
    private ProjectService projectService;

    @Spy
    private ProjectMapper projectMapper;

    private CreateProjectRequest request() {
        CreateProjectRequest request = new CreateProjectRequest();
        request.setProjectName("Moorea 2010 Survey");
        request.setDescription("Coral reef monitoring at the Moorea LTER site");
        return request;
    }

    private Project project() {
        Project project = new Project();
        project.setId(1L);
        project.setProjectName("Moorea 2010 Survey");
        project.setDescription("Coral reef monitoring at the Moorea LTER site");
        project.setCreatedBy("researcher_01");
        return project;
    }

    private User user() {
        User user = new User();
        user.setId(10L);
        user.setUsername("researcher_01");
        user.setDisplayName("researcher_01");
        return user;
    }

    @Test
    void createProject_success_savesProjectAndAddsOwner() {
        when(projectRepository.findByProjectName("Moorea 2010 Survey")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("researcher_01")).thenReturn(Optional.of(user()));
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        ProjectResponse response = projectService.createProject(request(), "researcher_01");

        assertThat(response.getProjectName()).isEqualTo("Moorea 2010 Survey");
        assertThat(response.getCreatedBy()).isEqualTo("researcher_01");
        verify(projectRepository).save(any(Project.class));
        verify(projectMemberService).addOwner(any(Project.class), any(User.class));
    }

    @Test
    void createProject_duplicateName_throwsConflict() {
        when(projectRepository.findByProjectName("Moorea 2010 Survey")).thenReturn(Optional.of(project()));

        assertThatThrownBy(() -> projectService.createProject(request(), "researcher_01"))
                .isInstanceOf(ConflictException.class);
        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    void getAllProjects_returnsOnlyMemberProjectsWithRole() {
        User user = user();
        ProjectMember membership = new ProjectMember();
        membership.setProject(project());
        membership.setUser(user);
        membership.setRole(ProjectRole.OWNER);

        when(userRepository.findByUsername("researcher_01")).thenReturn(Optional.of(user));
        when(projectMemberRepository.findByUserId(10L)).thenReturn(List.of(membership));
        when(projectRepository.findAllByIdInOrderByCreatedAtDesc(List.of(1L))).thenReturn(List.of(project()));
        when(datasetRepository.countByProjectId(1L)).thenReturn(3L);

        List<ProjectResponse> result = projectService.getAllProjects("researcher_01");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSourceCount()).isEqualTo(3L);
        assertThat(result.get(0).getCallerRole()).isEqualTo(ProjectRole.OWNER);
    }

    @Test
    void getAllProjects_noMemberships_returnsEmptyList() {
        User user = user();
        when(userRepository.findByUsername("researcher_01")).thenReturn(Optional.of(user));
        when(projectMemberRepository.findByUserId(10L)).thenReturn(List.of());

        List<ProjectResponse> result = projectService.getAllProjects("researcher_01");

        assertThat(result).isEmpty();
    }

    @Test
    void getProjectById_notFound_throwsNotFound() {
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getProjectById(99L, "researcher_01"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getProjectById_success_includesSourceCountAndCallerRole() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project()));
        when(userRepository.findByUsername("researcher_01")).thenReturn(Optional.of(user()));
        when(projectMemberRepository.findByProjectIdAndUserId(1L, 10L))
                .thenReturn(Optional.of(new ProjectMember() {{
                    setRole(ProjectRole.OWNER);
                }}));
        when(datasetRepository.countByProjectId(1L)).thenReturn(2L);

        ProjectResponse response = projectService.getProjectById(1L, "researcher_01");

        assertThat(response.getSourceCount()).isEqualTo(2L);
        assertThat(response.getCallerRole()).isEqualTo(ProjectRole.OWNER);
    }
}
