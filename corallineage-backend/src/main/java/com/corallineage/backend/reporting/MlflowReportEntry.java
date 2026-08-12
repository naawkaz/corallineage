package com.corallineage.backend.reporting;

import com.corallineage.backend.registry.model.MlflowDetailsResponse;

import java.util.List;

public record MlflowReportEntry(String modelTag, String mlflowRunId, MlflowDetailsResponse.Environment environment,
                                 List<MlflowDetailsResponse.EpochMetricPoint> epochMetrics,
                                 String confusionMatrixUrl) {
}
