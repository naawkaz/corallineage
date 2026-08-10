package com.corallineage.backend.logging;

import org.springframework.stereotype.Component;

@Component
public class AnnotationEventMapper {

    public AnnotationEventResponse toResponse(AnnotationEvent event) {
        AnnotationEventResponse response = new AnnotationEventResponse();
        response.setId(event.getId());
        response.setEventType(event.getEventType());
        response.setDatasetVersionId(event.getDatasetVersion() != null ? event.getDatasetVersion().getId() : null);
        response.setModelVersionId(event.getModelVersion() != null ? event.getModelVersion().getId() : null);
        response.setImageId(event.getImage() != null ? event.getImage().getId() : null);
        response.setPerformedBy(event.getPerformedBy());
        response.setOccurredAt(event.getOccurredAt());
        response.setDescription(event.getDescription());
        response.setMetadata(event.getMetadata());
        return response;
    }
}
