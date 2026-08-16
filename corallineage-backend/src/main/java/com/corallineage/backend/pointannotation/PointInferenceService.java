package com.corallineage.backend.pointannotation;

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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Point-level inference: crops a patch around each pending point (same crop math training uses)
 * and reuses {@link PythonMlServiceClient#annotate} completely unchanged — each patch is sent as
 * its own synthetic "image" (keyed by the {@link GeneratedPoint}'s own id), since that endpoint
 * already returns one prediction per submitted image with a confidence score.
 */
@Service
public class PointInferenceService {

    private final GeneratedPointRepository generatedPointRepository;
    private final LabelDefinitionRepository labelDefinitionRepository;
    private final AnnotationPointBridge annotationPointBridge;
    private final PythonMlServiceClient pythonMlServiceClient;
    private final Path workingDirectory;
    private final int patchSize;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PointInferenceService(GeneratedPointRepository generatedPointRepository,
                                 LabelDefinitionRepository labelDefinitionRepository,
                                 AnnotationPointBridge annotationPointBridge,
                                 PythonMlServiceClient pythonMlServiceClient,
                                 @Value("${app.dvc.working-directory}") Path workingDirectory,
                                 @Value("${app.training.patch-size:64}") int patchSize) {
        this.generatedPointRepository = generatedPointRepository;
        this.labelDefinitionRepository = labelDefinitionRepository;
        this.annotationPointBridge = annotationPointBridge;
        this.pythonMlServiceClient = pythonMlServiceClient;
        this.workingDirectory = workingDirectory;
        this.patchSize = patchSize;
    }

    void runInference(PointAnnotationJob job) {
        List<GeneratedPoint> pending = generatedPointRepository.findByJobIdAndSeedFalseAndStatus(job.getId(), "UNLABELED");
        if (pending.isEmpty()) {
            return;
        }

        ModelVersion model = job.getModelVersion();
        Map<String, String> classMappings = parseClassMappings(model);

        Path patchDir = workingDirectory.resolve("training-data").resolve("point-annotation-" + job.getId());
        try {
            Files.createDirectories(patchDir);
            Map<Long, GeneratedPoint> pointsById = new HashMap<>();
            Map<Long, BufferedImage> imageCache = new HashMap<>();
            List<AnnotateImageInput> inputs = new ArrayList<>();

            for (GeneratedPoint point : pending) {
                pointsById.put(point.getId(), point);
                BufferedImage source = imageCache.computeIfAbsent(point.getImage().getId(),
                        id -> ImagePatchUtil.readImage(workingDirectory, point.getImage().getFilePath()));
                BufferedImage patch = ImagePatchUtil.cropPatch(source, point.getRowPosition(), point.getColPosition(), patchSize);
                Path patchFile = patchDir.resolve("point-" + point.getId() + ".jpg");
                ImageIO.write(patch, "jpg", patchFile.toFile());
                inputs.add(new AnnotateImageInput(point.getId(), patchFile.toString()));
            }

            AnnotateResult result = pythonMlServiceClient.annotate(
                    model.getArchitecture(), model.getWeightsPath(), classMappings, inputs);

            applyPredictions(job, result, pointsById);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to prepare point patches for inference", ex);
        } finally {
            deleteRecursivelyQuietly(patchDir);
        }
    }

    private void applyPredictions(PointAnnotationJob job, AnnotateResult result, Map<Long, GeneratedPoint> pointsById) {
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
