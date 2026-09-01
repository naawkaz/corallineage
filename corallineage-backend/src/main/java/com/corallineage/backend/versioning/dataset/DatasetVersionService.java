package com.corallineage.backend.versioning.dataset;

import com.corallineage.backend.annotation.AnnotationJobRepository;
import com.corallineage.backend.datasets.Dataset;
import com.corallineage.backend.datasets.DatasetRepository;
import com.corallineage.backend.datasets.importservice.ImportJob;
import com.corallineage.backend.datasets.importservice.ImportJobRepository;
import com.corallineage.backend.exception.ConflictException;
import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.logging.AnnotationEventLoggerService;
import com.corallineage.backend.logging.EventType;
import com.corallineage.backend.ontology.DirtyAnnotationRepository;
import com.corallineage.backend.pointannotation.PointAnnotationJobRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final ImportJobRepository importJobRepository;
    private final PointAnnotationJobRepository pointAnnotationJobRepository;
    private final AnnotationJobRepository annotationJobRepository;

    public DatasetVersionService(DatasetRepository datasetRepository,
                                 DatasetVersionRepository datasetVersionRepository,
                                 LabelDefinitionRepository labelDefinitionRepository,
                                 DatasetVersionImageRepository datasetVersionImageRepository,
                                 AnnotationPointRepository annotationPointRepository,
                                 DirtyAnnotationRepository dirtyAnnotationRepository,
                                 DvcIntegrationService dvcIntegrationService,
                                 AnnotationEventLoggerService annotationEventLoggerService,
                                 DatasetVersionMapper datasetVersionMapper,
                                 ImportJobRepository importJobRepository,
                                 PointAnnotationJobRepository pointAnnotationJobRepository,
                                 AnnotationJobRepository annotationJobRepository) {
        this.datasetRepository = datasetRepository;
        this.datasetVersionRepository = datasetVersionRepository;
        this.labelDefinitionRepository = labelDefinitionRepository;
        this.datasetVersionImageRepository = datasetVersionImageRepository;
        this.annotationPointRepository = annotationPointRepository;
        this.dirtyAnnotationRepository = dirtyAnnotationRepository;
        this.dvcIntegrationService = dvcIntegrationService;
        this.annotationEventLoggerService = annotationEventLoggerService;
        this.datasetVersionMapper = datasetVersionMapper;
        this.importJobRepository = importJobRepository;
        this.pointAnnotationJobRepository = pointAnnotationJobRepository;
        this.annotationJobRepository = annotationJobRepository;
    }

    @Transactional
    public DatasetVersionResponse createSnapshot(Long datasetId, CreateVersionRequest request) {
        DatasetVersion previousLatest = datasetVersionRepository.findTopByDatasetIdOrderByCreatedAtDesc(datasetId).orElse(null);
        return createSnapshotBranchedFrom(datasetId, request, previousLatest);
    }

    /** Creates a staging version excluded from the version list */
    @Transactional
    public DatasetVersionResponse createStagingSnapshot(Long datasetId, CreateVersionRequest request) {
        DatasetVersion previousLatest = datasetVersionRepository.findTopByDatasetIdOrderByCreatedAtDesc(datasetId).orElse(null);
        return createSnapshotBranchedFrom(datasetId, request, previousLatest, true);
    }

    /** Creates a version branched from a given point rather than the latest */
    @Transactional
    public DatasetVersionResponse createSnapshotBranchedFrom(Long datasetId, CreateVersionRequest request,
                                                              DatasetVersion branchPoint) {
        return createSnapshotBranchedFrom(datasetId, request, branchPoint, false);
    }

    private DatasetVersionResponse createSnapshotBranchedFrom(Long datasetId, CreateVersionRequest request,
                                                               DatasetVersion branchPoint, boolean isStaging) {
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
        version.setIsStaging(isStaging);
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

    /** Copies the previous version's image membership forward */
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

    /** True when every dirty annotation on this version is resolved */
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
        return attachImportStatus(attachCompleteness(datasetVersionMapper.toResponseWithLabels(version, labels)));
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

    /** Excludes unresolved and incomplete versions from the list, version numbers stay permanent */
    @Transactional(readOnly = true)
    public Page<DatasetVersionResponse> getVersionHistory(Long datasetId, Pageable pageable) {
        if (!datasetRepository.existsById(datasetId)) {
            throw new NotFoundException("Dataset not found: " + datasetId);
        }
        List<DatasetVersion> all = datasetVersionRepository
                .findAllByDatasetIdAndIsStagingNotTrueOrderByCreatedAtDesc(datasetId);

        Map<Long, Integer> versionNumbersById = new HashMap<>();
        List<DatasetVersion> byIdAscending = new ArrayList<>(all);
        byIdAscending.sort(Comparator.comparing(DatasetVersion::getId));
        for (int i = 0; i < byIdAscending.size(); i++) {
            versionNumbersById.put(byIdAscending.get(i).getId(), i + 1);
        }

        List<DatasetVersionResponse> resolved = all.stream()
                .map(version -> {
                    DatasetVersionResponse response = attachImportStatus(attachCompleteness(datasetVersionMapper.toResponse(version)));
                    response.setVersionNumber(versionNumbersById.get(version.getId()));
                    return response;
                })
                .filter(response -> !"UNRESOLVED".equals(response.getResolveStatus()) && response.isComplete())
                .toList();

        int total = resolved.size();
        int start = Math.min((int) pageable.getOffset(), total);
        int end = Math.min(start + pageable.getPageSize(), total);
        return new PageImpl<>(resolved.subList(start, end), pageable, total);
    }

    /** A single version's permanent ordinal number */
    @Transactional(readOnly = true)
    public Integer getVersionNumber(DatasetVersion version) {
        return (int) datasetVersionRepository.countByDatasetIdAndIsStagingNotTrueAndIdLessThanEqual(
                version.getDataset().getId(), version.getId());
    }

    /** Versions from a completed blank import with no job started yet */
    @Transactional(readOnly = true)
    public List<DatasetVersionResponse> getPendingBlankStarts(Long datasetId) {
        return datasetVersionRepository
                .findAllByDatasetIdAndIsStagingNotTrueOrderByCreatedAtDesc(datasetId)
                .stream()
                .map(version -> {
                    DatasetVersionResponse response = attachImportStatus(datasetVersionMapper.toResponse(version));
                    response.setVersionNumber(getVersionNumber(version));
                    return response;
                })
                .filter(response -> "START".equals(response.getResolveRoute()))
                .toList();
    }

    /** Populates complete and unresolvedDirtyCount with one extra query per version */
    private DatasetVersionResponse attachCompleteness(DatasetVersionResponse response) {
        long unresolved = dirtyAnnotationRepository
                .countByOntologyChange_AffectedDatasetVersion_IdAndResolvedAtIsNull(response.getId());
        response.setUnresolvedDirtyCount(unresolved);
        response.setComplete(unresolved == 0);
        return response;
    }

    /** Populates import status from the most recent import job that produced this version */
    private DatasetVersionResponse attachImportStatus(DatasetVersionResponse response) {
        importJobRepository.findTopByCreatedDatasetVersionIdOrderBySubmittedAtDesc(response.getId())
                .ifPresent(job -> {
                    response.setImportStatus(job.getStatus());
                    response.setImportFormat(job.getFormat());
                });
        response.setBlankImportStarted(pointAnnotationJobRepository.existsByDatasetVersion_Id(response.getId()));
        attachResolveStatus(response);
        return response;
    }

    /** Populates resolve status and where to navigate to resolve a blank import version */
    private void attachResolveStatus(DatasetVersionResponse response) {
        var pointJob = pointAnnotationJobRepository.findFirstByDatasetVersion_IdOrderByCreatedAtDesc(response.getId());
        if (pointJob.isPresent() && !"COMPLETE".equals(pointJob.get().getStatus())) {
            response.setResolveStatus("UNRESOLVED");
            response.setResolveRoute("SEED_LABEL");
            response.setResolveJobId(pointJob.get().getId());
            return;
        }

        // A COMPLETE point annotation job may still have a pending delegated review, so fall through
        var annotationJob = annotationJobRepository.findFirstByDatasetVersion_IdAndCommitInPlaceTrueOrderByInitiatedAtDesc(response.getId());
        if (annotationJob.isPresent()) {
            if (annotationJob.get().getAddedToProjectVersionId() == null) {
                response.setResolveStatus("UNRESOLVED");
                response.setResolveRoute("ANNOTATION_REVIEW");
                response.setResolveJobId(annotationJob.get().getId());
            }
            return;
        }

        if ("BLANK".equals(response.getImportFormat()) && "COMPLETED".equals(response.getImportStatus())
                && !response.isBlankImportStarted()) {
            response.setResolveStatus("UNRESOLVED");
            response.setResolveRoute("START");
        }
    }

    /** Logs an event after the transaction commits */
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
