package com.corallineage.backend.training;

import com.corallineage.backend.datasets.Dataset;
import com.corallineage.backend.datasets.DatasetRepository;
import com.corallineage.backend.exception.InvalidRequestException;
import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.logging.AnnotationEventLoggerService;
import com.corallineage.backend.versioning.dataset.DatasetVersion;
import com.corallineage.backend.versioning.dataset.DatasetVersionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingJobServiceTest {

    @Mock private DatasetRepository datasetRepository;
    @Mock private DatasetVersionRepository datasetVersionRepository;
    @Mock private TrainingJobRepository trainingJobRepository;
    @Mock private ArchitectureRegistry architectureRegistry;
    @Mock private TrainingJobExecutor trainingJobExecutor;
    @Mock private AnnotationEventLoggerService annotationEventLoggerService;

    private final TrainingJobMapper trainingJobMapper = new TrainingJobMapper();

    private TrainingJobService newService() {
        return new TrainingJobService(datasetRepository, datasetVersionRepository, trainingJobRepository,
                trainingJobMapper, architectureRegistry, trainingJobExecutor, annotationEventLoggerService);
    }

    private Dataset dataset(Long id) {
        Dataset d = new Dataset();
        d.setId(id);
        return d;
    }

    private DatasetVersion version(Long id, Dataset dataset, String tag) {
        DatasetVersion v = new DatasetVersion();
        v.setId(id);
        v.setDataset(dataset);
        v.setVersionTag(tag);
        return v;
    }

    private InitiateTrainingRequest request(Long datasetId, Long versionId, String architecture) {
        InitiateTrainingRequest req = new InitiateTrainingRequest();
        req.setDatasetId(datasetId);
        req.setDatasetVersionId(versionId);
        req.setArchitecture(architecture);
        req.setInitiatedBy("researcher_01");
        return req;
    }

    @Test
    void initiateTrainingJob_datasetNotFound_throwsNotFound() {
        when(datasetRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().initiateTrainingJob(request(99L, null, null)))
                .isInstanceOf(NotFoundException.class);
        verify(trainingJobExecutor, never()).executeTrainingAsync(any());
    }

    @Test
    void initiateTrainingJob_noDatasetVersions_throwsInvalidRequest() {
        when(datasetRepository.findById(1L)).thenReturn(Optional.of(dataset(1L)));
        when(datasetVersionRepository.findTopByDatasetIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().initiateTrainingJob(request(1L, null, null)))
                .isInstanceOf(InvalidRequestException.class);
        verify(trainingJobExecutor, never()).executeTrainingAsync(any());
    }

    @Test
    void initiateTrainingJob_explicitVersionFromWrongDataset_throwsInvalidRequest() {
        Dataset otherDataset = dataset(2L);
        DatasetVersion wrongVersion = version(5L, otherDataset, "v1.0");
        when(datasetRepository.findById(1L)).thenReturn(Optional.of(dataset(1L)));
        when(datasetVersionRepository.findById(5L)).thenReturn(Optional.of(wrongVersion));

        assertThatThrownBy(() -> newService().initiateTrainingJob(request(1L, 5L, null)))
                .isInstanceOf(InvalidRequestException.class);
        verify(trainingJobExecutor, never()).executeTrainingAsync(any());
    }

    @Test
    void initiateTrainingJob_unsupportedArchitecture_throwsInvalidRequest() {
        Dataset dataset = dataset(1L);
        when(datasetRepository.findById(1L)).thenReturn(Optional.of(dataset));
        when(datasetVersionRepository.findTopByDatasetIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(version(3L, dataset, "v1.0")));
        when(architectureRegistry.isSupported("bogus_arch")).thenReturn(false);

        assertThatThrownBy(() -> newService().initiateTrainingJob(request(1L, null, "bogus_arch")))
                .isInstanceOf(InvalidRequestException.class);
        verify(trainingJobExecutor, never()).executeTrainingAsync(any());
    }

    @Test
    void initiateTrainingJob_success_defaultsArchitectureAndResolvesLatestVersionAndDispatches() {
        Dataset dataset = dataset(1L);
        DatasetVersion latest = version(3L, dataset, "v1.1");
        when(datasetRepository.findById(1L)).thenReturn(Optional.of(dataset));
        when(datasetVersionRepository.findTopByDatasetIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(latest));
        when(trainingJobRepository.save(any(TrainingJob.class))).thenAnswer(inv -> {
            TrainingJob job = inv.getArgument(0);
            job.setId(7L);
            return job;
        });

        TrainingJobResponse response = newService().initiateTrainingJob(request(1L, null, null));

        assertThat(response.getJobId()).isEqualTo(7L);
        assertThat(response.getStatus()).isEqualTo("QUEUED");
        assertThat(response.getArchitecture()).isEqualTo("efficientnet_b0");
        assertThat(response.getDatasetVersionTag()).isEqualTo("v1.1");
        verify(trainingJobExecutor).executeTrainingAsync(7L);
    }

    @Test
    void getTrainingJobById_notFound_throwsNotFound() {
        when(trainingJobRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().getTrainingJobById(99L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getTrainingJobs_noStatusFilter_scopesToDataset() {
        Pageable pageable = PageRequest.of(0, 20);
        when(trainingJobRepository.findByDatasetVersion_Dataset_IdOrderByInitiatedAtDesc(1L, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        Page<TrainingJobResponse> result = newService().getTrainingJobs(1L, pageable, null);

        assertThat(result.getContent()).isEmpty();
        verify(trainingJobRepository, never()).findByDatasetVersion_Dataset_IdAndStatusOrderByInitiatedAtDesc(any(), any(), any());
    }

    @Test
    void getTrainingJobs_withStatusFilter_scopesToDatasetAndStatus() {
        Pageable pageable = PageRequest.of(0, 20);
        when(trainingJobRepository.findByDatasetVersion_Dataset_IdAndStatusOrderByInitiatedAtDesc(1L, "COMPLETED", pageable))
                .thenReturn(new PageImpl<>(List.of()));

        newService().getTrainingJobs(1L, pageable, "COMPLETED");

        verify(trainingJobRepository).findByDatasetVersion_Dataset_IdAndStatusOrderByInitiatedAtDesc(1L, "COMPLETED", pageable);
    }
}
