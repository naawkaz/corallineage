package com.corallineage.backend.datasets.importservice;

import com.corallineage.backend.datasets.Dataset;
import com.corallineage.backend.datasets.DatasetRepository;
import com.corallineage.backend.exception.ConflictException;
import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.versioning.dataset.DatasetVersion;
import com.corallineage.backend.versioning.dataset.DatasetVersionRepository;
import com.corallineage.backend.versioning.dataset.DatasetVersionService;
import com.corallineage.backend.versioning.dataset.TriggerReason;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Service
public class DatasetImportService {

    private final DatasetRepository datasetRepository;
    private final DatasetVersionRepository datasetVersionRepository;
    private final DatasetVersionService datasetVersionService;
    private final ImportJobRepository importJobRepository;
    private final ImportJobMapper importJobMapper;
    private final DatasetImportExecutor datasetImportExecutor;

    public DatasetImportService(DatasetRepository datasetRepository,
            DatasetVersionRepository datasetVersionRepository,
            DatasetVersionService datasetVersionService,
            ImportJobRepository importJobRepository,
            ImportJobMapper importJobMapper,
            DatasetImportExecutor datasetImportExecutor) {
        this.datasetRepository = datasetRepository;
        this.datasetVersionRepository = datasetVersionRepository;
        this.datasetVersionService = datasetVersionService;
        this.importJobRepository = importJobRepository;
        this.importJobMapper = importJobMapper;
        this.datasetImportExecutor = datasetImportExecutor;
    }

    @Transactional
    public ImportJobResponse submitImport(Long datasetId, MultipartFile[] files, String[] relativePaths,
            ImportFormat format, String versionTag, String importedBy, String notes,
            List<LabelDeclarationRequest> labels) {
        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new NotFoundException("Dataset not found: " + datasetId));

        if (datasetVersionRepository.findByDatasetIdAndVersionTag(datasetId, versionTag).isPresent()) {
            throw new ConflictException("Dataset version tag already exists in this dataset: " + versionTag);
        }

        Path tempDir = persistUpload(files, relativePaths);

        DatasetVersion placeholder = format != ImportFormat.BLANK
                ? datasetVersionService.createImportPlaceholder(datasetId, versionTag, TriggerReason.IMAGES_ADDED,
                        importedBy, notes != null ? notes : "Imported via " + format.name())
                : null;

        ImportJob job = new ImportJob();
        job.setDataset(dataset);
        job.setFormat(format.name());
        job.setVersionTag(versionTag);
        job.setImportedBy(importedBy);
        job.setNotes(notes);
        job.setStatus("PROCESSING");
        if (placeholder != null) {
            job.setCreatedDatasetVersionId(placeholder.getId());
        }
        ImportJob saved = importJobRepository.save(job);

        // The async executor must not start until this transaction commits
        dispatchAfterCommit(saved.getId(), datasetId, tempDir, format, versionTag, importedBy, labels);

        return importJobMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ImportJobResponse getImportJob(Long datasetId, Long importJobId) {
        ImportJob job = importJobRepository.findByIdAndDatasetId(importJobId, datasetId)
                .orElseThrow(() -> new NotFoundException("Import job not found: " + importJobId));
        return importJobMapper.toResponse(job);
    }

    private void dispatchAfterCommit(Long jobId, Long datasetId, Path tempDir, ImportFormat format,
            String versionTag, String importedBy, List<LabelDeclarationRequest> labels) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    datasetImportExecutor.executeImportAsync(jobId, datasetId, tempDir, format, versionTag, importedBy,
                            labels);
                }
            });
        } else {
            datasetImportExecutor.executeImportAsync(jobId, datasetId, tempDir, format, versionTag, importedBy, labels);
        }
    }

    /** Reconstructs an uploaded directory into a temp directory */
    private Path persistUpload(MultipartFile[] files, String[] relativePaths) {
        if (files.length != relativePaths.length) {
            throw new ImportParseException("Mismatched files/relativePaths counts in upload");
        }
        try {
            Path tempDir = Files.createTempDirectory("coral-import-upload-");
            for (int i = 0; i < files.length; i++) {
                String relativePath = relativePaths[i];
                Path resolved = tempDir.resolve(relativePath).normalize();
                if (!resolved.startsWith(tempDir)) {
                    throw new ImportParseException("Uploaded file path escapes upload directory: " + relativePath);
                }
                Files.createDirectories(resolved.getParent());
                try (var in = files[i].getInputStream()) {
                    Files.copy(in, resolved, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return tempDir;
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read uploaded directory", ex);
        }
    }
}
