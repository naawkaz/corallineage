package com.corallineage.backend.reporting;

import java.time.Instant;

public record ReportSummaryResponse(String reportId, Instant fromDate, Instant toDate, Instant generatedAt,
                                     String requestedBy) {
}
