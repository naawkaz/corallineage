package com.corallineage.backend.reporting;

import java.time.Instant;

public record ReportPeriod(Instant from, Instant to) {
}
