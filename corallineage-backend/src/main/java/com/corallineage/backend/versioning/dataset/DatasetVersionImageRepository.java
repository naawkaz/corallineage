package com.corallineage.backend.versioning.dataset;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface DatasetVersionImageRepository extends JpaRepository<DatasetVersionImage, Long> {

    /** Paged membership rows for a version — backs the images grid. */
    Page<DatasetVersionImage> findByDatasetVersionId(Long datasetVersionId, Pageable pageable);

    /** Paged membership rows for a version, restricted to a given set of image ids; backs the "has human-labeled points" filter. */
    Page<DatasetVersionImage> findByDatasetVersionIdAndImageIdIn(Long datasetVersionId, List<Long> imageIds, Pageable pageable);

    /** Non-paged membership rows for a version — used to copy membership forward onto a new version. */
    List<DatasetVersionImage> findByDatasetVersionId(Long datasetVersionId);

    long countByDatasetVersionId(Long datasetVersionId);

    boolean existsByDatasetVersionIdAndImageId(Long datasetVersionId, Long imageId);

    @Query("select dvi.image.id from DatasetVersionImage dvi where dvi.datasetVersion.id = :datasetVersionId")
    Set<Long> findImageIdsByDatasetVersionId(@Param("datasetVersionId") Long datasetVersionId);
}
