package com.corallineage.backend.training;

import com.corallineage.backend.datasets.Dataset;
import com.corallineage.backend.datasets.DatasetRepository;
import com.corallineage.backend.exception.InvalidRequestException;
import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.logging.AnnotationEventLoggerService;
import com.corallineage.backend.logging.EventType;
import com.corallineage.backend.versioning.dataset.DatasetVersion;
import com.corallineage.backend.versioning.dataset.DatasetVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TrainingJobService {

    private static final String DEFAULT_ARCHITECTURE = "efficientnet_b0";

    private final DatasetRepository datasetRepository;
    private final DatasetVersionRepository datasetVersionRepository;
    private final TrainingJobRepository trainingJobRepository;
    private final TrainingJobMapper trainingJobMapper;
    private final ArchitectureRegistry architectureRegistry;
    private final TrainingJobExecutor trainingJobExecutor;
    private final AnnotationEventLoggerService annotationEventLoggerService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TrainingJobService(DatasetRepository datasetRepository,
                              DatasetVersionRepository datasetVersionRepository,
                              TrainingJobRepository trainingJobRepository,
                              TrainingJobMapper trainingJobMapper,
                              ArchitectureRegistry architectureRegistry,
                              TrainingJobExecutor trainingJobExecutor,
                              AnnotationEventLoggerService annotationEventLoggerService) {
        this.datasetRepository = datasetRepository;
        this.datasetVersionRepository = datasetVersionRepository;
        this.trainingJobRepository = trainingJobRepository;
        this.trainingJobMapper = trainingJobMapper;
        this.architectureRegistry = architectureRegistry;
        this.trainingJobExecutor = trainingJobExecutor;
        this.annotationEventLoggerService = annotationEventLoggerService;
    }

    @Transactional
    public TrainingJobResponse initiateTrainingJob(InitiateTrainingRequest request) {
        Dataset dataset = datasetRepository.findById(request.getDatasetId())
                .orElseThrow(() -> new NotFoundException("Dataset not found: " + request.getDatasetId()));

        DatasetVersion targetVersion = resolveDatasetVersion(dataset.getId(), request.getDatasetVersionId());
        String architecture = resolveArchitecture(request.getArchitecture());
        String hyperparametersJson = buildHyperparametersJson(request);

        TrainingJob job = new TrainingJob();
        job.setDatasetVersion(targetVersion);
        job.setArchitecture(architecture);
        job.setInitiatedBy(request.getInitiatedBy());
        job.setStatus("QUEUED");
        job.setHyperparameters(hyperparametersJson);
        TrainingJob saved = trainingJobRepository.save(job);

        logAfterCommit(EventType.TRAINING_JOB_STARTED, saved,
                "Training job started (" + architecture + ") on dataset version " + targetVersion.getVersionTag());
        dispatchAfterCommit(saved.getId());

        return trainingJobMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public TrainingJobResponse getTrainingJobById(Long id) {
        TrainingJob job = trainingJobRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Training job not found: " + id));
        return trainingJobMapper.toResponse(job);
    }

    @Transactional(readOnly = true)
    public Page<TrainingJobResponse> getTrainingJobs(Long datasetId, Pageable pageable, String status) {
        Page<TrainingJob> jobs = status != null && !status.isBlank()
                ? trainingJobRepository.findByDatasetVersion_Dataset_IdAndStatusOrderByInitiatedAtDesc(datasetId, status, pageable)
                : trainingJobRepository.findByDatasetVersion_Dataset_IdOrderByInitiatedAtDesc(datasetId, pageable);
        return jobs.map(trainingJobMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public List<ArchitectureInfo> getArchitectures() {
        return architectureRegistry.getAll();
    }

    private DatasetVersion resolveDatasetVersion(Long datasetId, Long requestedVersionId) {
        if (requestedVersionId != null) {
            DatasetVersion version = datasetVersionRepository.findById(requestedVersionId)
                    .orElseThrow(() -> new InvalidRequestException("Dataset version not found: " + requestedVersionId));
            if (!version.getDataset().getId().equals(datasetId)) {
                throw new InvalidRequestException("Dataset version " + requestedVersionId + " does not belong to dataset " + datasetId);
            }
            return version;
        }
        return datasetVersionRepository.findTopByDatasetIdOrderByCreatedAtDesc(datasetId)
                .orElseThrow(() -> new InvalidRequestException("Dataset has no versions to train on: " + datasetId));
    }

    private String resolveArchitecture(String requested) {
        if (requested == null || requested.isBlank()) {
            return DEFAULT_ARCHITECTURE;
        }
        if (!architectureRegistry.isSupported(requested)) {
            throw new InvalidRequestException("Unsupported architecture: " + requested);
        }
        return requested;
    }

    private String buildHyperparametersJson(InitiateTrainingRequest request) {
        Map<String, Object> hyperparameters = new LinkedHashMap<>();
        hyperparameters.put("learningRate", request.getLearningRate() != null ? request.getLearningRate() : 0.001);
        hyperparameters.put("batchSize", request.getBatchSize() != null ? request.getBatchSize() : 32);
        hyperparameters.put("epochs", request.getEpochs() != null ? request.getEpochs() : 10);
        try {
            return objectMapper.writeValueAsString(hyperparameters);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize hyperparameters", ex);
        }
    }

    /**
     * The @Async executor lives on a separate bean (TrainingJobExecutor) rather than a method on
     * this class — Spring's @Async proxy only intercepts calls made through the bean's proxy, and
     * a same-class "this.executeTrainingAsync(...)" self-invocation would silently run synchronously.
     * Mirrors the same split used for DatasetImportService/DatasetImportExecutor.
     */
    private void dispatchAfterCommit(Long jobId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    trainingJobExecutor.executeTrainingAsync(jobId);
                }
            });
        } else {
            trainingJobExecutor.executeTrainingAsync(jobId);
        }
    }

    private void logAfterCommit(EventType eventType, TrainingJob job, String description) {
        DatasetVersion datasetVersion = job.getDatasetVersion();
        String performedBy = job.getInitiatedBy();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    annotationEventLoggerService.log(eventType, datasetVersion, performedBy, description);
                }
            });
        } else {
            annotationEventLoggerService.log(eventType, datasetVersion, performedBy, description);
        }
    }
}
