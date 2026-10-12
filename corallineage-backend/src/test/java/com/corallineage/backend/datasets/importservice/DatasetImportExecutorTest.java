package com.corallineage.backend.datasets.importservice;

import com.corallineage.backend.logging.AnnotationEventLoggerService;
import com.corallineage.backend.versioning.dataset.AnnotationPointRepository;
import com.corallineage.backend.versioning.dataset.DatasetVersion;
import com.corallineage.backend.versioning.dataset.DatasetVersionImageRepository;
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
import org.springframework.data.domain.PageImpl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock private DatasetVersionImageRepository datasetVersionImageRepository;
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
                datasetVersionImageRepository,
                annotationPointRepository,
                new LabelCodeResolver(),
                annotationEventLoggerService,
                new MlcFormatParser(),
                new CoralNetCsvParser(),
                new CocoJsonParser(),
                new BlankFormatParser(),
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

    private void stubPreviousVersions(DatasetVersion... versions) {
        when(datasetVersionRepository.findAllByDatasetIdOrderByCreatedAtDesc(eq(10L), any()))
                .thenReturn(new PageImpl<>(List.of(versions)));
        when(importJobRepository.findFailedCreatedDatasetVersionIds(10L)).thenReturn(List.of());
    }

    @Test
    void bootstrapsLabelDefinitions_whenDatasetHasNoPriorVersion(@TempDir Path uploadDir) throws IOException {
        writeMlcFixture(uploadDir);
        when(importJobRepository.findById(1L)).thenReturn(Optional.of(job(1L)));
        stubPreviousVersions();

        DatasetVersionResponse versionResponse = new DatasetVersionResponse();
        versionResponse.setId(100L);
        when(datasetVersionService.createSnapshot(any(), any())).thenReturn(versionResponse);

        DatasetVersion newVersion = new DatasetVersion();
        newVersion.setId(100L);
        newVersion.setImageCount(0);
        newVersion.setAnnotationCount(0);
        when(datasetVersionRepository.findById(100L)).thenReturn(Optional.of(newVersion));

        // Fresh version has no labels yet - bootstrapLabelDefinitions is expected to create them.
        when(labelDefinitionRepository.findByDatasetVersionId(100L)).thenReturn(List.of());
        when(labelDefinitionRepository.save(any(LabelDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

        when(imageRepository.saveAll(any())).thenAnswer(inv -> {
            List<Image> images = inv.getArgument(0);
            for (Image image : images) image.setId(1L);
            return images;
        });

        executor.executeImportAsync(1L, 10L, uploadDir, ImportFormat.MLC, "v1.0", "researcher_01", List.of());

        verify(labelDefinitionRepository, org.mockito.Mockito.times(2)).save(any(LabelDefinition.class));
        verify(importJobRepository).save(org.mockito.ArgumentMatchers.argThat(saved ->
                "COMPLETED".equals(saved.getStatus()) && saved.getLabelsUnresolved() == 0));
    }

    @Test
    void populatesPlaceholderVersion_whenJobAlreadyHasCreatedVersionId(@TempDir Path uploadDir) throws IOException {
        writeMlcFixture(uploadDir);
        ImportJob job = job(4L);
        job.setCreatedDatasetVersionId(500L);
        when(importJobRepository.findById(4L)).thenReturn(Optional.of(job));
        stubPreviousVersions();

        DatasetVersion placeholder = new DatasetVersion();
        placeholder.setId(500L);
        placeholder.setImageCount(0);
        placeholder.setAnnotationCount(0);
        when(datasetVersionRepository.findById(500L)).thenReturn(Optional.of(placeholder));
        when(labelDefinitionRepository.findByDatasetVersionId(500L)).thenReturn(List.of());
        when(labelDefinitionRepository.save(any(LabelDefinition.class))).thenAnswer(inv -> inv.getArgument(0));
        when(imageRepository.saveAll(any())).thenAnswer(inv -> {
            List<Image> images = inv.getArgument(0);
            for (Image image : images) image.setId(1L);
            return images;
        });

        executor.executeImportAsync(4L, 10L, uploadDir, ImportFormat.MLC, "v1.0", "researcher_01", List.of());

        verify(datasetVersionService).finalizeImportVersion(500L, null);
        verify(datasetVersionService, never()).createSnapshot(any(), any());
        verify(importJobRepository).save(org.mockito.ArgumentMatchers.argThat(saved ->
                "COMPLETED".equals(saved.getStatus())));
    }

    @Test
    void resolvesVocabularyAgainstLastGoodVersion_ignoringFailedImportPlaceholders(@TempDir Path uploadDir)
            throws IOException {
        writeMlcFixture(uploadDir);
        ImportJob job = job(6L);
        job.setCreatedDatasetVersionId(700L);
        when(importJobRepository.findById(6L)).thenReturn(Optional.of(job));

        DatasetVersion goodVersion = new DatasetVersion();
        goodVersion.setId(5L);
        DatasetVersion failedPlaceholder = new DatasetVersion();
        failedPlaceholder.setId(701L);
        DatasetVersion currentPlaceholder = new DatasetVersion();
        currentPlaceholder.setId(700L);
        when(datasetVersionRepository.findAllByDatasetIdOrderByCreatedAtDesc(eq(10L), any()))
                .thenReturn(new PageImpl<>(List.of(currentPlaceholder, failedPlaceholder, goodVersion)));
        when(importJobRepository.findFailedCreatedDatasetVersionIds(10L)).thenReturn(List.of(701L));

        LabelDefinition acropora = new LabelDefinition();
        acropora.setLabelCode("ACROPORA");
        LabelDefinition sand = new LabelDefinition();
        sand.setLabelCode("SAND");
        when(labelDefinitionRepository.findByDatasetVersionId(5L)).thenReturn(List.of(acropora, sand));

        DatasetVersion placeholder = new DatasetVersion();
        placeholder.setId(700L);
        placeholder.setImageCount(0);
        placeholder.setAnnotationCount(0);
        when(datasetVersionRepository.findById(700L)).thenReturn(Optional.of(placeholder));
        when(labelDefinitionRepository.findByDatasetVersionId(700L)).thenReturn(List.of(acropora, sand));
        when(imageRepository.saveAll(any())).thenAnswer(inv -> {
            List<Image> images = inv.getArgument(0);
            for (Image image : images) image.setId(1L);
            return images;
        });

        executor.executeImportAsync(6L, 10L, uploadDir, ImportFormat.MLC, "v3.0", "researcher_01", List.of());

        verify(datasetVersionService).finalizeImportVersion(700L, goodVersion);
        verify(importJobRepository).save(org.mockito.ArgumentMatchers.argThat(saved ->
                "COMPLETED".equals(saved.getStatus())));
    }

    @Test
    void abortsBeforeAnyWrites_whenExistingLabelsDoNotResolveRequestedCode(@TempDir Path uploadDir) throws IOException {
        writeMlcFixture(uploadDir);
        when(importJobRepository.findById(2L)).thenReturn(Optional.of(job(2L)));

        DatasetVersion previousLatest = new DatasetVersion();
        previousLatest.setId(5L);
        stubPreviousVersions(previousLatest);

        LabelDefinition onlyKnownLabel = new LabelDefinition();
        onlyKnownLabel.setLabelCode("SAND");
        when(labelDefinitionRepository.findByDatasetVersionId(5L)).thenReturn(List.of(onlyKnownLabel));

        executor.executeImportAsync(2L, 10L, uploadDir, ImportFormat.MLC, "v2.0", "researcher_01", List.of());

        verify(datasetVersionService, never()).createSnapshot(any(), any());
        verify(importJobRepository).save(org.mockito.ArgumentMatchers.argThat(saved ->
                "FAILED".equals(saved.getStatus()) && saved.getLabelsUnresolved() != null && saved.getLabelsUnresolved() == 1));
    }

    private void writeBlankFixture(Path dir) throws IOException {
        Files.createFile(dir.resolve("raw001.jpg"));
        Files.createFile(dir.resolve("raw002.jpg"));
    }

    @Test
    void blankFormat_declaresProvidedLabelCodes_andImportsNoPoints(@TempDir Path uploadDir) throws IOException {
        writeBlankFixture(uploadDir);
        when(importJobRepository.findById(3L)).thenReturn(Optional.of(job(3L)));
        stubPreviousVersions();

        DatasetVersionResponse versionResponse = new DatasetVersionResponse();
        versionResponse.setId(200L);
        when(datasetVersionService.createSnapshot(any(), any())).thenReturn(versionResponse);

        DatasetVersion newVersion = new DatasetVersion();
        newVersion.setId(200L);
        newVersion.setImageCount(0);
        newVersion.setAnnotationCount(0);
        when(datasetVersionRepository.findById(200L)).thenReturn(Optional.of(newVersion));

        when(labelDefinitionRepository.findByDatasetVersionId(200L)).thenReturn(List.of());
        when(labelDefinitionRepository.save(any(LabelDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

        when(imageRepository.saveAll(any())).thenAnswer(inv -> {
            List<Image> images = inv.getArgument(0);
            for (Image image : images) image.setId(1L);
            return images;
        });

        executor.executeImportAsync(3L, 10L, uploadDir, ImportFormat.BLANK, "v1.0", "researcher_01",
                List.of(new LabelDeclarationRequest("CCA", "Crustose Coralline Algae", null),
                        new LabelDeclarationRequest("Sand", "Sand", "Substrate")));

        org.mockito.ArgumentCaptor<LabelDefinition> captor = org.mockito.ArgumentCaptor.forClass(LabelDefinition.class);
        verify(labelDefinitionRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues().stream().map(LabelDefinition::getLabelCode).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("CCA", "Sand");
        assertThat(captor.getAllValues().stream().map(LabelDefinition::getLabelName).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("Crustose Coralline Algae", "Sand");
        assertThat(captor.getAllValues().stream()
                .filter(l -> "Sand".equals(l.getLabelCode()))
                .findFirst().orElseThrow().getLabelCategory())
                .isEqualTo("Substrate");
        verify(annotationPointRepository).saveAll(List.of());
        verify(importJobRepository).save(org.mockito.ArgumentMatchers.argThat(saved ->
                "COMPLETED".equals(saved.getStatus()) && saved.getLabelsResolved() == 2
                        && saved.getAnnotationPointsImported() == 0));
    }
}
