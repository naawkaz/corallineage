package com.corallineage.backend.pointannotation;

import com.corallineage.backend.exception.InvalidRequestException;
import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.logging.AnnotationEventLoggerService;
import com.corallineage.backend.logging.EventType;
import com.corallineage.backend.registry.model.ModelVersion;
import com.corallineage.backend.training.InitiateTrainingRequest;
import com.corallineage.backend.training.TrainingJob;
import com.corallineage.backend.training.TrainingJobRepository;
import com.corallineage.backend.training.TrainingJobResponse;
import com.corallineage.backend.training.TrainingJobService;
import com.corallineage.backend.versioning.dataset.AnnotationPointRepository;
import com.corallineage.backend.versioning.dataset.DatasetVersion;
import com.corallineage.backend.versioning.dataset.DatasetVersionImageRepository;
import com.corallineage.backend.versioning.dataset.DatasetVersionRepository;
import com.corallineage.backend.versioning.dataset.Image;
import com.corallineage.backend.versioning.dataset.ImageRepository;
import com.corallineage.backend.versioning.dataset.LabelDefinition;
import com.corallineage.backend.versioning.dataset.LabelDefinitionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
public class PointAnnotationJobService {

    private static final int DEFAULT_POINTS_PER_IMAGE = 50;
    private static final BigDecimal DEFAULT_CONFIDENCE_THRESHOLD = new BigDecimal("0.8500");
    private static final int DEFAULT_SEED_IMAGE_COUNT = 20;

    private final DatasetVersionRepository datasetVersionRepository;
    private final DatasetVersionImageRepository datasetVersionImageRepository;
    private final ImageRepository imageRepository;
    private final PointAnnotationJobRepository pointAnnotationJobRepository;
    private final GeneratedPointRepository generatedPointRepository;
    private final PointGenerationService pointGenerationService;
    private final PointAnnotationMapper pointAnnotationMapper;
    private final AnnotationEventLoggerService annotationEventLoggerService;
    private final LabelDefinitionRepository labelDefinitionRepository;
    private final AnnotationPointRepository annotationPointRepository;
    private final AnnotationPointBridge annotationPointBridge;
    private final TrainingJobService trainingJobService;
    private final TrainingJobRepository trainingJobRepository;
    private final PointInferenceService pointInferenceService;

    public PointAnnotationJobService(DatasetVersionRepository datasetVersionRepository,
                                     DatasetVersionImageRepository datasetVersionImageRepository,
                                     ImageRepository imageRepository,
                                     PointAnnotationJobRepository pointAnnotationJobRepository,
                                     GeneratedPointRepository generatedPointRepository,
                                     PointGenerationService pointGenerationService,
                                     PointAnnotationMapper pointAnnotationMapper,
                                     AnnotationEventLoggerService annotationEventLoggerService,
                                     LabelDefinitionRepository labelDefinitionRepository,
                                     AnnotationPointRepository annotationPointRepository,
                                     AnnotationPointBridge annotationPointBridge,
                                     TrainingJobService trainingJobService,
                                     TrainingJobRepository trainingJobRepository,
                                     PointInferenceService pointInferenceService) {
        this.datasetVersionRepository = datasetVersionRepository;
        this.datasetVersionImageRepository = datasetVersionImageRepository;
        this.imageRepository = imageRepository;
        this.pointAnnotationJobRepository = pointAnnotationJobRepository;
        this.generatedPointRepository = generatedPointRepository;
        this.pointGenerationService = pointGenerationService;
        this.pointAnnotationMapper = pointAnnotationMapper;
        this.annotationEventLoggerService = annotationEventLoggerService;
        this.labelDefinitionRepository = labelDefinitionRepository;
        this.annotationPointRepository = annotationPointRepository;
        this.annotationPointBridge = annotationPointBridge;
        this.trainingJobService = trainingJobService;
        this.trainingJobRepository = trainingJobRepository;
        this.pointInferenceService = pointInferenceService;
    }

    /**
     * Generates the point grid synchronously within the request rather than dispatching to an
     * @Async executor (the pattern every other long-running job in this app uses) — a deliberate
     * scope cut for this proof of concept, acceptable at the small image counts this app's fixture
     * datasets use. Flagged as a known limitation to async-ify later if raw uploads grow large.
     */
    @Transactional
    public PointAnnotationJobResponse submitJob(StartPointAnnotationJobRequest request) {
        DatasetVersion version = datasetVersionRepository.findById(request.getDatasetVersionId())
                .orElseThrow(() -> new NotFoundException("Dataset version not found: " + request.getDatasetVersionId()));

        Set<Long> imageIds = datasetVersionImageRepository.findImageIdsByDatasetVersionId(version.getId());
        if (imageIds.isEmpty()) {
            throw new InvalidRequestException("Dataset version " + version.getVersionTag() + " has no images to annotate");
        }
        List<Image> images = new ArrayList<>(imageRepository.findAllById(imageIds));
        images.sort(Comparator.comparing(Image::getId));

        int pointsPerImage = request.getPointsPerImage() != null ? request.getPointsPerImage() : DEFAULT_POINTS_PER_IMAGE;
        BigDecimal confidenceThreshold = request.getConfidenceThreshold() != null
                ? request.getConfidenceThreshold() : DEFAULT_CONFIDENCE_THRESHOLD;
        int seedImageCount = request.getSeedImageCount() != null ? request.getSeedImageCount() : DEFAULT_SEED_IMAGE_COUNT;
        if (seedImageCount <= 0) {
            throw new InvalidRequestException("seedImageCount must be at least 1");
        }
        if (seedImageCount > images.size()) {
            throw new InvalidRequestException("seedImageCount (" + seedImageCount
                    + ") exceeds dataset version " + version.getVersionTag() + "'s image count (" + images.size() + ")");
        }

        PointAnnotationJob job = new PointAnnotationJob();
        job.setDatasetVersion(version);
        job.setPointsPerImage(pointsPerImage);
        job.setConfidenceThreshold(confidenceThreshold);
        job.setSeedImageCount(seedImageCount);
        job.setStatus("GENERATING");
        job.setInitiatedBy(request.getInitiatedBy());
        PointAnnotationJob savedJob = pointAnnotationJobRepository.save(job);

        List<GeneratedPoint> allPoints = new ArrayList<>();
        for (int i = 0; i < images.size(); i++) {
            boolean seed = i < seedImageCount;
            Image image = images.get(i);
            int startIndex = annotationPointRepository.findMaxPointIndexByImageId(image.getId()) + 1;
            allPoints.addAll(pointGenerationService.generateGrid(image, savedJob, seed, startIndex));
        }
        generatedPointRepository.saveAll(allPoints);

        savedJob.setStatus("SEED_LABELING");
        PointAnnotationJob completedJob = pointAnnotationJobRepository.save(savedJob);

        annotationEventLoggerService.log(EventType.POINTS_GENERATED, version, request.getInitiatedBy(),
                allPoints.size() + " points generated (" + pointsPerImage + "/image) across " + images.size()
                        + " image(s), " + seedImageCount + " seed image(s)");

        return toResponse(completedJob, images.size());
    }

    @Transactional(readOnly = true)
    public PointAnnotationJobResponse getJobById(Long id) {
        PointAnnotationJob job = findOrThrow(id);
        return toResponse(job, (int) datasetVersionImageRepository.countByDatasetVersionId(job.getDatasetVersion().getId()));
    }

    @Transactional(readOnly = true)
    public Page<PointAnnotationJobResponse> getJobs(Long datasetId, Pageable pageable) {
        return pointAnnotationJobRepository.findByDatasetVersion_Dataset_IdOrderByCreatedAtDesc(datasetId, pageable)
                .map(job -> toResponse(job, (int) datasetVersionImageRepository.countByDatasetVersionId(job.getDatasetVersion().getId())));
    }

    @Transactional(readOnly = true)
    public Page<GeneratedPointResponse> getPoints(Long jobId, boolean seed, Pageable pageable) {
        findOrThrow(jobId);
        return generatedPointRepository.findByJobIdAndSeed(jobId, seed, pageable).map(pointAnnotationMapper::toResponse);
    }

    /**
     * A human directly assigns a label to a generated point (the seed-labeling step — no model
     * prediction involved yet). Bridges into the existing training/diff pipeline by upserting a
     * real {@link AnnotationPoint} row, so downstream consumers of that entity need no changes.
     */
    @Transactional
    public GeneratedPointResponse labelPoint(Long pointId, LabelPointRequest request) {
        GeneratedPoint point = generatedPointRepository.findById(pointId)
                .orElseThrow(() -> new NotFoundException("Generated point not found: " + pointId));
        DatasetVersion version = point.getJob().getDatasetVersion();
        LabelDefinition label = labelDefinitionRepository
                .findByDatasetVersionIdAndLabelCode(version.getId(), request.getLabelCode())
                .orElseThrow(() -> new InvalidRequestException(
                        "Unknown label code " + request.getLabelCode() + " for dataset version " + version.getVersionTag()));

        point.setFinalLabelDefinition(label);
        point.setStatus("HUMAN_LABELED");
        GeneratedPoint saved = generatedPointRepository.save(point);

        annotationPointBridge.upsert(point, label, "HUMAN", request.getAnnotatedBy(), null);

        return pointAnnotationMapper.toResponse(saved);
    }

    /**
     * A human resolves a point left pending by auto-annotate (its model prediction was below the
     * job's confidence threshold) — either accepting the model's own predicted label or overriding
     * it. Mirrors {@code AnnotationJobService.reviewPrediction}'s accept/override semantics, but
     * bridges into {@link AnnotationPoint} like every other confirmation path in this module.
     */
    @Transactional
    public GeneratedPointResponse reviewPoint(Long pointId, ReviewGeneratedPointRequest request) {
        GeneratedPoint point = generatedPointRepository.findById(pointId)
                .orElseThrow(() -> new NotFoundException("Generated point not found: " + pointId));
        if (!"MODEL_PREDICTED".equals(point.getStatus())) {
            throw new InvalidRequestException("Point " + pointId + " is not pending review (status: " + point.getStatus() + ")");
        }

        boolean accepted = Boolean.TRUE.equals(request.getAccepted());
        LabelDefinition finalLabel;
        String annotationSource;
        BigDecimal confidenceScore;
        if (accepted) {
            finalLabel = point.getPredictedLabelDefinition();
            annotationSource = "MODEL_PREDICTED";
            confidenceScore = point.getConfidenceScore();
        } else {
            if (request.getCorrectedLabelCode() == null || request.getCorrectedLabelCode().isBlank()) {
                throw new InvalidRequestException("correctedLabelCode is required when overriding a prediction");
            }
            DatasetVersion version = point.getJob().getDatasetVersion();
            finalLabel = labelDefinitionRepository
                    .findByDatasetVersionIdAndLabelCode(version.getId(), request.getCorrectedLabelCode())
                    .orElseThrow(() -> new InvalidRequestException(
                            "Unknown label code " + request.getCorrectedLabelCode() + " for dataset version " + version.getVersionTag()));
            annotationSource = "MODEL_CORRECTED";
            confidenceScore = null;
        }

        point.setFinalLabelDefinition(finalLabel);
        point.setStatus("CONFIRMED");
        GeneratedPoint saved = generatedPointRepository.save(point);

        annotationPointBridge.upsert(point, finalLabel, annotationSource, request.getReviewedBy(), confidenceScore);

        if (!accepted) {
            annotationEventLoggerService.log(EventType.HUMAN_CORRECTION, point.getJob().getDatasetVersion(), request.getReviewedBy(),
                    "Point " + pointId + " (image " + point.getImage().getId() + ", index " + point.getPointIndex()
                            + ") overridden to " + finalLabel.getLabelCode());
        }

        updateReviewCompletion(point.getJob());

        return pointAnnotationMapper.toResponse(saved);
    }

    private void updateReviewCompletion(PointAnnotationJob job) {
        if (!"REVIEW".equals(job.getStatus())) {
            return;
        }
        long pendingReview = generatedPointRepository.countByJobIdAndSeedFalseAndStatus(job.getId(), "MODEL_PREDICTED");
        if (pendingReview == 0) {
            job.setStatus("COMPLETE");
            pointAnnotationJobRepository.save(job);
        }
    }

    /**
     * Submits a real training job on this point-annotation job's dataset version, reusing
     * {@link TrainingJobService} completely unchanged — {@code DatasetPreparationService} already
     * trains on whatever {@link com.corallineage.backend.versioning.dataset.AnnotationPoint} rows
     * exist, which by now include every seed point this job's labeling step materialized.
     */
    @Transactional
    public PointAnnotationJobResponse trainModel(Long jobId) {
        PointAnnotationJob job = findOrThrow(jobId);
        if (!"SEED_LABELING".equals(job.getStatus())) {
            throw new InvalidRequestException(
                    "Point annotation job " + jobId + " is not ready for training (status: " + job.getStatus() + ")");
        }
        long seedTotal = generatedPointRepository.countByJobIdAndSeedTrue(jobId);
        long seedLabeled = generatedPointRepository.countByJobIdAndSeedTrueAndStatus(jobId, "HUMAN_LABELED");
        if (seedLabeled < seedTotal) {
            throw new InvalidRequestException(
                    "Not all seed points are labeled yet (" + seedLabeled + "/" + seedTotal + ")");
        }

        InitiateTrainingRequest trainingRequest = new InitiateTrainingRequest();
        trainingRequest.setDatasetId(job.getDatasetVersion().getDataset().getId());
        trainingRequest.setDatasetVersionId(job.getDatasetVersion().getId());
        trainingRequest.setInitiatedBy(job.getInitiatedBy());
        TrainingJobResponse trainingJobResponse = trainingJobService.initiateTrainingJob(trainingRequest);

        TrainingJob trainingJob = trainingJobRepository.findById(trainingJobResponse.getJobId())
                .orElseThrow(() -> new IllegalStateException("Training job vanished immediately after creation"));
        job.setTrainingJob(trainingJob);
        job.setStatus("TRAINING");
        PointAnnotationJob saved = pointAnnotationJobRepository.save(job);

        return toResponse(saved, (int) datasetVersionImageRepository.countByDatasetVersionId(job.getDatasetVersion().getId()));
    }

    /**
     * Runs point-level inference on every non-seed point once the triggered training job has
     * completed, applying the job's confidence threshold (auto-confirm above, queue for human
     * review below). Runs synchronously within the request, same scope cut as {@link #submitJob}.
     */
    @Transactional
    public PointAnnotationJobResponse startAutoAnnotate(Long jobId) {
        PointAnnotationJob job = findOrThrow(jobId);
        if (!"TRAINING".equals(job.getStatus())) {
            throw new InvalidRequestException(
                    "Point annotation job " + jobId + " is not in TRAINING status (status: " + job.getStatus() + ")");
        }
        TrainingJob trainingJob = job.getTrainingJob();
        if (trainingJob == null || !"COMPLETED".equals(trainingJob.getStatus())) {
            throw new InvalidRequestException("Training job for point annotation job " + jobId + " has not completed yet");
        }
        ModelVersion model = trainingJob.getModelVersion();
        if (model == null) {
            throw new InvalidRequestException("Training completed but no model was registered");
        }

        job.setModelVersion(model);
        job.setStatus("AUTO_ANNOTATING");
        pointAnnotationJobRepository.save(job);

        pointInferenceService.runInference(job);

        long pendingReview = generatedPointRepository.countByJobIdAndSeedFalseAndStatus(jobId, "MODEL_PREDICTED");
        job.setStatus(pendingReview > 0 ? "REVIEW" : "COMPLETE");
        PointAnnotationJob saved = pointAnnotationJobRepository.save(job);

        annotationEventLoggerService.log(EventType.AUTO_ANNOTATION_COMPLETED, job.getDatasetVersion(), job.getInitiatedBy(),
                "Auto-annotation completed with model " + model.getModelTag() + ", " + pendingReview + " point(s) pending review");

        return toResponse(saved, (int) datasetVersionImageRepository.countByDatasetVersionId(job.getDatasetVersion().getId()));
    }

    private PointAnnotationJobResponse toResponse(PointAnnotationJob job, int totalImages) {
        long seedTotalPoints = generatedPointRepository.countByJobIdAndSeedTrue(job.getId());
        long seedLabeledPoints = generatedPointRepository.countByJobIdAndSeedTrueAndStatus(job.getId(), "HUMAN_LABELED");
        return pointAnnotationMapper.toResponse(job, totalImages, seedTotalPoints, seedLabeledPoints);
    }

    private PointAnnotationJob findOrThrow(Long id) {
        return pointAnnotationJobRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Point annotation job not found: " + id));
    }
}
