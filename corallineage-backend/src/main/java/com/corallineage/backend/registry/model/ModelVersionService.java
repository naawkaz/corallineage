package com.corallineage.backend.registry.model;

import com.corallineage.backend.training.TrainingJob;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Minimal slice of the Model Registry (Feature 7): just enough for TrainingJobService to
 * auto-register a completed run. No controller/GET endpoints/UI yet — those are a separate pass.
 */
@Service
public class ModelVersionService {

    private final ModelVersionRepository modelVersionRepository;

    public ModelVersionService(ModelVersionRepository modelVersionRepository) {
        this.modelVersionRepository = modelVersionRepository;
    }

    @Transactional
    public ModelVersion registerFromTrainingResult(TrainingJob job, String mlflowRunId, String weightsPath,
                                                    BigDecimal accuracy, String performanceMetricsJson,
                                                    String classMappingsJson) {
        ModelVersion model = new ModelVersion();
        model.setModelTag("model-v" + (modelVersionRepository.count() + 1));
        model.setTrainedOnDatasetVersion(job.getDatasetVersion());
        model.setTrainedAt(Instant.now());
        model.setTrainedBy(job.getInitiatedBy());
        model.setArchitecture(job.getArchitecture());
        model.setHyperparameters(job.getHyperparameters());
        model.setClassMappings(classMappingsJson);
        model.setAccuracy(accuracy);
        model.setPerformanceMetrics(performanceMetricsJson);
        model.setMlflowRunId(mlflowRunId);
        model.setWeightsPath(weightsPath);
        model.setStatus("ACTIVE");
        return modelVersionRepository.save(model);
    }
}
