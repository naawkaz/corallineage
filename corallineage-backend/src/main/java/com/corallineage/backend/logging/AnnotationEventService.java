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
     * Resolves a single dataset id to run the membership check against, from whichever scoping
     * filter the caller supplied (datasetId directly, or derived from datasetVersionId/modelVersionId).
     * Returns null when none of the three scoping filters were supplied, signalling the caller
     * should fall back to the accessible-set filtering path instead of a single-dataset check.
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

    /**
     * Used when the caller supplied no scoping filter at all: rather than returning every event
     * across every project to any authenticated user, results are filtered server-side to rows
     * whose derivable dataset id is in the requester's accessible set (see
     * AnnotationEventRepository.findAccessibleWithNoFilter for exactly how that's derived, and
     * how rows with no derivable dataset id at all are handled).
     */
    @Transactional(readOnly = true)
    public Page<AnnotationEventResponse> queryEventsAccessibleWithNoDatasetFilter(EventType eventType, Instant from, Instant to,
                                                                                   Set<Long> accessibleDatasetIds, Pageable pageable) {
        String eventTypeName = eventType != null ? eventType.name() : null;
        return annotationEventRepository
                .findAccessibleWithNoDatasetFilter(eventTypeName, from, to, accessibleDatasetIds, pageable)
                .map(annotationEventMapper::toResponse);
    }
}
