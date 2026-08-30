package com.corallineage.backend.datasets;

import com.corallineage.backend.exception.ConflictException;
import com.corallineage.backend.exception.ForbiddenException;
import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.exception.UnauthorizedException;
import com.corallineage.backend.projects.Project;
import com.corallineage.backend.projects.ProjectMemberService;
import com.corallineage.backend.projects.ProjectRepository;
import java.util.Set;
import com.corallineage.backend.versioning.dataset.DatasetVersionService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DatasetService {

    private final DatasetRepository datasetRepository;
    private final DatasetMapper datasetMapper;
    private final ProjectRepository projectRepository;
    private final ProjectMemberService projectMemberService;
    private final DatasetVersionService datasetVersionService;

    public DatasetService(DatasetRepository datasetRepository,
                          DatasetMapper datasetMapper,
                          ProjectRepository projectRepository,
                          ProjectMemberService projectMemberService,
                          DatasetVersionService datasetVersionService) {
        this.datasetRepository = datasetRepository;
        this.datasetMapper = datasetMapper;
        this.projectRepository = projectRepository;
        this.projectMemberService = projectMemberService;
        this.datasetVersionService = datasetVersionService;
    }

    /**
     * A version still sitting in the Unlabeled import queue (resolveStatus "UNRESOLVED" - see
     * DatasetVersionService.getVersionHistory's own filter) isn't a real, usable dataset version
     * yet, so it shouldn't count toward the "N versions" figure shown on the Sources page, or be
     * offered up as the "latest version" tag, either - both should match what a user actually sees
     * when they open the source's own version list, not a raw row count/latest-by-createdAt that
     * includes versions still waiting to be resolved.
     */
    private record ResolvedVersionSummary(long count, String latestVersionTag, int latestImageCount, int latestAnnotationCount) {
    }

    private ResolvedVersionSummary resolvedVersionSummary(Long datasetId) {
        // PageRequest.of(0, 1), not Pageable.unpaged(): getVersionHistory computes its full
        // resolved-version list and totalElements count internally regardless of page size, so a
        // 1-row page is exactly as cheap and gets everything this method needs (the total count
        // plus the single most-recent element for "latest version"). Unpaged() looks like the more
        // literal fit but isn't safe here - Spring's Unpaged.getPageSize() throws
        // UnsupportedOperationException, and getVersionHistory calls it to size its sublist.
        var resolved = datasetVersionService.getVersionHistory(datasetId, PageRequest.of(0, 1));
        var latest = resolved.getContent().isEmpty() ? null : resolved.getContent().get(0);
        return new ResolvedVersionSummary(resolved.getTotalElements(),
                latest != null ? latest.getVersionTag() : null,
                latest != null && latest.getImageCount() != null ? latest.getImageCount() : 0,
                latest != null && latest.getAnnotationCount() != null ? latest.getAnnotationCount() : 0);
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
                    ResolvedVersionSummary summary = resolvedVersionSummary(dataset.getId());
                    return datasetMapper.toSummaryResponse(dataset, summary.count(), summary.latestVersionTag());
                })
                .toList();
    }

    /**
     * Canonical membership/scope check for any operation on a single Dataset (Source).
     * Verifies the acting user belongs to the dataset's owning project, then, if that
     * user is a restricted collaborator, verifies this specific dataset is within their
     * explicit allowlist. Every other service that touches dataset-scoped resources
     * should resolve the relevant dataset id and call this before returning data or
     * performing a mutation, rather than re-implementing the check.
     */
    @Transactional(readOnly = true)
    public void requireAccess(Long datasetId, String actingUsername) {
        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new NotFoundException("Dataset not found: " + datasetId));

        Long projectId = dataset.getProject().getId();

        if (!projectMemberService.isMember(projectId, actingUsername)) {
            throw new ForbiddenException("You are not a member of the project that owns this dataset");
        }

        java.util.Optional<Set<Long>> accessibleSourceIds = projectMemberService.getAccessibleSourceIds(projectId, actingUsername);
        if (accessibleSourceIds.isPresent() && !accessibleSourceIds.get().contains(datasetId)) {
            throw new ForbiddenException("You do not have access to this dataset");
        }
    }

    @Transactional(readOnly = true)
    public DatasetResponse getDatasetById(Long id) {
        Dataset dataset = datasetRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Dataset not found: " + id));

        ResolvedVersionSummary summary = resolvedVersionSummary(id);

        return datasetMapper.toDetailResponse(dataset, summary.count(),
                summary.latestVersionTag(), summary.latestImageCount(), summary.latestAnnotationCount());
    }
}
