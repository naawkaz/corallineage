package com.corallineage.backend.versioning.dataset;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ImageController {

    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    @GetMapping("/api/v1/datasets/{datasetId}/versions/{versionId}/images")
    public Page<ImageResponse> getImages(
            @PathVariable Long datasetId,
            @PathVariable Long versionId,
            @RequestParam(defaultValue = "false") boolean humanLabeled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "40") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return imageService.listImages(datasetId, versionId, humanLabeled, pageable);
    }

    @GetMapping("/api/v1/datasets/{datasetId}/versions/{versionId}/points")
    public List<AnnotationPointSummaryResponse> getAnnotationPoints(
            @PathVariable Long datasetId,
            @PathVariable Long versionId) {
        return imageService.listAnnotationPoints(datasetId, versionId);
    }

    @GetMapping("/api/v1/images/{imageId}/file")
    public ResponseEntity<org.springframework.core.io.Resource> getImageFile(@PathVariable Long imageId) {
        ImageService.LoadedImage loaded = imageService.loadImageFile(imageId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(loaded.contentType()))
                .body(loaded.resource());
    }
}
