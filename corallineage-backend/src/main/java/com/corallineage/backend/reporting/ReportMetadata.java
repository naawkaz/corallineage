package com.corallineage.backend.reporting;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "report_metadata", indexes = {
        @Index(name = "idx_report_metadata_report_id", columnList = "report_id", unique = true),
        @Index(name = "idx_report_metadata_generated_at", columnList = "generated_at")
})
public class ReportMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", nullable = false, unique = true, length = 50)
    private String reportId;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    @Column(name = "requested_by", nullable = false, length = 100)
    private String requestedBy;

    @Column(name = "from_date", nullable = false)
    private Instant fromDate;

    @Column(name = "to_date", nullable = false)
    private Instant toDate;

    @Column(name = "include_event_log", nullable = false)
    private boolean includeEventLog;

    @Column(name = "include_ontology_changes", nullable = false)
    private boolean includeOntologyChanges;

    @Column(name = "include_model_registry", nullable = false)
    private boolean includeModelRegistry;

    @Column(name = "include_dataset_version_history", nullable = false)
    private boolean includeDatasetVersionHistory;

    @Column(name = "include_analytics", nullable = false)
    private boolean includeAnalytics;

    @Column(name = "include_mlflow_details", nullable = false)
    private boolean includeMlflowDetails;

    @PrePersist
    protected void onCreate() {
        if (generatedAt == null)
            generatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public Instant getFromDate() {
        return fromDate;
    }

    public void setFromDate(Instant fromDate) {
        this.fromDate = fromDate;
    }

    public Instant getToDate() {
        return toDate;
    }

    public void setToDate(Instant toDate) {
        this.toDate = toDate;
    }

    public boolean isIncludeEventLog() {
        return includeEventLog;
    }

    public void setIncludeEventLog(boolean includeEventLog) {
        this.includeEventLog = includeEventLog;
    }

    public boolean isIncludeOntologyChanges() {
        return includeOntologyChanges;
    }

    public void setIncludeOntologyChanges(boolean includeOntologyChanges) {
        this.includeOntologyChanges = includeOntologyChanges;
    }

    public boolean isIncludeModelRegistry() {
        return includeModelRegistry;
    }

    public void setIncludeModelRegistry(boolean includeModelRegistry) {
        this.includeModelRegistry = includeModelRegistry;
    }

    public boolean isIncludeDatasetVersionHistory() {
        return includeDatasetVersionHistory;
    }

    public void setIncludeDatasetVersionHistory(boolean includeDatasetVersionHistory) {
        this.includeDatasetVersionHistory = includeDatasetVersionHistory;
    }

    public boolean isIncludeAnalytics() {
        return includeAnalytics;
    }

    public void setIncludeAnalytics(boolean includeAnalytics) {
        this.includeAnalytics = includeAnalytics;
    }

    public boolean isIncludeMlflowDetails() {
        return includeMlflowDetails;
    }

    public void setIncludeMlflowDetails(boolean includeMlflowDetails) {
        this.includeMlflowDetails = includeMlflowDetails;
    }
}
