package com.corallineage.backend.ontology;

import com.corallineage.backend.exception.ConflictException;
import com.corallineage.backend.exception.InvalidRequestException;
import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.logging.AnnotationEvent;
import com.corallineage.backend.logging.AnnotationEventLoggerService;
import com.corallineage.backend.logging.EventType;
import com.corallineage.backend.versioning.dataset.AnnotationPoint;
import com.corallineage.backend.versioning.dataset.AnnotationPointRepository;
import com.corallineage.backend.versioning.dataset.DatasetVersion;
import com.corallineage.backend.versioning.dataset.DatasetVersionRepository;
import com.corallineage.backend.versioning.dataset.Image;
import com.corallineage.backend.versioning.dataset.LabelDefinitionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    @Mock private LabelDefinitionRepository labelDefinitionRepository;
    @Mock private AnnotationPointRepository annotationPointRepository;
    @Mock private AnnotationEventLoggerService annotationEventLoggerService;

    private final OntologyChangeMapper ontologyChangeMapper = new OntologyChangeMapper();

    private OntologyChangeService newService() {
        return new OntologyChangeService(ontologyChangeRepository, dirtyAnnotationRepository,
                datasetVersionRepository, labelDefinitionRepository, annotationPointRepository,
                ontologyChangeMapper, annotationEventLoggerService);
    }

    private DatasetVersion version(Long id) {
        DatasetVersion v = new DatasetVersion();
        v.setId(id);
        v.setVersionTag("v1.0");
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

    private AnnotationEvent event(Long id) {
        AnnotationEvent e = new AnnotationEvent();
        e.setId(id);
        return e;
    }

    private RecordOntologyChangeRequest request(ChangeType type, String oldCode, Long versionId) {
        RecordOntologyChangeRequest req = new RecordOntologyChangeRequest();
        req.setChangeType(type);
        req.setOldLabelCode(oldCode);
        req.setNewLabelCodes(oldCode != null ? List.of(oldCode + "_A", oldCode + "_B") : List.of());
        req.setAffectedDatasetVersionId(versionId);
        req.setChangedBy("researcher_01");
        return req;
    }

    @Test
    void recordChange_datasetVersionNotFound_throwsNotFound() {
        when(datasetVersionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().recordChange(request(ChangeType.LABEL_RENAMED, "OLD", 99L)))
                .isInstanceOf(NotFoundException.class);
        verify(ontologyChangeRepository, never()).save(any());
    }

    @Test
    void recordChange_oldLabelCodeNotFound_throwsInvalidRequest() {
        when(datasetVersionRepository.findById(1L)).thenReturn(Optional.of(version(1L)));
        when(labelDefinitionRepository.existsByDatasetVersionIdAndLabelCode(1L, "OLD")).thenReturn(false);

        assertThatThrownBy(() -> newService().recordChange(request(ChangeType.LABEL_RENAMED, "OLD", 1L)))
                .isInstanceOf(InvalidRequestException.class);
        verify(ontologyChangeRepository, never()).save(any());
        verify(annotationEventLoggerService, never()).log(any(), any(), anyString(), anyString());
    }

    @Test
    void recordChange_labelAdded_noOldCode_flagsNothing() {
        DatasetVersion version = version(1L);
        when(datasetVersionRepository.findById(1L)).thenReturn(Optional.of(version));
        when(annotationEventLoggerService.log(eq(EventType.ONTOLOGY_CHANGE_DETECTED), eq(version), anyString(), anyString()))
                .thenReturn(event(10L));
        when(ontologyChangeRepository.save(any(OntologyChange.class))).thenAnswer(inv -> {
            OntologyChange c = inv.getArgument(0);
            c.setId(5L);
            return c;
        });

        OntologyChangeResponse response = newService().recordChange(request(ChangeType.LABEL_ADDED, null, 1L));

        assertThat(response.getDirtyAnnotationsFlagged()).isZero();
        verify(annotationPointRepository, never()).findByImageDatasetVersionIdAndLabelDefinitionLabelCode(any(), any());
        verify(dirtyAnnotationRepository, never()).saveAll(any());
        verify(annotationEventLoggerService, never()).log(eq(EventType.DIRTY_FLAGS_RAISED), any(), anyString(), anyString());
    }

    @Test
    void recordChange_labelRenamed_flagsMatchingPointsAndLogsBothEvents() {
        DatasetVersion version = version(1L);
        Image img = image(101L, "moorea_001.jpg");
        when(datasetVersionRepository.findById(1L)).thenReturn(Optional.of(version));
        when(labelDefinitionRepository.existsByDatasetVersionIdAndLabelCode(1L, "OLD")).thenReturn(true);
        when(annotationEventLoggerService.log(eq(EventType.ONTOLOGY_CHANGE_DETECTED), eq(version), anyString(), anyString()))
                .thenReturn(event(10L));
        when(ontologyChangeRepository.save(any(OntologyChange.class))).thenAnswer(inv -> {
            OntologyChange c = inv.getArgument(0);
            c.setId(5L);
            return c;
        });
        when(annotationPointRepository.findByImageDatasetVersionIdAndLabelDefinitionLabelCode(1L, "OLD"))
                .thenReturn(List.of(point(img, 0), point(img, 1), point(img, 2)));

        OntologyChangeResponse response = newService().recordChange(request(ChangeType.LABEL_RENAMED, "OLD", 1L));

        assertThat(response.getDirtyAnnotationsFlagged()).isEqualTo(3);
        assertThat(response.getChangeType()).isEqualTo(ChangeType.LABEL_RENAMED);
        verify(dirtyAnnotationRepository).saveAll(argThatSize(3));
        verify(annotationEventLoggerService).log(eq(EventType.ONTOLOGY_CHANGE_DETECTED), eq(version), anyString(), anyString());
        verify(annotationEventLoggerService).log(eq(EventType.DIRTY_FLAGS_RAISED), eq(version), anyString(), anyString());
    }

    private static List<DirtyAnnotation> argThatSize(int size) {
        return org.mockito.ArgumentMatchers.argThat(list -> list != null && list.size() == size);
    }

    private DirtyAnnotation dirty(Long id, Long ontologyChangeId, DatasetVersion version) {
        DirtyAnnotation d = new DirtyAnnotation();
        d.setId(id);
        d.setImage(image(101L, "moorea_001.jpg"));
        d.setPointIndex(0);
        d.setCurrentLabelCode("OLD");
        OntologyChange change = new OntologyChange();
        change.setId(ontologyChangeId);
        change.setAffectedDatasetVersion(version);
        d.setOntologyChange(change);
        return d;
    }

    @Test
    void resolveDirtyAnnotation_alreadyResolved_throwsConflict() {
        DirtyAnnotation d = dirty(1L, 5L, version(1L));
        d.setResolvedAt(java.time.Instant.now());
        when(dirtyAnnotationRepository.findById(1L)).thenReturn(Optional.of(d));

        ResolveDirtyAnnotationRequest req = new ResolveDirtyAnnotationRequest();
        req.setNewLabelCode("NEW");
        req.setResolvedBy("researcher_01");

        assertThatThrownBy(() -> newService().resolveDirtyAnnotation(1L, req))
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

        DirtyAnnotationResponse response = newService().resolveDirtyAnnotation(1L, req);

        assertThat(response.isResolved()).isTrue();
        assertThat(response.getNewLabelCode()).isEqualTo("NEW");
        verify(annotationEventLoggerService).log(eq(EventType.DIRTY_FLAGS_RESOLVED), eq(version), anyString(), anyString());
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

        newService().resolveDirtyAnnotation(1L, req);

        verify(annotationEventLoggerService, never()).log(any(), any(), anyString(), anyString());
    }
}
