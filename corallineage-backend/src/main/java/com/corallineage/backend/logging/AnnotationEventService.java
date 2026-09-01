package com.corallineage.backend.logging;

import com.corallineage.backend.registry.model.ModelVersion;
import com.corallineage.backend.registry.model.ModelVersionRepository;
import com.corallineage.backend.versioning.dataset.DatasetVersion;
import com.corallineage.backend.versioning.dataset.DatasetVersionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

@Service
public class AnnotationEventService {

    private final AnnotationEventRepository annotationEventRepository;
    private final AnnotationEventMapper annotationEventMapper;
    private final DatasetVersionRepository datasetVersionRepository;
    private final ModelVersionRepository modelVersionRepository;

    public AnnotationEventService(AnnotationEventRepository annotationEventRepository,
            AnnotationEventMapper annotationEventMapper,
            DatasetVersionRepository datasetVersionRepository,
            ModelVersionRepository modelVersionRepository) {
        this.annotationEventRepository = annotationEventRepository;
        this.annotationEventMapper = annotationEventMapper;
        this.datasetVersionRepository = datasetVersionRepository;
        this.modelVersionRepository = modelVersionRepository;
    }

    /**
     * Resolves a single dataset id from the given scoping filter, or null if none
     * was given
     */
    @Transactional(readOnly = true)
    public Long resolveScopedDatasetId(Long datasetVersionId, Long modelVersionId, Long datasetId) {
        if (datasetId != null) {
            return datasetId;
        }
        if (datasetVersionId != null) {
            DatasetVersion version = datasetVersionRepository.findById(datasetVersionId)
                    .orElseThrow(() -> new com.corallineage.backend.exception.NotFoundException(
                            "Dataset version not found: " + datasetVersionId));
            return version.getDataset().getId();
        }
        if (modelVersionId != null) {
            ModelVersion model = modelVersionRepository.findById(modelVersionId)
                    .orElseThrow(() -> new com.corallineage.backend.exception.NotFoundException(
                            "Model version not found: " + modelVersionId));
            return model.getTrainedOnDatasetVersion().getDataset().getId();
        }
        return null;
    }

    @Transactional(readOnly = true)
    public Page<AnnotationEventResponse> queryEvents(EventType eventType, Long datasetVersionId,
            Long modelVersionId, Long datasetId, Instant from, Instant to,
            Pageable pageable) {
        String eventTypeName = eventType != null ? eventType.name() : null;
        return annotationEventRepository
                .findByFilters(eventTypeName, datasetVersionId, modelVersionId, datasetId, from, to, pageable)
                .map(annotationEventMapper::toResponse);
    }

    /** Filters events to the requester's accessible datasets */
    @Transactional(readOnly = true)
    public Page<AnnotationEventResponse> queryEventsAccessibleWithNoDatasetFilter(EventType eventType, Instant from,
            Instant to,
            Set<Long> accessibleDatasetIds, Pageable pageable) {
        String eventTypeName = eventType != null ? eventType.name() : null;
        return annotationEventRepository
                .findAccessibleWithNoDatasetFilter(eventTypeName, from, to, accessibleDatasetIds, pageable)
                .map(annotationEventMapper::toResponse);
    }
}
