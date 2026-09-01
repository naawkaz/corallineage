package com.corallineage.backend.logging;

import com.corallineage.backend.registry.model.ModelVersion;
import com.corallineage.backend.versioning.dataset.DatasetVersion;
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
        return log(eventType, datasetVersion, null, performedBy, description);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AnnotationEvent log(EventType eventType, DatasetVersion datasetVersion, ModelVersion modelVersion,
            String performedBy, String description) {
        AnnotationEvent event = new AnnotationEvent();
        event.setEventType(eventType);
        event.setDatasetVersion(datasetVersion);
        event.setModelVersion(modelVersion);
        event.setPerformedBy(performedBy);
        event.setDescription(description);
        return annotationEventRepository.save(event);
    }
}
