package com.corallineage.backend.pointannotation;

import com.corallineage.backend.versioning.dataset.Image;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Generates an evenly spaced grid of points per image, inset from the edges */
@Service
public class PointGenerationService {

    private final Path workingDirectory;

    public PointGenerationService(@Value("${app.dvc.working-directory}") Path workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    /**
     * @param startIndex first pointIndex to assign, keeps new indices disjoint from existing points
     */
    List<GeneratedPoint> generateGrid(Image image, PointAnnotationJob job, boolean seed, int startIndex) {
        BufferedImage buffered = ImagePatchUtil.readImage(workingDirectory, image.getFilePath());
        List<int[]> coordinates = computeGridCoordinates(buffered.getWidth(), buffered.getHeight(), job.getGridRows(), job.getGridCols());

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

    /** Lays out a grid inset by one cell, returns row and col pairs */
    private List<int[]> computeGridCoordinates(int width, int height, int rows, int cols) {
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
