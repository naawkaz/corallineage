package com.corallineage.backend.reporting;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public class GenerateReportRequest {

    @NotBlank(message = "requestedBy is required")
    private String requestedBy;

    @NotNull(message = "fromDate is required")
    private Instant fromDate;

    @NotNull(message = "toDate is required")
    private Instant toDate;

    private boolean includeEventLog;
    private boolean includeOntologyChanges;
    private boolean includeModelRegistry;
    private boolean includeDatasetVersionHistory;
    private boolean includeAnalytics;
    private boolean includeMlflowDetails;

    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }

    public Instant getFromDate() { return fromDate; }
    public void setFromDate(Instant fromDate) { this.fromDate = fromDate; }

    public Instant getToDate() { return toDate; }
    public void setToDate(Instant toDate) { this.toDate = toDate; }

    public boolean isIncludeEventLog() { return includeEventLog; }
    public void setIncludeEventLog(boolean includeEventLog) { this.includeEventLog = includeEventLog; }

    public boolean isIncludeOntologyChanges() { return includeOntologyChanges; }
    public void setIncludeOntologyChanges(boolean includeOntologyChanges) { this.includeOntologyChanges = includeOntologyChanges; }

    public boolean isIncludeModelRegistry() { return includeModelRegistry; }
    public void setIncludeModelRegistry(boolean includeModelRegistry) { this.includeModelRegistry = includeModelRegistry; }

    public boolean isIncludeDatasetVersionHistory() { return includeDatasetVersionHistory; }
    public void setIncludeDatasetVersionHistory(boolean includeDatasetVersionHistory) { this.includeDatasetVersionHistory = includeDatasetVersionHistory; }

    public boolean isIncludeAnalytics() { return includeAnalytics; }
    public void setIncludeAnalytics(boolean includeAnalytics) { this.includeAnalytics = includeAnalytics; }

    public boolean isIncludeMlflowDetails() { return includeMlflowDetails; }
    public void setIncludeMlflowDetails(boolean includeMlflowDetails) { this.includeMlflowDetails = includeMlflowDetails; }
}
