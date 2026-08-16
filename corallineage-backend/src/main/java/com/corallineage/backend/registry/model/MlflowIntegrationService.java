package com.corallineage.backend.registry.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Full passthrough layer to the real MLflow tracking server's REST API — so the React frontend
 * never has to call MLflow directly. Every method degrades to null (caller decides 404 vs 503)
 * rather than propagating a connectivity failure, per the checklist's explicit requirement that
 * every MLflow-touching flow degrade gracefully.
 */
@Service
public class MlflowIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(MlflowIntegrationService.class);
    // train_loss/val_loss/val_accuracy are logged by the real held-out validation split added to
    // the training loop; loss/accuracy are the older pre-validation-split keys, kept here so runs
    // trained before that change still resolve a metric history (single-line fallback in
    // getEpochMetrics / the frontend chart) instead of an empty series.
    private static final List<String> TRACKED_METRIC_KEYS =
            List.of("train_loss", "val_loss", "val_accuracy", "loss", "accuracy");

    private final RestClient restClient;
    private final String mlflowUrl;

    public MlflowIntegrationService(@Value("${app.mlflow.url}") String mlflowUrl) {
        this.mlflowUrl = mlflowUrl;
        this.restClient = RestClient.builder().baseUrl(mlflowUrl).build();
    }

    /** Returns the raw MLflow run payload (info + data.params + data.metrics), or null if unreachable. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getRunDetails(String mlflowRunId) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/2.0/mlflow/runs/get").queryParam("run_id", mlflowRunId).build())
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException ex) {
            log.warn("MLflow unreachable while fetching run details for run {}: {}", mlflowRunId, ex.getMessage());
            return null;
        }
    }

    /** Merges the per-step history of every tracked metric into one ordered epoch series, or null if unreachable. */
    public List<MlflowDetailsResponse.EpochMetricPoint> getEpochMetrics(String mlflowRunId) {
        try {
            Map<String, Map<Integer, Double>> historyByKey = new LinkedHashMap<>();
            Set<Integer> steps = new TreeSet<>();
            for (String key : TRACKED_METRIC_KEYS) {
                Map<Integer, Double> history;
                try {
                    history = fetchMetricHistory(mlflowRunId, key);
                } catch (RestClientResponseException ex) {
                    // MLflow was reached but returned an HTTP error for this specific key, e.g. a
                    // key this run never logged (train_loss/val_loss/val_accuracy on a model
                    // trained before the validation split was added). Skip only this key rather
                    // than failing the whole call. A connectivity failure (ResourceAccessException,
                    // a RestClientException but not this subtype) is intentionally NOT caught here
                    // so it still propagates to the outer catch and the whole method returns null.
                    log.debug("Metric key {} not available for run {}: {}", key, mlflowRunId, ex.getMessage());
                    history = Map.of();
                }
                historyByKey.put(key, history);
                steps.addAll(history.keySet());
            }

            List<MlflowDetailsResponse.EpochMetricPoint> points = new ArrayList<>();
            for (Integer step : steps) {
                points.add(new MlflowDetailsResponse.EpochMetricPoint(
                        step,
                        historyByKey.get("train_loss").get(step),
                        historyByKey.get("val_loss").get(step),
                        historyByKey.get("val_accuracy").get(step),
                        historyByKey.get("loss").get(step),
                        historyByKey.get("accuracy").get(step)));
            }
            return points;
        } catch (RestClientException ex) {
            log.warn("MLflow unreachable while fetching epoch metrics for run {}: {}", mlflowRunId, ex.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, Double> fetchMetricHistory(String mlflowRunId, String metricKey) {
        Map<String, Object> response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/2.0/mlflow/metrics/get-history")
                        .queryParam("run_id", mlflowRunId).queryParam("metric_key", metricKey).build())
                .retrieve()
                .body(Map.class);
        Map<Integer, Double> bySteps = new LinkedHashMap<>();
        if (response == null) {
            return bySteps;
        }
        List<Map<String, Object>> metrics = (List<Map<String, Object>>) response.getOrDefault("metrics", List.of());
        for (Map<String, Object> entry : metrics) {
            int step = ((Number) entry.getOrDefault("step", 0)).intValue();
            double value = ((Number) entry.get("value")).doubleValue();
            bySteps.put(step, value);
        }
        return bySteps;
    }

    /** Assembles the full mlflow-details response for an already-resolved model. Returns null if MLflow is unreachable. */
    @SuppressWarnings("unchecked")
    public MlflowDetailsResponse buildMlflowDetails(ModelVersion model) {
        String runId = model.getMlflowRunId();
        Map<String, Object> runDetails = getRunDetails(runId);
        if (runDetails == null) {
            return null;
        }

        Map<String, Object> run = (Map<String, Object>) runDetails.getOrDefault("run", Map.of());
        Map<String, Object> info = (Map<String, Object>) run.getOrDefault("info", Map.of());
        Map<String, Object> data = (Map<String, Object>) run.getOrDefault("data", Map.of());
        List<Map<String, Object>> params = (List<Map<String, Object>>) data.getOrDefault("params", List.of());
        Map<String, String> paramsByKey = new LinkedHashMap<>();
        for (Map<String, Object> param : params) {
            paramsByKey.put((String) param.get("key"), (String) param.get("value"));
        }

        Long durationSeconds = null;
        Object startTime = info.get("start_time");
        Object endTime = info.get("end_time");
        if (startTime instanceof Number start && endTime instanceof Number end) {
            durationSeconds = (end.longValue() - start.longValue()) / 1000;
        }
        MlflowDetailsResponse.Environment environment = new MlflowDetailsResponse.Environment(
                paramsByKey.get("pythonVersion"), paramsByKey.get("pytorchVersion"),
                paramsByKey.get("cudaVersion"), paramsByKey.get("randomSeed"), durationSeconds);

        List<MlflowDetailsResponse.EpochMetricPoint> epochMetrics = getEpochMetrics(runId);

        String experimentId = (String) info.get("experiment_id");
        // Note: byte-serving lives at MLflow's own /get-artifact route, not under /api/2.0/mlflow/
        // (that versioned REST API is metadata-only) — confirmed via a live call during this
        // session's verification pass; the /api/2.0/... path 404s even though the artifact exists.
        MlflowDetailsResponse.ArtifactUrls artifactUrls = new MlflowDetailsResponse.ArtifactUrls(
                mlflowUrl + "/get-artifact?path=confusion_matrix.png&run_id=" + runId, null);

        MlflowDetailsResponse response = new MlflowDetailsResponse();
        response.setModelId(model.getId());
        response.setMlflowRunId(runId);
        response.setEnvironment(environment);
        response.setEpochMetrics(epochMetrics != null ? epochMetrics : List.of());
        response.setArtifactUrls(artifactUrls);
        response.setMlflowUiUrl(mlflowUrl + "/#/experiments/" + experimentId + "/runs/" + runId);
        return response;
    }
}
