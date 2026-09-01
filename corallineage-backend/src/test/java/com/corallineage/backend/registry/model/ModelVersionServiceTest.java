package com.corallineage.backend.registry.model;

import com.corallineage.backend.datasets.Dataset;
import com.corallineage.backend.exception.InvalidRequestException;
import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.exception.ServiceUnavailableException;
import com.corallineage.backend.logging.AnnotationEventLoggerService;
import com.corallineage.backend.logging.EventType;
import com.corallineage.backend.training.TrainingJob;
import com.corallineage.backend.versioning.dataset.DatasetVersion;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelVersionServiceTest {

    @Mock private ModelVersionRepository modelVersionRepository;
    @Mock private MlflowIntegrationService mlflowIntegrationService;
    @Mock private AnnotationEventLoggerService annotationEventLoggerService;

    private final ModelVersionMapper modelVersionMapper = new ModelVersionMapper();

    private ModelVersionService newService() {
        return new ModelVersionService(modelVersionRepository, modelVersionMapper, mlflowIntegrationService, annotationEventLoggerService);
    }

    private ModelVersion model(Long id) {
        ModelVersion model = new ModelVersion();
        model.setId(id);
        model.setModelTag("model-v" + id);
        model.setStatus("ACTIVE");
        model.setClassMappings("{\"0\":\"ACROPORA\"}");
        return model;
    }

    @Test
    void getModelById_notFound_throwsNotFound() {
        when(modelVersionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().getModelById(99L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void getAllModels_noStatusFilter_scopesToDataset() {
        Pageable pageable = PageRequest.of(0, 20);
        when(modelVersionRepository.findByTrainedOnDatasetVersion_Dataset_Id(7L, pageable))
                .thenReturn(new PageImpl<>(List.of(model(1L))));

        Page<ModelVersionResponse> result = newService().getAllModels(7L, pageable, null);

        assertThat(result.getContent()).hasSize(1);
        verify(modelVersionRepository, never()).findByTrainedOnDatasetVersion_Dataset_IdAndStatus(any(), any(), any());
    }

    @Test
    void getAllModels_withStatusFilter_scopesToDatasetAndStatus() {
        Pageable pageable = PageRequest.of(0, 20);
        when(modelVersionRepository.findByTrainedOnDatasetVersion_Dataset_IdAndStatus(7L, "ARCHIVED", pageable))
                .thenReturn(new PageImpl<>(List.of()));

        newService().getAllModels(7L, pageable, "ARCHIVED");

        verify(modelVersionRepository).findByTrainedOnDatasetVersion_Dataset_IdAndStatus(7L, "ARCHIVED", pageable);
        verify(modelVersionRepository, never()).findByTrainedOnDatasetVersion_Dataset_Id(any(), any());
    }

    @Test
    void updateStatus_invalidStatus_throwsInvalidRequest() {
        assertThatThrownBy(() -> newService().updateStatus(1L, "BOGUS", "researcher_01"))
                .isInstanceOf(InvalidRequestException.class);
        verify(modelVersionRepository, never()).save(any());
    }

    @Test
    void updateStatus_success_persistsAndLogsEvent() {
        ModelVersion existing = model(1L);
        when(modelVersionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(modelVersionRepository.save(any(ModelVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        ModelVersionResponse response = newService().updateStatus(1L, "ARCHIVED", "researcher_01");

        assertThat(response.getStatus()).isEqualTo("ARCHIVED");
        verify(annotationEventLoggerService).log(eq(EventType.MODEL_STATUS_CHANGED), any(), eq(existing),
                eq("researcher_01"), any());
    }

    @Test
    void getMlflowDetails_noMlflowRunId_throwsNotFound() {
        ModelVersion model = model(1L);
        model.setMlflowRunId(null);
        when(modelVersionRepository.findById(1L)).thenReturn(Optional.of(model));

        assertThatThrownBy(() -> newService().getMlflowDetails(1L)).isInstanceOf(NotFoundException.class);
        verify(mlflowIntegrationService, never()).buildMlflowDetails(any());
    }

    @Test
    void getMlflowDetails_mlflowUnreachable_throwsServiceUnavailable() {
        ModelVersion model = model(1L);
        model.setMlflowRunId("run-123");
        when(modelVersionRepository.findById(1L)).thenReturn(Optional.of(model));
        when(mlflowIntegrationService.buildMlflowDetails(model)).thenReturn(null);

        assertThatThrownBy(() -> newService().getMlflowDetails(1L)).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void registerFromTrainingResult_counterStaleAndTagTaken_skipsToNextFreeTag() {
        // Counter says 1 but model-v1 already exists, so the candidate tag must skip forward
        Dataset dataset = new Dataset();
        dataset.setNextModelFallbackNumber(1);
        DatasetVersion datasetVersion = new DatasetVersion();
        datasetVersion.setDataset(dataset);
        TrainingJob job = new TrainingJob();
        job.setDatasetVersion(datasetVersion);
        job.setArchitecture("efficientnet_b0");
        job.setHyperparameters("{}");
        job.setInitiatedBy("researcher_01");

        when(modelVersionRepository.existsByModelTag("model-v1")).thenReturn(true);
        when(modelVersionRepository.existsByModelTag("model-v2")).thenReturn(false);
        when(modelVersionRepository.save(any(ModelVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        ModelVersion saved = newService().registerFromTrainingResult(
                job, "run-123", "/weights/path", null, "{}", "{}");

        assertThat(saved.getModelTag()).isEqualTo("model-v2");
        assertThat(dataset.getNextModelFallbackNumber()).isEqualTo(3);
    }

    @Test
    void getMlflowDetails_success_returnsDetails() {
        ModelVersion model = model(1L);
        model.setMlflowRunId("run-123");
        when(modelVersionRepository.findById(1L)).thenReturn(Optional.of(model));
        MlflowDetailsResponse details = new MlflowDetailsResponse();
        details.setModelId(1L);
        when(mlflowIntegrationService.buildMlflowDetails(model)).thenReturn(details);

        MlflowDetailsResponse result = newService().getMlflowDetails(1L);

        assertThat(result).isSameAs(details);
    }
}
