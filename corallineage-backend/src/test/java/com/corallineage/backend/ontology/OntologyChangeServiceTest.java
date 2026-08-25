package com.corallineage.backend.ontology;

import com.corallineage.backend.datasets.Dataset;
import com.corallineage.backend.datasets.DatasetService;
import com.corallineage.backend.exception.ConflictException;
import com.corallineage.backend.exception.ForbiddenException;
import com.corallineage.backend.exception.InvalidRequestException;
import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.logging.AnnotationEvent;
import com.corallineage.backend.logging.AnnotationEventLoggerService;
import com.corallineage.backend.logging.AnnotationEventRepository;
import com.corallineage.backend.logging.EventType;
import com.corallineage.backend.versioning.dataset.AnnotationPoint;
import com.corallineage.backend.versioning.dataset.AnnotationPointRepository;
import com.corallineage.backend.versioning.dataset.DatasetVersion;
import com.corallineage.backend.versioning.dataset.DatasetVersionRepository;
import com.corallineage.backend.versioning.dataset.DatasetVersionResponse;
import com.corallineage.backend.versioning.dataset.DatasetVersionService;
import com.corallineage.backend.versioning.dataset.Image;
import com.corallineage.backend.versioning.dataset.LabelDefinition;
import com.corallineage.backend.versioning.dataset.LabelDefinitionRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OntologyChangeServiceTest {

    @Mock private OntologyChangeRepository ontologyChangeRepository;
    @Mock private DirtyAnnotationRepository dirtyAnnotationRepository;
    @Mock private DatasetVersionRepository datasetVersionRepository;
    @Mock private DatasetVersionService datasetVersionService;
    @Mock private LabelDefinitionRepository labelDefinitionRepository;
    @Mock private AnnotationPointRepository annotationPointRepository;
    @Mock private AnnotationEventLoggerService annotationEventLoggerService;
    @Mock private AnnotationEventRepository annotationEventRepository;
    @Mock private DatasetService datasetService;

    private OntologyChangeService newService() {
        return new OntologyChangeService(ontologyChangeRepository, dirtyAnnotationRepository,
                datasetVersionRepository, datasetVersionService, labelDefinitionRepository,
                annotationPointRepository, new OntologyChangeMapper(annotationPointRepository),
                annotationEventLoggerService, annotationEventRepository, datasetService);
    }

    private Dataset dataset(Long id) {
        Dataset d = new Dataset();
        d.setId(id);
        return d;
    }

    private DatasetVersion version(Long id) {
        DatasetVersion v = new DatasetVersion();
        v.setId(id);
        v.setVersionTag("v1.0");
        v.setDataset(dataset(1L));
        return v;
    }

    private Image image(Long id, String filename) {
        Image img = new Image();
        img.setId(id);
        img.setFilename(filename);
        return img;
    }

    private AnnotationPoint point(Image image, int pointIndex) {
        AnnotationPoint p = new AnnotationPoint();
        p.setImage(image);
        p.setPointIndex(pointIndex);
        return p;
    }

    private NewLabelSpec spec(String code, String name, String category) {
        NewLabelSpec s = new NewLabelSpec();
        s.setCode(code);
        s.setName(name);
        s.setCategory(category);
        return s;
    }

    private RecordOntologyChangeRequest request(ChangeType type, String oldCode, Long versionId, List<NewLabelSpec> newLabels) {
        RecordOntologyChangeRequest req = new RecordOntologyChangeRequest();
        req.setChangeType(type);
        req.setOldLabelCode(oldCode);
        req.setNewLabels(newLabels);
        req.setBasedOnDatasetVersionId(versionId);
        req.setNewVersionTag("oc-" + versionId + "-test");
        req.setChangedBy("researcher_01");
        return req;
    }

    /** Stubs the createSnapshot branch-off call and returns the freshly "branched" version. */
    private DatasetVersion stubBranchedVersion(Long newVersionId) {
        DatasetVersionResponse response = new DatasetVersionResponse();
        response.setId(newVersionId);
        when(datasetVersionService.createSnapshotBranchedFrom(eq(1L), any(), any())).thenReturn(response);
        DatasetVersion newVersion = version(newVersionId);
        when(datasetVersionRepository.findById(newVersionId)).thenReturn(Optional.of(newVersion));
        return newVersion;
    }

    @Test
    void recordChange_basedOnVersionNotFound_throwsNotFound() {
        when(datasetVersionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().recordChange(request(ChangeType.LABEL_RENAMED, "OLD", 99L, List.of(spec("NEW", "New", "CORAL"))), "researcher_01"))
                .isInstanceOf(NotFoundException.class);
        verify(ontologyChangeRepository, never()).save(any());
    }

    @Test
    void recordChange_actingUserNotProjectMember_throwsForbidden() {
        when(datasetVersionRepository.findById(1L)).thenReturn(Optional.of(version(1L)));
        org.mockito.Mockito.doThrow(new ForbiddenException("You are not a member of the project that owns this dataset"))
                .when(datasetService).requireAccess(1L, "outsider");

        assertThatThrownBy(() -> newService().recordChange(
                request(ChangeType.LABEL_RENAMED, "OLD", 1L, List.of(spec("NEW", "New", "CORAL"))), "outsider"))
                .isInstanceOf(ForbiddenException.class);
        verify(ontologyChangeRepository, never()).save(any());
    }

    @Test
    void resolveDirtyAnnotation_actingUserNotProjectMember_throwsForbidden() {
        DirtyAnnotation d = dirty(1L, 5L, version(1L));
        when(dirtyAnnotationRepository.findById(1L)).thenReturn(Optional.of(d));
        org.mockito.Mockito.doThrow(new ForbiddenException("You are not a member of the project that owns this dataset"))
                .when(datasetService).requireAccess(1L, "outsider");

        ResolveDirtyAnnotationRequest req = new ResolveDirtyAnnotationRequest();
        req.setNewLabelCode("NEW");
        req.setResolvedBy("outsider");

        assertThatThrownBy(() -> newService().resolveDirtyAnnotation(1L, req, "outsider"))
                .isInstanceOf(ForbiddenException.class);
        verify(dirtyAnnotationRepository, never()).save(any());
    }

    @Test
    void recordChange_oldLabelCodeNotFound_throwsInvalidRequest() {
        when(datasetVersionRepository.findById(1L)).thenReturn(Optional.of(version(1L)));
        when(labelDefinitionRepository.existsByDatasetVersionIdAndLabelCode(1L, "OLD")).thenReturn(false);

        assertThatThrownBy(() -> newService().recordChange(request(ChangeType.LABEL_RENAMED, "OLD", 1L, List.of(spec("NEW", "New", "CORAL"))), "researcher_01"))
                .isInstanceOf(InvalidRequestException.class);
        verify(ontologyChangeRepository, never()).save(any());
        verify(annotationEventLoggerService, never()).log(any(), any(), anyString(), anyString());
    }

    @Test
    void recordChange_added_withOldCode_throwsInvalidRequest() {
        when(datasetVersionRepository.findById(1L)).thenReturn(Optional.of(version(1L)));

        assertThatThrownBy(() -> newService().recordChange(request(ChangeType.LABEL_ADDED, "SHOULD_NOT_BE_SET", 1L, List.of(spec("NEW", "New", "CORAL"))), "researcher_01"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void recordChange_deprecated_withNewLabels_throwsInvalidRequest() {
        when(datasetVersionRepository.findById(1L)).thenReturn(Optional.of(version(1L)));
        when(labelDefinitionRepository.existsByDatasetVersionIdAndLabelCode(1L, "OLD")).thenReturn(true);

        assertThatThrownBy(() -> newService().recordChange(request(ChangeType.LABEL_DEPRECATED, "OLD", 1L, List.of(spec("NEW", "New", "CORAL"))), "researcher_01"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void recordChange_split_withNoNewLabels_throwsInvalidRequest() {
        when(datasetVersionRepository.findById(1L)).thenReturn(Optional.of(version(1L)));
        when(labelDefinitionRepository.existsByDatasetVersionIdAndLabelCode(1L, "OLD")).thenReturn(true);

        assertThatThrownBy(() -> newService().recordChange(request(ChangeType.LABEL_SPLIT, "OLD", 1L, List.of()), "researcher_01"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void recordChange_added_createsNewVersionAndLabel_flagsNothing() {
        DatasetVersion basedOn = version(1L);
        when(datasetVersionRepository.findById(1L)).thenReturn(Optional.of(basedOn));
        DatasetVersion newVersion = stubBranchedVersion(2L);
        when(annotationEventRepository.save(any(AnnotationEvent.class))).thenAnswer(inv -> {
            AnnotationEvent e = inv.getArgument(0);
            e.setId(10L);
            return e;
        });
        when(ontologyChangeRepository.save(any(OntologyChange.class))).thenAnswer(inv -> {
            OntologyChange c = inv.getArgument(0);
            c.setId(5L);
            return c;
        });
        when(labelDefinitionRepository.existsByDatasetVersionIdAndLabelCode(2L, "MACRO")).thenReturn(false);
        when(labelDefinitionRepository.save(any(LabelDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

        OntologyChangeResponse response = newService().recordChange(
                request(ChangeType.LABEL_ADDED, null, 1L, List.of(spec("MACRO", "Macroalgae", "ALGAE"))), "researcher_01");

        assertThat(response.getDirtyAnnotationsFlagged()).isZero();
        assertThat(response.getAffectedDatasetVersionId()).isEqualTo(2L);
        verify(annotationPointRepository, never()).findByLabelCodeAndDatasetVersionMembership(any(), any());
        verify(dirtyAnnotationRepository, never()).saveAll(any());
        verify(annotationEventLoggerService, never()).log(eq(EventType.DIRTY_FLAGS_RAISED), any(), anyString(), anyString());
    }

    @Test
    void recordChange_renamed_updatesLabelInPlace_neverFlagsDirty() {
        DatasetVersion basedOn = version(1L);
        when(datasetVersionRepository.findById(1L)).thenReturn(Optional.of(basedOn));
        when(labelDefinitionRepository.existsByDatasetVersionIdAndLabelCode(1L, "OLD")).thenReturn(true);
        DatasetVersion newVersion = stubBranchedVersion(2L);
        when(annotationEventRepository.save(any(AnnotationEvent.class))).thenAnswer(inv -> {
            AnnotationEvent e = inv.getArgument(0);
            e.setId(10L);
            return e;
        });
        when(ontologyChangeRepository.save(any(OntologyChange.class))).thenAnswer(inv -> {
            OntologyChange c = inv.getArgument(0);
            c.setId(5L);
            return c;
        });
        LabelDefinition copiedOld = new LabelDefinition();
        copiedOld.setLabelCode("OLD");
        copiedOld.setLabelName("Old Name");
        when(labelDefinitionRepository.findByDatasetVersionIdAndLabelCode(2L, "OLD")).thenReturn(Optional.of(copiedOld));
        when(labelDefinitionRepository.save(any(LabelDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

        OntologyChangeResponse response = newService().recordChange(
                request(ChangeType.LABEL_RENAMED, "OLD", 1L, List.of(spec("NEW", "New Name", "CORAL"))), "researcher_01");

        assertThat(response.getDirtyAnnotationsFlagged()).isZero();
        assertThat(response.getChangeType()).isEqualTo(ChangeType.LABEL_RENAMED);
        assertThat(copiedOld.getLabelCode()).isEqualTo("NEW");
        assertThat(copiedOld.getLabelName()).isEqualTo("New Name");
        assertThat(copiedOld.getDeprecatedAt()).isNull();
        verify(annotationPointRepository, never()).findByLabelCodeAndDatasetVersionMembership(any(), any());
        verify(dirtyAnnotationRepository, never()).saveAll(any());
        verify(annotationEventLoggerService, never()).log(eq(EventType.DIRTY_FLAGS_RAISED), any(), anyString(), anyString());
    }

    @Test
    void recordChange_split_deprecatesOldCreatesNewAndFlagsMembershipPoints() {
        DatasetVersion basedOn = version(1L);
        when(datasetVersionRepository.findById(1L)).thenReturn(Optional.of(basedOn));
        when(labelDefinitionRepository.existsByDatasetVersionIdAndLabelCode(1L, "OLD")).thenReturn(true);
        DatasetVersion newVersion = stubBranchedVersion(2L);
        when(annotationEventRepository.save(any(AnnotationEvent.class))).thenAnswer(inv -> {
            AnnotationEvent e = inv.getArgument(0);
            e.setId(10L);
            return e;
        });
        when(ontologyChangeRepository.save(any(OntologyChange.class))).thenAnswer(inv -> {
            OntologyChange c = inv.getArgument(0);
            c.setId(5L);
            return c;
        });
        LabelDefinition copiedOld = new LabelDefinition();
        copiedOld.setLabelCode("OLD");
        when(labelDefinitionRepository.findByDatasetVersionIdAndLabelCode(2L, "OLD")).thenReturn(Optional.of(copiedOld));
        when(labelDefinitionRepository.existsByDatasetVersionIdAndLabelCode(eq(2L), anyString())).thenReturn(false);
        when(labelDefinitionRepository.save(any(LabelDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

        Image img = image(101L, "moorea_001.jpg");
        when(annotationPointRepository.findByLabelCodeAndDatasetVersionMembership(2L, "OLD"))
                .thenReturn(List.of(point(img, 0), point(img, 1), point(img, 2)));

        OntologyChangeResponse response = newService().recordChange(request(ChangeType.LABEL_SPLIT, "OLD", 1L,
                List.of(spec("OLD_A", "Old A", "CORAL"), spec("OLD_B", "Old B", "CORAL"))), "researcher_01");

        assertThat(response.getDirtyAnnotationsFlagged()).isEqualTo(3);
        assertThat(copiedOld.getDeprecatedAt()).isNotNull();
        verify(dirtyAnnotationRepository).saveAll(argThatSize(3));
        verify(labelDefinitionRepository, org.mockito.Mockito.times(2)).save(org.mockito.ArgumentMatchers.argThat(
                (LabelDefinition l) -> l.getLabelCode().equals("OLD_A") || l.getLabelCode().equals("OLD_B")));
        verify(annotationEventLoggerService).log(eq(EventType.DIRTY_FLAGS_RAISED), eq(newVersion), anyString(), anyString());
    }

    @Test
    void recordChange_deprecated_withValidReplacement_flagsMembershipPoints() {
        DatasetVersion basedOn = version(1L);
        when(datasetVersionRepository.findById(1L)).thenReturn(Optional.of(basedOn));
        when(labelDefinitionRepository.existsByDatasetVersionIdAndLabelCode(1L, "OLD")).thenReturn(true);
        LabelDefinition replacement = new LabelDefinition();
        replacement.setLabelCode("REPLACEMENT");
        when(labelDefinitionRepository.findByDatasetVersionIdAndLabelCode(1L, "REPLACEMENT")).thenReturn(Optional.of(replacement));
        DatasetVersion newVersion = stubBranchedVersion(2L);
        when(annotationEventRepository.save(any(AnnotationEvent.class))).thenAnswer(inv -> {
            AnnotationEvent e = inv.getArgument(0);
            e.setId(10L);
            return e;
        });
        when(ontologyChangeRepository.save(any(OntologyChange.class))).thenAnswer(inv -> {
            OntologyChange c = inv.getArgument(0);
            c.setId(5L);
            return c;
        });
        LabelDefinition copiedOld = new LabelDefinition();
        copiedOld.setLabelCode("OLD");
        when(labelDefinitionRepository.findByDatasetVersionIdAndLabelCode(2L, "OLD")).thenReturn(Optional.of(copiedOld));
        when(labelDefinitionRepository.save(any(LabelDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

        Image img = image(101L, "moorea_001.jpg");
        when(annotationPointRepository.findByLabelCodeAndDatasetVersionMembership(2L, "OLD"))
                .thenReturn(List.of(point(img, 0)));

        RecordOntologyChangeRequest req = request(ChangeType.LABEL_DEPRECATED, "OLD", 1L, List.of());
        req.setReplacementLabelCode("REPLACEMENT");

        OntologyChangeResponse response = newService().recordChange(req, "researcher_01");

        assertThat(response.getDirtyAnnotationsFlagged()).isEqualTo(1);
        assertThat(response.getReplacementLabelCode()).isEqualTo("REPLACEMENT");
        assertThat(copiedOld.getDeprecatedAt()).isNotNull();
    }

    @Test
    void recordChange_deprecated_replacementNotOnBasedOnVersion_throwsInvalidRequest() {
        DatasetVersion basedOn = version(1L);
        when(datasetVersionRepository.findById(1L)).thenReturn(Optional.of(basedOn));
        when(labelDefinitionRepository.existsByDatasetVersionIdAndLabelCode(1L, "OLD")).thenReturn(true);
        when(labelDefinitionRepository.findByDatasetVersionIdAndLabelCode(1L, "MISSING")).thenReturn(Optional.empty());

        RecordOntologyChangeRequest req = request(ChangeType.LABEL_DEPRECATED, "OLD", 1L, List.of());
        req.setReplacementLabelCode("MISSING");

        assertThatThrownBy(() -> newService().recordChange(req, "researcher_01"))
                .isInstanceOf(InvalidRequestException.class);
        verify(ontologyChangeRepository, never()).save(any());
    }

    private static List<DirtyAnnotation> argThatSize(int size) {
        return org.mockito.ArgumentMatchers.argThat(list -> list != null && list.size() == size);
    }

    private DirtyAnnotation dirty(Long id, Long ontologyChangeId, DatasetVersion version) {
        DirtyAnnotation d = new DirtyAnnotation();
        d.setId(id);
        Image img = image(101L, "moorea_001.jpg");
        img.setOriginDatasetVersion(version);
        d.setImage(img);
        d.setPointIndex(0);
        d.setCurrentLabelCode("OLD");
        OntologyChange change = new OntologyChange();
        change.setId(ontologyChangeId);
        change.setAffectedDatasetVersion(version);
        d.setOntologyChange(change);
        return d;
    }

    @Test
    void getChanges_populatesResolvedCount() {
        OntologyChange change = new OntologyChange();
        change.setId(5L);
        change.setChangeType(ChangeType.LABEL_RENAMED.name());
        change.setOldLabelCode("OLD");
        change.setChangedBy("researcher_01");

        Pageable pageable = PageRequest.of(0, 20);
        Page<OntologyChange> page = new PageImpl<>(List.of(change));
        when(ontologyChangeRepository.findByAffectedDatasetVersion_Dataset_IdOrderByChangedAtDesc(9L, pageable)).thenReturn(page);
        when(dirtyAnnotationRepository.countByOntologyChangeId(5L)).thenReturn(3L);
        when(dirtyAnnotationRepository.countByOntologyChangeIdAndResolvedAtIsNotNull(5L)).thenReturn(2L);

        Page<OntologyChangeResponse> result = newService().getChanges(9L, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getDirtyAnnotationsFlagged()).isEqualTo(3);
        assertThat(result.getContent().get(0).getResolvedCount()).isEqualTo(2);
    }

    @Test
    void getChanges_explicitDatasetVersionId_usesLegacyQuery() {
        OntologyChange change = new OntologyChange();
        change.setId(5L);
        change.setChangeType(ChangeType.LABEL_RENAMED.name());
        change.setOldLabelCode("OLD");
        change.setChangedBy("researcher_01");

        Pageable pageable = PageRequest.of(0, 20);
        Page<OntologyChange> page = new PageImpl<>(List.of(change));
        when(ontologyChangeRepository.findByAffectedDatasetVersionIdOrderByChangedAtDesc(1L, pageable)).thenReturn(page);
        when(dirtyAnnotationRepository.countByOntologyChangeId(5L)).thenReturn(0L);
        when(dirtyAnnotationRepository.countByOntologyChangeIdAndResolvedAtIsNotNull(5L)).thenReturn(0L);

        Page<OntologyChangeResponse> result = newService().getChanges(9L, 1L, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(ontologyChangeRepository, never()).findByAffectedDatasetVersion_Dataset_IdOrderByChangedAtDesc(any(), any());
    }

    @Test
    void resolveDirtyAnnotation_alreadyResolved_throwsConflict() {
        DirtyAnnotation d = dirty(1L, 5L, version(1L));
        d.setResolvedAt(java.time.Instant.now());
        when(dirtyAnnotationRepository.findById(1L)).thenReturn(Optional.of(d));

        ResolveDirtyAnnotationRequest req = new ResolveDirtyAnnotationRequest();
        req.setNewLabelCode("NEW");
        req.setResolvedBy("researcher_01");

        assertThatThrownBy(() -> newService().resolveDirtyAnnotation(1L, req, "researcher_01"))
                .isInstanceOf(ConflictException.class);
        verify(dirtyAnnotationRepository, never()).save(any());
    }

    @Test
    void resolveDirtyAnnotation_lastUnresolved_logsDirtyFlagsResolved() {
        DatasetVersion version = version(1L);
        DirtyAnnotation d = dirty(1L, 5L, version);
        when(dirtyAnnotationRepository.findById(1L)).thenReturn(Optional.of(d));
        when(dirtyAnnotationRepository.save(any(DirtyAnnotation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dirtyAnnotationRepository.countByOntologyChangeId(5L)).thenReturn(3L);
        when(dirtyAnnotationRepository.countByOntologyChangeIdAndResolvedAtIsNotNull(5L)).thenReturn(3L);

        ResolveDirtyAnnotationRequest req = new ResolveDirtyAnnotationRequest();
        req.setNewLabelCode("NEW");
        req.setResolvedBy("researcher_01");

        DirtyAnnotationResponse response = newService().resolveDirtyAnnotation(1L, req, "researcher_01");

        assertThat(response.isResolved()).isTrue();
        assertThat(response.getNewLabelCode()).isEqualTo("NEW");
        verify(annotationEventLoggerService).log(eq(EventType.DIRTY_FLAGS_RESOLVED), eq(version), anyString(), anyString());
    }

    @Test
    void toDirtyResponse_populatesRowAndColPositionFromMatchingAnnotationPoint() {
        DatasetVersion version = version(1L);
        DirtyAnnotation resolved = dirty(1L, 5L, version);
        resolved.setResolvedAt(java.time.Instant.now());
        resolved.setNewLabelCode("NEW");
        DirtyAnnotation unresolved = dirty(2L, 5L, version);

        Image sharedImage = image(101L, "moorea_001.jpg");
        AnnotationPoint matchingPoint = point(sharedImage, 0);
        matchingPoint.setRowPosition(120);
        matchingPoint.setColPosition(340);
        when(annotationPointRepository.findByImageIdAndPointIndex(101L, 0)).thenReturn(Optional.of(matchingPoint));

        when(dirtyAnnotationRepository.findByOntologyChangeId(eq(5L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(resolved, unresolved)));

        Page<DirtyAnnotationResponse> result = newService().getDirtyAnnotations(5L, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
        for (DirtyAnnotationResponse response : result.getContent()) {
            assertThat(response.getRowPosition()).isEqualTo(120);
            assertThat(response.getColPosition()).isEqualTo(340);
        }
    }

    @Test
    void resolveDirtyAnnotation_notLastUnresolved_doesNotLog() {
        DatasetVersion version = version(1L);
        DirtyAnnotation d = dirty(1L, 5L, version);
        when(dirtyAnnotationRepository.findById(1L)).thenReturn(Optional.of(d));
        when(dirtyAnnotationRepository.save(any(DirtyAnnotation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dirtyAnnotationRepository.countByOntologyChangeId(5L)).thenReturn(3L);
        when(dirtyAnnotationRepository.countByOntologyChangeIdAndResolvedAtIsNotNull(5L)).thenReturn(1L);

        ResolveDirtyAnnotationRequest req = new ResolveDirtyAnnotationRequest();
        req.setNewLabelCode("NEW");
        req.setResolvedBy("researcher_01");

        newService().resolveDirtyAnnotation(1L, req, "researcher_01");

        verify(annotationEventLoggerService, never()).log(any(), any(), anyString(), anyString());
    }
}
