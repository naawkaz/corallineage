package com.corallineage.backend.versioning.dataset;

import com.corallineage.backend.datasets.Dataset;
import com.corallineage.backend.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

    @Mock
    private DatasetVersionRepository datasetVersionRepository;
    @Mock
    private ImageRepository imageRepository;
    @Mock
    private DatasetVersionImageRepository datasetVersionImageRepository;
    @Mock
    private AnnotationPointRepository annotationPointRepository;

    @Spy
    private ImageMapper imageMapper;

    private ImageService imageService;

    private ImageService newService() {
        return new ImageService(datasetVersionRepository, imageRepository, datasetVersionImageRepository,
                annotationPointRepository, imageMapper, Path.of("."));
    }

    private DatasetVersion version(Long datasetId, Long versionId) {
        Dataset dataset = new Dataset();
        dataset.setId(datasetId);
        DatasetVersion version = new DatasetVersion();
        version.setId(versionId);
        version.setDataset(dataset);
        return version;
    }

    private AnnotationPoint point(Long id, Long imageId, int pointIndex, String labelCode) {
        Image image = new Image();
        image.setId(imageId);
        LabelDefinition label = new LabelDefinition();
        label.setLabelCode(labelCode);
        AnnotationPoint point = new AnnotationPoint();
        point.setId(id);
        point.setImage(image);
        point.setPointIndex(pointIndex);
        point.setRowPosition(100);
        point.setColPosition(200);
        point.setLabelDefinition(label);
        return point;
    }

    @Test
    void listAnnotationPoints_success_mapsPointsToSummaries() {
        imageService = newService();
        when(datasetVersionRepository.findById(10L)).thenReturn(Optional.of(version(1L, 10L)));
        when(annotationPointRepository.findByDatasetVersionMembership(10L))
                .thenReturn(List.of(point(100L, 5L, 0, "ACROPORA"), point(101L, 5L, 1, "CCA")));

        List<AnnotationPointSummaryResponse> result = imageService.listAnnotationPoints(1L, 10L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getImageId()).isEqualTo(5L);
        assertThat(result.get(0).getLabelCode()).isEqualTo("ACROPORA");
        assertThat(result.get(0).getRowPosition()).isEqualTo(100);
        assertThat(result.get(0).getColPosition()).isEqualTo(200);
        assertThat(result.get(1).getLabelCode()).isEqualTo("CCA");
    }

    @Test
    void listAnnotationPoints_versionNotFound_throwsNotFound() {
        imageService = newService();
        when(datasetVersionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> imageService.listAnnotationPoints(1L, 99L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listAnnotationPoints_wrongDataset_throwsNotFound() {
        imageService = newService();
        when(datasetVersionRepository.findById(10L)).thenReturn(Optional.of(version(1L, 10L)));

        assertThatThrownBy(() -> imageService.listAnnotationPoints(2L, 10L))
                .isInstanceOf(NotFoundException.class);
    }
}
