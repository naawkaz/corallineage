package com.corallineage.backend.service;

import com.corallineage.backend.dto.CreateDatasetRequest;
import com.corallineage.backend.dto.DatasetResponse;
import com.corallineage.backend.entity.Dataset;
import com.corallineage.backend.entity.DatasetVersion;
import com.corallineage.backend.exception.ConflictException;
import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.mapper.DatasetMapper;
import com.corallineage.backend.repository.DatasetRepository;
import com.corallineage.backend.repository.DatasetVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DatasetService {

    private final DatasetRepository datasetRepository;
    private final DatasetVersionRepository datasetVersionRepository;
    private final DatasetMapper datasetMapper;

    public DatasetService(DatasetRepository datasetRepository,
                          DatasetVersionRepository datasetVersionRepository,
                          DatasetMapper datasetMapper) {
        this.datasetRepository = datasetRepository;
        this.datasetVersionRepository = datasetVersionRepository;
        this.datasetMapper = datasetMapper;
    }

    @Transactional
    public DatasetResponse createDataset(CreateDatasetRequest request) {
        if (datasetRepository.findByDatasetName(request.getDatasetName()).isPresent()) {
            throw new ConflictException("Dataset name already exists: " + request.getDatasetName());
        }

        Dataset dataset = new Dataset();
        dataset.setDatasetName(request.getDatasetName());
        dataset.setDescription(request.getDescription());
        dataset.setCreatedBy(request.getCreatedBy());
        Dataset saved = datasetRepository.save(dataset);

        return datasetMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<DatasetResponse> getAllDatasets() {
        return datasetRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(dataset -> {
                    long versionCount = datasetVersionRepository.countByDatasetId(dataset.getId());
                    String latestVersionTag = datasetVersionRepository
                            .findTopByDatasetIdOrderByCreatedAtDesc(dataset.getId())
                            .map(DatasetVersion::getVersionTag)
                            .orElse(null);
                    return datasetMapper.toSummaryResponse(dataset, versionCount, latestVersionTag);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public DatasetResponse getDatasetById(Long id) {
        Dataset dataset = datasetRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Dataset not found: " + id));

        long versionCount = datasetVersionRepository.countByDatasetId(id);
        DatasetVersion latest = datasetVersionRepository
                .findTopByDatasetIdOrderByCreatedAtDesc(id)
                .orElse(null);

        return datasetMapper.toDetailResponse(dataset, versionCount,
                latest != null ? latest.getVersionTag() : null,
                latest != null ? latest.getImageCount() : 0,
                latest != null ? latest.getAnnotationCount() : 0);
    }
}
