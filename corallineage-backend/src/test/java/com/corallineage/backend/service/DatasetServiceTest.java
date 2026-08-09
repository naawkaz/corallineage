package com.corallineage.backend.service;

import com.corallineage.backend.dto.CreateDatasetRequest;
import com.corallineage.backend.dto.DatasetResponse;
import com.corallineage.backend.entity.Dataset;
import com.corallineage.backend.entity.DatasetVersion;
import com.corallineage.backend.exception.ConflictException;
import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.mapper.DatasetMapper;
import com.corallineage.backend.repository.DatasetRepository;
import com.corallineage.backend.repository.DatasetVersionRepository;
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
        return dataset;
    }

    @Test
    void createDataset_success_savesDataset() {
        when(datasetRepository.findByDatasetName("Moorea Labeled Corals")).thenReturn(Optional.empty());
        when(datasetRepository.save(any(Dataset.class))).thenAnswer(inv -> inv.getArgument(0));

        DatasetResponse response = datasetService.createDataset(request());

        assertThat(response.getDatasetName()).isEqualTo("Moorea Labeled Corals");
        assertThat(response.getCreatedBy()).isEqualTo("researcher_01");
        verify(datasetRepository).save(any(Dataset.class));
    }

    @Test
    void createDataset_duplicateName_throwsConflict() {
        when(datasetRepository.findByDatasetName("Moorea Labeled Corals"))
                .thenReturn(Optional.of(dataset()));

        assertThatThrownBy(() -> datasetService.createDataset(request()))
                .isInstanceOf(ConflictException.class);
        verify(datasetRepository, never()).save(any(Dataset.class));
    }

    @Test
    void getAllDatasets_returnsSummaryWithVersionCountAndLatestTag() {
        DatasetVersion latest = new DatasetVersion();
        latest.setVersionTag("v1.2");

        when(datasetRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(dataset()));
        when(datasetVersionRepository.countByDatasetId(1L)).thenReturn(3L);
        when(datasetVersionRepository.findTopByDatasetIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(latest));

        List<DatasetResponse> result = datasetService.getAllDatasets();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getVersionCount()).isEqualTo(3L);
        assertThat(result.get(0).getLatestVersionTag()).isEqualTo("v1.2");
    }

    @Test
    void getAllDatasets_noVersionsYet_latestTagIsNull() {
        when(datasetRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(dataset()));
        when(datasetVersionRepository.countByDatasetId(1L)).thenReturn(0L);
        when(datasetVersionRepository.findTopByDatasetIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());

        List<DatasetResponse> result = datasetService.getAllDatasets();

        assertThat(result.get(0).getVersionCount()).isZero();
        assertThat(result.get(0).getLatestVersionTag()).isNull();
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
    }

    @Test
    void getDatasetById_notFound_throwsNotFound() {
        when(datasetRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> datasetService.getDatasetById(99L))
                .isInstanceOf(NotFoundException.class);
    }
}
