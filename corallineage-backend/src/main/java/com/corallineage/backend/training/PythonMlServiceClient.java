package com.corallineage.backend.training;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** HTTP client for the Python ML service's /architectures, /train and /annotate endpoints.
 *
 * /train and /annotate are fire-and-forget kickoffs now: the Flask side runs the actual work
 * in a background thread and returns immediately (202 STARTED), so this client also exposes
 * poll methods that read the job's current status from Flask's in-memory status dicts.
 */
@Component
public class PythonMlServiceClient {

    private record ArchitecturesResponse(List<ArchitectureInfo> architectures) {
    }

    private record StartedResponse(String status, String jobId) {
    }

    /** Mirrors the Flask GET /train/{jobId}/status response body. */
    public record TrainingStatus(String status, String mlflowRunId, TrainResult result, String error) {
    }

    /**
     * Mirrors the Flask GET /annotate/{jobId}/status response body. currentPointId maps from
     * Flask's "currentImageId" key, which despite the name actually holds the GeneratedPoint id
     * being processed (see PointInferenceService's request-assembly comment for that naming).
     */
    public record AnnotateStatus(String status, Integer processedCount, Integer totalCount,
                                  @JsonProperty("currentImageId") Long currentPointId,
                                  AnnotateResult result, String error) {
    }

    private final RestClient restClient;

    public PythonMlServiceClient(@Value("${app.ml-service.url}") String mlServiceUrl) {
        this.restClient = RestClient.builder().baseUrl(mlServiceUrl).build();
    }

    public List<ArchitectureInfo> fetchArchitectures() {
        try {
            ArchitecturesResponse response = restClient.get()
                    .uri("/architectures")
                    .retrieve()
                    .body(ArchitecturesResponse.class);
            return response != null ? response.architectures() : List.of();
        } catch (ResourceAccessException ex) {
            throw new MlServiceException("Python ML service is unreachable: " + ex.getMessage(), ex);
        } catch (RestClientResponseException ex) {
            throw new MlServiceException("Python ML service /architectures failed: " + ex.getResponseBodyAsString(), ex);
        }
    }

    /** Kicks off training on the Python side and returns as soon as it acknowledges the job started. */
    public void startTraining(String architecture, String datasetPath, Map<String, Object> hyperparameters, Long jobId) {
        try {
            restClient.post()
                    .uri("/train")
                    .body(Map.of(
                            "architecture", architecture,
                            "datasetPath", datasetPath,
                            "hyperparameters", hyperparameters,
                            "jobId", String.valueOf(jobId)))
                    .retrieve()
                    .body(StartedResponse.class);
        } catch (ResourceAccessException ex) {
            throw new MlServiceException("Python ML service is unreachable: " + ex.getMessage(), ex);
        } catch (RestClientResponseException ex) {
            throw new MlServiceException("Starting training failed: " + ex.getResponseBodyAsString(), ex);
        }
    }

    /**
     * Polls the Python service's in-memory training job status. Returns a status of "UNKNOWN"
     * rather than throwing when the poll fails or the job isn't tracked there yet, a single
     * flaky poll must never interrupt the caller's polling loop or fail the training job itself.
     */
    public TrainingStatus pollTrainingStatus(Long jobId) {
        try {
            TrainingStatus status = restClient.get()
                    .uri("/train/{jobId}/status", jobId)
                    .retrieve()
                    .body(TrainingStatus.class);
            return status != null ? status : new TrainingStatus("UNKNOWN", null, null, null);
        } catch (ResourceAccessException | RestClientResponseException ex) {
            return new TrainingStatus("UNKNOWN", null, null, null);
        }
    }

    /** Kicks off annotation on the Python side and returns as soon as it acknowledges the job started. */
    public void startAnnotate(String architecture, String weightsPath, Map<String, String> classMappings,
                               List<AnnotateImageInput> images, Long jobId) {
        try {
            restClient.post()
                    .uri("/annotate")
                    .body(Map.of(
                            "architecture", architecture,
                            "weightsPath", weightsPath,
                            "classMappings", classMappings,
                            "images", images,
                            "jobId", String.valueOf(jobId)))
                    .retrieve()
                    .body(StartedResponse.class);
        } catch (ResourceAccessException ex) {
            throw new MlServiceException("Python ML service is unreachable: " + ex.getMessage(), ex);
        } catch (RestClientResponseException ex) {
            throw new MlServiceException("Starting inference failed: " + ex.getResponseBodyAsString(), ex);
        }
    }

    /**
     * Polls the Python service's in-memory annotate job status. Returns a status of "UNKNOWN"
     * rather than throwing when the poll fails, for the same reason as pollTrainingStatus.
     */
    public AnnotateStatus pollAnnotateStatus(Long jobId) {
        try {
            AnnotateStatus status = restClient.get()
                    .uri("/annotate/{jobId}/status", jobId)
                    .retrieve()
                    .body(AnnotateStatus.class);
            return status != null ? status : new AnnotateStatus("UNKNOWN", null, null, null, null, null);
        } catch (ResourceAccessException | RestClientResponseException ex) {
            return new AnnotateStatus("UNKNOWN", null, null, null, null, null);
        }
    }

    /**
     * Legacy synchronous wrapper kept for AnnotationJobExecutor (a separate, already-@Async
     * annotation job system unrelated to point-annotation, with three call sites and its own
     * test suite built around this exact blocking signature). Flask's /annotate route itself is
     * async now (202 immediately, batch completes on a background thread), so this method starts
     * the batch under a synthesized job id namespaced away from real point-annotation/training job
     * ids, then polls GET /annotate/{jobId}/status until the batch reaches a terminal state.
     * Blocking here is safe: every caller of this method already runs on its own @Async background
     * thread, never on an HTTP request thread, so it cannot tie up Flask's (or Spring's) request
     * handling the way the pre-fix design used to.
     */
    public AnnotateResult annotate(String architecture, String weightsPath, Map<String, String> classMappings,
                                    List<AnnotateImageInput> images) {
        String legacyJobId = "legacy-" + UUID.randomUUID();
        try {
            restClient.post()
                    .uri("/annotate")
                    .body(Map.of(
                            "architecture", architecture,
                            "weightsPath", weightsPath,
                            "classMappings", classMappings,
                            "images", images,
                            "jobId", legacyJobId))
                    .retrieve()
                    .body(StartedResponse.class);
        } catch (ResourceAccessException ex) {
            throw new MlServiceException("Python ML service is unreachable: " + ex.getMessage(), ex);
        } catch (RestClientResponseException ex) {
            throw new MlServiceException("Inference failed: " + ex.getResponseBodyAsString(), ex);
        }

        while (true) {
            AnnotateStatus status;
            try {
                status = restClient.get()
                        .uri("/annotate/{jobId}/status", legacyJobId)
                        .retrieve()
                        .body(AnnotateStatus.class);
            } catch (ResourceAccessException | RestClientResponseException ex) {
                throw new MlServiceException("Python ML service is unreachable while polling inference status: " + ex.getMessage(), ex);
            }
            if (status == null) {
                throw new MlServiceException("Python ML service returned no status for inference job " + legacyJobId);
            }
            if ("COMPLETED".equals(status.status())) {
                return status.result();
            }
            if ("FAILED".equals(status.status())) {
                throw new MlServiceException("Inference failed: " + status.error());
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new MlServiceException("Interrupted while waiting for inference result", ex);
            }
        }
    }
}
