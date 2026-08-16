package com.corallineage.backend.pointannotation;

import com.corallineage.backend.versioning.dataset.Image;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PointGenerationServiceTest {

    private void writeTestImage(Path file, int width, int height) throws IOException {
        Files.createDirectories(file.getParent());
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image, "jpg", file.toFile());
    }

    private PointAnnotationJob job(int pointsPerImage) {
        PointAnnotationJob job = new PointAnnotationJob();
        job.setPointsPerImage(pointsPerImage);
        return job;
    }

    @Test
    void generateGrid_producesExactlyThePointsPerImageCount(@TempDir Path workingDirectory) throws IOException {
        writeTestImage(workingDirectory.resolve("dataset/img001.jpg"), 1000, 500);
        Image image = new Image();
        image.setFilename("img001.jpg");
        image.setFilePath("dataset/img001.jpg");

        List<GeneratedPoint> points = new PointGenerationService(workingDirectory).generateGrid(image, job(50), true, 0);

        assertThat(points).hasSize(50);
        assertThat(points).allMatch(GeneratedPoint::isSeed);
        assertThat(points).allMatch(p -> "UNLABELED".equals(p.getStatus()));
    }

    @Test
    void generateGrid_pointIndicesAreZeroBasedAndSequential(@TempDir Path workingDirectory) throws IOException {
        writeTestImage(workingDirectory.resolve("dataset/img001.jpg"), 400, 400);
        Image image = new Image();
        image.setFilename("img001.jpg");
        image.setFilePath("dataset/img001.jpg");

        List<GeneratedPoint> points = new PointGenerationService(workingDirectory).generateGrid(image, job(10), false, 0);

        assertThat(points).extracting(GeneratedPoint::getPointIndex)
                .containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        assertThat(points).allMatch(p -> !p.isSeed());
    }

    @Test
    void generateGrid_nonZeroStartIndex_offsetsEveryPointIndex(@TempDir Path workingDirectory) throws IOException {
        writeTestImage(workingDirectory.resolve("dataset/img001.jpg"), 400, 400);
        Image image = new Image();
        image.setFilename("img001.jpg");
        image.setFilePath("dataset/img001.jpg");

        List<GeneratedPoint> points = new PointGenerationService(workingDirectory).generateGrid(image, job(5), false, 200);

        assertThat(points).extracting(GeneratedPoint::getPointIndex)
                .containsExactly(200, 201, 202, 203, 204);
    }

    @Test
    void generateGrid_everyCoordinateIsInsetInsideImageBounds(@TempDir Path workingDirectory) throws IOException {
        writeTestImage(workingDirectory.resolve("dataset/img001.jpg"), 200, 200);
        Image image = new Image();
        image.setFilename("img001.jpg");
        image.setFilePath("dataset/img001.jpg");

        List<GeneratedPoint> points = new PointGenerationService(workingDirectory).generateGrid(image, job(50), true, 0);

        assertThat(points).allSatisfy(p -> {
            assertThat(p.getRowPosition()).isBetween(0, 199);
            assertThat(p.getColPosition()).isBetween(0, 199);
        });
        // Inset from the true (0,0) corner — the grid margin should keep points off the edge.
        assertThat(points).noneMatch(p -> p.getRowPosition() == 0 && p.getColPosition() == 0);
    }

    @Test
    void computeGridCoordinates_boundaryClamping_neverExceedsImageBounds() throws Exception {
        Method method = PointGenerationService.class.getDeclaredMethod(
                "computeGridCoordinates", int.class, int.class, int.class);
        method.setAccessible(true);
        PointGenerationService service = new PointGenerationService(Path.of("."));

        @SuppressWarnings("unchecked")
        List<int[]> coordinates = (List<int[]>) method.invoke(service, 50, 50, 200);

        assertThat(coordinates).isNotEmpty();
        assertThat(coordinates).allSatisfy(c -> {
            assertThat(c[0]).isBetween(0, 49);
            assertThat(c[1]).isBetween(0, 49);
        });
    }
}
