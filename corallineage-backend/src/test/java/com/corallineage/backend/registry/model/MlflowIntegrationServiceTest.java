package com.corallineage.backend.registry.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MlflowIntegrationServiceTest {

    // Deliberately unused local port - nothing listens here, so every call deterministically
    // exercises the "MLflow unreachable" degradation path without needing a mock HTTP server.
    private final MlflowIntegrationService service = new MlflowIntegrationService("http://localhost:59999");

    @Test
    void getRunDetails_mlflowUnreachable_returnsNull() {
        Map<String, Object> result = service.getRunDetails("any-run-id");

        assertThat(result).isNull();
    }

    @Test
    void getEpochMetrics_mlflowUnreachable_returnsNull() {
        List<MlflowDetailsResponse.EpochMetricPoint> result = service.getEpochMetrics("any-run-id");

        assertThat(result).isNull();
    }

    @Test
    void buildMlflowDetails_mlflowUnreachable_returnsNull() {
        ModelVersion model = new ModelVersion();
        model.setId(1L);
        model.setMlflowRunId("any-run-id");

        MlflowDetailsResponse result = service.buildMlflowDetails(model);

        assertThat(result).isNull();
    }
}
