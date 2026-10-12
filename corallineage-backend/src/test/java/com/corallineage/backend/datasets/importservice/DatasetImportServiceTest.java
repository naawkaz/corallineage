package com.corallineage.backend.datasets.importservice;

import com.corallineage.backend.datasets.Dataset;
import com.corallineage.backend.datasets.DatasetRepository;
import com.corallineage.backend.exception.ConflictException;
import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.versioning.dataset.DatasetVersion;
import com.corallineage.backend.versioning.dataset.DatasetVersionRepository;
import com.corallineage.backend.versioning.dataset.DatasetVersionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatasetImportServiceTest {

    @Mock
    private DatasetRepository datasetRepository;
    @Mock
    private DatasetVersionRepository datasetVersionRepository;
    @Mock
    private DatasetVersionService datasetVersionService;
    @Mock
    private ImportJobRepository importJobRepository;
    @Mock
    private DatasetImportExecutor datasetImportExecutor;

    @InjectMocks
    private DatasetImportService datasetImportService;

    @Spy
    private ImportJobMapper importJobMapper;

    private Dataset dataset() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setDatasetName("Moorea Labeled Corals");
        return dataset;
    }

    private MockMultipartFile[] files() {
        return new MockMultipartFile[] {
                new MockMultipartFile("files", "qu1.jpg", "image/jpeg", new byte[] {1, 2, 3}),
        };
    }

    private String[] relativePaths() {
        return new String[] {"qu1.jpg"};
    }

    @Test
    void submitImport_datasetNotFound_throwsNotFound() {
        when(datasetRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> datasetImportService.submitImport(
                99L, files(), relativePaths(), ImportFormat.MLC, "v1.0", "researcher_01", null, List.of()))
                .isInstanceOf(NotFoundException.class);
        verify(datasetImportExecutor, never()).executeImportAsync(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void submitImport_duplicateVersionTag_throwsConflict() {
        DatasetVersion existing = new DatasetVersion();
        existing.setId(5L);

        when(datasetRepository.findById(1L)).thenReturn(Optional.of(dataset()));
        when(datasetVersionRepository.findByDatasetIdAndVersionTag(1L, "v1.0")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> datasetImportService.submitImport(
                1L, files(), relativePaths(), ImportFormat.MLC, "v1.0", "researcher_01", null, List.of()))
                .isInstanceOf(ConflictException.class);
        verify(datasetImportExecutor, never()).executeImportAsync(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void submitImport_success_createsJobWithProcessingStatusAndDispatches() {
        when(datasetRepository.findById(1L)).thenReturn(Optional.of(dataset()));
        when(datasetVersionRepository.findByDatasetIdAndVersionTag(1L, "v1.0")).thenReturn(Optional.empty());
        DatasetVersion placeholder = new DatasetVersion();
        placeholder.setId(42L);
        when(datasetVersionService.createImportPlaceholder(eq(1L), eq("v1.0"), any(), eq("researcher_01"), any()))
                .thenReturn(placeholder);
        when(importJobRepository.save(any(ImportJob.class))).thenAnswer(inv -> {
            ImportJob job = inv.getArgument(0);
            job.setId(7L);
            return job;
        });

        ImportJobResponse response = datasetImportService.submitImport(
                1L, files(), relativePaths(), ImportFormat.CORALNET_CSV, "v1.0", "researcher_01", "test notes", List.of());

        assertThat(response.getImportJobId()).isEqualTo("IMP-7");
        assertThat(response.getStatus()).isEqualTo("PROCESSING");
        assertThat(response.getFormat()).isEqualTo("CORALNET_CSV");
        verify(datasetImportExecutor).executeImportAsync(
                eq(7L), eq(1L), any(), eq(ImportFormat.CORALNET_CSV), eq("v1.0"), eq("researcher_01"), eq(List.of()));
        verify(importJobRepository).save(org.mockito.ArgumentMatchers.argThat(j -> Long.valueOf(42L).equals(j.getCreatedDatasetVersionId())));
    }

    @Test
    void submitImport_blankFormat_passesLabelDeclarationsThrough() {
        when(datasetRepository.findById(1L)).thenReturn(Optional.of(dataset()));
        when(datasetVersionRepository.findByDatasetIdAndVersionTag(1L, "v1.0")).thenReturn(Optional.empty());
        when(importJobRepository.save(any(ImportJob.class))).thenAnswer(inv -> {
            ImportJob job = inv.getArgument(0);
            job.setId(8L);
            return job;
        });

        List<LabelDeclarationRequest> labels = List.of(
                new LabelDeclarationRequest("CCA", "Crustose Coralline Algae", null),
                new LabelDeclarationRequest("Sand", "Sand", "Substrate"));

        datasetImportService.submitImport(
                1L, files(), relativePaths(), ImportFormat.BLANK, "v1.0", "researcher_01", null, labels);

        verify(datasetImportExecutor).executeImportAsync(
                eq(8L), eq(1L), any(), eq(ImportFormat.BLANK), eq("v1.0"), eq("researcher_01"), eq(labels));
    }

    @Test
    void getImportJob_notFound_throwsNotFound() {
        when(importJobRepository.findByIdAndDatasetId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> datasetImportService.getImportJob(1L, 99L))
                .isInstanceOf(NotFoundException.class);
    }
}
