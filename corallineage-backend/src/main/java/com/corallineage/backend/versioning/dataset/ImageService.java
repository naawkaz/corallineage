package com.corallineage.backend.versioning.dataset;

import com.corallineage.backend.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class ImageService {

    private final DatasetVersionRepository datasetVersionRepository;
    private final ImageRepository imageRepository;
    private final DatasetVersionImageRepository datasetVersionImageRepository;
    private final AnnotationPointRepository annotationPointRepository;
    private final ImageMapper imageMapper;
    private final Path workingDirectory;

    public ImageService(DatasetVersionRepository datasetVersionRepository,
                        ImageRepository imageRepository,
                        DatasetVersionImageRepository datasetVersionImageRepository,
                        AnnotationPointRepository annotationPointRepository,
                        ImageMapper imageMapper,
                        @Value("${app.dvc.working-directory}") Path workingDirectory) {
        this.datasetVersionRepository = datasetVersionRepository;
        this.imageRepository = imageRepository;
        this.datasetVersionImageRepository = datasetVersionImageRepository;
        this.annotationPointRepository = annotationPointRepository;
        this.imageMapper = imageMapper;
        this.workingDirectory = workingDirectory;
    }

    @Transactional(readOnly = true)
    public Page<ImageResponse> listImages(Long datasetId, Long versionId, Pageable pageable) {
        return listImages(datasetId, versionId, false, pageable);
    }

    /**
     * @param humanLabeledOnly when true, restricts the page to images that are current members of
     *                         the version and have at least one HUMAN-sourced AnnotationPoint - the
     *                         annotation job Submit page's Mode A "has human-labeled points" filter.
     */
    @Transactional(readOnly = true)
    public Page<ImageResponse> listImages(Long datasetId, Long versionId, boolean humanLabeledOnly, Pageable pageable) {
        DatasetVersion version = datasetVersionRepository.findById(versionId)
                .orElseThrow(() -> new NotFoundException("Dataset version not found: " + versionId));
        if (!version.getDataset().getId().equals(datasetId)) {
            throw new NotFoundException("Dataset version not found: " + versionId);
        }
        if (!humanLabeledOnly) {
            return datasetVersionImageRepository.findByDatasetVersionId(versionId, pageable)
                    .map(membership -> imageMapper.toResponse(membership.getImage()));
        }
        List<Long> eligibleIds = annotationPointRepository
                .findDistinctImageIdsByDatasetVersionAndAnnotationSourceIn(versionId, List.of("HUMAN"));
        return datasetVersionImageRepository.findByDatasetVersionIdAndImageIdIn(versionId, eligibleIds, pageable)
                .map(membership -> imageMapper.toResponse(membership.getImage()));
    }

    /**
     * Every annotation point on every image currently a member of {@code versionId}, for the
     * Dataset Versions image grid's optional points overlay. Callers are expected to only invoke
     * this when the user has switched the overlay toggle on, since it returns the whole version's
     * points in one shot rather than paging.
     */
    @Transactional(readOnly = true)
    public List<AnnotationPointSummaryResponse> listAnnotationPoints(Long datasetId, Long versionId) {
        DatasetVersion version = datasetVersionRepository.findById(versionId)
                .orElseThrow(() -> new NotFoundException("Dataset version not found: " + versionId));
        if (!version.getDataset().getId().equals(datasetId)) {
            throw new NotFoundException("Dataset version not found: " + versionId);
        }
        return annotationPointRepository.findByDatasetVersionMembership(versionId).stream()
                .map(imageMapper::toPointSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public LoadedImage loadImageFile(Long imageId) {
        Image image = imageRepository.findById(imageId)
                .orElseThrow(() -> new NotFoundException("Image not found: " + imageId));
        Path filePath = workingDirectory.resolve(image.getFilePath());
        if (!Files.isRegularFile(filePath)) {
            throw new NotFoundException("Image file not found on disk: " + image.getFilePath());
        }
        String contentType;
        try {
            contentType = Files.probeContentType(filePath);
        } catch (IOException ex) {
            contentType = null;
        }
        return new LoadedImage(new FileSystemResource(filePath), contentType != null ? contentType : "application/octet-stream");
    }

    public record LoadedImage(Resource resource, String contentType) {
    }
}
