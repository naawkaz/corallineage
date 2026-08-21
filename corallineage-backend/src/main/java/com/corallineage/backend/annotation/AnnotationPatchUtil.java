package com.corallineage.backend.annotation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Image read/crop/grid helpers for point-level annotation jobs. A deliberate small duplicate of
 * {@code pointannotation.ImagePatchUtil} and {@code pointannotation.PointGenerationService}'s grid
 * math (package-private there, so it cannot be imported directly) rather than a cross-module
 * extraction, matching this codebase's established convention (see that class's own javadoc,
 * itself a documented duplicate of {@code training.DatasetPreparationService}).
 */
final class AnnotationPatchUtil {

    private AnnotationPatchUtil() {
    }

    static BufferedImage readImage(Path workingDirectory, String relativeFilePath) {
        try {
            BufferedImage image = ImageIO.read(workingDirectory.resolve(relativeFilePath).toFile());
            if (image == null) {
                throw new IllegalStateException("Could not read image file: " + relativeFilePath);
            }
            return image;
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read image file: " + relativeFilePath, ex);
        }
    }

    static BufferedImage cropPatch(BufferedImage source, int row, int col, int patchSize) {
        int width = Math.min(patchSize, source.getWidth());
        int height = Math.min(patchSize, source.getHeight());
        int half = patchSize / 2;
        int x = clamp(col - half, 0, source.getWidth() - width);
        int y = clamp(row - half, 0, source.getHeight() - height);
        return source.getSubimage(x, y, width, height);
    }

    /**
     * Lays out an explicit rows x cols grid, inset by one grid cell from every edge. Returns
     * {row, col} pixel coordinate pairs, always exactly {@code rows * cols} of them. Mirrors
     * PointGenerationService.computeGridCoordinates.
     */
    static List<int[]> computeGridCoordinates(int width, int height, int rows, int cols) {
        int cellWidth = width / (cols + 1);
        int cellHeight = height / (rows + 1);

        List<int[]> coordinates = new ArrayList<>();
        for (int r = 1; r <= rows; r++) {
            for (int c = 1; c <= cols; c++) {
                int x = Math.min(width - 1, c * cellWidth);
                int y = Math.min(height - 1, r * cellHeight);
                coordinates.add(new int[]{y, x});
            }
        }
        return coordinates;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, Math.max(min, max)));
    }
}
