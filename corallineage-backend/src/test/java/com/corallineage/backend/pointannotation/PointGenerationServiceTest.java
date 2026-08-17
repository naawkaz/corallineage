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
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

        // job(10) on a square image completes to a 3x4 = 12-point rectangle (see
        // computeGridCoordinates_alwaysReturnsACompleteRectangle_neverAPartialLastRow), not a
        // truncated 10, indices must still be zero-based and sequential over however many the
        // completed grid actually returns.
        List<GeneratedPoint> points = new PointGenerationService(workingDirectory).generateGrid(image, job(10), false, 0);

        assertThat(points).extracting(GeneratedPoint::getPointIndex)
                .containsExactlyElementsOf(IntStream.range(0, points.size()).boxed().toList());
        assertThat(points).allMatch(p -> !p.isSeed());
    }

    @Test
    void generateGrid_nonZeroStartIndex_offsetsEveryPointIndex(@TempDir Path workingDirectory) throws IOException {
        writeTestImage(workingDirectory.resolve("dataset/img001.jpg"), 400, 400);
        Image image = new Image();
        image.setFilename("img001.jpg");
        image.setFilePath("dataset/img001.jpg");

        // job(5) on a square image completes to a 2x3 = 6-point rectangle, same rectangle-completion
        // behavior as above, the offset just needs to still land on every point returned.
        List<GeneratedPoint> points = new PointGenerationService(workingDirectory).generateGrid(image, job(5), false, 200);

        assertThat(points).extracting(GeneratedPoint::getPointIndex)
                .containsExactlyElementsOf(IntStream.range(200, 200 + points.size()).boxed().toList());
    }

    @Test
    void computeGridCoordinates_alwaysReturnsACompleteRectangle_neverAPartialLastRow() throws Exception {
        Method method = PointGenerationService.class.getDeclaredMethod(
                "computeGridCoordinates", int.class, int.class, int.class);
        method.setAccessible(true);
        PointGenerationService service = new PointGenerationService(Path.of("."));

        // 50 points on a square (aspect 1) image drives cols = round(sqrt(50)) = 7,
        // rows = ceil(50 / 7) = 8, the old code truncated mid-row-8 after 1 point (49 + 1 stray),
        // the fix must return the full 7x8 = 56-point rectangle instead.
        @SuppressWarnings("unchecked")
        List<int[]> coordinates = (List<int[]>) method.invoke(service, 1000, 1000, 50);

        assertThat(coordinates).hasSize(56);
        Map<Integer, Long> countsByRow = coordinates.stream()
                .collect(Collectors.groupingBy(c -> c[0], Collectors.counting()));
        assertThat(countsByRow).hasSize(8);
        assertThat(countsByRow.values()).allMatch(count -> count == 7L);
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
