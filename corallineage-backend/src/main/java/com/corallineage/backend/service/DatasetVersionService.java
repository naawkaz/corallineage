package com.corallineage.backend.service;

import com.corallineage.backend.dto.CreateVersionRequest;
import com.corallineage.backend.dto.DatasetVersionResponse;
import com.corallineage.backend.entity.Dataset;
import com.corallineage.backend.entity.DatasetVersion;
import com.corallineage.backend.entity.LabelDefinition;
import com.corallineage.backend.entity.enums.EventType;
import com.corallineage.backend.exception.ConflictException;
import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.mapper.DatasetVersionMapper;
import com.corallineage.backend.repository.DatasetRepository;
import com.corallineage.backend.repository.DatasetVersionRepository;
import com.corallineage.backend.repository.ImageRepository;
import com.corallineage.backend.repository.LabelDefinitionRepository;
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
    private final ImageRepository imageRepository;
    private final DvcIntegrationService dvcIntegrationService;
    private final AnnotationEventLoggerService annotationEventLoggerService;
    private final DatasetVersionMapper datasetVersionMapper;

    public DatasetVersionService(DatasetRepository datasetRepository,
                                 DatasetVersionRepository datasetVersionRepository,
                                 LabelDefinitionRepository labelDefinitionRepository,
                                 ImageRepository imageRepository,
                                 DvcIntegrationService dvcIntegrationService,
                                 AnnotationEventLoggerService annotationEventLoggerService,
                                 DatasetVersionMapper datasetVersionMapper) {
        this.datasetRepository = datasetRepository;
        this.datasetVersionRepository = datasetVersionRepository;
        this.labelDefinitionRepository = labelDefinitionRepository;
        this.imageRepository = imageRepository;
        this.dvcIntegrationService = dvcIntegrationService;
        this.annotationEventLoggerService = annotationEventLoggerService;
        this.datasetVersionMapper = datasetVersionMapper;
    }

    @Transactional
    public DatasetVersionResponse createSnapshot(Long datasetId, CreateVersionRequest request) {
        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new NotFoundException("Dataset not found: " + datasetId));

        if (datasetVersionRepository.findByDatasetIdAndVersionTag(datasetId, request.getVersionTag()).isPresent()) {
            throw new ConflictException("Dataset version tag already exists in this dataset: " + request.getVersionTag());
        }

        long imageCount = imageRepository.countByDatasetVersionDatasetId(datasetId);
        DatasetVersion previousLatest = datasetVersionRepository.findTopByDatasetIdOrderByCreatedAtDesc(datasetId).orElse(null);
        String dvcCommitHash = dvcIntegrationService.commitCurrentState(request.getVersionTag());

        DatasetVersion version = new DatasetVersion();
        version.setDataset(dataset);
        version.setVersionTag(request.getVersionTag());
        version.setCreatedBy(request.getCreatedBy());
        version.setTriggerReason(request.getTriggerReason().name());
        version.setImageCount((int) imageCount);
        version.setAnnotationCount(0);
        version.setDvcCommitHash(dvcCommitHash);
        version.setNotes(request.getNotes());
        DatasetVersion savedVersion = datasetVersionRepository.save(version);

        List<LabelDefinition> labels = snapshotCurrentLabelDefinitions(savedVersion, previousLatest);

        logAfterCommit(EventType.DATASET_VERSION_CREATED, savedVersion,
                request.getCreatedBy(), "Created dataset version " + request.getVersionTag());

        return datasetVersionMapper.toResponseWithLabels(savedVersion, labels);
    }

    @Transactional(readOnly = true)
    public DatasetVersionResponse getVersionById(Long datasetId, Long id) {
        DatasetVersion version = datasetVersionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Dataset version not found: " + id));
        if (!version.getDataset().getId().equals(datasetId)) {
            throw new NotFoundException("Dataset version not found: " + id);
        }
        List<LabelDefinition> labels = labelDefinitionRepository.findByDatasetVersionId(id);
        return datasetVersionMapper.toResponseWithLabels(version, labels);
    }

    @Transactional(readOnly = true)
    public Page<DatasetVersionResponse> getVersionHistory(Long datasetId, Pageable pageable) {
        if (!datasetRepository.existsById(datasetId)) {
            throw new NotFoundException("Dataset not found: " + datasetId);
        }
        return datasetVersionRepository.findAllByDatasetIdOrderByCreatedAtDesc(datasetId, pageable)
                .map(datasetVersionMapper::toResponse);
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
