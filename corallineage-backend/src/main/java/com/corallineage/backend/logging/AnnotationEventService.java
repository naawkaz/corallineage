package com.corallineage.backend.logging;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AnnotationEventService {

    private final AnnotationEventRepository annotationEventRepository;
    private final AnnotationEventMapper annotationEventMapper;

    public AnnotationEventService(AnnotationEventRepository annotationEventRepository,
                                  AnnotationEventMapper annotationEventMapper) {
        this.annotationEventRepository = annotationEventRepository;
        this.annotationEventMapper = annotationEventMapper;
    }

    @Transactional(readOnly = true)
    public Page<AnnotationEventResponse> queryEvents(EventType eventType, Long datasetVersionId,
                                                      Long modelVersionId, Instant from, Instant to,
                                                      Pageable pageable) {
        String eventTypeName = eventType != null ? eventType.name() : null;
        return annotationEventRepository
                .findByFilters(eventTypeName, datasetVersionId, modelVersionId, from, to, pageable)
                .map(annotationEventMapper::toResponse);
    }
}
