package com.corallineage.backend.versioning.diff;

import com.corallineage.backend.datasets.Dataset;
import com.corallineage.backend.exception.InvalidRequestException;
import com.corallineage.backend.versioning.dataset.DatasetVersion;
import com.corallineage.backend.versioning.dataset.DatasetVersionRepository;
import com.corallineage.backend.versioning.dataset.Image;
import com.corallineage.backend.versioning.dataset.ImageRepository;
import com.corallineage.backend.versioning.dataset.LabelDefinition;
import com.corallineage.backend.versioning.dataset.LabelDefinitionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatasetDiffServiceTest {

    @Mock private DatasetVersionRepository datasetVersionRepository;
    @Mock private LabelDefinitionRepository labelDefinitionRepository;
    @Mock private ImageRepository imageRepository;
    @Mock private ClassDistributionService classDistributionService;

    private DatasetDiffService newService() {
        return new DatasetDiffService(datasetVersionRepository, labelDefinitionRepository, imageRepository,
                classDistributionService);
    }

    private Dataset dataset(Long id) {
        Dataset d = new Dataset();
        d.setId(id);
        return d;
    }

    private DatasetVersion version(Long id, Dataset dataset, String tag, Instant createdAt) {
        DatasetVersion v = new DatasetVersion();
        v.setId(id);
        v.setDataset(dataset);
        v.setVersionTag(tag);
        v.setCreatedAt(createdAt);
        return v;
    }

    private LabelDefinition label(String code, String name) {
        LabelDefinition l = new LabelDefinition();
        l.setLabelCode(code);
        l.setLabelName(name);
        return l;
    }

    private Image image(Long id, String filename) {
        Image img = new Image();
        img.setId(id);
        img.setFilename(filename);
        return img;
    }

    @Test
    void computeDiff_sameVersion_throwsInvalidRequest() {
        assertThatThrownBy(() -> newService().computeDiff(1L, 5L, 5L))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void computeDiff_versionNotFound_throwsInvalidRequest() {
        when(datasetVersionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().computeDiff(1L, 99L, 5L))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void computeDiff_versionBelongsToDifferentDataset_throwsInvalidRequest() {
        Dataset otherDataset = dataset(2L);
        DatasetVersion wrongVersion = version(5L, otherDataset, "v1.0", Instant.now());
        when(datasetVersionRepository.findById(5L)).thenReturn(Optional.of(wrongVersion));

        assertThatThrownBy(() -> newService().computeDiff(1L, 5L, 6L))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void computeDiff_happyPath_computesImageLabelAndClassDeltas() {
        Dataset dataset = dataset(1L);
        Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-02-01T00:00:00Z");
        DatasetVersion v1 = version(1L, dataset, "v1.0", t1);
        DatasetVersion v2 = version(2L, dataset, "v1.1", t2);
        when(datasetVersionRepository.findById(1L)).thenReturn(Optional.of(v1));
        when(datasetVersionRepository.findById(2L)).thenReturn(Optional.of(v2));

        when(labelDefinitionRepository.findByDatasetVersionId(1L))
                .thenReturn(List.of(label("A", "Label A"), label("B", "Label B")));
        when(labelDefinitionRepository.findByDatasetVersionId(2L))
                .thenReturn(List.of(label("A", "Label A"), label("C", "Label C")));

        Image img1 = image(101L, "img1.jpg");
        Image img2 = image(102L, "img2.jpg");
        Image img3 = image(103L, "img3.jpg");
        when(imageRepository.findByDatasetVersionDatasetIdAndDatasetVersionCreatedAtLessThanEqual(1L, t1))
                .thenReturn(List.of(img1, img2));
        when(imageRepository.findByDatasetVersionDatasetIdAndDatasetVersionCreatedAtLessThanEqual(1L, t2))
                .thenReturn(List.of(img1, img2, img3));

        when(classDistributionService.countByLabelUpToVersion(1L, v1)).thenReturn(Map.of(
                "A", new ClassCount("A", "Label A", 5),
                "B", new ClassCount("B", "Label B", 3)));
        when(classDistributionService.countByLabelUpToVersion(1L, v2)).thenReturn(Map.of(
                "A", new ClassCount("A", "Label A", 7),
                "C", new ClassCount("C", "Label C", 2)));

        DatasetDiffResponse response = newService().computeDiff(1L, 1L, 2L);

        assertThat(response.fromVersion().versionTag()).isEqualTo("v1.0");
        assertThat(response.toVersion().versionTag()).isEqualTo("v1.1");

        assertThat(response.imagesAdded()).extracting(ImageRef::filename).containsExactly("img3.jpg");
        assertThat(response.imagesRemoved()).isEmpty();
        assertThat(response.summary().imagesAdded()).isEqualTo(1);
        assertThat(response.summary().imagesRemoved()).isZero();

        assertThat(response.labelChanges()).hasSize(2);
        assertThat(response.labelChanges()).anySatisfy(c -> {
            assertThat(c.changeType()).isEqualTo("ADDED");
            assertThat(c.labelCode()).isEqualTo("C");
        });
        assertThat(response.labelChanges()).anySatisfy(c -> {
            assertThat(c.changeType()).isEqualTo("DEPRECATED");
            assertThat(c.labelCode()).isEqualTo("B");
        });
        assertThat(response.summary().labelsAdded()).isEqualTo(1);
        assertThat(response.summary().labelsDeprecated()).isEqualTo(1);

        assertThat(response.classDistributionDiff()).hasSize(3);
        ClassDistributionEntry entryA = response.classDistributionDiff().stream()
                .filter(e -> e.labelCode().equals("A")).findFirst().orElseThrow();
        assertThat(entryA.countInV1()).isEqualTo(5);
        assertThat(entryA.countInV2()).isEqualTo(7);
        assertThat(entryA.delta()).isEqualTo(2);

        ClassDistributionEntry entryB = response.classDistributionDiff().stream()
                .filter(e -> e.labelCode().equals("B")).findFirst().orElseThrow();
        assertThat(entryB.countInV1()).isEqualTo(3);
        assertThat(entryB.countInV2()).isZero();
        assertThat(entryB.delta()).isEqualTo(-3);

        ClassDistributionEntry entryC = response.classDistributionDiff().stream()
                .filter(e -> e.labelCode().equals("C")).findFirst().orElseThrow();
        assertThat(entryC.countInV1()).isZero();
        assertThat(entryC.countInV2()).isEqualTo(2);
        assertThat(entryC.delta()).isEqualTo(2);

        // |2| + |-3| + |2| = 7
        assertThat(response.summary().annotationsModified()).isEqualTo(7);
    }
}
