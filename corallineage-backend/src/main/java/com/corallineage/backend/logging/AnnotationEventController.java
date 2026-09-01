package com.corallineage.backend.logging;

import com.corallineage.backend.datasets.DatasetService;
import com.corallineage.backend.projects.ProjectMemberService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/events")
public class AnnotationEventController {

    private final AnnotationEventService annotationEventService;
    private final DatasetService datasetService;
    private final ProjectMemberService projectMemberService;

    public AnnotationEventController(AnnotationEventService annotationEventService,
                                      DatasetService datasetService,
                                      ProjectMemberService projectMemberService) {
        this.annotationEventService = annotationEventService;
        this.datasetService = datasetService;
        this.projectMemberService = projectMemberService;
    }

    @GetMapping
    public Page<AnnotationEventResponse> getEvents(
            @RequestParam(required = false) EventType eventType,
            @RequestParam(required = false) Long datasetVersionId,
            @RequestParam(required = false) Long modelVersionId,
            @RequestParam(required = false) Long datasetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        // Sort field is the native SQL column name (occurred_at), not the entity
        // property name - findByFilters is a native query, so Spring Data appends
        // the ORDER BY clause using this string verbatim rather than translating it.
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurred_at"));

        Long scopedDatasetId = annotationEventService.resolveScopedDatasetId(datasetVersionId, modelVersionId, datasetId);
        if (scopedDatasetId != null) {
            datasetService.requireAccess(scopedDatasetId, authentication.getName());
            return annotationEventService.queryEvents(eventType, datasetVersionId, modelVersionId, datasetId, from, to, pageable);
        }

        // No dataset-scoping filter: filter server-side to the requester's accessible datasets
        // rather than blocking the call (this is the Global Event Log's "no filter" default view).
        // eventType/from/to still apply normally.
        Set<Long> accessibleDatasetIds = projectMemberService.getAllAccessibleDatasetIds(authentication.getName());
        return annotationEventService.queryEventsAccessibleWithNoDatasetFilter(eventType, from, to, accessibleDatasetIds, pageable);
    }
}
