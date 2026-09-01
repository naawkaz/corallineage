package com.corallineage.backend.ontology;

import com.corallineage.backend.datasets.DatasetService;
import com.corallineage.backend.exception.ConflictException;
import com.corallineage.backend.exception.InvalidRequestException;
import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.logging.AnnotationEvent;
import com.corallineage.backend.logging.AnnotationEventLoggerService;
import com.corallineage.backend.logging.AnnotationEventRepository;
import com.corallineage.backend.logging.EventType;
import com.corallineage.backend.versioning.dataset.AnnotationPoint;
import com.corallineage.backend.versioning.dataset.AnnotationPointRepository;
import com.corallineage.backend.versioning.dataset.CreateVersionRequest;
import com.corallineage.backend.versioning.dataset.DatasetVersion;
import com.corallineage.backend.versioning.dataset.DatasetVersionRepository;
import com.corallineage.backend.versioning.dataset.DatasetVersionResponse;
import com.corallineage.backend.versioning.dataset.DatasetVersionService;
import com.corallineage.backend.versioning.dataset.LabelDefinition;
import com.corallineage.backend.versioning.dataset.LabelDefinitionRepository;
import com.corallineage.backend.versioning.dataset.TriggerReason;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class OntologyChangeService {

    private final OntologyChangeRepository ontologyChangeRepository;
    private final DirtyAnnotationRepository dirtyAnnotationRepository;
    private final DatasetVersionRepository datasetVersionRepository;
    private final DatasetVersionService datasetVersionService;
    private final LabelDefinitionRepository labelDefinitionRepository;
    private final AnnotationPointRepository annotationPointRepository;
    private final OntologyChangeMapper ontologyChangeMapper;
    private final AnnotationEventLoggerService annotationEventLoggerService;
    private final AnnotationEventRepository annotationEventRepository;
    private final DatasetService datasetService;

    public OntologyChangeService(OntologyChangeRepository ontologyChangeRepository,
                                 DirtyAnnotationRepository dirtyAnnotationRepository,
                                 DatasetVersionRepository datasetVersionRepository,
                                 DatasetVersionService datasetVersionService,
                                 LabelDefinitionRepository labelDefinitionRepository,
                                 AnnotationPointRepository annotationPointRepository,
                                 OntologyChangeMapper ontologyChangeMapper,
                                 AnnotationEventLoggerService annotationEventLoggerService,
                                 AnnotationEventRepository annotationEventRepository,
                                 DatasetService datasetService) {
        this.ontologyChangeRepository = ontologyChangeRepository;
        this.dirtyAnnotationRepository = dirtyAnnotationRepository;
        this.datasetVersionRepository = datasetVersionRepository;
        this.datasetVersionService = datasetVersionService;
        this.labelDefinitionRepository = labelDefinitionRepository;
        this.annotationPointRepository = annotationPointRepository;
        this.ontologyChangeMapper = ontologyChangeMapper;
        this.annotationEventLoggerService = annotationEventLoggerService;
        this.annotationEventRepository = annotationEventRepository;
        this.datasetService = datasetService;
    }

    /**
     * Records an ontology change by creating a NEW DatasetVersion branched from
     * {@code basedOnDatasetVersionId} - mutating a version's label set in place after models have
     * already trained against it would silently corrupt reproducibility, since label_definitions is
     * scoped per-version specifically to keep a version's label set a frozen historical fact. The new
     * version starts as an exact copy (labels + image membership, via DatasetVersionService's snapshot
     * machinery) and then has the specific label mutation for {@code changeType} applied on top.
     */
    @Transactional
    public OntologyChangeResponse recordChange(RecordOntologyChangeRequest request, String actingUsername) {
        DatasetVersion basedOnVersion = datasetVersionRepository.findById(request.getBasedOnDatasetVersionId())
                .orElseThrow(() -> new NotFoundException(
                        "Dataset version not found: " + request.getBasedOnDatasetVersionId()));
        datasetService.requireAccess(basedOnVersion.getDataset().getId(), actingUsername);

        ChangeType changeType = request.getChangeType();
        String oldLabelCode = request.getOldLabelCode();
        List<NewLabelSpec> newLabels = request.getNewLabels() != null ? request.getNewLabels() : List.of();

        validateOldLabelCode(changeType, oldLabelCode, basedOnVersion);
        validateNewLabels(changeType, newLabels);
        validateReplacementLabelCode(changeType, request.getReplacementLabelCode(), oldLabelCode, basedOnVersion);

        DatasetVersion newVersion = branchNewVersion(basedOnVersion, changeType, request);

        List<String> createdLabelCodes = applyLabelMutation(changeType, oldLabelCode, newLabels, newVersion);

        // newVersion was created earlier in THIS same transaction (via datasetVersionService.createSnapshot,
        // which joins this method's transaction rather than opening its own) and is not yet committed -
        // annotationEventLoggerService.log() runs in REQUIRES_NEW on a separate connection and would fail
        // an FK check against an uncommitted row, so this event is persisted directly on the current
        // connection/transaction instead of going through the REQUIRES_NEW logger.
        AnnotationEvent detectedEvent = new AnnotationEvent();
        detectedEvent.setEventType(EventType.ONTOLOGY_CHANGE_DETECTED);
        detectedEvent.setDatasetVersion(newVersion);
        detectedEvent.setPerformedBy(request.getChangedBy());
        detectedEvent.setDescription("Ontology change recorded (" + changeType + ")"
                + (oldLabelCode != null ? ": " + oldLabelCode : ""));
        detectedEvent = annotationEventRepository.save(detectedEvent);

        OntologyChange change = new OntologyChange();
        change.setChangeType(changeType.name());
        change.setOldLabelCode(oldLabelCode);
        change.setNewLabelCodes(createdLabelCodes);
        change.setAffectedDatasetVersion(newVersion);
        change.setChangedBy(request.getChangedBy());
        change.setAnnotationEvent(detectedEvent);
        change.setNotes(request.getNotes());
        change.setReplacementLabelCode(request.getReplacementLabelCode());
        OntologyChange savedChange = ontologyChangeRepository.save(change);

        // Only SPLIT/DEPRECATE leave annotations pointing at an ambiguous/stale label; RENAME is a
        // straight identity-preserving 1:1 update and never flags anything dirty (behavior change
        // from the old implementation, which flagged dirty for every change type with an oldLabelCode).
        List<DirtyAnnotation> dirtyAnnotations = new ArrayList<>();
        if (changeType == ChangeType.LABEL_SPLIT || changeType == ChangeType.LABEL_DEPRECATED) {
            List<AnnotationPoint> affectedPoints = annotationPointRepository
                    .findByLabelCodeAndDatasetVersionMembership(newVersion.getId(), oldLabelCode);
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
            logAfterCommit(EventType.DIRTY_FLAGS_RAISED, newVersion, request.getChangedBy(),
                    dirtyAnnotations.size() + " annotation point(s) flagged dirty following ontology change "
                            + savedChange.getId());
        }

        return ontologyChangeMapper.toResponse(savedChange, dirtyAnnotations.size(), 0);
    }

    /**
     * Defers the log call until after this transaction commits, so the REQUIRES_NEW logger (running
     * on a separate connection) can see rows - e.g. {@code newVersion} - that only become visible at
     * commit. Mirrors DatasetVersionService.logAfterCommit / DatasetImportExecutor's equivalent helper.
     */
    private void logAfterCommit(EventType eventType, DatasetVersion version, String performedBy, String description) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    annotationEventLoggerService.log(eventType, version, performedBy, description);
                }
            });
        } else {
            annotationEventLoggerService.log(eventType, version, performedBy, description);
        }
    }

    /** Branches a new DatasetVersion off {@code basedOnVersion}, copying labels + image membership forward. */
    private DatasetVersion branchNewVersion(DatasetVersion basedOnVersion, ChangeType changeType,
                                            RecordOntologyChangeRequest request) {
        CreateVersionRequest versionRequest = new CreateVersionRequest();
        versionRequest.setVersionTag(request.getNewVersionTag());
        versionRequest.setTriggerReason(TriggerReason.LABEL_DEFINITION_CHANGED);
        versionRequest.setCreatedBy(request.getChangedBy());
        versionRequest.setNotes(request.getNotes() != null ? request.getNotes()
                : "Ontology change (" + changeType + ") based on " + basedOnVersion.getVersionTag());
        DatasetVersionResponse newVersionResponse = datasetVersionService.createSnapshotBranchedFrom(
                basedOnVersion.getDataset().getId(), versionRequest, basedOnVersion);
        return datasetVersionRepository.findById(newVersionResponse.getId())
                .orElseThrow(() -> new NotFoundException("Dataset version not found: " + newVersionResponse.getId()));
    }

    private void validateOldLabelCode(ChangeType changeType, String oldLabelCode, DatasetVersion basedOnVersion) {
        boolean blank = oldLabelCode == null || oldLabelCode.isBlank();
        switch (changeType) {
            case LABEL_ADDED -> {
                if (!blank) {
                    throw new InvalidRequestException("oldLabelCode must not be set for LABEL_ADDED");
                }
            }
            case LABEL_RENAMED, LABEL_SPLIT, LABEL_DEPRECATED -> {
                if (blank) {
                    throw new InvalidRequestException("oldLabelCode is required for " + changeType);
                }
                if (!labelDefinitionRepository.existsByDatasetVersionIdAndLabelCode(basedOnVersion.getId(), oldLabelCode)) {
                    throw new InvalidRequestException("oldLabelCode does not exist on this dataset version: " + oldLabelCode);
                }
            }
        }
    }

    private void validateNewLabels(ChangeType changeType, List<NewLabelSpec> newLabels) {
        switch (changeType) {
            case LABEL_ADDED, LABEL_RENAMED -> {
                if (newLabels.size() != 1) {
                    throw new InvalidRequestException("Exactly one newLabels entry is required for " + changeType);
                }
            }
            case LABEL_SPLIT -> {
                if (newLabels.isEmpty()) {
                    throw new InvalidRequestException("At least one newLabels entry is required for LABEL_SPLIT");
                }
            }
            case LABEL_DEPRECATED -> {
                if (!newLabels.isEmpty()) {
                    throw new InvalidRequestException("newLabels must be empty for LABEL_DEPRECATED");
                }
            }
        }
    }

    private void validateReplacementLabelCode(ChangeType changeType, String replacementLabelCode,
                                              String oldLabelCode, DatasetVersion basedOnVersion) {
        if (replacementLabelCode == null || replacementLabelCode.isBlank()) {
            return;
        }
        if (changeType != ChangeType.LABEL_DEPRECATED) {
            throw new InvalidRequestException("replacementLabelCode is only valid for LABEL_DEPRECATED");
        }
        if (replacementLabelCode.equals(oldLabelCode)) {
            throw new InvalidRequestException("replacementLabelCode must not be the same as oldLabelCode");
        }
        LabelDefinition replacement = labelDefinitionRepository
                .findByDatasetVersionIdAndLabelCode(basedOnVersion.getId(), replacementLabelCode)
                .orElseThrow(() -> new InvalidRequestException(
                        "replacementLabelCode does not exist on this dataset version: " + replacementLabelCode));
        if (replacement.getDeprecatedAt() != null) {
            throw new InvalidRequestException("replacementLabelCode must reference a non-deprecated label: " + replacementLabelCode);
        }
    }

    /** Applies the changeType-specific label mutation to newVersion's freshly-copied label set. Returns the new label codes created (if any). */
    private List<String> applyLabelMutation(ChangeType changeType, String oldLabelCode, List<NewLabelSpec> newLabels,
                                            DatasetVersion newVersion) {
        return switch (changeType) {
            case LABEL_ADDED -> List.of(createLabel(newVersion, newLabels.get(0)).getLabelCode());
            case LABEL_RENAMED -> {
                LabelDefinition existing = requireCopiedLabel(newVersion, oldLabelCode);
                NewLabelSpec spec = newLabels.get(0);
                ensureCodeAvailable(newVersion, spec.getCode(), oldLabelCode);
                existing.setLabelCode(spec.getCode());
                existing.setLabelName(spec.getName());
                existing.setLabelCategory(spec.getCategory());
                labelDefinitionRepository.save(existing);
                yield List.of(spec.getCode());
            }
            case LABEL_SPLIT -> {
                LabelDefinition existing = requireCopiedLabel(newVersion, oldLabelCode);
                existing.setDeprecatedAt(Instant.now());
                labelDefinitionRepository.save(existing);
                List<String> created = new ArrayList<>();
                for (NewLabelSpec spec : newLabels) {
                    created.add(createLabel(newVersion, spec).getLabelCode());
                }
                yield created;
            }
            case LABEL_DEPRECATED -> {
                LabelDefinition existing = requireCopiedLabel(newVersion, oldLabelCode);
                existing.setDeprecatedAt(Instant.now());
                labelDefinitionRepository.save(existing);
                yield List.of();
            }
        };
    }

    private LabelDefinition requireCopiedLabel(DatasetVersion newVersion, String labelCode) {
        return labelDefinitionRepository.findByDatasetVersionIdAndLabelCode(newVersion.getId(), labelCode)
                .orElseThrow(() -> new NotFoundException(
                        "Label code not found on newly branched version: " + labelCode));
    }

    private void ensureCodeAvailable(DatasetVersion newVersion, String code, String currentCodeBeingRenamed) {
        if (code.equals(currentCodeBeingRenamed)) {
            return;
        }
        if (labelDefinitionRepository.existsByDatasetVersionIdAndLabelCode(newVersion.getId(), code)) {
            throw new ConflictException("Label code already exists on this dataset version: " + code);
        }
    }

    private LabelDefinition createLabel(DatasetVersion newVersion, NewLabelSpec spec) {
        if (labelDefinitionRepository.existsByDatasetVersionIdAndLabelCode(newVersion.getId(), spec.getCode())) {
            throw new ConflictException("Label code already exists on this dataset version: " + spec.getCode());
        }
        LabelDefinition label = new LabelDefinition();
        label.setDatasetVersion(newVersion);
        label.setLabelCode(spec.getCode());
        label.setLabelName(spec.getName());
        label.setLabelCategory(spec.getCategory());
        return labelDefinitionRepository.save(label);
    }

    @Transactional(readOnly = true)
    public Page<OntologyChangeResponse> getChanges(Long datasetId, Long datasetVersionId, Pageable pageable) {
        Page<OntologyChange> changes = datasetVersionId != null
                ? ontologyChangeRepository.findByAffectedDatasetVersionIdOrderByChangedAtDesc(datasetVersionId, pageable)
                : ontologyChangeRepository.findByAffectedDatasetVersion_Dataset_IdOrderByChangedAtDesc(datasetId, pageable);
        return changes.map(change -> ontologyChangeMapper.toResponse(change,
                dirtyAnnotationRepository.countByOntologyChangeId(change.getId()),
                dirtyAnnotationRepository.countByOntologyChangeIdAndResolvedAtIsNotNull(change.getId())));
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

    /**
     * Resolves the owning dataset id for a scoped dirty-annotation query (ontologyChangeId takes
     * precedence over imageId when both are given, matching getDirtyAnnotations' own precedence).
     * Returns null when neither scoping filter was supplied.
     */
    @Transactional(readOnly = true)
    public Long resolveDatasetIdForDirtyFilter(Long ontologyChangeId, Long imageId) {
        if (ontologyChangeId != null) {
            OntologyChange change = ontologyChangeRepository.findById(ontologyChangeId)
                    .orElseThrow(() -> new NotFoundException("Ontology change not found: " + ontologyChangeId));
            return change.getAffectedDatasetVersion().getDataset().getId();
        }
        if (imageId != null) {
            List<DirtyAnnotation> forImage = dirtyAnnotationRepository.findByImageId(imageId, Pageable.unpaged()).getContent();
            if (!forImage.isEmpty()) {
                return forImage.get(0).getImage().getOriginDatasetVersion().getDataset().getId();
            }
            return null;
        }
        return null;
    }

    /**
     * Filters dirty-annotation rows down to the requester's accessible datasets, used only when
     * getDirtyAnnotations is called with no scoping filter at all (see the special-case handling
     * in OntologyChangeController mirroring AnnotationEventController's Global Event Log).
     */
    @Transactional(readOnly = true)
    public Page<DirtyAnnotationResponse> getDirtyAnnotationsAccessibleWithNoFilter(java.util.Set<Long> accessibleDatasetIds, Pageable pageable) {
        // Filters before paginating (not after) so page metadata (total elements, page count)
        // stays correct for the filtered set rather than for the unfiltered table.
        List<DirtyAnnotationResponse> filtered = dirtyAnnotationRepository.findAll().stream()
                .filter(d -> accessibleDatasetIds.contains(d.getImage().getOriginDatasetVersion().getDataset().getId()))
                .sorted(Comparator.comparing(DirtyAnnotation::getFlaggedAt).reversed())
                .map(ontologyChangeMapper::toDirtyResponse)
                .toList();
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<DirtyAnnotationResponse> pageContent = start >= filtered.size() ? List.of() : filtered.subList(start, end);
        return new org.springframework.data.domain.PageImpl<>(pageContent, pageable, filtered.size());
    }

    @Transactional
    public DirtyAnnotationResponse resolveDirtyAnnotation(Long id, ResolveDirtyAnnotationRequest request, String actingUsername) {
        DirtyAnnotation dirty = dirtyAnnotationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Dirty annotation not found: " + id));
        datasetService.requireAccess(dirty.getImage().getOriginDatasetVersion().getDataset().getId(), actingUsername);
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
