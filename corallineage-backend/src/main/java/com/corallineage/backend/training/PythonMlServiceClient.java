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

/** HTTP client for the ML service's train annotate and architecture endpoints */
@Component
public class PythonMlServiceClient {

    private record ArchitecturesResponse(List<ArchitectureInfo> architectures) {
    }

    private record StartedResponse(String status, String jobId) {
    }

    /** Mirrors the training status response */
    public record TrainingStatus(String status, String mlflowRunId, TrainResult result, String error) {
    }

    /** Mirrors the annotate status response */
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

    /** Starts training and returns once acknowledged */
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

    /** Polls training status, returns unknown on a failed poll */
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

    /** Starts annotation and returns once acknowledged */
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

    /** Polls annotate status, returns unknown on a failed poll */
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

    /** Synchronous wrapper over the annotate route */
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
