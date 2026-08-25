package com.corallineage.backend.annotation;

import com.corallineage.backend.datasets.Dataset;
import com.corallineage.backend.datasets.DatasetService;
import com.corallineage.backend.exception.ConflictException;
import com.corallineage.backend.exception.InvalidRequestException;
import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.logging.AnnotationEventLoggerService;
import com.corallineage.backend.logging.EventType;
import com.corallineage.backend.registry.model.ModelVersion;
import com.corallineage.backend.registry.model.ModelVersionRepository;
import com.corallineage.backend.versioning.dataset.AnnotationPoint;
import com.corallineage.backend.versioning.dataset.AnnotationPointRepository;
import com.corallineage.backend.versioning.dataset.CreateVersionRequest;
import com.corallineage.backend.versioning.dataset.DatasetVersion;
import com.corallineage.backend.versioning.dataset.DatasetVersionImage;
import com.corallineage.backend.versioning.dataset.DatasetVersionImageRepository;
import com.corallineage.backend.versioning.dataset.DatasetVersionRepository;
import com.corallineage.backend.versioning.dataset.DatasetVersionResponse;
import com.corallineage.backend.versioning.dataset.DatasetVersionService;
import com.corallineage.backend.versioning.dataset.Image;
import com.corallineage.backend.versioning.dataset.ImageRepository;
import com.corallineage.backend.versioning.dataset.LabelDefinition;
import com.corallineage.backend.versioning.dataset.LabelDefinitionRepository;
import com.corallineage.backend.versioning.dataset.TriggerReason;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class AnnotationJobService {

    private final AnnotationJobRepository annotationJobRepository;
    private final AnnotationPredictionRepository annotationPredictionRepository;
    private final ModelVersionRepository modelVersionRepository;
    private final ImageRepository imageRepository;
    private final AnnotationJobMapper annotationJobMapper;
    private final AnnotationJobExecutor annotationJobExecutor;
    private final AnnotationEventLoggerService annotationEventLoggerService;
    private final DatasetVersionRepository datasetVersionRepository;
    private final DatasetVersionImageRepository datasetVersionImageRepository;
    private final AnnotationPointRepository annotationPointRepository;
    private final LabelDefinitionRepository labelDefinitionRepository;
    private final DatasetVersionService datasetVersionService;
    private final DatasetService datasetService;

    public AnnotationJobService(AnnotationJobRepository annotationJobRepository,
                                AnnotationPredictionRepository annotationPredictionRepository,
                                ModelVersionRepository modelVersionRepository,
                                ImageRepository imageRepository,
                                AnnotationJobMapper annotationJobMapper,
                                AnnotationJobExecutor annotationJobExecutor,
                                AnnotationEventLoggerService annotationEventLoggerService,
                                DatasetVersionRepository datasetVersionRepository,
                                DatasetVersionImageRepository datasetVersionImageRepository,
                                AnnotationPointRepository annotationPointRepository,
                                LabelDefinitionRepository labelDefinitionRepository,
                                DatasetVersionService datasetVersionService,
                                DatasetService datasetService) {
        this.annotationJobRepository = annotationJobRepository;
        this.annotationPredictionRepository = annotationPredictionRepository;
        this.modelVersionRepository = modelVersionRepository;
        this.imageRepository = imageRepository;
        this.annotationJobMapper = annotationJobMapper;
        this.annotationJobExecutor = annotationJobExecutor;
        this.annotationEventLoggerService = annotationEventLoggerService;
        this.datasetVersionRepository = datasetVersionRepository;
        this.datasetVersionImageRepository = datasetVersionImageRepository;
        this.annotationPointRepository = annotationPointRepository;
        this.labelDefinitionRepository = labelDefinitionRepository;
        this.datasetVersionService = datasetVersionService;
        this.datasetService = datasetService;
    }

    // --- Legacy whole-image submit path (kept for backward compatibility; no longer exposed by
    // the frontend, which now submits through submitValidationJob/submitNewImagesJob instead). ---

    @Transactional
    public AnnotationJobResponse submitAnnotationJob(InitiateAnnotationJobRequest request, String actingUsername) {
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
        datasetService.requireAccess(dataset.getId(), actingUsername);

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

    // --- Mode A: "Validate against human labels" ---

    @Transactional
    public AnnotationJobResponse submitValidationJob(SubmitValidationJobRequest request, String actingUsername) {
        ModelVersion model = modelVersionRepository.findById(request.getModelVersionId())
                .orElseThrow(() -> new NotFoundException("Model not found: " + request.getModelVersionId()));
        if (!"ACTIVE".equals(model.getStatus())) {
            throw new InvalidRequestException(
                    "Model " + model.getModelTag() + " is not ACTIVE (status: " + model.getStatus() + ")");
        }

        DatasetVersion version = datasetVersionRepository.findById(request.getDatasetVersionId())
                .orElseThrow(() -> new NotFoundException("Dataset version not found: " + request.getDatasetVersionId()));
        datasetService.requireAccess(version.getDataset().getId(), actingUsername);

        List<Image> images = imageRepository.findAllById(request.getImageIds());
        if (images.size() != request.getImageIds().size()) {
            throw new NotFoundException("One or more images not found");
        }
        for (Long imageId : request.getImageIds()) {
            if (!datasetVersionImageRepository.existsByDatasetVersionIdAndImageId(version.getId(), imageId)) {
                throw new InvalidRequestException(
                        "Image " + imageId + " is not a member of dataset version " + version.getId());
            }
        }

        AnnotationJob job = new AnnotationJob();
        job.setModelVersion(model);
        job.setDataset(version.getDataset());
        job.setDatasetVersion(version);
        job.setJobType("VALIDATION");
        job.setInitiatedBy(request.getInitiatedBy());
        job.setStatus("RUNNING");
        job.setImageIds(request.getImageIds());
        // No accept/override step for a validation job (it's a direct comparison, not a
        // labeling workflow), so reviewStatus starts already resolved rather than PENDING.
        job.setReviewStatus("COMPLETED");
        AnnotationJob saved = annotationJobRepository.save(job);

        logAfterCommit(EventType.ANNOTATION_JOB_STARTED, saved,
                "Validation job started with model " + model.getModelTag() + " against " + images.size()
                        + " image(s) of human-labeled ground truth");
        dispatchValidationAfterCommit(saved.getId());

        return annotationJobMapper.toResponse(saved, 0, 0, 0);
    }

    // --- Mode B: "Annotate new images" ---

    private static final BigDecimal DEFAULT_CONFIDENCE_THRESHOLD = new BigDecimal("0.8500");

    @Transactional
    public AnnotationJobResponse submitNewImagesJob(Long modelVersionId, Long datasetId, String initiatedBy,
                                                     int gridRows, int gridCols,
                                                     BigDecimal confidenceThreshold, MultipartFile[] files) {
        ModelVersion model = modelVersionRepository.findById(modelVersionId)
                .orElseThrow(() -> new NotFoundException("Model not found: " + modelVersionId));
        if (!"ACTIVE".equals(model.getStatus())) {
            throw new InvalidRequestException(
                    "Model " + model.getModelTag() + " is not ACTIVE (status: " + model.getStatus() + ")");
        }
        if (files == null || files.length == 0) {
            throw new InvalidRequestException("At least one image file is required");
        }

        Path uploadDir = persistUpload(files);
        BigDecimal threshold = confidenceThreshold != null ? confidenceThreshold : DEFAULT_CONFIDENCE_THRESHOLD;

        AnnotationJob job = new AnnotationJob();
        job.setModelVersion(model);
        // job.dataset is set once the executor resolves the target Dataset; left null here would
        // violate no DB constraint (dataset is nullable, see AnnotationJob's own javadoc) but the
        // executor still needs a datasetId to work against, passed through directly below rather
        // than round-tripped through the entity before the async dispatch.
        job.setJobType("NEW_IMAGES");
        job.setInitiatedBy(initiatedBy);
        job.setStatus("RUNNING");
        job.setReviewStatus("PENDING");
        job.setImageIds(List.of());
        job.setConfidenceThreshold(threshold);
        AnnotationJob saved = annotationJobRepository.save(job);

        logAfterCommit(EventType.ANNOTATION_JOB_STARTED, saved,
                "New-images annotation job started with model " + model.getModelTag() + " on "
                        + files.length + " uploaded image(s)");
        dispatchNewImagesAfterCommit(saved.getId(), datasetId, uploadDir, gridRows, gridCols);

        return annotationJobMapper.toResponse(saved, 0, 0);
    }

    // --- Warm-start blank-import: annotate an existing dataset version's already-present images ---

    private static final int DEFAULT_EXISTING_VERSION_GRID_ROWS = 3;
    private static final int DEFAULT_EXISTING_VERSION_GRID_COLS = 4;

    @Transactional
    public AnnotationJobResponse submitExistingVersionJob(SubmitExistingVersionJobRequest request, String actingUsername) {
        ModelVersion model = modelVersionRepository.findById(request.getModelVersionId())
                .orElseThrow(() -> new NotFoundException("Model not found: " + request.getModelVersionId()));
        if (!"ACTIVE".equals(model.getStatus())) {
            throw new InvalidRequestException(
                    "Model " + model.getModelTag() + " is not ACTIVE (status: " + model.getStatus() + ")");
        }

        DatasetVersion version = datasetVersionRepository.findById(request.getDatasetVersionId())
                .orElseThrow(() -> new NotFoundException("Dataset version not found: " + request.getDatasetVersionId()));
        datasetService.requireAccess(version.getDataset().getId(), actingUsername);

        List<Image> images = imageRepository.findAllById(request.getImageIds());
        if (images.size() != request.getImageIds().size()) {
            throw new NotFoundException("One or more images not found");
        }
        for (Long imageId : request.getImageIds()) {
            if (!datasetVersionImageRepository.existsByDatasetVersionIdAndImageId(version.getId(), imageId)) {
                throw new InvalidRequestException(
                        "Image " + imageId + " is not a member of dataset version " + version.getId());
            }
        }

        int gridRows = request.getGridRows() != null ? request.getGridRows() : DEFAULT_EXISTING_VERSION_GRID_ROWS;
        int gridCols = request.getGridCols() != null ? request.getGridCols() : DEFAULT_EXISTING_VERSION_GRID_COLS;
        BigDecimal threshold = request.getConfidenceThreshold() != null
                ? request.getConfidenceThreshold() : DEFAULT_CONFIDENCE_THRESHOLD;

        AnnotationJob job = new AnnotationJob();
        job.setModelVersion(model);
        job.setDataset(version.getDataset());
        job.setDatasetVersion(version);
        job.setJobType("NEW_IMAGES");
        job.setCommitInPlace(true);
        job.setInitiatedBy(request.getInitiatedBy());
        job.setStatus("RUNNING");
        job.setReviewStatus("PENDING");
        job.setImageIds(request.getImageIds());
        job.setConfidenceThreshold(threshold);
        AnnotationJob saved = annotationJobRepository.save(job);

        logAfterCommit(EventType.ANNOTATION_JOB_STARTED, saved,
                "Existing-version annotation job started with model " + model.getModelTag() + " on "
                        + images.size() + " image(s) of dataset version " + version.getId());
        dispatchExistingVersionAfterCommit(saved.getId(), gridRows, gridCols);

        return annotationJobMapper.toResponse(saved, 0, 0);
    }

    /**
     * Commits a fully-reviewed existing-version job's accepted/overridden predictions directly onto
     * the job's already-set (real, pre-existing) dataset version, no new version is branched. The
     * counterpart to addToProject for jobs created via submitExistingVersionJob (commitInPlace=true).
     */
    @Transactional
    public AnnotationJobResponse applyToExistingVersion(Long jobId, String appliedBy) {
        AnnotationJob job = findOrThrow(jobId);
        if (!Boolean.TRUE.equals(job.getCommitInPlace())) {
            throw new InvalidRequestException("Only existing-version jobs can be applied in place");
        }
        if (!"COMPLETED".equals(job.getReviewStatus())) {
            throw new InvalidRequestException("All points must be reviewed (accepted or overridden) before applying to the version");
        }
        if (job.getAddedToProjectVersionId() != null) {
            throw new ConflictException("Job " + jobId + " was already applied to dataset version "
                    + job.getAddedToProjectVersionId());
        }
        DatasetVersion version = job.getDatasetVersion();
        if (version == null) {
            throw new InvalidRequestException("Job " + jobId + " has no target dataset version");
        }

        List<AnnotationPrediction> predictions = annotationPredictionRepository.findByAnnotationJobId(jobId);
        int written = 0;
        for (AnnotationPrediction prediction : predictions) {
            boolean accepted = Boolean.TRUE.equals(prediction.getAccepted());
            String finalCode = accepted ? prediction.getPredictedLabelCode() : prediction.getCorrectedLabelCode();
            if (finalCode == null) {
                continue; // shouldn't happen once reviewStatus == COMPLETED, but skip defensively
            }
            LabelDefinition label = resolveOrDeclareLabel(version, finalCode);

            AnnotationPoint point = annotationPointRepository
                    .findByImageIdAndPointIndex(prediction.getImage().getId(), prediction.getPointIndex())
                    .orElseGet(AnnotationPoint::new);
            point.setImage(prediction.getImage());
            point.setPointIndex(prediction.getPointIndex());
            point.setRowPosition(prediction.getRowPosition() != null ? prediction.getRowPosition() : 0);
            point.setColPosition(prediction.getColPosition() != null ? prediction.getColPosition() : 0);
            point.setLabelDefinition(label);
            point.setAnnotationSource(accepted ? "MODEL_PREDICTED" : "MODEL_CORRECTED");
            point.setConfidenceScore(accepted ? prediction.getConfidenceScore() : null);
            point.setAnnotatedBy(appliedBy);
            annotationPointRepository.save(point);
            written++;
        }

        version.setAnnotationCount(version.getAnnotationCount() + written);
        datasetVersionRepository.save(version);

        job.setAddedToProjectVersionId(version.getId());
        annotationJobRepository.save(job);

        return annotationJobMapper.toResponse(job, countTotal(jobId), countReviewed(jobId));
    }

    private void dispatchExistingVersionAfterCommit(Long jobId, int gridRows, int gridCols) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    annotationJobExecutor.executeExistingVersionAsync(jobId, gridRows, gridCols);
                }
            });
        } else {
            annotationJobExecutor.executeExistingVersionAsync(jobId, gridRows, gridCols);
        }
    }

    /** Mirrors DatasetImportService.persistUpload, minus the relative-path directory structure (flat file list here). */
    private Path persistUpload(MultipartFile[] files) {
        try {
            Path tempDir = Files.createTempDirectory("coral-annotation-upload-");
            for (MultipartFile file : files) {
                String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "image";
                Path resolved = tempDir.resolve(filename).normalize();
                if (!resolved.startsWith(tempDir)) {
                    throw new InvalidRequestException("Uploaded file name escapes upload directory: " + filename);
                }
                try (var in = file.getInputStream()) {
                    Files.copy(in, resolved, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return tempDir;
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read uploaded images", ex);
        }
    }

    /**
     * Commits a fully-reviewed NEW_IMAGES job's images and final point labels into a new, real
     * dataset version on the project, branched from the dataset's current latest version.
     */
    @Transactional
    public AnnotationJobResponse addToProject(Long jobId, AddToProjectRequest request) {
        AnnotationJob job = findOrThrow(jobId);
        if (!"NEW_IMAGES".equals(job.getJobType())) {
            throw new InvalidRequestException("Only NEW_IMAGES jobs can be added to a project");
        }
        if (!"COMPLETED".equals(job.getReviewStatus())) {
            throw new InvalidRequestException("All points must be reviewed (accepted or overridden) before adding to a project");
        }
        if (job.getAddedToProjectVersionId() != null) {
            throw new ConflictException("Job " + jobId + " was already added to the project as dataset version "
                    + job.getAddedToProjectVersionId());
        }
        if (job.getDataset() == null) {
            throw new InvalidRequestException("Job " + jobId + " has no staged images to add");
        }

        Long datasetId = job.getDataset().getId();
        DatasetVersion branchPoint = datasetVersionRepository.findTopByDatasetIdOrderByCreatedAtDesc(datasetId).orElse(null);

        CreateVersionRequest versionRequest = new CreateVersionRequest();
        versionRequest.setVersionTag(request.getVersionTag());
        versionRequest.setTriggerReason(TriggerReason.IMAGES_ADDED);
        versionRequest.setCreatedBy(request.getCreatedBy());
        versionRequest.setNotes(request.getNotes() != null ? request.getNotes()
                : "Added from annotation job #" + jobId + " (" + job.getImageIds().size() + " reviewed image(s))");
        DatasetVersionResponse versionResponse = datasetVersionService.createSnapshotBranchedFrom(datasetId, versionRequest, branchPoint);
        DatasetVersion newVersion = datasetVersionRepository.findById(versionResponse.getId())
                .orElseThrow(() -> new NotFoundException("Dataset version not found: " + versionResponse.getId()));

        List<Image> jobImages = imageRepository.findAllById(job.getImageIds());
        Set<Long> alreadyMember = new HashSet<>();
        for (Image image : jobImages) {
            if (datasetVersionImageRepository.existsByDatasetVersionIdAndImageId(newVersion.getId(), image.getId())) {
                alreadyMember.add(image.getId());
            }
        }
        List<DatasetVersionImage> newMembership = new ArrayList<>();
        for (Image image : jobImages) {
            if (!alreadyMember.contains(image.getId())) {
                DatasetVersionImage membership = new DatasetVersionImage();
                membership.setDatasetVersion(newVersion);
                membership.setImage(image);
                newMembership.add(membership);
            }
        }
        datasetVersionImageRepository.saveAll(newMembership);

        List<AnnotationPrediction> predictions = annotationPredictionRepository.findByAnnotationJobId(jobId);
        List<AnnotationPoint> newPoints = new ArrayList<>();
        for (AnnotationPrediction prediction : predictions) {
            boolean accepted = Boolean.TRUE.equals(prediction.getAccepted());
            String finalCode = accepted ? prediction.getPredictedLabelCode() : prediction.getCorrectedLabelCode();
            if (finalCode == null) {
                continue; // shouldn't happen once reviewStatus == COMPLETED, but skip defensively
            }
            LabelDefinition label = resolveOrDeclareLabel(newVersion, finalCode);

            AnnotationPoint point = new AnnotationPoint();
            point.setImage(prediction.getImage());
            point.setPointIndex(prediction.getPointIndex());
            point.setRowPosition(prediction.getRowPosition() != null ? prediction.getRowPosition() : 0);
            point.setColPosition(prediction.getColPosition() != null ? prediction.getColPosition() : 0);
            point.setLabelDefinition(label);
            point.setAnnotationSource(accepted ? "MODEL_PREDICTED" : "MODEL_CORRECTED");
            point.setConfidenceScore(accepted ? prediction.getConfidenceScore() : null);
            point.setAnnotatedBy(request.getCreatedBy());
            newPoints.add(point);
        }
        annotationPointRepository.saveAll(newPoints);

        newVersion.setImageCount(newVersion.getImageCount() + newMembership.size());
        newVersion.setAnnotationCount(newVersion.getAnnotationCount() + newPoints.size());
        datasetVersionRepository.save(newVersion);

        job.setAddedToProjectVersionId(newVersion.getId());
        annotationJobRepository.save(job);

        return annotationJobMapper.toResponse(job, countTotal(jobId), countReviewed(jobId));
    }

    private LabelDefinition resolveOrDeclareLabel(DatasetVersion version, String code) {
        return labelDefinitionRepository.findByDatasetVersionIdAndLabelCode(version.getId(), code)
                .orElseGet(() -> {
                    LabelDefinition label = new LabelDefinition();
                    label.setDatasetVersion(version);
                    label.setLabelCode(code);
                    label.setLabelName(code);
                    return labelDefinitionRepository.save(label);
                });
    }

    /**
     * Resolves the owning dataset id for a job, falling back to datasetVersion.dataset when
     * job.dataset is null (legacy NEW_IMAGES rows before the executor resolves a target dataset,
     * or other pre-backfill rows, see AnnotationJob's own field javadoc).
     */
    @Transactional(readOnly = true)
    public Long getDatasetIdForJob(Long jobId) {
        AnnotationJob job = findOrThrow(jobId);
        if (job.getDataset() != null) {
            return job.getDataset().getId();
        }
        if (job.getDatasetVersion() != null) {
            return job.getDatasetVersion().getDataset().getId();
        }
        throw new NotFoundException("Annotation job " + jobId + " has no resolvable owning dataset");
    }

    @Transactional(readOnly = true)
    public AnnotationJobResponse getJobById(Long id) {
        AnnotationJob job = findOrThrow(id);
        if ("VALIDATION".equals(job.getJobType())) {
            return annotationJobMapper.toResponse(job, countTotal(id), countReviewed(id), countCorrect(id));
        }
        return annotationJobMapper.toResponse(job, countTotal(id), countReviewed(id));
    }

    @Transactional(readOnly = true)
    public Page<AnnotationJobResponse> getJobs(Long datasetId, Pageable pageable) {
        return annotationJobRepository.findByDataset_IdOrderByInitiatedAtDesc(datasetId, pageable)
                .map(job -> "VALIDATION".equals(job.getJobType())
                        ? annotationJobMapper.toResponse(job, countTotal(job.getId()), countReviewed(job.getId()), countCorrect(job.getId()))
                        : annotationJobMapper.toResponse(job, countTotal(job.getId()), countReviewed(job.getId())));
    }

    /** Scoped jobs list for a specific model's own detail page, replaces the old dataset-wide list page. */
    @Transactional(readOnly = true)
    public Page<AnnotationJobResponse> getJobsByModel(Long modelVersionId, Pageable pageable) {
        return annotationJobRepository.findByModelVersion_IdOrderByInitiatedAtDesc(modelVersionId, pageable)
                .map(job -> "VALIDATION".equals(job.getJobType())
                        ? annotationJobMapper.toResponse(job, countTotal(job.getId()), countReviewed(job.getId()), countCorrect(job.getId()))
                        : annotationJobMapper.toResponse(job, countTotal(job.getId()), countReviewed(job.getId())));
    }

    /**
     * Derives the job's dataset server-side from the submitted images rather than trusting a
     * client-supplied dataset id, every image must belong to the same dataset (via its
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
        if ("VALIDATION".equals(job.getJobType())) {
            throw new InvalidRequestException(
                    "Validation jobs cannot be reviewed; they are a direct model-versus-human-label comparison, not a labeling workflow");
        }
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

    /**
     * Bulk-accepts every still-pending prediction on a job at its current predicted label - the
     * "Accept All Labels" action, equivalent to calling reviewPrediction({accepted: true}) on each
     * remaining pending prediction individually, in one request.
     */
    @Transactional
    public AnnotationJobResponse acceptAllPredictions(Long jobId, String reviewedBy) {
        AnnotationJob job = findOrThrow(jobId);
        if ("VALIDATION".equals(job.getJobType())) {
            throw new InvalidRequestException(
                    "Validation jobs cannot be reviewed; they are a direct model-versus-human-label comparison, not a labeling workflow");
        }
        List<AnnotationPrediction> pending = annotationPredictionRepository.findByAnnotationJobIdAndAcceptedIsNull(jobId);
        Instant now = Instant.now();
        for (AnnotationPrediction prediction : pending) {
            prediction.setAccepted(true);
            prediction.setReviewedBy(reviewedBy);
            prediction.setReviewedAt(now);
        }
        annotationPredictionRepository.saveAll(pending);

        updateReviewStatus(job);

        if ("VALIDATION".equals(job.getJobType())) {
            return annotationJobMapper.toResponse(job, countTotal(jobId), countReviewed(jobId), countCorrect(jobId));
        }
        return annotationJobMapper.toResponse(job, countTotal(jobId), countReviewed(jobId));
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

    private long countCorrect(Long jobId) {
        return annotationPredictionRepository.countByAnnotationJobIdAndAgreesWithHumanTrue(jobId);
    }

    private AnnotationJob findOrThrow(Long id) {
        return annotationJobRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Annotation job not found: " + id));
    }

    /**
     * The @Async executor lives on a separate bean (AnnotationJobExecutor) rather than a method on
     * this class, mirrors TrainingJobService/TrainingJobExecutor's split, needed because Spring's
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

    private void dispatchValidationAfterCommit(Long jobId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    annotationJobExecutor.executeValidationAsync(jobId);
                }
            });
        } else {
            annotationJobExecutor.executeValidationAsync(jobId);
        }
    }

    private void dispatchNewImagesAfterCommit(Long jobId, Long datasetId, Path uploadDir, int gridRows, int gridCols) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    annotationJobExecutor.executeNewImagesAsync(jobId, datasetId, uploadDir, gridRows, gridCols);
                }
            });
        } else {
            annotationJobExecutor.executeNewImagesAsync(jobId, datasetId, uploadDir, gridRows, gridCols);
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
