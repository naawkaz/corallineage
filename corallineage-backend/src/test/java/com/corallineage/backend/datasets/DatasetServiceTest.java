package com.corallineage.backend.datasets;

import com.corallineage.backend.exception.ConflictException;
import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.exception.UnauthorizedException;
import com.corallineage.backend.projects.Project;
import com.corallineage.backend.projects.ProjectMemberService;
import com.corallineage.backend.projects.ProjectRepository;
import com.corallineage.backend.versioning.dataset.DatasetVersion;
import com.corallineage.backend.versioning.dataset.DatasetVersionRepository;
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
class DatasetServiceTest {

    @Mock
    private DatasetRepository datasetRepository;
    @Mock
    private DatasetVersionRepository datasetVersionRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectMemberService projectMemberService;

    @InjectMocks
    private DatasetService datasetService;

    @Spy
    private DatasetMapper datasetMapper;

    private CreateDatasetRequest request() {
        CreateDatasetRequest request = new CreateDatasetRequest();
        request.setDatasetName("Moorea Labeled Corals");
        request.setDescription("Point-annotation survey images from the Moorea LTER site");
        request.setCreatedBy("researcher_01");
        return request;
    }

    private Dataset dataset() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setDatasetName("Moorea Labeled Corals");
        dataset.setDescription("Point-annotation survey images from the Moorea LTER site");
        dataset.setCreatedBy("researcher_01");
        dataset.setProject(project());
        return dataset;
    }

    private Project project() {
        Project project = new Project();
        project.setId(5L);
        project.setProjectName("Moorea 2010 Survey");
        return project;
    }

    @Test
    void createDataset_success_savesDatasetScopedToProject() {
        when(datasetRepository.findByDatasetName("Moorea Labeled Corals")).thenReturn(Optional.empty());
        when(projectRepository.findById(5L)).thenReturn(Optional.of(project()));
        when(projectMemberService.isMember(5L, "researcher_01")).thenReturn(true);
        when(datasetRepository.save(any(Dataset.class))).thenAnswer(inv -> inv.getArgument(0));

        DatasetResponse response = datasetService.createDataset(request(), 5L, "researcher_01");

        assertThat(response.getDatasetName()).isEqualTo("Moorea Labeled Corals");
        assertThat(response.getCreatedBy()).isEqualTo("researcher_01");
        verify(datasetRepository).save(any(Dataset.class));
    }

    @Test
    void createDataset_duplicateName_throwsConflict() {
        when(datasetRepository.findByDatasetName("Moorea Labeled Corals"))
                .thenReturn(Optional.of(dataset()));

        assertThatThrownBy(() -> datasetService.createDataset(request(), 5L, "researcher_01"))
                .isInstanceOf(ConflictException.class);
        verify(datasetRepository, never()).save(any(Dataset.class));
    }

    @Test
    void createDataset_projectNotFound_throwsNotFound() {
        when(datasetRepository.findByDatasetName("Moorea Labeled Corals")).thenReturn(Optional.empty());
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> datasetService.createDataset(request(), 99L, "researcher_01"))
                .isInstanceOf(NotFoundException.class);
        verify(datasetRepository, never()).save(any(Dataset.class));
    }

    @Test
    void createDataset_actingUserNotAProjectMember_throwsUnauthorized() {
        when(datasetRepository.findByDatasetName("Moorea Labeled Corals")).thenReturn(Optional.empty());
        when(projectRepository.findById(5L)).thenReturn(Optional.of(project()));
        when(projectMemberService.isMember(5L, "stranger")).thenReturn(false);

        assertThatThrownBy(() -> datasetService.createDataset(request(), 5L, "stranger"))
                .isInstanceOf(UnauthorizedException.class);
        verify(datasetRepository, never()).save(any(Dataset.class));
    }

    @Test
    void getAllDatasets_returnsSummaryWithVersionCountAndLatestTag() {
        DatasetVersion latest = new DatasetVersion();
        latest.setVersionTag("v1.2");

        when(projectRepository.findById(5L)).thenReturn(Optional.of(project()));
        when(projectMemberService.isMember(5L, "researcher_01")).thenReturn(true);
        when(datasetRepository.findAllByProjectIdOrderByCreatedAtDesc(5L)).thenReturn(List.of(dataset()));
        when(datasetVersionRepository.countByDatasetId(1L)).thenReturn(3L);
        when(datasetVersionRepository.findTopByDatasetIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(latest));

        List<DatasetResponse> result = datasetService.getAllDatasets(5L, "researcher_01");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getVersionCount()).isEqualTo(3L);
        assertThat(result.get(0).getLatestVersionTag()).isEqualTo("v1.2");
    }

    @Test
    void getAllDatasets_notAMember_throwsUnauthorized() {
        when(projectRepository.findById(5L)).thenReturn(Optional.of(project()));
        when(projectMemberService.isMember(5L, "stranger")).thenReturn(false);

        assertThatThrownBy(() -> datasetService.getAllDatasets(5L, "stranger"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void getDatasetById_success_includesAggregatedCounts() {
        DatasetVersion latest = new DatasetVersion();
        latest.setVersionTag("v1.2");
        latest.setImageCount(250);
        latest.setAnnotationCount(50000);

        when(datasetRepository.findById(1L)).thenReturn(Optional.of(dataset()));
        when(datasetVersionRepository.countByDatasetId(1L)).thenReturn(3L);
        when(datasetVersionRepository.findTopByDatasetIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(latest));

        DatasetResponse response = datasetService.getDatasetById(1L);

        assertThat(response.getImageCount()).isEqualTo(250);
        assertThat(response.getAnnotationCount()).isEqualTo(50000);
        assertThat(response.getProjectId()).isEqualTo(5L);
    }

    @Test
    void getDatasetById_notFound_throwsNotFound() {
        when(datasetRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> datasetService.getDatasetById(99L))
                .isInstanceOf(NotFoundException.class);
    }
}
