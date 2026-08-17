package com.corallineage.backend.projects;

import com.corallineage.backend.auth.User;
import com.corallineage.backend.auth.UserRepository;
import com.corallineage.backend.datasets.DatasetRepository;
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
import static org.mockito.ArgumentMatchers.argThat;
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
    @Mock
    private ProjectMemberSourceAccessRepository sourceAccessRepository;
    @Mock
    private DatasetRepository datasetRepository;

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
        verify(projectMemberRepository, org.mockito.Mockito.atLeastOnce()).save(any(ProjectMember.class));
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

    private com.corallineage.backend.datasets.Dataset dataset(long id) {
        com.corallineage.backend.datasets.Dataset dataset = new com.corallineage.backend.datasets.Dataset();
        dataset.setId(id);
        return dataset;
    }

    @Test
    void addMember_withAllSourcesChecked_isUnrestricted() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project()));
        when(userRepository.findByUsername("owner_user")).thenReturn(Optional.of(owner()));
        when(projectMemberRepository.findByProjectIdAndUserId(1L, 10L)).thenReturn(Optional.of(ownerMembership()));
        when(userRepository.findByUsername("collab_user")).thenReturn(Optional.of(collaborator()));
        when(projectMemberRepository.existsByProjectIdAndUserId(1L, 20L)).thenReturn(false);
        when(datasetRepository.findAllByProjectIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(dataset(1L), dataset(2L)));
        when(projectMemberRepository.save(any(ProjectMember.class))).thenAnswer(inv -> {
            ProjectMember m = inv.getArgument(0);
            m.setId(200L);
            return m;
        });

        AddMemberRequest request = new AddMemberRequest();
        request.setUsername("collab_user");
        request.setRole(ProjectRole.COLLABORATOR);
        request.setSourceIds(List.of(1L, 2L));

        ProjectMemberResponse response = projectMemberService.addMember(1L, request, "owner_user");

        assertThat(response.isSourceAccessRestricted()).isFalse();
        verify(sourceAccessRepository, never()).saveAll(any());
    }

    @Test
    void addMember_withSourceSubsetChecked_isRestrictedAndPersistsRows() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project()));
        when(userRepository.findByUsername("owner_user")).thenReturn(Optional.of(owner()));
        when(projectMemberRepository.findByProjectIdAndUserId(1L, 10L)).thenReturn(Optional.of(ownerMembership()));
        when(userRepository.findByUsername("collab_user")).thenReturn(Optional.of(collaborator()));
        when(projectMemberRepository.existsByProjectIdAndUserId(1L, 20L)).thenReturn(false);
        when(datasetRepository.findAllByProjectIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(dataset(1L), dataset(2L)));
        when(projectMemberRepository.save(any(ProjectMember.class))).thenAnswer(inv -> {
            ProjectMember m = inv.getArgument(0);
            m.setId(200L);
            return m;
        });

        AddMemberRequest request = new AddMemberRequest();
        request.setUsername("collab_user");
        request.setRole(ProjectRole.COLLABORATOR);
        request.setSourceIds(List.of(1L));

        ProjectMemberResponse response = projectMemberService.addMember(1L, request, "owner_user");

        assertThat(response.isSourceAccessRestricted()).isTrue();
        verify(sourceAccessRepository).saveAll(argThat(rows -> {
            List<ProjectMemberSourceAccess> list = new java.util.ArrayList<>();
            rows.forEach(list::add);
            return list.size() == 1 && list.get(0).getDataset().getId().equals(1L);
        }));
    }

    @Test
    void getAccessibleSourceIds_unrestrictedMember_returnsEmptyOptional() {
        when(userRepository.findByUsername("collab_user")).thenReturn(Optional.of(collaborator()));
        ProjectMember member = new ProjectMember();
        member.setId(300L);
        member.setRole(ProjectRole.COLLABORATOR);
        member.setSourceAccessRestricted(false);
        when(projectMemberRepository.findByProjectIdAndUserId(1L, 20L)).thenReturn(Optional.of(member));

        Optional<java.util.Set<Long>> result = projectMemberService.getAccessibleSourceIds(1L, "collab_user");

        assertThat(result).isEmpty();
    }

    @Test
    void getAccessibleSourceIds_restrictedMember_returnsGrantedSet() {
        when(userRepository.findByUsername("collab_user")).thenReturn(Optional.of(collaborator()));
        ProjectMember member = new ProjectMember();
        member.setId(300L);
        member.setRole(ProjectRole.COLLABORATOR);
        member.setSourceAccessRestricted(true);
        when(projectMemberRepository.findByProjectIdAndUserId(1L, 20L)).thenReturn(Optional.of(member));

        ProjectMemberSourceAccess access = new ProjectMemberSourceAccess();
        access.setDataset(dataset(5L));
        when(sourceAccessRepository.findByProjectMemberId(300L)).thenReturn(List.of(access));

        Optional<java.util.Set<Long>> result = projectMemberService.getAccessibleSourceIds(1L, "collab_user");

        assertThat(result).contains(java.util.Set.of(5L));
    }

    @Test
    void getAccessibleSourceIds_owner_returnsEmptyOptionalRegardlessOfFlag() {
        when(userRepository.findByUsername("owner_user")).thenReturn(Optional.of(owner()));
        ProjectMember member = ownerMembership();
        member.setSourceAccessRestricted(true);
        when(projectMemberRepository.findByProjectIdAndUserId(1L, 10L)).thenReturn(Optional.of(member));

        Optional<java.util.Set<Long>> result = projectMemberService.getAccessibleSourceIds(1L, "owner_user");

        assertThat(result).isEmpty();
    }

    @Test
    void setSourceAccess_ownerCannotBeRestricted_throwsConflict() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project()));
        when(userRepository.findByUsername("owner_user")).thenReturn(Optional.of(owner()));
        when(projectMemberRepository.findByProjectIdAndUserId(1L, 10L)).thenReturn(Optional.of(ownerMembership()));

        SourceAccessRequest request = new SourceAccessRequest();
        request.setSourceIds(List.of(1L));

        assertThatThrownBy(() -> projectMemberService.setSourceAccess(1L, 10L, request, "owner_user"))
                .isInstanceOf(ConflictException.class);
    }
}
