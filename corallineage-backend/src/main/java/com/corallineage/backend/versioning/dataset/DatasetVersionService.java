package com.corallineage.backend.versioning.dataset;

import com.corallineage.backend.datasets.Dataset;
import com.corallineage.backend.datasets.DatasetRepository;
import com.corallineage.backend.exception.ConflictException;
import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.logging.AnnotationEventLoggerService;
import com.corallineage.backend.logging.EventType;
import com.corallineage.backend.ontology.DirtyAnnotationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

@Service
public class DatasetVersionService {

    private final DatasetRepository datasetRepository;
    private final DatasetVersionRepository datasetVersionRepository;
    private final LabelDefinitionRepository labelDefinitionRepository;
    private final DatasetVersionImageRepository datasetVersionImageRepository;
    private final AnnotationPointRepository annotationPointRepository;
    private final DirtyAnnotationRepository dirtyAnnotationRepository;
    private final DvcIntegrationService dvcIntegrationService;
    private final AnnotationEventLoggerService annotationEventLoggerService;
    private final DatasetVersionMapper datasetVersionMapper;

    public DatasetVersionService(DatasetRepository datasetRepository,
                                 DatasetVersionRepository datasetVersionRepository,
                                 LabelDefinitionRepository labelDefinitionRepository,
                                 DatasetVersionImageRepository datasetVersionImageRepository,
                                 AnnotationPointRepository annotationPointRepository,
                                 DirtyAnnotationRepository dirtyAnnotationRepository,
                                 DvcIntegrationService dvcIntegrationService,
                                 AnnotationEventLoggerService annotationEventLoggerService,
                                 DatasetVersionMapper datasetVersionMapper) {
        this.datasetRepository = datasetRepository;
        this.datasetVersionRepository = datasetVersionRepository;
        this.labelDefinitionRepository = labelDefinitionRepository;
        this.datasetVersionImageRepository = datasetVersionImageRepository;
        this.annotationPointRepository = annotationPointRepository;
        this.dirtyAnnotationRepository = dirtyAnnotationRepository;
        this.dvcIntegrationService = dvcIntegrationService;
        this.annotationEventLoggerService = annotationEventLoggerService;
        this.datasetVersionMapper = datasetVersionMapper;
    }

    @Transactional
    public DatasetVersionResponse createSnapshot(Long datasetId, CreateVersionRequest request) {
        DatasetVersion previousLatest = datasetVersionRepository.findTopByDatasetIdOrderByCreatedAtDesc(datasetId).orElse(null);
        return createSnapshotBranchedFrom(datasetId, request, previousLatest);
    }

    /**
     * Like {@link #createSnapshot}, but copies labels + image membership forward from an explicit
     * {@code branchPoint} rather than always the dataset's overall latest version. Used by ontology
     * change recording, where the new version must branch from whichever version the user picked
     * (which may not be the dataset's current latest) — using the plain createSnapshot here would
     * silently branch from the wrong version instead.
     */
    @Transactional
    public DatasetVersionResponse createSnapshotBranchedFrom(Long datasetId, CreateVersionRequest request,
                                                              DatasetVersion branchPoint) {
        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new NotFoundException("Dataset not found: " + datasetId));

        if (datasetVersionRepository.findByDatasetIdAndVersionTag(datasetId, request.getVersionTag()).isPresent()) {
            throw new ConflictException("Dataset version tag already exists in this dataset: " + request.getVersionTag());
        }

        DatasetVersion previousLatest = branchPoint;
        String dvcCommitHash = dvcIntegrationService.commitCurrentState(request.getVersionTag());

        DatasetVersion version = new DatasetVersion();
        version.setDataset(dataset);
        version.setVersionTag(request.getVersionTag());
        version.setCreatedBy(request.getCreatedBy());
        version.setTriggerReason(request.getTriggerReason().name());
        version.setImageCount(0);
        version.setAnnotationCount(0);
        version.setDvcCommitHash(dvcCommitHash);
        version.setNotes(request.getNotes());
        DatasetVersion savedVersion = datasetVersionRepository.save(version);

        List<LabelDefinition> labels = snapshotCurrentLabelDefinitions(savedVersion, previousLatest);
        List<DatasetVersionImage> membership = snapshotCurrentImageMembership(savedVersion, previousLatest);

        savedVersion.setImageCount(membership.size());
        savedVersion.setAnnotationCount((int) annotationPointRepository.countByImageOriginDatasetVersionDatasetId(datasetId));
        savedVersion = datasetVersionRepository.save(savedVersion);

        logAfterCommit(EventType.DATASET_VERSION_CREATED, savedVersion,
                request.getCreatedBy(), "Created dataset version " + request.getVersionTag());

        return attachCompleteness(datasetVersionMapper.toResponseWithLabels(savedVersion, labels));
    }

    /**
     * Copies forward the previous version's image membership rows onto {@code version} — cheap
     * (just new join rows, no Image data duplicated). A dataset's very first version starts with
     * no membership; the importer adds rows for the freshly-imported images afterward.
     */
    List<DatasetVersionImage> snapshotCurrentImageMembership(DatasetVersion version, DatasetVersion previousLatest) {
        List<DatasetVersionImage> snapshot = new ArrayList<>();
        if (previousLatest == null) {
            return snapshot;
        }
        for (DatasetVersionImage membershipRow : datasetVersionImageRepository.findByDatasetVersionId(previousLatest.getId())) {
            DatasetVersionImage copy = new DatasetVersionImage();
            copy.setDatasetVersion(version);
            copy.setImage(membershipRow.getImage());
            snapshot.add(datasetVersionImageRepository.save(copy));
        }
        return snapshot;
    }

    /** {@code true} when every dirty annotation raised against this version has been resolved. */
    @Transactional(readOnly = true)
    public boolean isComplete(Long datasetVersionId) {
        return dirtyAnnotationRepository
                .countByOntologyChange_AffectedDatasetVersion_IdAndResolvedAtIsNull(datasetVersionId) == 0;
    }

    @Transactional(readOnly = true)
    public DatasetVersionResponse getVersionById(Long datasetId, Long id) {
        DatasetVersion version = datasetVersionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Dataset version not found: " + id));
        if (!version.getDataset().getId().equals(datasetId)) {
            throw new NotFoundException("Dataset version not found: " + id);
        }
        List<LabelDefinition> labels = labelDefinitionRepository.findByDatasetVersionId(id);
        return attachCompleteness(datasetVersionMapper.toResponseWithLabels(version, labels));
    }

    @Transactional
    public LabelDefinitionResponse addLabelDefinition(Long datasetId, Long versionId, CreateLabelDefinitionRequest request) {
        DatasetVersion version = datasetVersionRepository.findById(versionId)
                .orElseThrow(() -> new NotFoundException("Dataset version not found: " + versionId));
        if (!version.getDataset().getId().equals(datasetId)) {
            throw new NotFoundException("Dataset version not found: " + versionId);
        }
        if (labelDefinitionRepository.existsByDatasetVersionIdAndLabelCode(versionId, request.getLabelCode())) {
            throw new ConflictException("Label code already exists on this version: " + request.getLabelCode());
        }

        LabelDefinition label = new LabelDefinition();
        label.setDatasetVersion(version);
        label.setLabelCode(request.getLabelCode());
        label.setLabelName(request.getLabelName());
        label.setLabelCategory(request.getLabelCategory());
        LabelDefinition saved = labelDefinitionRepository.save(label);

        return datasetVersionMapper.toLabelResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<DatasetVersionResponse> getVersionHistory(Long datasetId, Pageable pageable) {
        if (!datasetRepository.existsById(datasetId)) {
            throw new NotFoundException("Dataset not found: " + datasetId);
        }
        return datasetVersionRepository.findAllByDatasetIdOrderByCreatedAtDesc(datasetId, pageable)
                .map(version -> attachCompleteness(datasetVersionMapper.toResponse(version)));
    }

    /**
     * Populates {@code complete}/{@code unresolvedDirtyCount} with one extra query per version.
     * Accepted N+1 given current data volumes rather than a bespoke aggregate query — see plan notes.
     */
    private DatasetVersionResponse attachCompleteness(DatasetVersionResponse response) {
        long unresolved = dirtyAnnotationRepository
                .countByOntologyChange_AffectedDatasetVersion_IdAndResolvedAtIsNull(response.getId());
        response.setUnresolvedDirtyCount(unresolved);
        response.setComplete(unresolved == 0);
        return response;
    }

    /**
     * Logs an annotation event in its own REQUIRES_NEW transaction. When called
     * inside an active transaction (normal Spring flow), the log is deferred until
     * after the surrounding transaction commits so the event can reference rows
     * (e.g. a freshly created dataset version) that only become visible at commit.
     * Falls back to logging immediately when no transaction is active (e.g. unit tests).
     */
    private void logAfterCommit(EventType eventType, DatasetVersion version,
                                String performedBy, String description) {
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

    private List<LabelDefinition> snapshotCurrentLabelDefinitions(DatasetVersion version, DatasetVersion previousLatest) {
        List<LabelDefinition> snapshot = new ArrayList<>();
        if (previousLatest == null) {
            return snapshot;
        }
        for (LabelDefinition label : labelDefinitionRepository.findByDatasetVersionId(previousLatest.getId())) {
            LabelDefinition copy = new LabelDefinition();
            copy.setDatasetVersion(version);
            copy.setLabelCode(label.getLabelCode());
            copy.setLabelName(label.getLabelName());
            copy.setLabelCategory(label.getLabelCategory());
            copy.setDeprecatedAt(label.getDeprecatedAt());
            snapshot.add(labelDefinitionRepository.save(copy));
        }
        return snapshot;
    }
}
