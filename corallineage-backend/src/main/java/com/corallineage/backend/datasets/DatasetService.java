package com.corallineage.backend.datasets;

import com.corallineage.backend.exception.ConflictException;
import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.exception.UnauthorizedException;
import com.corallineage.backend.projects.Project;
import com.corallineage.backend.projects.ProjectMemberService;
import com.corallineage.backend.projects.ProjectRepository;
import java.util.Set;
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
    private final ProjectRepository projectRepository;
    private final ProjectMemberService projectMemberService;

    public DatasetService(DatasetRepository datasetRepository,
                          DatasetVersionRepository datasetVersionRepository,
                          DatasetMapper datasetMapper,
                          ProjectRepository projectRepository,
                          ProjectMemberService projectMemberService) {
        this.datasetRepository = datasetRepository;
        this.datasetVersionRepository = datasetVersionRepository;
        this.datasetMapper = datasetMapper;
        this.projectRepository = projectRepository;
        this.projectMemberService = projectMemberService;
    }

    @Transactional
    public DatasetResponse createDataset(CreateDatasetRequest request, Long projectId, String actingUsername) {
        if (datasetRepository.findByDatasetName(request.getDatasetName()).isPresent()) {
            throw new ConflictException("Dataset name already exists: " + request.getDatasetName());
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));

        if (!projectMemberService.isMember(projectId, actingUsername)) {
            throw new UnauthorizedException("You are not a member of this project");
        }

        Dataset dataset = new Dataset();
        dataset.setDatasetName(request.getDatasetName());
        dataset.setDescription(request.getDescription());
        dataset.setCreatedBy(request.getCreatedBy());
        dataset.setProject(project);
        Dataset saved = datasetRepository.save(dataset);

        project.setUpdatedAt(java.time.Instant.now());
        projectRepository.save(project);

        return datasetMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<DatasetResponse> getAllDatasets(Long projectId, String actingUsername) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));

        if (!projectMemberService.isMember(projectId, actingUsername)) {
            throw new UnauthorizedException("You are not a member of this project");
        }

        java.util.Optional<Set<Long>> accessibleSourceIds = projectMemberService.getAccessibleSourceIds(projectId, actingUsername);

        return datasetRepository.findAllByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .filter(dataset -> accessibleSourceIds.isEmpty() || accessibleSourceIds.get().contains(dataset.getId()))
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
