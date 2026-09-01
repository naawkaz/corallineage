package com.corallineage.backend.reporting;

public record ReportSummary(int datasetVersionsCreated, int modelsRegistered, int ontologyChanges,
        int totalAnnotationEvents) {
}
