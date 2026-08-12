package com.corallineage.backend.datasets.importservice;

import com.corallineage.backend.logging.AnnotationEventLoggerService;
import com.corallineage.backend.versioning.dataset.AnnotationPointRepository;
import com.corallineage.backend.versioning.dataset.DatasetVersion;
import com.corallineage.backend.versioning.dataset.DatasetVersionRepository;
import com.corallineage.backend.versioning.dataset.DatasetVersionResponse;
import com.corallineage.backend.versioning.dataset.DatasetVersionService;
import com.corallineage.backend.versioning.dataset.Image;
import com.corallineage.backend.versioning.dataset.ImageRepository;
import com.corallineage.backend.versioning.dataset.LabelDefinition;
import com.corallineage.backend.versioning.dataset.LabelDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatasetImportExecutorTest {

    @Mock private ImportJobRepository importJobRepository;
    @Mock private DatasetVersionRepository datasetVersionRepository;
    @Mock private DatasetVersionService datasetVersionService;
    @Mock private LabelDefinitionRepository labelDefinitionRepository;
    @Mock private ImageRepository imageRepository;
    @Mock private AnnotationPointRepository annotationPointRepository;
    @Mock private AnnotationEventLoggerService annotationEventLoggerService;

    private DatasetImportExecutor executor;

    @BeforeEach
    void setUp(@TempDir Path workingDirectory) {
        executor = new DatasetImportExecutor(
                importJobRepository,
                datasetVersionRepository,
                datasetVersionService,
                labelDefinitionRepository,
                imageRepository,
                annotationPointRepository,
                new LabelCodeResolver(),
                annotationEventLoggerService,
                new MlcFormatParser(),
                new CoralNetCsvParser(),
                new CocoJsonParser(),
                workingDirectory,
                "dataset");
    }

    private ImportJob job(Long id) {
        ImportJob job = new ImportJob();
        job.setId(id);
        return job;
    }

    private void writeMlcFixture(Path dir) throws IOException {
        Files.createFile(dir.resolve("img001.jpg"));
        Files.writeString(dir.resolve("img001.txt"), "10 20 ACROPORA\n30 40 SAND\n");
    }

    @Test
    void bootstrapsLabelDefinitions_whenDatasetHasNoPriorVersion(@TempDir Path uploadDir) throws IOException {
        writeMlcFixture(uploadDir);
        when(importJobRepository.findById(1L)).thenReturn(Optional.of(job(1L)));
        when(datasetVersionRepository.findTopByDatasetIdOrderByCreatedAtDesc(10L)).thenReturn(Optional.empty());

        DatasetVersionResponse versionResponse = new DatasetVersionResponse();
        versionResponse.setId(100L);
        when(datasetVersionService.createSnapshot(any(), any())).thenReturn(versionResponse);

        DatasetVersion newVersion = new DatasetVersion();
        newVersion.setId(100L);
        newVersion.setImageCount(0);
        when(datasetVersionRepository.findById(100L)).thenReturn(Optional.of(newVersion));

        // Fresh version has no labels yet — bootstrapLabelDefinitions is expected to create them.
        when(labelDefinitionRepository.findByDatasetVersionId(100L)).thenReturn(List.of());
        when(labelDefinitionRepository.save(any(LabelDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

        when(imageRepository.saveAll(any())).thenAnswer(inv -> {
            List<Image> images = inv.getArgument(0);
            for (Image image : images) image.setId(1L);
            return images;
        });
        when(annotationPointRepository.countByImageDatasetVersionDatasetId(10L)).thenReturn(0L);

        executor.executeImportAsync(1L, 10L, uploadDir, ImportFormat.MLC, "v1.0", "researcher_01");

        verify(labelDefinitionRepository, org.mockito.Mockito.times(2)).save(any(LabelDefinition.class));
        verify(importJobRepository).save(org.mockito.ArgumentMatchers.argThat(saved ->
                "COMPLETED".equals(saved.getStatus()) && saved.getLabelsUnresolved() == 0));
    }

    @Test
    void abortsBeforeAnyWrites_whenExistingLabelsDoNotResolveRequestedCode(@TempDir Path uploadDir) throws IOException {
        writeMlcFixture(uploadDir);
        when(importJobRepository.findById(2L)).thenReturn(Optional.of(job(2L)));

        DatasetVersion previousLatest = new DatasetVersion();
        previousLatest.setId(5L);
        when(datasetVersionRepository.findTopByDatasetIdOrderByCreatedAtDesc(10L)).thenReturn(Optional.of(previousLatest));

        LabelDefinition onlyKnownLabel = new LabelDefinition();
        onlyKnownLabel.setLabelCode("SAND");
        when(labelDefinitionRepository.findByDatasetVersionId(5L)).thenReturn(List.of(onlyKnownLabel));

        executor.executeImportAsync(2L, 10L, uploadDir, ImportFormat.MLC, "v2.0", "researcher_01");

        verify(datasetVersionService, never()).createSnapshot(any(), any());
        verify(importJobRepository).save(org.mockito.ArgumentMatchers.argThat(saved ->
                "FAILED".equals(saved.getStatus()) && saved.getLabelsUnresolved() != null && saved.getLabelsUnresolved() == 1));
    }
}
