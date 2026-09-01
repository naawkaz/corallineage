package com.corallineage.backend.datasets.importservice;

import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.logging.AnnotationEventLoggerService;
import com.corallineage.backend.logging.EventType;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DatasetImportExecutor {

    private final ImportJobRepository importJobRepository;
    private final DatasetVersionRepository datasetVersionRepository;
    private final DatasetVersionService datasetVersionService;
    private final LabelDefinitionRepository labelDefinitionRepository;
    private final ImageRepository imageRepository;
    private final DatasetVersionImageRepository datasetVersionImageRepository;
    private final AnnotationPointRepository annotationPointRepository;
    private final LabelCodeResolver labelCodeResolver;
    private final AnnotationEventLoggerService annotationEventLoggerService;
    private final MlcFormatParser mlcFormatParser;
    private final CoralNetCsvParser coralNetCsvParser;
    private final CocoJsonParser cocoJsonParser;
    private final BlankFormatParser blankFormatParser;
    private final Path workingDirectory;
    private final String dataDir;

    public DatasetImportExecutor(ImportJobRepository importJobRepository,
            DatasetVersionRepository datasetVersionRepository,
            DatasetVersionService datasetVersionService,
            LabelDefinitionRepository labelDefinitionRepository,
            ImageRepository imageRepository,
            DatasetVersionImageRepository datasetVersionImageRepository,
            AnnotationPointRepository annotationPointRepository,
            LabelCodeResolver labelCodeResolver,
            AnnotationEventLoggerService annotationEventLoggerService,
            MlcFormatParser mlcFormatParser,
            CoralNetCsvParser coralNetCsvParser,
            CocoJsonParser cocoJsonParser,
            BlankFormatParser blankFormatParser,
            @Value("${app.dvc.working-directory}") Path workingDirectory,
            @Value("${app.dvc.data-dir}") String dataDir) {
        this.importJobRepository = importJobRepository;
        this.datasetVersionRepository = datasetVersionRepository;
        this.datasetVersionService = datasetVersionService;
        this.labelDefinitionRepository = labelDefinitionRepository;
        this.imageRepository = imageRepository;
        this.datasetVersionImageRepository = datasetVersionImageRepository;
        this.annotationPointRepository = annotationPointRepository;
        this.labelCodeResolver = labelCodeResolver;
        this.annotationEventLoggerService = annotationEventLoggerService;
        this.mlcFormatParser = mlcFormatParser;
        this.coralNetCsvParser = coralNetCsvParser;
        this.cocoJsonParser = cocoJsonParser;
        this.blankFormatParser = blankFormatParser;
        this.workingDirectory = workingDirectory;
        this.dataDir = dataDir;
    }

    @Async
    @Transactional
    public void executeImportAsync(Long jobId, Long datasetId, Path uploadDir, ImportFormat format,
            String versionTag, String importedBy, List<LabelDeclarationRequest> labels) {
        ImportJob job = importJobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Import job not found: " + jobId));
        try {
            ParseResult parseResult = parser(format).parse(uploadDir);
            if (parseResult.images().isEmpty()) {
                throw new ImportParseException("No images found in the uploaded archive");
            }

            Set<String> requestedCodes = parseResult.points().stream()
                    .map(ParsedAnnotationPoint::labelCode)
                    .collect(Collectors.toSet());

            DatasetVersion previousLatest = datasetVersionRepository
                    .findTopByDatasetIdOrderByCreatedAtDesc(datasetId).orElse(null);
            // The first import defines the label vocabulary, later imports validate against
            // it
            if (previousLatest != null) {
                List<LabelDefinition> existingLabels = labelDefinitionRepository
                        .findByDatasetVersionId(previousLatest.getId());
                // Validate every requested code before anything is written to disk
                labelCodeResolver.resolve(existingLabels, requestedCodes);
            }

            Path dataPath = workingDirectory.resolve(dataDir);
            Files.createDirectories(dataPath);
            for (ParsedImage image : parseResult.images()) {
                Files.copy(image.sourcePath(), dataPath.resolve(image.filename()), StandardCopyOption.REPLACE_EXISTING);
            }

            CreateVersionRequest request = new CreateVersionRequest();
            request.setVersionTag(versionTag);
            request.setTriggerReason(TriggerReason.IMAGES_ADDED);
            request.setCreatedBy(importedBy);
            request.setNotes("Imported via " + format + " (" + parseResult.images().size() + " images)");
            DatasetVersionResponse versionResponse = datasetVersionService.createSnapshot(datasetId, request);
            DatasetVersion newVersion = datasetVersionRepository.findById(versionResponse.getId())
                    .orElseThrow(() -> new NotFoundException("Dataset version not found: " + versionResponse.getId()));

            List<LabelDefinition> newLabels = labelDefinitionRepository.findByDatasetVersionId(newVersion.getId());
            if (previousLatest == null) {
                newLabels = bootstrapLabelDefinitions(newVersion, requestedCodes);
            }
            int labelsDeclared = 0;
            if (format == ImportFormat.BLANK && labels != null && !labels.isEmpty()) {
                int before = newLabels.size();
                newLabels = declareLabelDefinitions(newVersion, newLabels, labels);
                labelsDeclared = newLabels.size() - before;
            }
            Map<String, LabelDefinition> resolvedByCode = labelCodeResolver.resolve(newLabels, requestedCodes);

            List<Image> imageEntities = new ArrayList<>();
            for (ParsedImage parsed : parseResult.images()) {
                Image image = new Image();
                image.setOriginDatasetVersion(newVersion);
                image.setFilename(parsed.filename());
                image.setFilePath(dataDir + "/" + parsed.filename());
                imageEntities.add(image);
            }
            List<Image> savedImages = imageRepository.saveAll(imageEntities);
            Map<String, Image> imagesByFilename = savedImages.stream()
                    .collect(Collectors.toMap(Image::getFilename, img -> img, (a, b) -> a));

            // Add membership rows for the freshly imported images on top of the copied
            // forward membership
            List<DatasetVersionImage> newMembership = new ArrayList<>();
            for (Image savedImage : savedImages) {
                DatasetVersionImage membership = new DatasetVersionImage();
                membership.setDatasetVersion(newVersion);
                membership.setImage(savedImage);
                newMembership.add(membership);
            }
            datasetVersionImageRepository.saveAll(newMembership);

            List<AnnotationPoint> pointEntities = new ArrayList<>();
            for (ParsedAnnotationPoint parsed : parseResult.points()) {
                AnnotationPoint point = new AnnotationPoint();
                point.setImage(imagesByFilename.get(parsed.imageFilename()));
                point.setPointIndex(parsed.pointIndex());
                point.setRowPosition(parsed.row());
                point.setColPosition(parsed.col());
                point.setLabelDefinition(resolvedByCode.get(parsed.labelCode()));
                point.setAnnotationSource("HUMAN");
                point.setAnnotatedBy(importedBy);
                pointEntities.add(point);
            }
            annotationPointRepository.saveAll(pointEntities);

            newVersion.setImageCount(newVersion.getImageCount() + savedImages.size());
            newVersion.setAnnotationCount(newVersion.getAnnotationCount() + pointEntities.size());
            datasetVersionRepository.save(newVersion);

            logImportCompletedAfterCommit(newVersion, importedBy, format, savedImages.size(), pointEntities.size());

            job.setStatus("COMPLETED");
            job.setImagesImported(savedImages.size());
            job.setAnnotationPointsImported(pointEntities.size());
            job.setLabelsResolved(format == ImportFormat.BLANK ? labelsDeclared : resolvedByCode.size());
            job.setLabelsUnresolved(0);
            job.setCreatedDatasetVersionId(newVersion.getId());
            job.setCompletedAt(Instant.now());
            importJobRepository.save(job);
        } catch (UnresolvableLabelCodesException ex) {
            job.setStatus("FAILED");
            job.setErrorMessage(ex.getMessage());
            job.setLabelsUnresolved(ex.getUnresolvedCodes().size());
            job.setCompletedAt(Instant.now());
            importJobRepository.save(job);
        } catch (Exception ex) {
            job.setStatus("FAILED");
            job.setErrorMessage(ex.getMessage());
            job.setCompletedAt(Instant.now());
            importJobRepository.save(job);
        } finally {
            deleteRecursivelyQuietly(uploadDir);
        }
    }

    /**
     * Creates one label per distinct code found when a dataset has no prior
     * versions
     */
    private List<LabelDefinition> bootstrapLabelDefinitions(DatasetVersion newVersion, Set<String> codes) {
        List<LabelDefinition> created = new ArrayList<>();
        for (String code : codes) {
            LabelDefinition label = new LabelDefinition();
            label.setDatasetVersion(newVersion); // LabelDefinition.datasetVersion is unrelated to
                                                 // Image.originDatasetVersion
            label.setLabelCode(code);
            label.setLabelName(code);
            created.add(labelDefinitionRepository.save(label));
        }
        return created;
    }

    /** Applies a caller declared label vocabulary for a blank import */
    private List<LabelDefinition> declareLabelDefinitions(DatasetVersion version, List<LabelDefinition> existing,
            List<LabelDeclarationRequest> declarations) {
        Set<String> existingCodes = existing.stream().map(LabelDefinition::getLabelCode).collect(Collectors.toSet());
        List<LabelDefinition> all = new ArrayList<>(existing);
        for (LabelDeclarationRequest declaration : declarations) {
            String code = declaration.getLabelCode() != null ? declaration.getLabelCode().trim() : "";
            String name = declaration.getLabelName() != null ? declaration.getLabelName().trim() : "";
            if (code.isEmpty() || name.isEmpty() || existingCodes.contains(code)) {
                continue;
            }
            String category = declaration.getLabelCategory() != null ? declaration.getLabelCategory().trim() : null;
            LabelDefinition label = new LabelDefinition();
            label.setDatasetVersion(version);
            label.setLabelCode(code);
            label.setLabelName(name);
            label.setLabelCategory((category == null || category.isEmpty()) ? null : category);
            all.add(labelDefinitionRepository.save(label));
            existingCodes.add(code);
        }
        return all;
    }

    private DatasetFormatParser parser(ImportFormat format) {
        return switch (format) {
            case MLC -> mlcFormatParser;
            case CORALNET_CSV -> coralNetCsvParser;
            case COCO_JSON -> cocoJsonParser;
            case BLANK -> blankFormatParser;
        };
    }

    /** Logs the import event after the transaction commits */
    private void logImportCompletedAfterCommit(DatasetVersion version, String performedBy, ImportFormat format,
            int imageCount, int pointCount) {
        String description = "Imported " + imageCount + " images / " + pointCount
                + " annotation points via " + format;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    annotationEventLoggerService.log(EventType.DATASET_IMPORT_COMPLETED, version, performedBy,
                            description);
                }
            });
        } else {
            annotationEventLoggerService.log(EventType.DATASET_IMPORT_COMPLETED, version, performedBy, description);
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
