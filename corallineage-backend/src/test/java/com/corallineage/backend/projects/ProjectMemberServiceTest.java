package com.corallineage.backend.projects;

import com.corallineage.backend.auth.User;
import com.corallineage.backend.auth.UserRepository;
import com.corallineage.backend.exception.ConflictException;
import com.corallineage.backend.exception.ForbiddenException;
import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.exception.UnauthorizedException;
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
class ProjectMemberServiceTest {

    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private UserRepository userRepository;

    @Spy
    private ProjectMemberMapper projectMemberMapper;

    @InjectMocks
    private ProjectMemberService projectMemberService;

    private Project project() {
        Project project = new Project();
        project.setId(1L);
        project.setProjectName("Moorea 2010 Survey");
        return project;
    }

    private User owner() {
        User user = new User();
        user.setId(10L);
        user.setUsername("owner_user");
        user.setDisplayName("Owner");
        return user;
    }

    private User collaborator() {
        User user = new User();
        user.setId(20L);
        user.setUsername("collab_user");
        user.setDisplayName("Collaborator");
        return user;
    }

    private ProjectMember ownerMembership() {
        ProjectMember member = new ProjectMember();
        member.setId(100L);
        member.setProject(project());
        member.setUser(owner());
        member.setRole(ProjectRole.OWNER);
        return member;
    }

    @Test
    void addMember_ownerAddsNewCollaborator_success() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project()));
        when(userRepository.findByUsername("owner_user")).thenReturn(Optional.of(owner()));
        when(projectMemberRepository.findByProjectIdAndUserId(1L, 10L)).thenReturn(Optional.of(ownerMembership()));
        when(userRepository.findByUsername("collab_user")).thenReturn(Optional.of(collaborator()));
        when(projectMemberRepository.existsByProjectIdAndUserId(1L, 20L)).thenReturn(false);
        when(projectMemberRepository.save(any(ProjectMember.class))).thenAnswer(inv -> {
            ProjectMember m = inv.getArgument(0);
            m.setId(200L);
            return m;
        });

        AddMemberRequest request = new AddMemberRequest();
        request.setUsername("collab_user");
        request.setRole(ProjectRole.COLLABORATOR);

        ProjectMemberResponse response = projectMemberService.addMember(1L, request, "owner_user");

        assertThat(response.getUsername()).isEqualTo("collab_user");
        assertThat(response.getRole()).isEqualTo(ProjectRole.COLLABORATOR);
        verify(projectMemberRepository).save(any(ProjectMember.class));
    }

    @Test
    void addMember_nonOwnerActing_throwsForbidden() {
        ProjectMember collabMembership = new ProjectMember();
        collabMembership.setProject(project());
        collabMembership.setUser(collaborator());
        collabMembership.setRole(ProjectRole.COLLABORATOR);

        when(projectRepository.findById(1L)).thenReturn(Optional.of(project()));
        when(userRepository.findByUsername("collab_user")).thenReturn(Optional.of(collaborator()));
        when(projectMemberRepository.findByProjectIdAndUserId(1L, 20L)).thenReturn(Optional.of(collabMembership));

        AddMemberRequest request = new AddMemberRequest();
        request.setUsername("someone_else");
        request.setRole(ProjectRole.COLLABORATOR);

        assertThatThrownBy(() -> projectMemberService.addMember(1L, request, "collab_user"))
                .isInstanceOf(ForbiddenException.class);
        verify(projectMemberRepository, never()).save(any(ProjectMember.class));
    }

    @Test
    void addMember_actingUserNotAMember_throwsUnauthorized() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project()));
        when(userRepository.findByUsername("stranger")).thenReturn(Optional.of(collaborator()));
        when(projectMemberRepository.findByProjectIdAndUserId(1L, 20L)).thenReturn(Optional.empty());

        AddMemberRequest request = new AddMemberRequest();
        request.setUsername("someone_else");
        request.setRole(ProjectRole.COLLABORATOR);

        assertThatThrownBy(() -> projectMemberService.addMember(1L, request, "stranger"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void addMember_targetUsernameNotFound_throwsNotFound() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project()));
        when(userRepository.findByUsername("owner_user")).thenReturn(Optional.of(owner()));
        when(projectMemberRepository.findByProjectIdAndUserId(1L, 10L)).thenReturn(Optional.of(ownerMembership()));
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        AddMemberRequest request = new AddMemberRequest();
        request.setUsername("ghost");
        request.setRole(ProjectRole.COLLABORATOR);

        assertThatThrownBy(() -> projectMemberService.addMember(1L, request, "owner_user"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void addMember_alreadyMember_throwsConflict() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project()));
        when(userRepository.findByUsername("owner_user")).thenReturn(Optional.of(owner()));
        when(projectMemberRepository.findByProjectIdAndUserId(1L, 10L)).thenReturn(Optional.of(ownerMembership()));
        when(userRepository.findByUsername("collab_user")).thenReturn(Optional.of(collaborator()));
        when(projectMemberRepository.existsByProjectIdAndUserId(1L, 20L)).thenReturn(true);

        AddMemberRequest request = new AddMemberRequest();
        request.setUsername("collab_user");
        request.setRole(ProjectRole.COLLABORATOR);

        assertThatThrownBy(() -> projectMemberService.addMember(1L, request, "owner_user"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void removeMember_lastRemainingOwner_throwsConflict() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project()));
        when(userRepository.findByUsername("owner_user")).thenReturn(Optional.of(owner()));
        when(projectMemberRepository.findByProjectIdAndUserId(1L, 10L)).thenReturn(Optional.of(ownerMembership()));
        when(projectMemberRepository.countByProjectIdAndRole(1L, ProjectRole.OWNER)).thenReturn(1L);

        assertThatThrownBy(() -> projectMemberService.removeMember(1L, 10L, "owner_user"))
                .isInstanceOf(ConflictException.class);
        verify(projectMemberRepository, never()).delete(any(ProjectMember.class));
    }

    @Test
    void removeMember_collaborator_success() {
        ProjectMember collabMembership = new ProjectMember();
        collabMembership.setProject(project());
        collabMembership.setUser(collaborator());
        collabMembership.setRole(ProjectRole.COLLABORATOR);

        when(projectRepository.findById(1L)).thenReturn(Optional.of(project()));
        when(userRepository.findByUsername("owner_user")).thenReturn(Optional.of(owner()));
        when(projectMemberRepository.findByProjectIdAndUserId(1L, 10L))
                .thenReturn(Optional.of(ownerMembership()));
        when(projectMemberRepository.findByProjectIdAndUserId(1L, 20L))
                .thenReturn(Optional.of(collabMembership));

        projectMemberService.removeMember(1L, 20L, "owner_user");

        verify(projectMemberRepository).delete(collabMembership);
    }

    @Test
    void listMembers_returnsAllMembers() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project()));
        when(userRepository.findByUsername("owner_user")).thenReturn(Optional.of(owner()));
        when(projectMemberRepository.findByProjectIdAndUserId(1L, 10L)).thenReturn(Optional.of(ownerMembership()));
        when(projectMemberRepository.findByProjectId(1L)).thenReturn(List.of(ownerMembership()));

        List<ProjectMemberResponse> members = projectMemberService.listMembers(1L, "owner_user");

        assertThat(members).hasSize(1);
        assertThat(members.get(0).getUsername()).isEqualTo("owner_user");
        assertThat(members.get(0).getRole()).isEqualTo(ProjectRole.OWNER);
    }

    @Test
    void isMember_realMember_returnsTrue() {
        when(userRepository.findByUsername("owner_user")).thenReturn(Optional.of(owner()));
        when(projectMemberRepository.existsByProjectIdAndUserId(1L, 10L)).thenReturn(true);

        assertThat(projectMemberService.isMember(1L, "owner_user")).isTrue();
    }

    @Test
    void isMember_unknownUser_returnsFalse() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThat(projectMemberService.isMember(1L, "ghost")).isFalse();
    }
}
