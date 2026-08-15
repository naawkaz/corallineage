package com.corallineage.backend.annotation;

import com.corallineage.backend.datasets.Dataset;
import com.corallineage.backend.exception.InvalidRequestException;
import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.logging.AnnotationEventLoggerService;
import com.corallineage.backend.logging.EventType;
import com.corallineage.backend.registry.model.ModelVersion;
import com.corallineage.backend.registry.model.ModelVersionRepository;
import com.corallineage.backend.versioning.dataset.Image;
import com.corallineage.backend.versioning.dataset.ImageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;

@Service
public class AnnotationJobService {

    private final AnnotationJobRepository annotationJobRepository;
    private final AnnotationPredictionRepository annotationPredictionRepository;
    private final ModelVersionRepository modelVersionRepository;
    private final ImageRepository imageRepository;
    private final AnnotationJobMapper annotationJobMapper;
    private final AnnotationJobExecutor annotationJobExecutor;
    private final AnnotationEventLoggerService annotationEventLoggerService;

    public AnnotationJobService(AnnotationJobRepository annotationJobRepository,
                                AnnotationPredictionRepository annotationPredictionRepository,
                                ModelVersionRepository modelVersionRepository,
                                ImageRepository imageRepository,
                                AnnotationJobMapper annotationJobMapper,
                                AnnotationJobExecutor annotationJobExecutor,
                                AnnotationEventLoggerService annotationEventLoggerService) {
        this.annotationJobRepository = annotationJobRepository;
        this.annotationPredictionRepository = annotationPredictionRepository;
        this.modelVersionRepository = modelVersionRepository;
        this.imageRepository = imageRepository;
        this.annotationJobMapper = annotationJobMapper;
        this.annotationJobExecutor = annotationJobExecutor;
        this.annotationEventLoggerService = annotationEventLoggerService;
    }

    @Transactional
    public AnnotationJobResponse submitAnnotationJob(InitiateAnnotationJobRequest request) {
        ModelVersion model = modelVersionRepository.findById(request.getModelVersionId())
                .orElseThrow(() -> new NotFoundException("Model not found: " + request.getModelVersionId()));
        if (!"ACTIVE".equals(model.getStatus())) {
            throw new InvalidRequestException(
                    "Model " + model.getModelTag() + " is not ACTIVE (status: " + model.getStatus() + ")");
        }

        List<Image> images = imageRepository.findAllById(request.getImageIds());
        if (images.size() != request.getImageIds().size()) {
            throw new NotFoundException("One or more images not found");
        }

        Dataset dataset = resolveCommonDataset(images);

        AnnotationJob job = new AnnotationJob();
        job.setModelVersion(model);
        job.setDataset(dataset);
        job.setInitiatedBy(request.getInitiatedBy());
        job.setStatus("RUNNING");
        job.setImageIds(request.getImageIds());
        AnnotationJob saved = annotationJobRepository.save(job);

        logAfterCommit(EventType.ANNOTATION_JOB_STARTED, saved,
                "Annotation job started with model " + model.getModelTag() + " on " + images.size() + " image(s)");
        dispatchAfterCommit(saved.getId());

        return annotationJobMapper.toResponse(saved, 0, 0);
    }

    @Transactional(readOnly = true)
    public AnnotationJobResponse getJobById(Long id) {
        AnnotationJob job = findOrThrow(id);
        return annotationJobMapper.toResponse(job, countTotal(id), countReviewed(id));
    }

    @Transactional(readOnly = true)
    public Page<AnnotationJobResponse> getJobs(Long datasetId, Pageable pageable) {
        return annotationJobRepository.findByDataset_IdOrderByInitiatedAtDesc(datasetId, pageable)
                .map(job -> annotationJobMapper.toResponse(job, countTotal(job.getId()), countReviewed(job.getId())));
    }

    /**
     * Derives the job's dataset server-side from the submitted images rather than trusting a
     * client-supplied dataset id — every image must belong to the same dataset (via its
     * datasetVersion.dataset), matching the invariant the UI's image picker already enforces.
     */
    private Dataset resolveCommonDataset(List<Image> images) {
        Dataset first = images.get(0).getOriginDatasetVersion().getDataset();
        for (Image image : images) {
            Dataset current = image.getOriginDatasetVersion().getDataset();
            if (!current.getId().equals(first.getId())) {
                throw new InvalidRequestException(
                        "All images must belong to the same dataset; image " + image.getId()
                                + " belongs to dataset " + current.getId() + ", expected " + first.getId());
            }
        }
        return first;
    }

    @Transactional(readOnly = true)
    public Page<AnnotationPredictionResponse> getPredictions(Long jobId, Long imageIdFilter, Boolean acceptedFilter,
                                                               Pageable pageable) {
        findOrThrow(jobId);
        Page<AnnotationPrediction> predictions;
        if (imageIdFilter != null && acceptedFilter != null) {
            predictions = annotationPredictionRepository.findByAnnotationJobIdAndImageIdAndAccepted(
                    jobId, imageIdFilter, acceptedFilter, pageable);
        } else if (imageIdFilter != null) {
            predictions = annotationPredictionRepository.findByAnnotationJobIdAndImageId(jobId, imageIdFilter, pageable);
        } else if (acceptedFilter != null) {
            predictions = annotationPredictionRepository.findByAnnotationJobIdAndAccepted(jobId, acceptedFilter, pageable);
        } else {
            predictions = annotationPredictionRepository.findByAnnotationJobId(jobId, pageable);
        }
        return predictions.map(annotationJobMapper::toPredictionResponse);
    }

    @Transactional
    public AnnotationPredictionResponse reviewPrediction(Long jobId, Long predictionId, ReviewPredictionRequest request) {
        AnnotationJob job = findOrThrow(jobId);
        AnnotationPrediction prediction = annotationPredictionRepository.findById(predictionId)
                .orElseThrow(() -> new NotFoundException("Prediction not found: " + predictionId));
        if (!prediction.getAnnotationJob().getId().equals(jobId)) {
            throw new NotFoundException("Prediction " + predictionId + " does not belong to job " + jobId);
        }

        boolean accepted = Boolean.TRUE.equals(request.getAccepted());
        if (!accepted && (request.getCorrectedLabelCode() == null || request.getCorrectedLabelCode().isBlank())) {
            throw new InvalidRequestException("correctedLabelCode is required when overriding a prediction");
        }

        String originalLabel = prediction.getPredictedLabelCode();
        prediction.setAccepted(request.getAccepted());
        prediction.setCorrectedLabelCode(accepted ? null : request.getCorrectedLabelCode());
        prediction.setReviewedBy(request.getReviewedBy());
        prediction.setReviewedAt(Instant.now());
        AnnotationPrediction saved = annotationPredictionRepository.save(prediction);

        if (!accepted) {
            annotationEventLoggerService.log(EventType.HUMAN_CORRECTION, null, job.getModelVersion(),
                    request.getReviewedBy(),
                    "Prediction overridden for image " + prediction.getImage().getId() + " point "
                            + prediction.getPointIndex() + ": " + originalLabel + " -> " + request.getCorrectedLabelCode());
        }

        updateReviewStatus(job);

        return annotationJobMapper.toPredictionResponse(saved);
    }

    private void updateReviewStatus(AnnotationJob job) {
        long total = countTotal(job.getId());
        long reviewed = countReviewed(job.getId());
        String newStatus = reviewed == 0 ? "PENDING" : (reviewed < total ? "IN_PROGRESS" : "COMPLETED");
        if (!newStatus.equals(job.getReviewStatus())) {
            job.setReviewStatus(newStatus);
            annotationJobRepository.save(job);
        }
    }

    private long countTotal(Long jobId) {
        return annotationPredictionRepository.countByAnnotationJobId(jobId);
    }

    private long countReviewed(Long jobId) {
        return annotationPredictionRepository.countByAnnotationJobIdAndAcceptedIsNotNull(jobId);
    }

    private AnnotationJob findOrThrow(Long id) {
        return annotationJobRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Annotation job not found: " + id));
    }

    /**
     * The @Async executor lives on a separate bean (AnnotationJobExecutor) rather than a method on
     * this class — mirrors TrainingJobService/TrainingJobExecutor's split, needed because Spring's
     * @Async proxy does not intercept same-class self-invocation.
     */
    private void dispatchAfterCommit(Long jobId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    annotationJobExecutor.executeAnnotationAsync(jobId);
                }
            });
        } else {
            annotationJobExecutor.executeAnnotationAsync(jobId);
        }
    }

    private void logAfterCommit(EventType eventType, AnnotationJob job, String description) {
        ModelVersion modelVersion = job.getModelVersion();
        String performedBy = job.getInitiatedBy();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    annotationEventLoggerService.log(eventType, null, modelVersion, performedBy, description);
                }
            });
        } else {
            annotationEventLoggerService.log(eventType, null, modelVersion, performedBy, description);
        }
    }
}
