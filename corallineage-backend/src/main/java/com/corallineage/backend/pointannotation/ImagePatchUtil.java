package com.corallineage.backend.pointannotation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/**
 * Shared image read/crop helpers for point generation and point-level inference. Mirrors the crop
 * math already in {@code training.DatasetPreparationService} (kept as a separate small copy here
 * rather than a cross-module extraction, since it's ~10 lines and the two modules otherwise have
 * no dependency on each other).
 */
final class ImagePatchUtil {

    private ImagePatchUtil() {
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

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, Math.max(min, max)));
    }
}
