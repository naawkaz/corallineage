package com.corallineage.backend.pointannotation;

import com.corallineage.backend.versioning.dataset.Image;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates an evenly spaced grid of points per image, inset from the edges so no point lands
 * exactly on a border pixel. Grid-only for this proof of concept — CoralNet also supports random
 * and stratified-random point placement, but those aren't needed to demonstrate the pipeline.
 */
@Service
public class PointGenerationService {

    private final Path workingDirectory;

    public PointGenerationService(@Value("${app.dvc.working-directory}") Path workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    /**
     * @param startIndex first pointIndex to assign (see {@code AnnotationPointRepository.findMaxPointIndexByImageId})
     *                   — keeps this job's grid indices disjoint from any points already on the image
     *                   (import data, or an earlier point-annotation job), since indices bridge into the
     *                   shared, globally-unique-per-image {@code AnnotationPoint} table once labeled.
     */
    List<GeneratedPoint> generateGrid(Image image, PointAnnotationJob job, boolean seed, int startIndex) {
        BufferedImage buffered = ImagePatchUtil.readImage(workingDirectory, image.getFilePath());
        List<int[]> coordinates = computeGridCoordinates(buffered.getWidth(), buffered.getHeight(), job.getPointsPerImage());

        List<GeneratedPoint> points = new ArrayList<>();
        int index = startIndex;
        for (int[] coordinate : coordinates) {
            GeneratedPoint point = new GeneratedPoint();
            point.setImage(image);
            point.setJob(job);
            point.setPointIndex(index++);
            point.setRowPosition(coordinate[0]);
            point.setColPosition(coordinate[1]);
            point.setSeed(seed);
            point.setStatus("UNLABELED");
            points.add(point);
        }
        return points;
    }

    /**
     * Lays out a cols x rows grid sized to roughly match the image's aspect ratio, inset by one
     * grid cell from every edge. Always returns the complete cols x rows rectangle, never a
     * partial row: truncating mid-row after {@code totalPoints} cells used to leave a single
     * stray point hanging off the bottom of an otherwise-clean grid (e.g. 50 points at a typical
     * aspect ratio produced a 7-wide by 8-tall layout cut off after just 1 point into row 8).
     * The actual count returned can therefore be slightly above {@code totalPoints} when it isn't
     * a perfect square for the chosen aspect-driven cols/rows; callers treat the returned list's
     * size as authoritative rather than assuming exactly {@code totalPoints} points come back.
     * Returns {row, col} pixel coordinate pairs.
     */
    private List<int[]> computeGridCoordinates(int width, int height, int totalPoints) {
        double aspect = (double) width / height;
        int cols = Math.max(1, (int) Math.round(Math.sqrt(totalPoints * aspect)));
        int rows = Math.max(1, (int) Math.ceil((double) totalPoints / cols));

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
}
