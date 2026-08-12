package com.corallineage.backend.training;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

/** HTTP client for the Python ML service's /architectures and /train endpoints. */
@Component
public class PythonMlServiceClient {

    private record ArchitecturesResponse(List<ArchitectureInfo> architectures) {
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

    public TrainResult train(String architecture, String datasetPath, Map<String, Object> hyperparameters) {
        try {
            return restClient.post()
                    .uri("/train")
                    .body(Map.of(
                            "architecture", architecture,
                            "datasetPath", datasetPath,
                            "hyperparameters", hyperparameters))
                    .retrieve()
                    .body(TrainResult.class);
        } catch (ResourceAccessException ex) {
            throw new MlServiceException("Python ML service is unreachable: " + ex.getMessage(), ex);
        } catch (RestClientResponseException ex) {
            throw new MlServiceException("Training failed: " + ex.getResponseBodyAsString(), ex);
        }
    }

    public AnnotateResult annotate(String architecture, String weightsPath, Map<String, String> classMappings,
                                    List<AnnotateImageInput> images) {
        try {
            return restClient.post()
                    .uri("/annotate")
                    .body(Map.of(
                            "architecture", architecture,
                            "weightsPath", weightsPath,
                            "classMappings", classMappings,
                            "images", images))
                    .retrieve()
                    .body(AnnotateResult.class);
        } catch (ResourceAccessException ex) {
            throw new MlServiceException("Python ML service is unreachable: " + ex.getMessage(), ex);
        } catch (RestClientResponseException ex) {
            throw new MlServiceException("Inference failed: " + ex.getResponseBodyAsString(), ex);
        }
    }
}
