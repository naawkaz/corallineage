package com.corallineage.backend.logging;

import com.corallineage.backend.versioning.dataset.DatasetVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnotationEventServiceTest {

    @Mock
    private AnnotationEventRepository annotationEventRepository;

    @InjectMocks
    private AnnotationEventService annotationEventService;

    @Spy
    private AnnotationEventMapper annotationEventMapper;

    private AnnotationEvent event(EventType type, String performedBy) {
        AnnotationEvent event = new AnnotationEvent();
        event.setId(1L);
        event.setEventType(type);
        event.setPerformedBy(performedBy);
        event.setOccurredAt(Instant.now());
        return event;
    }

    @Test
    void queryEvents_noFilters_delegatesWithNullsAndMapsResults() {
        Page<AnnotationEvent> page = new PageImpl<>(List.of(event(EventType.DATASET_VERSION_CREATED, "researcher_01")));
        when(annotationEventRepository.findByFilters(isNull(), isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(page);

        Page<AnnotationEventResponse> result = annotationEventService.queryEvents(
                null, null, null, null, null, PageRequest.of(0, 50));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getEventType()).isEqualTo(EventType.DATASET_VERSION_CREATED);
        assertThat(result.getContent().get(0).getPerformedBy()).isEqualTo("researcher_01");
    }

    @Test
    void queryEvents_withFilters_passesThemThroughToRepository() {
        Instant from = Instant.now().minusSeconds(3600);
        Instant to = Instant.now();
        Page<AnnotationEvent> page = new PageImpl<>(List.of(event(EventType.HUMAN_CORRECTION, "researcher_01")));
        when(annotationEventRepository.findByFilters(
                eq("HUMAN_CORRECTION"), eq(3L), eq(2L), eq(from), eq(to), any()))
                .thenReturn(page);

        Page<AnnotationEventResponse> result = annotationEventService.queryEvents(
                EventType.HUMAN_CORRECTION, 3L, 2L, from, to, PageRequest.of(0, 50));

        assertThat(result.getContent()).hasSize(1);
        verify(annotationEventRepository).findByFilters(
                eq("HUMAN_CORRECTION"), eq(3L), eq(2L), eq(from), eq(to), any());
    }

    @Test
    void queryEvents_mapsDatasetVersionIdFromNestedEntity() {
        DatasetVersion version = new DatasetVersion();
        version.setId(7L);
        AnnotationEvent evt = event(EventType.DATASET_VERSION_CREATED, "researcher_01");
        evt.setDatasetVersion(version);

        when(annotationEventRepository.findByFilters(isNull(), isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(evt)));

        Page<AnnotationEventResponse> result = annotationEventService.queryEvents(
                null, null, null, null, null, PageRequest.of(0, 50));

        assertThat(result.getContent().get(0).getDatasetVersionId()).isEqualTo(7L);
    }
}
