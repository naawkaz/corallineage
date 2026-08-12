package com.corallineage.backend.reporting;

import java.util.List;
import java.util.Map;

public record ReportAnalytics(List<ClassDistributionItem> classDistribution, Map<String, Long> annotationSourceSplit,
                               List<PerClassMetric> perClassMetrics, List<MlflowReportEntry> mlflowDetails) {
}
