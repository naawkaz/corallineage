package com.corallineage.backend.reporting;

import com.corallineage.backend.logging.AnnotationEventResponse;
import com.corallineage.backend.ontology.OntologyChangeResponse;
import com.corallineage.backend.registry.model.ModelVersionResponse;
import com.corallineage.backend.versioning.dataset.DatasetVersionResponse;

import java.time.Instant;
import java.util.List;

public record ReproducibilityReportResponse(String reportId, Instant generatedAt, String requestedBy,
                                             ReportPeriod period, ReportSummary summary,
                                             List<DatasetVersionResponse> datasetVersionHistory,
                                             List<ModelVersionResponse> modelRegistry,
                                             List<OntologyChangeResponse> ontologyChanges,
                                             List<AnnotationEventResponse> eventLog,
                                             ReportAnalytics analytics) {
}
