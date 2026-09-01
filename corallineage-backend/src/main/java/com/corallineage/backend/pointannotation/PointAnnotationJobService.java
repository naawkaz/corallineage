package com.corallineage.backend.pointannotation;

import com.corallineage.backend.annotation.AnnotationJob;
import com.corallineage.backend.annotation.AnnotationJobRepository;
import com.corallineage.backend.datasets.DatasetService;
import com.corallineage.backend.datasets.importservice.ImportJob;
import com.corallineage.backend.datasets.importservice.ImportJobRepository;
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
import com.corallineage.backend.versioning.dataset.DatasetVersionResponse;
import com.corallineage.backend.versioning.dataset.DatasetVersionService;
import com.corallineage.backend.versioning.dataset.Image;
import com.corallineage.backend.versioning.dataset.ImageRepository;
import com.corallineage.backend.versioning.dataset.LabelDefinition;
import com.corallineage.backend.versioning.dataset.LabelDefinitionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
public class PointAnnotationJobService {

    private static final int DEFAULT_GRID_ROWS = 7;
    private static final int DEFAULT_GRID_COLS = 7;
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
    private final AutoAnnotationExecutor autoAnnotationExecutor;
    private final DatasetService datasetService;
    private final AnnotationJobRepository annotationJobRepository;
    private final DatasetVersionService datasetVersionService;
    private final ImportJobRepository importJobRepository;

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
            AutoAnnotationExecutor autoAnnotationExecutor,
            DatasetService datasetService,
            AnnotationJobRepository annotationJobRepository,
            DatasetVersionService datasetVersionService,
            ImportJobRepository importJobRepository) {
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
        this.autoAnnotationExecutor = autoAnnotationExecutor;
        this.datasetService = datasetService;
        this.annotationJobRepository = annotationJobRepository;
        this.datasetVersionService = datasetVersionService;
        this.importJobRepository = importJobRepository;
    }

    /** Generates the point grid synchronously within the request */
    @Transactional
    public PointAnnotationJobResponse submitJob(StartPointAnnotationJobRequest request, String actingUsername) {
        DatasetVersion version = datasetVersionRepository.findById(request.getDatasetVersionId())
                .orElseThrow(
                        () -> new NotFoundException("Dataset version not found: " + request.getDatasetVersionId()));
        datasetService.requireAccess(version.getDataset().getId(), actingUsername);

        Set<Long> imageIds = datasetVersionImageRepository.findImageIdsByDatasetVersionId(version.getId());
        if (imageIds.isEmpty()) {
            throw new InvalidRequestException(
                    "Dataset version " + version.getVersionTag() + " has no images to annotate");
        }
        List<Image> images = new ArrayList<>(imageRepository.findAllById(imageIds));
        images.sort(Comparator.comparing(Image::getId));

        int gridRows = request.getGridRows() != null ? request.getGridRows() : DEFAULT_GRID_ROWS;
        int gridCols = request.getGridCols() != null ? request.getGridCols() : DEFAULT_GRID_COLS;
        BigDecimal confidenceThreshold = request.getConfidenceThreshold() != null
                ? request.getConfidenceThreshold()
                : DEFAULT_CONFIDENCE_THRESHOLD;
        int seedImageCount = request.getSeedImageCount() != null ? request.getSeedImageCount()
                : DEFAULT_SEED_IMAGE_COUNT;
        if (seedImageCount <= 0) {
            throw new InvalidRequestException("seedImageCount must be at least 1");
        }
        if (seedImageCount > images.size()) {
            throw new InvalidRequestException("seedImageCount (" + seedImageCount
                    + ") exceeds dataset version " + version.getVersionTag() + "'s image count (" + images.size()
                    + ")");
        }

        PointAnnotationJob job = new PointAnnotationJob();
        job.setDatasetVersion(version);
        job.setGridRows(gridRows);
        job.setGridCols(gridCols);
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
                allPoints.size() + " points generated (" + gridRows + "x" + gridCols + " grid/image) across "
                        + images.size()
                        + " image(s), " + seedImageCount + " seed image(s)");

        return toResponse(completedJob, images.size());
    }

    /** Resolves the owning dataset id for a point annotation job */
    @Transactional(readOnly = true)
    public Long getDatasetIdForJob(Long jobId) {
        PointAnnotationJob job = findOrThrow(jobId);
        return job.getDatasetVersion().getDataset().getId();
    }

    /** Resolves the owning dataset id for a generated point */
    @Transactional(readOnly = true)
    public Long getDatasetIdForPoint(Long pointId) {
        GeneratedPoint point = generatedPointRepository.findById(pointId)
                .orElseThrow(() -> new NotFoundException("Generated point not found: " + pointId));
        return point.getJob().getDatasetVersion().getDataset().getId();
    }

    @Transactional(readOnly = true)
    public PointAnnotationJobResponse getJobById(Long id) {
        PointAnnotationJob job = findOrThrow(id);
        return toResponse(job,
                (int) datasetVersionImageRepository.countByDatasetVersionId(job.getDatasetVersion().getId()));
    }

    @Transactional(readOnly = true)
    public Page<PointAnnotationJobResponse> getJobs(Long datasetId, Pageable pageable) {
        return pointAnnotationJobRepository.findByDatasetVersion_Dataset_IdOrderByCreatedAtDesc(datasetId, pageable)
                .map(job -> toResponse(job,
                        (int) datasetVersionImageRepository.countByDatasetVersionId(job.getDatasetVersion().getId())));
    }

    /**
     * Merges pending point annotation and in place annotation jobs into one sorted
     * queue
     */
    @Transactional(readOnly = true)
    public Page<UnlabeledImportQueueEntryResponse> getUnlabeledImportQueue(Long datasetId, Pageable pageable) {
        List<UnlabeledImportQueueEntryResponse> entries = new ArrayList<>();

        for (PointAnnotationJob job : pointAnnotationJobRepository
                .findByDatasetVersion_Dataset_IdOrderByCreatedAtDesc(datasetId, Pageable.unpaged())) {
            boolean delegated = "COMPLETE".equals(job.getStatus()) && job.getAnnotationJob() != null;
            if (delegated)
                continue;
            entries.add(toQueueEntry(job));
        }
        for (AnnotationJob job : annotationJobRepository
                .findByDataset_IdAndCommitInPlaceTrueAndAddedToProjectVersionIdIsNullOrderByInitiatedAtDesc(
                        datasetId)) {
            entries.add(toQueueEntry(job));
        }
        // A blank import still uploading, no version exists yet to attach a job to
        for (ImportJob importJob : importJobRepository
                .findByDataset_IdAndFormatAndCreatedDatasetVersionIdIsNullOrderBySubmittedAtDesc(datasetId, "BLANK")) {
            if (!"PROCESSING".equals(importJob.getStatus()) && !"FAILED".equals(importJob.getStatus()))
                continue;
            entries.add(toQueueEntry(importJob));
        }
        // A completed blank import with no start chosen yet
        for (DatasetVersionResponse pendingStart : datasetVersionService.getPendingBlankStarts(datasetId)) {
            entries.add(toQueueEntry(pendingStart));
        }
        entries.sort(Comparator.comparing(UnlabeledImportQueueEntryResponse::getStartedAt).reversed());

        int total = entries.size();
        int start = Math.min((int) pageable.getOffset(), total);
        int end = Math.min(start + pageable.getPageSize(), total);
        return new PageImpl<>(entries.subList(start, end), pageable, total);
    }

    /** Resolve route is seed label for jobs not yet delegated */
    private UnlabeledImportQueueEntryResponse toQueueEntry(PointAnnotationJob job) {
        UnlabeledImportQueueEntryResponse entry = new UnlabeledImportQueueEntryResponse();
        entry.setJobType("POINT_ANNOTATION");
        entry.setJobId(job.getId());
        entry.setVersionTag(job.getDatasetVersion().getVersionTag());
        entry.setVersionNumber(datasetVersionService.getVersionNumber(job.getDatasetVersion()));
        entry.setStatus(job.getStatus());
        entry.setSeedTotalPoints(generatedPointRepository.countByJobIdAndSeedTrue(job.getId()));
        entry.setSeedLabeledPoints(
                generatedPointRepository.countByJobIdAndSeedTrueAndStatus(job.getId(), "HUMAN_LABELED"));
        entry.setModelTag(job.getModelVersion() != null ? job.getModelVersion().getModelTag() : null);
        entry.setStartedAt(job.getCreatedAt());
        entry.setResolveRoute("SEED_LABEL");
        entry.setResolveJobId(job.getId());
        return entry;
    }

    /** A blank import job still uploading, not clickable */
    private UnlabeledImportQueueEntryResponse toQueueEntry(ImportJob importJob) {
        UnlabeledImportQueueEntryResponse entry = new UnlabeledImportQueueEntryResponse();
        entry.setJobType("IMPORT");
        entry.setJobId(importJob.getId());
        entry.setVersionTag(importJob.getVersionTag());
        entry.setStatus(importJob.getStatus());
        entry.setStartedAt(importJob.getSubmittedAt());
        entry.setResolveRoute(null);
        entry.setResolveJobId(null);
        return entry;
    }

    /** A completed blank import with no start chosen yet */
    private UnlabeledImportQueueEntryResponse toQueueEntry(DatasetVersionResponse pendingStart) {
        UnlabeledImportQueueEntryResponse entry = new UnlabeledImportQueueEntryResponse();
        entry.setJobType("VERSION_START");
        entry.setJobId(pendingStart.getId());
        entry.setVersionTag(pendingStart.getVersionTag());
        entry.setVersionNumber(pendingStart.getVersionNumber());
        entry.setStatus("PENDING_START");
        entry.setStartedAt(pendingStart.getCreatedAt());
        entry.setResolveRoute("START");
        entry.setResolveJobId(pendingStart.getId());
        entry.setImageCount(pendingStart.getImageCount());
        return entry;
    }

    private UnlabeledImportQueueEntryResponse toQueueEntry(AnnotationJob job) {
        UnlabeledImportQueueEntryResponse entry = new UnlabeledImportQueueEntryResponse();
        entry.setJobType("ANNOTATION");
        entry.setJobId(job.getId());
        entry.setVersionTag(job.getDatasetVersion() != null ? job.getDatasetVersion().getVersionTag() : null);
        entry.setVersionNumber(
                job.getDatasetVersion() != null ? datasetVersionService.getVersionNumber(job.getDatasetVersion())
                        : null);
        entry.setStatus("COMPLETED".equals(job.getStatus()) ? "REVIEW" : job.getStatus());
        entry.setModelTag(job.getModelVersion() != null ? job.getModelVersion().getModelTag() : null);
        entry.setStartedAt(job.getInitiatedAt());
        entry.setResolveRoute("ANNOTATION_REVIEW");
        entry.setResolveJobId(job.getId());
        return entry;
    }

    @Transactional(readOnly = true)
    public Page<GeneratedPointResponse> getPoints(Long jobId, boolean seed, Pageable pageable) {
        findOrThrow(jobId);
        return generatedPointRepository.findByJobIdAndSeedOrderByIdAsc(jobId, seed, pageable)
                .map(pointAnnotationMapper::toResponse);
    }

    /** Labels a point and upserts a confirmed annotation point */
    @Transactional
    public GeneratedPointResponse labelPoint(Long pointId, LabelPointRequest request) {
        GeneratedPoint point = generatedPointRepository.findById(pointId)
                .orElseThrow(() -> new NotFoundException("Generated point not found: " + pointId));
        DatasetVersion version = point.getJob().getDatasetVersion();
        LabelDefinition label = labelDefinitionRepository
                .findByDatasetVersionIdAndLabelCode(version.getId(), request.getLabelCode())
                .orElseThrow(() -> new InvalidRequestException(
                        "Unknown label code " + request.getLabelCode() + " for dataset version "
                                + version.getVersionTag()));

        point.setFinalLabelDefinition(label);
        point.setStatus("HUMAN_LABELED");
        GeneratedPoint saved = generatedPointRepository.save(point);

        annotationPointBridge.upsert(point, label, "HUMAN", request.getAnnotatedBy(), null);

        return pointAnnotationMapper.toResponse(saved);
    }

    /** Submits a training job on this job's dataset version */
    @Transactional
    public PointAnnotationJobResponse trainModel(Long jobId, TrainPointAnnotationJobRequest request) {
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
        if (request != null) {
            trainingRequest.setArchitecture(request.getArchitecture());
            trainingRequest.setLearningRate(request.getLearningRate());
            trainingRequest.setBatchSize(request.getBatchSize());
            trainingRequest.setEpochs(request.getEpochs());
        }
        TrainingJobResponse trainingJobResponse = trainingJobService.initiateTrainingJob(trainingRequest);

        TrainingJob trainingJob = trainingJobRepository.findById(trainingJobResponse.getJobId())
                .orElseThrow(() -> new IllegalStateException("Training job vanished immediately after creation"));
        job.setTrainingJob(trainingJob);
        job.setStatus("TRAINING");
        PointAnnotationJob saved = pointAnnotationJobRepository.save(job);

        return toResponse(saved,
                (int) datasetVersionImageRepository.countByDatasetVersionId(job.getDatasetVersion().getId()));
    }

    /**
     * Delegates the job's non seed points into a new annotation job for inference
     * and review
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
            throw new InvalidRequestException(
                    "Training job for point annotation job " + jobId + " has not completed yet");
        }
        ModelVersion model = trainingJob.getModelVersion();
        if (model == null) {
            throw new InvalidRequestException("Training completed but no model was registered");
        }

        job.setModelVersion(model);

        List<Long> nonSeedImageIds = generatedPointRepository.findByJobIdAndSeedFalseAndStatus(jobId, "UNLABELED")
                .stream().map(point -> point.getImage().getId()).distinct().toList();

        AnnotationJob delegatedJob = new AnnotationJob();
        delegatedJob.setModelVersion(model);
        delegatedJob.setDataset(job.getDatasetVersion().getDataset());
        delegatedJob.setDatasetVersion(job.getDatasetVersion());
        delegatedJob.setJobType("NEW_IMAGES");
        delegatedJob.setCommitInPlace(true);
        delegatedJob.setInitiatedBy(job.getInitiatedBy());
        delegatedJob.setStatus("RUNNING");
        delegatedJob.setReviewStatus("PENDING");
        delegatedJob.setImageIds(nonSeedImageIds);
        delegatedJob.setConfidenceThreshold(job.getConfidenceThreshold());
        AnnotationJob savedDelegatedJob = annotationJobRepository.save(delegatedJob);

        job.setAnnotationJob(savedDelegatedJob);
        job.setStatus("COMPLETE");
        PointAnnotationJob saved = pointAnnotationJobRepository.save(job);

        dispatchAutoAnnotateAfterCommit(jobId);

        return toResponse(saved,
                (int) datasetVersionImageRepository.countByDatasetVersionId(job.getDatasetVersion().getId()));
    }

    /** Delegates into auto annotate once training completes */
    @Transactional
    public void autoAnnotateAfterTraining(Long trainingJobId) {
        pointAnnotationJobRepository.findByTrainingJob_Id(trainingJobId)
                .filter(job -> "TRAINING".equals(job.getStatus()))
                .ifPresent(job -> startAutoAnnotate(job.getId()));
    }

    /** Reverts a job from training back to seed labeling on training failure */
    @Transactional
    public void revertAfterTrainingFailure(Long trainingJobId) {
        pointAnnotationJobRepository.findByTrainingJob_Id(trainingJobId)
                .filter(job -> "TRAINING".equals(job.getStatus()))
                .ifPresent(job -> {
                    job.setStatus("SEED_LABELING");
                    pointAnnotationJobRepository.save(job);
                });
    }

    /** Runs on a separate bean, dispatched only after the transaction commits */
    private void dispatchAutoAnnotateAfterCommit(Long jobId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    autoAnnotationExecutor.executeAutoAnnotationAsync(jobId);
                }
            });
        } else {
            autoAnnotationExecutor.executeAutoAnnotationAsync(jobId);
        }
    }

    private PointAnnotationJobResponse toResponse(PointAnnotationJob job, int totalImages) {
        long seedTotalPoints = generatedPointRepository.countByJobIdAndSeedTrue(job.getId());
        long seedLabeledPoints = generatedPointRepository.countByJobIdAndSeedTrueAndStatus(job.getId(),
                "HUMAN_LABELED");
        return pointAnnotationMapper.toResponse(job, totalImages, seedTotalPoints, seedLabeledPoints);
    }

    private PointAnnotationJob findOrThrow(Long id) {
        return pointAnnotationJobRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Point annotation job not found: " + id));
    }
}
