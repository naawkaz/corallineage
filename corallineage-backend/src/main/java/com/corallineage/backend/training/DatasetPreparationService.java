package com.corallineage.backend.training;

import com.corallineage.backend.versioning.dataset.AnnotationPoint;
import com.corallineage.backend.versioning.dataset.AnnotationPointRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Bridges the gap between the point-annotated import data model (row/col + label per point,
 * CoralNet-style) and the ImageFolder format PyTorch/timm training expects (one subdirectory
 * per class). For every AnnotationPoint on a dataset version, crops a small patch centered on
 * its row/col and writes it to preparedDir/<labelCode>/<image>_p<pointIndex>.jpg.
 */
@Service
public class DatasetPreparationService {

    private final AnnotationPointRepository annotationPointRepository;
    private final Path workingDirectory;
    private final int patchSize;

    public DatasetPreparationService(AnnotationPointRepository annotationPointRepository,
                                     @Value("${app.dvc.working-directory}") Path workingDirectory,
                                     @Value("${app.training.patch-size:64}") int patchSize) {
        this.annotationPointRepository = annotationPointRepository;
        this.workingDirectory = workingDirectory;
        this.patchSize = patchSize;
    }

    @Transactional(readOnly = true)
    public Path prepareImageFolderDataset(Long datasetVersionId, Long jobId) {
        List<AnnotationPoint> points = annotationPointRepository.findByImageOriginDatasetVersionId(datasetVersionId);
        if (points.isEmpty()) {
            throw new DatasetPreparationException("Dataset version has no annotation points to train on");
        }
        Map<String, Long> distinctClasses = points.stream()
                .collect(Collectors.groupingBy(p -> p.getLabelDefinition().getLabelCode(), Collectors.counting()));
        if (distinctClasses.size() < 2) {
            throw new DatasetPreparationException(
                    "Need at least 2 distinct label classes to train, found " + distinctClasses.size());
        }

        Path preparedDir = workingDirectory.resolve("training-data").resolve(String.valueOf(jobId));
        try {
            Files.createDirectories(preparedDir);
            Map<String, BufferedImage> imageCache = new HashMap<>();
            for (AnnotationPoint point : points) {
                String labelCode = point.getLabelDefinition().getLabelCode();
                Path classDir = preparedDir.resolve(labelCode);
                Files.createDirectories(classDir);

                String imageFilePath = point.getImage().getFilePath();
                BufferedImage sourceImage = imageCache.computeIfAbsent(imageFilePath, this::readImage);

                BufferedImage patch = cropPatch(sourceImage, point.getRowPosition(), point.getColPosition());
                String baseName = stripExtension(point.getImage().getFilename());
                Path outFile = classDir.resolve(baseName + "_p" + point.getPointIndex() + ".jpg");
                ImageIO.write(patch, "jpg", outFile.toFile());
            }
            return preparedDir;
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to prepare training dataset", ex);
        }
    }

    private BufferedImage readImage(String relativeFilePath) {
        try {
            BufferedImage image = ImageIO.read(workingDirectory.resolve(relativeFilePath).toFile());
            if (image == null) {
                throw new DatasetPreparationException("Could not read image file: " + relativeFilePath);
            }
            return image;
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read image file: " + relativeFilePath, ex);
        }
    }

    private BufferedImage cropPatch(BufferedImage source, int row, int col) {
        int half = patchSize / 2;
        int x = clamp(col - half, 0, source.getWidth() - patchSize);
        int y = clamp(row - half, 0, source.getHeight() - patchSize);
        int width = Math.min(patchSize, source.getWidth());
        int height = Math.min(patchSize, source.getHeight());
        return source.getSubimage(Math.max(x, 0), Math.max(y, 0), width, height);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, Math.max(min, max)));
    }

    private String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
