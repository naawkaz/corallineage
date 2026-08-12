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
import com.corallineage.backend.versioning.dataset.LabelDefinitionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class OntologyChangeService {

    private final OntologyChangeRepository ontologyChangeRepository;
    private final DirtyAnnotationRepository dirtyAnnotationRepository;
    private final DatasetVersionRepository datasetVersionRepository;
    private final LabelDefinitionRepository labelDefinitionRepository;
    private final AnnotationPointRepository annotationPointRepository;
    private final OntologyChangeMapper ontologyChangeMapper;
    private final AnnotationEventLoggerService annotationEventLoggerService;

    public OntologyChangeService(OntologyChangeRepository ontologyChangeRepository,
                                 DirtyAnnotationRepository dirtyAnnotationRepository,
                                 DatasetVersionRepository datasetVersionRepository,
                                 LabelDefinitionRepository labelDefinitionRepository,
                                 AnnotationPointRepository annotationPointRepository,
                                 OntologyChangeMapper ontologyChangeMapper,
                                 AnnotationEventLoggerService annotationEventLoggerService) {
        this.ontologyChangeRepository = ontologyChangeRepository;
        this.dirtyAnnotationRepository = dirtyAnnotationRepository;
        this.datasetVersionRepository = datasetVersionRepository;
        this.labelDefinitionRepository = labelDefinitionRepository;
        this.annotationPointRepository = annotationPointRepository;
        this.ontologyChangeMapper = ontologyChangeMapper;
        this.annotationEventLoggerService = annotationEventLoggerService;
    }

    @Transactional
    public OntologyChangeResponse recordChange(RecordOntologyChangeRequest request) {
        DatasetVersion version = datasetVersionRepository.findById(request.getAffectedDatasetVersionId())
                .orElseThrow(() -> new NotFoundException(
                        "Dataset version not found: " + request.getAffectedDatasetVersionId()));

        String oldLabelCode = request.getOldLabelCode();
        if (oldLabelCode != null && !oldLabelCode.isBlank()
                && !labelDefinitionRepository.existsByDatasetVersionIdAndLabelCode(version.getId(), oldLabelCode)) {
            throw new InvalidRequestException("oldLabelCode does not exist on this dataset version: " + oldLabelCode);
        }

        AnnotationEvent detectedEvent = annotationEventLoggerService.log(EventType.ONTOLOGY_CHANGE_DETECTED, version,
                request.getChangedBy(), "Ontology change recorded (" + request.getChangeType() + ")"
                        + (oldLabelCode != null ? ": " + oldLabelCode : ""));

        OntologyChange change = new OntologyChange();
        change.setChangeType(request.getChangeType().name());
        change.setOldLabelCode(oldLabelCode);
        change.setNewLabelCodes(request.getNewLabelCodes());
        change.setAffectedDatasetVersion(version);
        change.setChangedBy(request.getChangedBy());
        change.setAnnotationEvent(detectedEvent);
        change.setNotes(request.getNotes());
        OntologyChange savedChange = ontologyChangeRepository.save(change);

        List<DirtyAnnotation> dirtyAnnotations = new ArrayList<>();
        if (oldLabelCode != null && !oldLabelCode.isBlank()) {
            List<AnnotationPoint> affectedPoints = annotationPointRepository
                    .findByImageDatasetVersionIdAndLabelDefinitionLabelCode(version.getId(), oldLabelCode);
            for (AnnotationPoint point : affectedPoints) {
                DirtyAnnotation dirty = new DirtyAnnotation();
                dirty.setImage(point.getImage());
                dirty.setPointIndex(point.getPointIndex());
                dirty.setCurrentLabelCode(oldLabelCode);
                dirty.setOntologyChange(savedChange);
                dirtyAnnotations.add(dirty);
            }
            dirtyAnnotationRepository.saveAll(dirtyAnnotations);
        }

        if (!dirtyAnnotations.isEmpty()) {
            annotationEventLoggerService.log(EventType.DIRTY_FLAGS_RAISED, version, request.getChangedBy(),
                    dirtyAnnotations.size() + " annotation point(s) flagged dirty following ontology change "
                            + savedChange.getId());
        }

        return ontologyChangeMapper.toResponse(savedChange, dirtyAnnotations.size());
    }

    @Transactional(readOnly = true)
    public Page<OntologyChangeResponse> getChanges(Long datasetVersionId, Pageable pageable) {
        Page<OntologyChange> changes = datasetVersionId != null
                ? ontologyChangeRepository.findByAffectedDatasetVersionIdOrderByChangedAtDesc(datasetVersionId, pageable)
                : ontologyChangeRepository.findAllByOrderByChangedAtDesc(pageable);
        return changes.map(change -> ontologyChangeMapper.toResponse(change,
                dirtyAnnotationRepository.countByOntologyChangeId(change.getId())));
    }

    @Transactional(readOnly = true)
    public Page<DirtyAnnotationResponse> getDirtyAnnotations(Long ontologyChangeId, Long imageId, Pageable pageable) {
        Page<DirtyAnnotation> dirty;
        if (ontologyChangeId != null && imageId != null) {
            dirty = dirtyAnnotationRepository.findByOntologyChangeIdAndImageId(ontologyChangeId, imageId, pageable);
        } else if (ontologyChangeId != null) {
            dirty = dirtyAnnotationRepository.findByOntologyChangeId(ontologyChangeId, pageable);
        } else if (imageId != null) {
            dirty = dirtyAnnotationRepository.findByImageId(imageId, pageable);
        } else {
            dirty = dirtyAnnotationRepository.findAll(pageable);
        }
        return dirty.map(ontologyChangeMapper::toDirtyResponse);
    }

    @Transactional
    public DirtyAnnotationResponse resolveDirtyAnnotation(Long id, ResolveDirtyAnnotationRequest request) {
        DirtyAnnotation dirty = dirtyAnnotationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Dirty annotation not found: " + id));
        if (dirty.getResolvedAt() != null) {
            throw new ConflictException("Dirty annotation already resolved: " + id);
        }

        dirty.setResolvedAt(Instant.now());
        dirty.setResolvedBy(request.getResolvedBy());
        dirty.setNewLabelCode(request.getNewLabelCode());
        DirtyAnnotation saved = dirtyAnnotationRepository.save(dirty);

        long ontologyChangeId = saved.getOntologyChange().getId();
        long total = dirtyAnnotationRepository.countByOntologyChangeId(ontologyChangeId);
        long resolved = dirtyAnnotationRepository.countByOntologyChangeIdAndResolvedAtIsNotNull(ontologyChangeId);
        if (resolved == total) {
            DatasetVersion version = saved.getOntologyChange().getAffectedDatasetVersion();
            annotationEventLoggerService.log(EventType.DIRTY_FLAGS_RESOLVED, version, request.getResolvedBy(),
                    "All dirty annotations resolved for ontology change " + ontologyChangeId);
        }

        return ontologyChangeMapper.toDirtyResponse(saved);
    }
}
