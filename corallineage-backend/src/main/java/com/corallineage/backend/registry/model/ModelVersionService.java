package com.corallineage.backend.registry.model;

import com.corallineage.backend.exception.InvalidRequestException;
import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.exception.ServiceUnavailableException;
import com.corallineage.backend.logging.AnnotationEventLoggerService;
import com.corallineage.backend.logging.EventType;
import com.corallineage.backend.training.TrainingJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

@Service
public class ModelVersionService {

    private static final Set<String> VALID_STATUSES = Set.of("ACTIVE", "ARCHIVED", "DEPRECATED");

    private final ModelVersionRepository modelVersionRepository;
    private final ModelVersionMapper modelVersionMapper;
    private final MlflowIntegrationService mlflowIntegrationService;
    private final AnnotationEventLoggerService annotationEventLoggerService;

    public ModelVersionService(ModelVersionRepository modelVersionRepository,
                               ModelVersionMapper modelVersionMapper,
                               MlflowIntegrationService mlflowIntegrationService,
                               AnnotationEventLoggerService annotationEventLoggerService) {
        this.modelVersionRepository = modelVersionRepository;
        this.modelVersionMapper = modelVersionMapper;
        this.mlflowIntegrationService = mlflowIntegrationService;
        this.annotationEventLoggerService = annotationEventLoggerService;
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

    @Transactional(readOnly = true)
    public ModelVersionResponse getModelById(Long id) {
        ModelVersion model = findOrThrow(id);
        return modelVersionMapper.toResponse(model);
    }

    @Transactional(readOnly = true)
    public Page<ModelVersionResponse> getAllModels(Long datasetId, Pageable pageable, String statusFilter) {
        Page<ModelVersion> models = statusFilter != null && !statusFilter.isBlank()
                ? modelVersionRepository.findByTrainedOnDatasetVersion_Dataset_IdAndStatus(datasetId, statusFilter, pageable)
                : modelVersionRepository.findByTrainedOnDatasetVersion_Dataset_Id(datasetId, pageable);
        return models.map(modelVersionMapper::toResponse);
    }

    @Transactional
    public ModelVersionResponse updateStatus(Long id, String newStatus, String changedBy) {
        if (!VALID_STATUSES.contains(newStatus)) {
            throw new InvalidRequestException("Invalid model status: " + newStatus);
        }
        ModelVersion model = findOrThrow(id);
        String previousStatus = model.getStatus();
        model.setStatus(newStatus);
        ModelVersion saved = modelVersionRepository.save(model);

        annotationEventLoggerService.log(EventType.MODEL_STATUS_CHANGED, saved.getTrainedOnDatasetVersion(), saved,
                changedBy, "Model " + saved.getModelTag() + " status changed " + previousStatus + " -> " + newStatus);

        return modelVersionMapper.toResponse(saved);
    }

    /** Delegates to MlflowIntegrationService; owns the 404-vs-503 distinction the spec calls for. */
    @Transactional(readOnly = true)
    public MlflowDetailsResponse getMlflowDetails(Long id) {
        ModelVersion model = findOrThrow(id);
        if (model.getMlflowRunId() == null) {
            throw new NotFoundException("No MLflow run recorded for model " + id);
        }
        MlflowDetailsResponse details = mlflowIntegrationService.buildMlflowDetails(model);
        if (details == null) {
            throw new ServiceUnavailableException("MLflow tracking server is unreachable");
        }
        return details;
    }

    /** Resolves the owning dataset id for a model, for the membership check. */
    @Transactional(readOnly = true)
    public Long getDatasetIdForModel(Long id) {
        return findOrThrow(id).getTrainedOnDatasetVersion().getDataset().getId();
    }

    private ModelVersion findOrThrow(Long id) {
        return modelVersionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Model not found: " + id));
    }
}
