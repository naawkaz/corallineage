package com.corallineage.backend.datasets;

import com.corallineage.backend.auth.User;
import com.corallineage.backend.auth.UserRepository;
import com.corallineage.backend.exception.ConflictException;
import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.exception.UnauthorizedException;
import com.corallineage.backend.versioning.dataset.DatasetVersion;
import com.corallineage.backend.versioning.dataset.DatasetVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DatasetService {

    private final DatasetRepository datasetRepository;
    private final DatasetVersionRepository datasetVersionRepository;
    private final DatasetMapper datasetMapper;
    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectMemberService projectMemberService;

    public DatasetService(DatasetRepository datasetRepository,
                          DatasetVersionRepository datasetVersionRepository,
                          DatasetMapper datasetMapper,
                          UserRepository userRepository,
                          ProjectMemberRepository projectMemberRepository,
                          ProjectMemberService projectMemberService) {
        this.datasetRepository = datasetRepository;
        this.datasetVersionRepository = datasetVersionRepository;
        this.datasetMapper = datasetMapper;
        this.userRepository = userRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.projectMemberService = projectMemberService;
    }

    @Transactional
    public DatasetResponse createDataset(CreateDatasetRequest request, String actingUsername) {
        if (datasetRepository.findByDatasetName(request.getDatasetName()).isPresent()) {
            throw new ConflictException("Dataset name already exists: " + request.getDatasetName());
        }

        User owner = userRepository.findByUsername(actingUsername)
                .orElseThrow(() -> new UnauthorizedException("Unknown user"));

        Dataset dataset = new Dataset();
        dataset.setDatasetName(request.getDatasetName());
        dataset.setDescription(request.getDescription());
        dataset.setCreatedBy(request.getCreatedBy());
        Dataset saved = datasetRepository.save(dataset);

        projectMemberService.addOwner(saved, owner);

        return datasetMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<DatasetResponse> getAllDatasets(String actingUsername) {
        User user = userRepository.findByUsername(actingUsername)
                .orElseThrow(() -> new UnauthorizedException("Unknown user"));

        List<Long> memberDatasetIds = projectMemberRepository.findByUserId(user.getId()).stream()
                .map(m -> m.getDataset().getId())
                .toList();

        if (memberDatasetIds.isEmpty()) {
            return List.of();
        }

        return datasetRepository.findAllByIdInOrderByCreatedAtDesc(memberDatasetIds).stream()
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
