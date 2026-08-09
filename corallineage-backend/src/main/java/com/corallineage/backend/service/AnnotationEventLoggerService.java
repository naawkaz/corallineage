package com.corallineage.backend.service;

import com.corallineage.backend.entity.AnnotationEvent;
import com.corallineage.backend.entity.DatasetVersion;
import com.corallineage.backend.entity.enums.EventType;
import com.corallineage.backend.repository.AnnotationEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnnotationEventLoggerService {

    private final AnnotationEventRepository annotationEventRepository;

    public AnnotationEventLoggerService(AnnotationEventRepository annotationEventRepository) {
        this.annotationEventRepository = annotationEventRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AnnotationEvent log(EventType eventType, DatasetVersion datasetVersion,
                               String performedBy, String description) {
        AnnotationEvent event = new AnnotationEvent();
        event.setEventType(eventType);
        event.setDatasetVersion(datasetVersion);
        event.setPerformedBy(performedBy);
        event.setDescription(description);
        return annotationEventRepository.save(event);
    }
}
