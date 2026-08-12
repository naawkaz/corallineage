package com.corallineage.backend.annotation;

import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.logging.AnnotationEventLoggerService;
import com.corallineage.backend.logging.EventType;
import com.corallineage.backend.registry.model.ModelVersion;
import com.corallineage.backend.training.AnnotateImageInput;
import com.corallineage.backend.training.AnnotateResult;
import com.corallineage.backend.training.PythonMlServiceClient;
import com.corallineage.backend.versioning.dataset.Image;
import com.corallineage.backend.versioning.dataset.ImageRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnnotationJobExecutor {

    private final AnnotationJobRepository annotationJobRepository;
    private final AnnotationPredictionRepository annotationPredictionRepository;
    private final ImageRepository imageRepository;
    private final PythonMlServiceClient pythonMlServiceClient;
    private final AnnotationEventLoggerService annotationEventLoggerService;
    private final Path workingDirectory;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnnotationJobExecutor(AnnotationJobRepository annotationJobRepository,
                                 AnnotationPredictionRepository annotationPredictionRepository,
                                 ImageRepository imageRepository,
                                 PythonMlServiceClient pythonMlServiceClient,
                                 AnnotationEventLoggerService annotationEventLoggerService,
                                 @Value("${app.dvc.working-directory}") Path workingDirectory) {
        this.annotationJobRepository = annotationJobRepository;
        this.annotationPredictionRepository = annotationPredictionRepository;
        this.imageRepository = imageRepository;
        this.pythonMlServiceClient = pythonMlServiceClient;
        this.annotationEventLoggerService = annotationEventLoggerService;
        this.workingDirectory = workingDirectory;
    }

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
}
