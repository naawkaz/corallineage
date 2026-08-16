package com.corallineage.backend.annotation;

import com.corallineage.backend.datasets.Dataset;
import com.corallineage.backend.datasets.DatasetRepository;
import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.logging.AnnotationEventLoggerService;
import com.corallineage.backend.logging.EventType;
import com.corallineage.backend.registry.model.ModelVersion;
import com.corallineage.backend.training.AnnotateImageInput;
import com.corallineage.backend.training.AnnotateResult;
import com.corallineage.backend.training.PythonMlServiceClient;
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
import com.corallineage.backend.versioning.dataset.TriggerReason;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnnotationJobExecutor {

    private final AnnotationJobRepository annotationJobRepository;
    private final AnnotationPredictionRepository annotationPredictionRepository;
    private final ImageRepository imageRepository;
    private final AnnotationPointRepository annotationPointRepository;
    private final DatasetRepository datasetRepository;
    private final DatasetVersionRepository datasetVersionRepository;
    private final DatasetVersionImageRepository datasetVersionImageRepository;
    private final DatasetVersionService datasetVersionService;
    private final PythonMlServiceClient pythonMlServiceClient;
    private final AnnotationEventLoggerService annotationEventLoggerService;
    private final Path workingDirectory;
    private final String dataDir;
    private final int patchSize;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnnotationJobExecutor(AnnotationJobRepository annotationJobRepository,
                                 AnnotationPredictionRepository annotationPredictionRepository,
                                 ImageRepository imageRepository,
                                 AnnotationPointRepository annotationPointRepository,
                                 DatasetRepository datasetRepository,
                                 DatasetVersionRepository datasetVersionRepository,
                                 DatasetVersionImageRepository datasetVersionImageRepository,
                                 DatasetVersionService datasetVersionService,
                                 PythonMlServiceClient pythonMlServiceClient,
                                 AnnotationEventLoggerService annotationEventLoggerService,
                                 @Value("${app.dvc.working-directory}") Path workingDirectory,
                                 @Value("${app.dvc.data-dir}") String dataDir,
                                 @Value("${app.training.patch-size:64}") int patchSize) {
        this.annotationJobRepository = annotationJobRepository;
        this.annotationPredictionRepository = annotationPredictionRepository;
        this.imageRepository = imageRepository;
        this.annotationPointRepository = annotationPointRepository;
        this.datasetRepository = datasetRepository;
        this.datasetVersionRepository = datasetVersionRepository;
        this.datasetVersionImageRepository = datasetVersionImageRepository;
        this.datasetVersionService = datasetVersionService;
        this.pythonMlServiceClient = pythonMlServiceClient;
        this.annotationEventLoggerService = annotationEventLoggerService;
        this.workingDirectory = workingDirectory;
        this.dataDir = dataDir;
        this.patchSize = patchSize;
    }

    /** Legacy whole-image path (kept for backward compatibility; see AnnotationJobService). */
    @Async
    @Transactional
    public void executeAnnotationAsync(Long jobId) {
        AnnotationJob job = annotationJobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Annotation job not found: " + jobId));
        try {
            ModelVersion model = job.getModelVersion();
            List<Image> images = imageRepository.findAllById(job.getImageIds());
            Map<Long, Image> imagesById = new HashMap<>();
            for (Image image : images) {
                imagesById.put(image.getId(), image);
            }

            List<AnnotateImageInput> imageInputs = images.stream()
                    .map(image -> new AnnotateImageInput(image.getId(), workingDirectory.resolve(image.getFilePath()).toString()))
                    .toList();

            Map<String, String> classMappings = objectMapper.readValue(
                    model.getClassMappings(), new TypeReference<Map<String, String>>() {
                    });

            AnnotateResult result = pythonMlServiceClient.annotate(
                    model.getArchitecture(), model.getWeightsPath(), classMappings, imageInputs);

            List<AnnotationPrediction> predictions = new ArrayList<>();
            for (AnnotateResult.AnnotatedImage annotatedImage : result.predictions()) {
                Image image = imagesById.get(annotatedImage.imageId());
                for (AnnotateResult.AnnotatedPoint point : annotatedImage.points()) {
                    AnnotationPrediction prediction = new AnnotationPrediction();
                    prediction.setAnnotationJob(job);
                    prediction.setImage(image);
                    prediction.setPointIndex(point.pointIndex());
                    prediction.setPredictedLabelCode(point.predictedLabelCode());
                    prediction.setConfidenceScore(point.confidenceScore() != null
                            ? point.confidenceScore().setScale(4, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO);
                    predictions.add(prediction);
                }
            }
            annotationPredictionRepository.saveAll(predictions);

            job.setStatus("COMPLETED");
            job.setCompletedAt(Instant.now());
            annotationJobRepository.save(job);

            annotationEventLoggerService.log(EventType.MODEL_APPLIED, null, model, job.getInitiatedBy(),
                    "Model " + model.getModelTag() + " applied to " + images.size() + " image(s), "
                            + predictions.size() + " prediction(s) produced");
        } catch (Exception ex) {
            job.setStatus("FAILED");
            job.setErrorMessage(ex.getMessage());
            job.setCompletedAt(Instant.now());
            annotationJobRepository.save(job);
        }
    }

    /**
     * Mode A ("Validate against human labels"): crops a patch around each real, existing
     * {@code AnnotationPoint} (source HUMAN) on the job's images, classifies each patch through
     * the model (same trick as pointannotation.PointInferenceService, every patch is its own
     * synthetic "image" to the unmodified /annotate endpoint), and records agreement/disagreement
     * with the human label. No review step: this job is a comparison, not a labeling workflow.
     */
    @Async
    @Transactional
    public void executeValidationAsync(Long jobId) {
        AnnotationJob job = annotationJobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Annotation job not found: " + jobId));
        Path patchDir = workingDirectory.resolve("training-data").resolve("annotation-validation-" + jobId);
        try {
            ModelVersion model = job.getModelVersion();
            List<Image> images = imageRepository.findAllById(job.getImageIds());

            Map<Long, AnnotationPoint> pointsById = new HashMap<>();
            List<AnnotateImageInput> inputs = new ArrayList<>();
            Files.createDirectories(patchDir);

            for (Image image : images) {
                List<AnnotationPoint> humanPoints = annotationPointRepository.findByImageIdAndAnnotationSource(image.getId(), "HUMAN");
                if (humanPoints.isEmpty()) {
                    continue;
                }
                BufferedImage source = AnnotationPatchUtil.readImage(workingDirectory, image.getFilePath());
                for (AnnotationPoint point : humanPoints) {
                    pointsById.put(point.getId(), point);
                    BufferedImage patch = AnnotationPatchUtil.cropPatch(source, point.getRowPosition(), point.getColPosition(), patchSize);
                    Path patchFile = patchDir.resolve("point-" + point.getId() + ".jpg");
                    ImageIO.write(patch, "jpg", patchFile.toFile());
                    inputs.add(new AnnotateImageInput(point.getId(), patchFile.toString()));
                }
            }

            List<AnnotationPrediction> predictions = new ArrayList<>();
            if (!inputs.isEmpty()) {
                Map<String, String> classMappings = objectMapper.readValue(
                        model.getClassMappings(), new TypeReference<Map<String, String>>() {
                        });
                AnnotateResult result = pythonMlServiceClient.annotate(
                        model.getArchitecture(), model.getWeightsPath(), classMappings, inputs);

                for (AnnotateResult.AnnotatedImage annotated : result.predictions()) {
                    AnnotationPoint point = pointsById.get(annotated.imageId());
                    if (point == null || annotated.points().isEmpty()) {
                        continue;
                    }
                    AnnotateResult.AnnotatedPoint predicted = annotated.points().get(0);

                    AnnotationPrediction prediction = new AnnotationPrediction();
                    prediction.setAnnotationJob(job);
                    prediction.setImage(point.getImage());
                    prediction.setPointIndex(point.getPointIndex());
                    prediction.setPredictedLabelCode(predicted.predictedLabelCode());
                    prediction.setConfidenceScore(predicted.confidenceScore() != null
                            ? predicted.confidenceScore().setScale(4, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO);
                    prediction.setRowPosition(point.getRowPosition());
                    prediction.setColPosition(point.getColPosition());
                    String humanLabelCode = point.getLabelDefinition() != null ? point.getLabelDefinition().getLabelCode() : null;
                    prediction.setAgreesWithHuman(humanLabelCode != null && humanLabelCode.equals(predicted.predictedLabelCode()));
                    predictions.add(prediction);
                }
                annotationPredictionRepository.saveAll(predictions);
            }

            job.setStatus("COMPLETED");
            job.setCompletedAt(Instant.now());
            annotationJobRepository.save(job);

            long correct = predictions.stream().filter(p -> Boolean.TRUE.equals(p.getAgreesWithHuman())).count();
            annotationEventLoggerService.log(EventType.MODEL_APPLIED, null, model, job.getInitiatedBy(),
                    "Validation job compared model " + model.getModelTag() + " against " + predictions.size()
                            + " human-labeled point(s): " + correct + " agree, " + (predictions.size() - correct) + " disagree");
        } catch (Exception ex) {
            job.setStatus("FAILED");
            job.setErrorMessage(ex.getMessage());
            job.setCompletedAt(Instant.now());
            annotationJobRepository.save(job);
        } finally {
            deleteRecursivelyQuietly(patchDir);
        }
    }

    /**
     * Mode B ("Annotate new images"): copies the uploaded raw files into the DVC-tracked working
     * directory, creates a real staging {@link DatasetVersion} on the target dataset to hold them
     * (Image.originDatasetVersion cannot be null, see that entity's javadoc), generates an evenly
     * spaced grid of points per image (mirrors pointannotation.PointGenerationService, package-private
     * there so duplicated here per this codebase's established small-utility-duplication convention),
     * classifies each patch through the model, and leaves every point PENDING review (accept/override,
     * exactly like the legacy single-point flow but now genuinely multi-point). The staging version
     * is real (its own DVC commit, satisfies every FK) but is not what the researcher browses as "their"
     * project data until "Add to Project" branches a second, real version from it after review.
     */
    @Async
    @Transactional
    public void executeNewImagesAsync(Long jobId, Long datasetId, Path uploadDir, int pointsPerImage) {
        AnnotationJob job = annotationJobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Annotation job not found: " + jobId));
        Path patchDir = workingDirectory.resolve("training-data").resolve("annotation-newimages-" + jobId);
        try {
            Dataset dataset = datasetRepository.findById(datasetId)
                    .orElseThrow(() -> new NotFoundException("Dataset not found: " + datasetId));

            List<Path> uploadedFiles;
            try (var stream = Files.list(uploadDir)) {
                uploadedFiles = stream.filter(Files::isRegularFile).sorted().toList();
            }
            if (uploadedFiles.isEmpty()) {
                throw new IllegalStateException("No image files found in upload");
            }

            Path dataPath = workingDirectory.resolve(dataDir);
            Files.createDirectories(dataPath);
            List<String> filenames = new ArrayList<>();
            for (Path file : uploadedFiles) {
                String filename = file.getFileName().toString();
                Files.copy(file, dataPath.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
                filenames.add(filename);
            }

            CreateVersionRequest versionRequest = new CreateVersionRequest();
            versionRequest.setVersionTag("staging-annotation-job-" + jobId);
            versionRequest.setTriggerReason(TriggerReason.IMAGES_ADDED);
            versionRequest.setCreatedBy(job.getInitiatedBy());
            versionRequest.setNotes("Staging area for annotation job #" + jobId
                    + ", holding " + filenames.size() + " newly uploaded image(s) pending review before being added to the project.");
            DatasetVersionResponse versionResponse = datasetVersionService.createSnapshot(datasetId, versionRequest);
            DatasetVersion stagingVersion = datasetVersionRepository.findById(versionResponse.getId())
                    .orElseThrow(() -> new NotFoundException("Dataset version not found: " + versionResponse.getId()));

            List<Image> imageEntities = new ArrayList<>();
            for (String filename : filenames) {
                Image image = new Image();
                image.setOriginDatasetVersion(stagingVersion);
                image.setFilename(filename);
                image.setFilePath(dataDir + "/" + filename);
                imageEntities.add(image);
            }
            List<Image> savedImages = imageRepository.saveAll(imageEntities);

            List<DatasetVersionImage> membership = new ArrayList<>();
            for (Image image : savedImages) {
                DatasetVersionImage m = new DatasetVersionImage();
                m.setDatasetVersion(stagingVersion);
                m.setImage(image);
                membership.add(m);
            }
            datasetVersionImageRepository.saveAll(membership);

            stagingVersion.setImageCount(stagingVersion.getImageCount() + savedImages.size());
            datasetVersionRepository.save(stagingVersion);

            job.setDataset(dataset);
            job.setDatasetVersion(stagingVersion);
            job.setImageIds(savedImages.stream().map(Image::getId).toList());
            annotationJobRepository.save(job);

            // Placeholder predictions first (so each patch has a real, persisted id to key the
            // /annotate batch call by), then overwritten with real predicted labels below.
            Files.createDirectories(patchDir);
            List<AnnotationPrediction> pending = new ArrayList<>();
            Map<Image, BufferedImage> sourceByImage = new HashMap<>();
            for (Image image : savedImages) {
                BufferedImage source = AnnotationPatchUtil.readImage(workingDirectory, image.getFilePath());
                sourceByImage.put(image, source);
                List<int[]> coordinates = AnnotationPatchUtil.computeGridCoordinates(source.getWidth(), source.getHeight(), pointsPerImage);
                int pointIndex = 0;
                for (int[] coordinate : coordinates) {
                    AnnotationPrediction prediction = new AnnotationPrediction();
                    prediction.setAnnotationJob(job);
                    prediction.setImage(image);
                    prediction.setPointIndex(pointIndex++);
                    prediction.setRowPosition(coordinate[0]);
                    prediction.setColPosition(coordinate[1]);
                    prediction.setPredictedLabelCode("");
                    prediction.setConfidenceScore(BigDecimal.ZERO);
                    pending.add(prediction);
                }
            }
            List<AnnotationPrediction> savedPredictions = annotationPredictionRepository.saveAll(pending);

            ModelVersion model = job.getModelVersion();
            List<AnnotateImageInput> inputs = new ArrayList<>();
            for (AnnotationPrediction prediction : savedPredictions) {
                BufferedImage source = sourceByImage.get(prediction.getImage());
                BufferedImage patch = AnnotationPatchUtil.cropPatch(source, prediction.getRowPosition(), prediction.getColPosition(), patchSize);
                Path patchFile = patchDir.resolve("point-" + prediction.getId() + ".jpg");
                ImageIO.write(patch, "jpg", patchFile.toFile());
                inputs.add(new AnnotateImageInput(prediction.getId(), patchFile.toString()));
            }

            if (!inputs.isEmpty()) {
                Map<String, String> classMappings = objectMapper.readValue(
                        model.getClassMappings(), new TypeReference<Map<String, String>>() {
                        });
                AnnotateResult result = pythonMlServiceClient.annotate(
                        model.getArchitecture(), model.getWeightsPath(), classMappings, inputs);

                Map<Long, AnnotationPrediction> predictionsById = new HashMap<>();
                for (AnnotationPrediction prediction : savedPredictions) {
                    predictionsById.put(prediction.getId(), prediction);
                }
                for (AnnotateResult.AnnotatedImage annotated : result.predictions()) {
                    AnnotationPrediction prediction = predictionsById.get(annotated.imageId());
                    if (prediction == null || annotated.points().isEmpty()) {
                        continue;
                    }
                    AnnotateResult.AnnotatedPoint predicted = annotated.points().get(0);
                    prediction.setPredictedLabelCode(predicted.predictedLabelCode());
                    prediction.setConfidenceScore(predicted.confidenceScore() != null
                            ? predicted.confidenceScore().setScale(4, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO);
                }
                annotationPredictionRepository.saveAll(savedPredictions);
            }

            job.setStatus("COMPLETED");
            job.setCompletedAt(Instant.now());
            annotationJobRepository.save(job);

            annotationEventLoggerService.log(EventType.MODEL_APPLIED, null, model, job.getInitiatedBy(),
                    "Model " + model.getModelTag() + " applied to " + savedImages.size() + " newly uploaded image(s), "
                            + savedPredictions.size() + " point prediction(s) ready for review");
        } catch (Exception ex) {
            job.setStatus("FAILED");
            job.setErrorMessage(ex.getMessage());
            job.setCompletedAt(Instant.now());
            annotationJobRepository.save(job);
        } finally {
            deleteRecursivelyQuietly(patchDir);
            deleteRecursivelyQuietly(uploadDir);
        }
    }

    private void deleteRecursivelyQuietly(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort temp directory cleanup
                }
            });
        } catch (IOException ignored) {
            // best-effort temp directory cleanup
        }
    }
}
