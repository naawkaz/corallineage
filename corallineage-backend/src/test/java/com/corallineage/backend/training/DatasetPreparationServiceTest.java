package com.corallineage.backend.training;

import com.corallineage.backend.versioning.dataset.AnnotationPoint;
import com.corallineage.backend.versioning.dataset.AnnotationPointRepository;
import com.corallineage.backend.versioning.dataset.Image;
import com.corallineage.backend.versioning.dataset.LabelDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatasetPreparationServiceTest {

    @Mock
    private AnnotationPointRepository annotationPointRepository;

    private DatasetPreparationService newService(Path workingDirectory, int patchSize) {
        return new DatasetPreparationService(annotationPointRepository, workingDirectory, patchSize);
    }

    private void writeTestImage(Path file, int width, int height) throws IOException {
        Files.createDirectories(file.getParent());
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image, "jpg", file.toFile());
    }

    private AnnotationPoint point(Image image, int index, int row, int col, String labelCode) {
        LabelDefinition label = new LabelDefinition();
        label.setLabelCode(labelCode);
        AnnotationPoint point = new AnnotationPoint();
        point.setImage(image);
        point.setPointIndex(index);
        point.setRowPosition(row);
        point.setColPosition(col);
        point.setLabelDefinition(label);
        return point;
    }

    @Test
    void prepareImageFolderDataset_writesOnePatchPerPointUnderItsLabelSubdirectory(@TempDir Path workingDirectory) throws IOException {
        writeTestImage(workingDirectory.resolve("dataset/img001.jpg"), 200, 200);

        Image image = new Image();
        image.setFilename("img001.jpg");
        image.setFilePath("dataset/img001.jpg");

        List<AnnotationPoint> points = List.of(
                point(image, 0, 50, 50, "ACROPORA"),
                point(image, 1, 100, 100, "SAND"),
                point(image, 2, 150, 150, "ACROPORA")
        );
        when(annotationPointRepository.findByDatasetVersionMembership(3L)).thenReturn(points);

        Path preparedDir = newService(workingDirectory, 64).prepareImageFolderDataset(3L, 42L);

        assertThat(preparedDir).exists();
        assertThat(preparedDir.resolve("ACROPORA")).isDirectory();
        assertThat(preparedDir.resolve("SAND")).isDirectory();
        assertThat(preparedDir.resolve("ACROPORA/img001_p0.jpg")).exists();
        assertThat(preparedDir.resolve("ACROPORA/img001_p2.jpg")).exists();
        assertThat(preparedDir.resolve("SAND/img001_p1.jpg")).exists();
    }

    @Test
    void prepareImageFolderDataset_noPoints_throwsDatasetPreparationException(@TempDir Path workingDirectory) {
        when(annotationPointRepository.findByDatasetVersionMembership(3L)).thenReturn(List.of());

        assertThatThrownBy(() -> newService(workingDirectory, 64).prepareImageFolderDataset(3L, 42L))
                .isInstanceOf(DatasetPreparationException.class)
                .hasMessageContaining("no annotation points");
    }

    @Test
    void prepareImageFolderDataset_onlyOneClass_throwsDatasetPreparationException(@TempDir Path workingDirectory) throws IOException {
        writeTestImage(workingDirectory.resolve("dataset/img001.jpg"), 200, 200);
        Image image = new Image();
        image.setFilename("img001.jpg");
        image.setFilePath("dataset/img001.jpg");

        List<AnnotationPoint> points = List.of(point(image, 0, 50, 50, "ACROPORA"));
        when(annotationPointRepository.findByDatasetVersionMembership(3L)).thenReturn(points);

        assertThatThrownBy(() -> newService(workingDirectory, 64).prepareImageFolderDataset(3L, 42L))
                .isInstanceOf(DatasetPreparationException.class)
                .hasMessageContaining("at least 2 distinct label classes");
    }

    @Test
    void prepareImageFolderDataset_patchNearEdge_clampsWithinImageBounds(@TempDir Path workingDirectory) throws IOException {
        writeTestImage(workingDirectory.resolve("dataset/img002.jpg"), 100, 100);
        Image image = new Image();
        image.setFilename("img002.jpg");
        image.setFilePath("dataset/img002.jpg");

        // Point right at the corner (0,0) - patch must clamp to stay inside the 100x100 image.
        List<AnnotationPoint> points = List.of(
                point(image, 0, 0, 0, "ACROPORA"),
                point(image, 1, 99, 99, "SAND")
        );
        when(annotationPointRepository.findByDatasetVersionMembership(3L)).thenReturn(points);

        Path preparedDir = newService(workingDirectory, 64).prepareImageFolderDataset(3L, 42L);

        BufferedImage patch = ImageIO.read(preparedDir.resolve("ACROPORA/img002_p0.jpg").toFile());
        assertThat(patch.getWidth()).isEqualTo(64);
        assertThat(patch.getHeight()).isEqualTo(64);
    }
}
