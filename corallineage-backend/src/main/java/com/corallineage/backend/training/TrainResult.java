package com.corallineage.backend.training;

import java.math.BigDecimal;
import java.util.Map;

public record TrainResult(String mlflowRunId, String weightsPath, BigDecimal accuracy,
        Object performanceMetrics, Map<String, String> classMappings) {
}
