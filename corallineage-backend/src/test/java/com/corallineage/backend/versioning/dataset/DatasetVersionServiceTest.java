package com.corallineage.backend.versioning.dataset;

import com.corallineage.backend.annotation.AnnotationJobRepository;
import com.corallineage.backend.datasets.Dataset;
import com.corallineage.backend.datasets.DatasetRepository;
import com.corallineage.backend.datasets.importservice.ImportJobRepository;
import com.corallineage.backend.exception.ConflictException;
import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.logging.AnnotationEventLoggerService;
import com.corallineage.backend.logging.EventType;
import com.corallineage.backend.ontology.DirtyAnnotationRepository;
import com.corallineage.backend.pointannotation.PointAnnotationJob;
import com.corallineage.backend.pointannotation.PointAnnotationJobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatasetVersionServiceTest {

    @Mock
    private DatasetRepository datasetRepository;
    @Mock
    private DatasetVersionRepository datasetVersionRepository;
    @Mock
    private LabelDefinitionRepository labelDefinitionRepository;
    @Mock
    private DatasetVersionImageRepository datasetVersionImageRepository;
    @Mock
    private AnnotationPointRepository annotationPointRepository;
    @Mock
    private DirtyAnnotationRepository dirtyAnnotationRepository;
    @Mock
    private DvcIntegrationService dvcIntegrationService;
    @Mock
    private AnnotationEventLoggerService annotationEventLoggerService;
    @Mock
    private ImportJobRepository importJobRepository;
    @Mock
    private PointAnnotationJobRepository pointAnnotationJobRepository;
    @Mock
    private AnnotationJobRepository annotationJobRepository;

    @InjectMocks
    private DatasetVersionService datasetVersionService;

    @Spy
    private DatasetVersionMapper datasetVersionMapper;

    private Dataset dataset() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setDatasetName("Moorea Labeled Corals");
        return dataset;
    }

    private CreateVersionRequest request() {
        CreateVersionRequest request = new CreateVersionRequest();
        request.setVersionTag("v1.0");
        request.setTriggerReason(TriggerReason.MANUAL_SNAPSHOT);
        request.setCreatedBy("researcher_01");
        request.setNotes("Initial snapshot");
        return request;
    }

    private LabelDefinition label(String code) {
        LabelDefinition label = new LabelDefinition();
        label.setId(1L);
        label.setLabelCode(code);
        label.setLabelName(code.toLowerCase());
        label.setLabelCategory("CORAL");
        return label;
    }

    @Test
    void createSnapshot_success_savesVersionAndLogsEvent() {
        when(datasetRepository.findById(1L)).thenReturn(Optional.of(dataset()));
        when(datasetVersionRepository.findByDatasetIdAndVersionTag(1L, "v1.0")).thenReturn(Optional.empty());
        when(datasetVersionRepository.findTopByDatasetIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.empty());
        when(annotationPointRepository.countByImageOriginDatasetVersionDatasetId(1L)).thenReturn(0L);
        when(dvcIntegrationService.commitCurrentState("v1.0")).thenReturn("a3f9c21d");
        when(datasetVersionRepository.save(any(DatasetVersion.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        DatasetVersionResponse response = datasetVersionService.createSnapshot(1L, request());

        assertThat(response.getVersionTag()).isEqualTo("v1.0");
        assertThat(response.getImageCount()).isZero();
        assertThat(response.getAnnotationCount()).isZero();
        assertThat(response.getDvcCommitHash()).isEqualTo("a3f9c21d");
        assertThat(response.getLabelDefinitions()).isEmpty();
        assertThat(response.isComplete()).isTrue();
        verify(datasetVersionRepository, org.mockito.Mockito.times(2)).save(any(DatasetVersion.class));
        verify(annotationEventLoggerService).log(eq(EventType.DATASET_VERSION_CREATED),
                any(DatasetVersion.class), eq("researcher_01"), anyString());
    }

    private Image imageFixture(Long id) {
        Image image = new Image();
        image.setId(id);
        image.setFilename("img" + id + ".jpg");
        return image;
    }

    private DatasetVersionImage membershipRow(DatasetVersion version, Image image) {
        DatasetVersionImage row = new DatasetVersionImage();
        row.setDatasetVersion(version);
        row.setImage(image);
        return row;
    }

    @Test
    void createSnapshot_success_copiesLabelsFromPreviousVersion() {
        DatasetVersion previous = new DatasetVersion();
        previous.setId(1L);
        previous.setVersionTag("v0.9");
        previous.setCreatedAt(Instant.now().minusSeconds(60));

        when(datasetRepository.findById(1L)).thenReturn(Optional.of(dataset()));
        when(datasetVersionRepository.findByDatasetIdAndVersionTag(1L, "v1.0")).thenReturn(Optional.empty());
        when(datasetVersionRepository.findTopByDatasetIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(previous));
        when(annotationPointRepository.countByImageOriginDatasetVersionDatasetId(1L)).thenReturn(12L);
        when(dvcIntegrationService.commitCurrentState("v1.0")).thenReturn("a3f9c21d");
        when(datasetVersionRepository.save(any(DatasetVersion.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(labelDefinitionRepository.findByDatasetVersionId(1L))
                .thenReturn(List.of(label("ACROPORA"), label("SAND")));
        when(labelDefinitionRepository.save(any(LabelDefinition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(datasetVersionImageRepository.findByDatasetVersionId(1L))
                .thenReturn(List.of(membershipRow(previous, imageFixture(101L)), membershipRow(previous, imageFixture(102L))));
        when(datasetVersionImageRepository.save(any(DatasetVersionImage.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        DatasetVersionResponse response = datasetVersionService.createSnapshot(1L, request());

        assertThat(response.getLabelDefinitions()).hasSize(2);
        assertThat(response.getLabelDefinitions().get(0).getLabelCode()).isEqualTo("ACROPORA");
        assertThat(response.getImageCount()).isEqualTo(2);
        assertThat(response.getAnnotationCount()).isEqualTo(12);
        verify(labelDefinitionRepository, org.mockito.Mockito.times(2))
                .save(any(LabelDefinition.class));
    }

    @Test
    void createSnapshotBranchedFrom_usesExplicitBranchPoint_notDatasetsActualLatest() {
        DatasetVersion olderBranchPoint = new DatasetVersion();
        olderBranchPoint.setId(1L);
        olderBranchPoint.setVersionTag("v1.0-old");

        when(datasetRepository.findById(1L)).thenReturn(Optional.of(dataset()));
        when(datasetVersionRepository.findByDatasetIdAndVersionTag(1L, "v1.0")).thenReturn(Optional.empty());
        when(annotationPointRepository.countByImageOriginDatasetVersionDatasetId(1L)).thenReturn(0L);
        when(dvcIntegrationService.commitCurrentState("v1.0")).thenReturn("a3f9c21d");
        when(datasetVersionRepository.save(any(DatasetVersion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(labelDefinitionRepository.findByDatasetVersionId(1L)).thenReturn(List.of(label("OLD_BRANCH_LABEL")));
        when(labelDefinitionRepository.save(any(LabelDefinition.class))).thenAnswer(inv -> inv.getArgument(0));
        when(datasetVersionImageRepository.findByDatasetVersionId(1L)).thenReturn(List.of());

        DatasetVersionResponse response =
                datasetVersionService.createSnapshotBranchedFrom(1L, request(), olderBranchPoint);

        // Must never consult "the dataset's actual latest version" — only the explicit branch point.
        verify(datasetVersionRepository, never()).findTopByDatasetIdOrderByCreatedAtDesc(any());
        assertThat(response.getLabelDefinitions()).extracting("labelCode").containsExactly("OLD_BRANCH_LABEL");
    }

    @Test
    void createSnapshot_duplicateTag_throwsConflict() {
        DatasetVersion existing = new DatasetVersion();
        existing.setId(1L);
        existing.setVersionTag("v1.0");

        when(datasetRepository.findById(1L)).thenReturn(Optional.of(dataset()));
        when(datasetVersionRepository.findByDatasetIdAndVersionTag(1L, "v1.0")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> datasetVersionService.createSnapshot(1L, request()))
                .isInstanceOf(ConflictException.class);
        verify(dvcIntegrationService, never()).commitCurrentState(anyString());
        verify(datasetVersionRepository, never()).save(any(DatasetVersion.class));
    }

    @Test
    void createSnapshot_datasetNotFound_throwsNotFound() {
        when(datasetRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> datasetVersionService.createSnapshot(99L, request()))
                .isInstanceOf(NotFoundException.class);
        verify(dvcIntegrationService, never()).commitCurrentState(anyString());
        verify(datasetVersionRepository, never()).save(any(DatasetVersion.class));
    }

    @Test
    void createSnapshot_dvcFailure_rollsBackWithoutPersisting() {
        when(datasetRepository.findById(1L)).thenReturn(Optional.of(dataset()));
        when(datasetVersionRepository.findByDatasetIdAndVersionTag(1L, "v1.0")).thenReturn(Optional.empty());
        when(datasetVersionRepository.findTopByDatasetIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.empty());
        when(dvcIntegrationService.commitCurrentState("v1.0"))
                .thenThrow(new DvcIntegrationException("dvc command failed"));

        assertThatThrownBy(() -> datasetVersionService.createSnapshot(1L, request()))
                .isInstanceOf(DvcIntegrationException.class);
        verify(datasetVersionRepository, never()).save(any(DatasetVersion.class));
        verify(annotationEventLoggerService, never())
                .log(any(), any(), any(), any());
    }

    @Test
    void getVersionById_success_includesLabels() {
        DatasetVersion version = new DatasetVersion();
        version.setId(3L);
        version.setDataset(dataset());
        version.setVersionTag("v1.2");
        version.setCreatedAt(Instant.now());
        version.setCreatedBy("researcher_01");
        version.setTriggerReason("ANNOTATIONS_MODIFIED");
        version.setImageCount(250);
        version.setAnnotationCount(50000);
        version.setDvcCommitHash("a3f9c21d");

        when(datasetVersionRepository.findById(3L)).thenReturn(Optional.of(version));
        when(labelDefinitionRepository.findByDatasetVersionId(3L))
                .thenReturn(List.of(label("ACROPORA")));

        DatasetVersionResponse response = datasetVersionService.getVersionById(1L, 3L);

        assertThat(response.getId()).isEqualTo(3L);
        assertThat(response.getVersionTag()).isEqualTo("v1.2");
        assertThat(response.getLabelDefinitions()).hasSize(1);
    }

    @Test
    void getVersionById_notFound_throwsNotFound() {
        when(datasetVersionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> datasetVersionService.getVersionById(1L, 99L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getVersionById_belongsToDifferentDataset_throwsNotFound() {
        Dataset otherDataset = new Dataset();
        otherDataset.setId(2L);

        DatasetVersion version = new DatasetVersion();
        version.setId(3L);
        version.setDataset(otherDataset);
        version.setVersionTag("v1.2");

        when(datasetVersionRepository.findById(3L)).thenReturn(Optional.of(version));

        assertThatThrownBy(() -> datasetVersionService.getVersionById(1L, 3L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getVersionHistory_returnsMappedPage() {
        DatasetVersion version = new DatasetVersion();
        version.setId(1L);
        version.setVersionTag("v1.0");

        when(datasetRepository.existsById(1L)).thenReturn(true);
        when(datasetVersionRepository.findAllByDatasetIdAndIsStagingNotTrueOrderByCreatedAtDesc(eq(1L)))
                .thenReturn(List.of(version));

        Page<DatasetVersionResponse> result =
                datasetVersionService.getVersionHistory(1L, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getVersionTag()).isEqualTo("v1.0");
    }

    @Test
    void getVersionHistory_usesStagingFilteredQuery_excludingStagingVersions() {
        DatasetVersion nonStaging = new DatasetVersion();
        nonStaging.setId(1L);
        nonStaging.setVersionTag("v1.0");

        // The staging version is simply never returned by the filtered repository query the
        // service now calls - the exclusion happens in the repository's JPQL, not in this
        // service method, so the test's job is to confirm the service calls the filtered query
        // (not the unfiltered one) and passes its result straight through.
        when(datasetRepository.existsById(1L)).thenReturn(true);
        when(datasetVersionRepository.findAllByDatasetIdAndIsStagingNotTrueOrderByCreatedAtDesc(eq(1L)))
                .thenReturn(List.of(nonStaging));

        Page<DatasetVersionResponse> result =
                datasetVersionService.getVersionHistory(1L, PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(DatasetVersionResponse::getVersionTag).containsExactly("v1.0");
        verify(datasetVersionRepository, never()).findAllByDatasetIdOrderByCreatedAtDesc(eq(1L), any());
    }

    @Test
    void getVersionHistory_excludesUnresolvedVersions() {
        DatasetVersion resolvedVersion = new DatasetVersion();
        resolvedVersion.setId(1L);
        resolvedVersion.setVersionTag("v1.0");
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        resolvedVersion.setDataset(dataset);

        DatasetVersion unresolvedVersion = new DatasetVersion();
        unresolvedVersion.setId(2L);
        unresolvedVersion.setVersionTag("blank1");
        unresolvedVersion.setDataset(dataset);

        when(datasetRepository.existsById(1L)).thenReturn(true);
        when(datasetVersionRepository.findAllByDatasetIdAndIsStagingNotTrueOrderByCreatedAtDesc(eq(1L)))
                .thenReturn(List.of(resolvedVersion, unresolvedVersion));

        PointAnnotationJob pointJob = new PointAnnotationJob();
        pointJob.setId(9L);
        pointJob.setStatus("SEED_LABELING");
        when(pointAnnotationJobRepository.findFirstByDatasetVersion_IdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());
        when(pointAnnotationJobRepository.findFirstByDatasetVersion_IdOrderByCreatedAtDesc(2L))
                .thenReturn(Optional.of(pointJob));

        Page<DatasetVersionResponse> result =
                datasetVersionService.getVersionHistory(1L, PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(DatasetVersionResponse::getVersionTag).containsExactly("v1.0");
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void getVersionHistory_datasetNotFound_throwsNotFound() {
        when(datasetRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> datasetVersionService.getVersionHistory(99L, PageRequest.of(0, 20)))
                .isInstanceOf(NotFoundException.class);
    }

    private CreateLabelDefinitionRequest labelRequest(String code) {
        CreateLabelDefinitionRequest request = new CreateLabelDefinitionRequest();
        request.setLabelCode(code);
        request.setLabelName(code.toLowerCase());
        request.setLabelCategory("CORAL");
        return request;
    }

    @Test
    void addLabelDefinition_success_persistsAndReturnsLabel() {
        DatasetVersion version = new DatasetVersion();
        version.setId(3L);
        version.setDataset(dataset());

        when(datasetVersionRepository.findById(3L)).thenReturn(Optional.of(version));
        when(labelDefinitionRepository.existsByDatasetVersionIdAndLabelCode(3L, "MACRO")).thenReturn(false);
        when(labelDefinitionRepository.save(any(LabelDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = datasetVersionService.addLabelDefinition(1L, 3L, labelRequest("MACRO"));

        assertThat(response.getLabelCode()).isEqualTo("MACRO");
        assertThat(response.getLabelName()).isEqualTo("macro");
        assertThat(response.isDeprecated()).isFalse();
        verify(labelDefinitionRepository).save(any(LabelDefinition.class));
    }

    @Test
    void addLabelDefinition_versionNotFound_throwsNotFound() {
        when(datasetVersionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> datasetVersionService.addLabelDefinition(1L, 99L, labelRequest("MACRO")))
                .isInstanceOf(NotFoundException.class);
        verify(labelDefinitionRepository, never()).save(any(LabelDefinition.class));
    }

    @Test
    void addLabelDefinition_belongsToDifferentDataset_throwsNotFound() {
        Dataset otherDataset = new Dataset();
        otherDataset.setId(2L);
        DatasetVersion version = new DatasetVersion();
        version.setId(3L);
        version.setDataset(otherDataset);

        when(datasetVersionRepository.findById(3L)).thenReturn(Optional.of(version));

        assertThatThrownBy(() -> datasetVersionService.addLabelDefinition(1L, 3L, labelRequest("MACRO")))
                .isInstanceOf(NotFoundException.class);
        verify(labelDefinitionRepository, never()).save(any(LabelDefinition.class));
    }

    @Test
    void addLabelDefinition_duplicateCode_throwsConflict() {
        DatasetVersion version = new DatasetVersion();
        version.setId(3L);
        version.setDataset(dataset());

        when(datasetVersionRepository.findById(3L)).thenReturn(Optional.of(version));
        when(labelDefinitionRepository.existsByDatasetVersionIdAndLabelCode(3L, "MACRO")).thenReturn(true);

        assertThatThrownBy(() -> datasetVersionService.addLabelDefinition(1L, 3L, labelRequest("MACRO")))
                .isInstanceOf(ConflictException.class);
        verify(labelDefinitionRepository, never()).save(any(LabelDefinition.class));
    }
}
