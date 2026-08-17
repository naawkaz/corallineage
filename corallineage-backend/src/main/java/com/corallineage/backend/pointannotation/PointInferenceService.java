package com.corallineage.backend.pointannotation;

import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.registry.model.ModelVersion;
import com.corallineage.backend.training.AnnotateImageInput;
import com.corallineage.backend.training.AnnotateResult;
import com.corallineage.backend.training.PythonMlServiceClient;
import com.corallineage.backend.versioning.dataset.LabelDefinition;
import com.corallineage.backend.versioning.dataset.LabelDefinitionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Point-level inference: crops a patch around each pending point (same crop math training uses)
 * and drives {@link PythonMlServiceClient#startAnnotate}/{@code pollAnnotateStatus} through
 * {@link AutoAnnotationExecutor}, since each patch is sent as its own synthetic "image" (keyed by
 * the {@link GeneratedPoint}'s own id), and that endpoint now runs the batch on a background
 * thread on the Python side rather than blocking the caller.
 *
 * Split into two @Transactional entry points (prepareAndStartAnnotate, applyPredictions) rather
 * than one long runInference call, since AutoAnnotationExecutor's poll loop between them can run
 * for seconds to minutes and must not hold a single transaction (and DB connection) open across
 * that whole span. See PointAnnotationJobStatusService's class comment for the same reasoning
 * applied to the job's status writes.
 */
@Service
public class PointInferenceService {

    private final PointAnnotationJobRepository pointAnnotationJobRepository;
    private final GeneratedPointRepository generatedPointRepository;
    private final LabelDefinitionRepository labelDefinitionRepository;
    private final AnnotationPointBridge annotationPointBridge;
    private final PythonMlServiceClient pythonMlServiceClient;
    private final Path workingDirectory;
    private final int patchSize;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PointInferenceService(PointAnnotationJobRepository pointAnnotationJobRepository,
                                 GeneratedPointRepository generatedPointRepository,
                                 LabelDefinitionRepository labelDefinitionRepository,
                                 AnnotationPointBridge annotationPointBridge,
                                 PythonMlServiceClient pythonMlServiceClient,
                                 @Value("${app.dvc.working-directory}") Path workingDirectory,
                                 @Value("${app.training.patch-size:64}") int patchSize) {
        this.pointAnnotationJobRepository = pointAnnotationJobRepository;
        this.generatedPointRepository = generatedPointRepository;
        this.labelDefinitionRepository = labelDefinitionRepository;
        this.annotationPointBridge = annotationPointBridge;
        this.pythonMlServiceClient = pythonMlServiceClient;
        this.workingDirectory = workingDirectory;
        this.patchSize = patchSize;
    }

    /** Result of kicking off a batch: whether there was anything to send, and (if so) the patch
     * directory the caller must clean up once the batch (polled elsewhere) finishes. */
    public record AnnotationKickoff(boolean hasPendingWork, Path patchDir) {
    }

    /**
     * Crops every pending point's patch (unchanged patch-cropping/temp-file-writing logic) and
     * starts the Python batch. Returns immediately once Flask acknowledges the batch started;
     * does not wait for predictions.
     */
    @Transactional
    public AnnotationKickoff prepareAndStartAnnotate(Long jobId) {
        PointAnnotationJob job = pointAnnotationJobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Point annotation job not found: " + jobId));
        List<GeneratedPoint> pending = generatedPointRepository.findByJobIdAndSeedFalseAndStatus(jobId, "UNLABELED");
        if (pending.isEmpty()) {
            return new AnnotationKickoff(false, null);
        }

        ModelVersion model = job.getModelVersion();
        Map<String, String> classMappings = parseClassMappings(model);

        Path patchDir = workingDirectory.resolve("training-data").resolve("point-annotation-" + jobId);
        try {
            Files.createDirectories(patchDir);
            Map<Long, BufferedImage> imageCache = new HashMap<>();
            List<AnnotateImageInput> inputs = new ArrayList<>();

            for (GeneratedPoint point : pending) {
                BufferedImage source = imageCache.computeIfAbsent(point.getImage().getId(),
                        id -> ImagePatchUtil.readImage(workingDirectory, point.getImage().getFilePath()));
                BufferedImage patch = ImagePatchUtil.cropPatch(source, point.getRowPosition(), point.getColPosition(), patchSize);
                Path patchFile = patchDir.resolve("point-" + point.getId() + ".jpg");
                ImageIO.write(patch, "jpg", patchFile.toFile());
                inputs.add(new AnnotateImageInput(point.getId(), patchFile.toString()));
            }

            pythonMlServiceClient.startAnnotate(model.getArchitecture(), model.getWeightsPath(), classMappings, inputs, jobId);
            return new AnnotationKickoff(true, patchDir);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to prepare point patches for inference", ex);
        }
    }

    /**
     * Applies a completed batch's predictions once AutoAnnotationExecutor's poll loop sees
     * COMPLETED. Re-fetches the points fresh by id rather than reusing entities from
     * prepareAndStartAnnotate's now-closed transaction (those would be detached after minutes of
     * polling in between).
     */
    @Transactional
    public void applyPredictions(Long jobId, AnnotateResult result) {
        PointAnnotationJob job = pointAnnotationJobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Point annotation job not found: " + jobId));

        List<Long> pointIds = result.predictions().stream().map(AnnotateResult.AnnotatedImage::imageId).toList();
        Map<Long, GeneratedPoint> pointsById = new HashMap<>();
        for (GeneratedPoint point : generatedPointRepository.findAllById(pointIds)) {
            pointsById.put(point.getId(), point);
        }

        List<GeneratedPoint> toSave = new ArrayList<>();
        for (AnnotateResult.AnnotatedImage annotated : result.predictions()) {
            GeneratedPoint point = pointsById.get(annotated.imageId());
            if (point == null || annotated.points().isEmpty()) {
                continue;
            }
            AnnotateResult.AnnotatedPoint prediction = annotated.points().get(0);
            LabelDefinition predictedLabel = labelDefinitionRepository
                    .findByDatasetVersionIdAndLabelCode(job.getDatasetVersion().getId(), prediction.predictedLabelCode())
                    .orElse(null);
            // A predicted code no longer in the version's vocabulary (e.g. deprecated since training) —
            // leave the point UNLABELED rather than materializing a dangling reference; it can still be
            // labeled manually.
            if (predictedLabel == null) {
                continue;
            }

            point.setPredictedLabelDefinition(predictedLabel);
            point.setConfidenceScore(prediction.confidenceScore());
            boolean aboveThreshold = prediction.confidenceScore() != null
                    && prediction.confidenceScore().compareTo(job.getConfidenceThreshold()) >= 0;
            if (aboveThreshold) {
                point.setFinalLabelDefinition(predictedLabel);
                point.setStatus("CONFIRMED");
                annotationPointBridge.upsert(point, predictedLabel, "MODEL_PREDICTED",
                        job.getModelVersion().getModelTag(), prediction.confidenceScore());
            } else {
                point.setStatus("MODEL_PREDICTED");
            }
            toSave.add(point);
        }
        generatedPointRepository.saveAll(toSave);
    }

    private Map<String, String> parseClassMappings(ModelVersion model) {
        try {
            return objectMapper.readValue(model.getClassMappings(), new TypeReference<Map<String, String>>() {
            });
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to parse class mappings for model " + model.getModelTag(), ex);
        }
    }
}
