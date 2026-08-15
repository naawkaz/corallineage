package com.corallineage.backend.versioning.diff;

import com.corallineage.backend.exception.InvalidRequestException;
import com.corallineage.backend.versioning.dataset.DatasetVersion;
import com.corallineage.backend.versioning.dataset.DatasetVersionImageRepository;
import com.corallineage.backend.versioning.dataset.DatasetVersionRepository;
import com.corallineage.backend.versioning.dataset.Image;
import com.corallineage.backend.versioning.dataset.ImageRepository;
import com.corallineage.backend.versioning.dataset.LabelDefinition;
import com.corallineage.backend.versioning.dataset.LabelDefinitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
public class DatasetDiffService {

    private final DatasetVersionRepository datasetVersionRepository;
    private final LabelDefinitionRepository labelDefinitionRepository;
    private final ImageRepository imageRepository;
    private final DatasetVersionImageRepository datasetVersionImageRepository;
    private final ClassDistributionService classDistributionService;

    public DatasetDiffService(DatasetVersionRepository datasetVersionRepository,
                              LabelDefinitionRepository labelDefinitionRepository,
                              ImageRepository imageRepository,
                              DatasetVersionImageRepository datasetVersionImageRepository,
                              ClassDistributionService classDistributionService) {
        this.datasetVersionRepository = datasetVersionRepository;
        this.labelDefinitionRepository = labelDefinitionRepository;
        this.imageRepository = imageRepository;
        this.datasetVersionImageRepository = datasetVersionImageRepository;
        this.classDistributionService = classDistributionService;
    }

    @Transactional(readOnly = true)
    public DatasetDiffResponse computeDiff(Long datasetId, Long v1Id, Long v2Id) {
        if (v1Id.equals(v2Id)) {
            throw new InvalidRequestException("v1 and v2 must be different dataset versions");
        }
        DatasetVersion v1 = resolveVersion(datasetId, v1Id);
        DatasetVersion v2 = resolveVersion(datasetId, v2Id);

        List<LabelChangeEntry> labelChanges = diffLabels(v1Id, v2Id);
        int labelsAdded = (int) labelChanges.stream().filter(c -> "ADDED".equals(c.changeType())).count();
        int labelsDeprecated = (int) labelChanges.stream().filter(c -> "DEPRECATED".equals(c.changeType())).count();

        ImageDiff imageDiff = diffImages(datasetId, v1, v2);

        ClassDistributionResult classDistribution = diffClassDistribution(datasetId, v1, v2);

        DiffSummary summary = new DiffSummary(imageDiff.added().size(), imageDiff.removed().size(),
                classDistribution.annotationsModified(), labelsAdded, labelsDeprecated);

        return new DatasetDiffResponse(
                new VersionRef(v1.getId(), v1.getVersionTag()),
                new VersionRef(v2.getId(), v2.getVersionTag()),
                summary, labelChanges, classDistribution.entries(), imageDiff.added(), imageDiff.removed());
    }

    private DatasetVersion resolveVersion(Long datasetId, Long versionId) {
        DatasetVersion version = datasetVersionRepository.findById(versionId)
                .orElseThrow(() -> new InvalidRequestException("Dataset version not found: " + versionId));
        if (!version.getDataset().getId().equals(datasetId)) {
            throw new InvalidRequestException(
                    "Dataset version " + versionId + " does not belong to dataset " + datasetId);
        }
        return version;
    }

    private List<LabelChangeEntry> diffLabels(Long v1Id, Long v2Id) {
        Map<String, LabelDefinition> v1Labels = toLabelMap(labelDefinitionRepository.findByDatasetVersionId(v1Id));
        Map<String, LabelDefinition> v2Labels = toLabelMap(labelDefinitionRepository.findByDatasetVersionId(v2Id));

        List<LabelChangeEntry> changes = new ArrayList<>();
        for (Map.Entry<String, LabelDefinition> entry : v2Labels.entrySet()) {
            if (!v1Labels.containsKey(entry.getKey())) {
                changes.add(new LabelChangeEntry("ADDED", entry.getKey(), entry.getValue().getLabelName()));
            }
        }
        for (Map.Entry<String, LabelDefinition> entry : v1Labels.entrySet()) {
            if (!v2Labels.containsKey(entry.getKey())) {
                changes.add(new LabelChangeEntry("DEPRECATED", entry.getKey(), entry.getValue().getLabelName()));
            }
        }
        return changes;
    }

    private Map<String, LabelDefinition> toLabelMap(List<LabelDefinition> labels) {
        return labels.stream().collect(Collectors.toMap(
                LabelDefinition::getLabelCode, l -> l, (a, b) -> a, LinkedHashMap::new));
    }

    private ImageDiff diffImages(Long datasetId, DatasetVersion v1, DatasetVersion v2) {
        Set<Long> v1Ids = datasetVersionImageRepository.findImageIdsByDatasetVersionId(v1.getId());
        Set<Long> v2Ids = datasetVersionImageRepository.findImageIdsByDatasetVersionId(v2.getId());

        Set<Long> addedIds = v2Ids.stream().filter(id -> !v1Ids.contains(id)).collect(Collectors.toSet());
        Set<Long> removedIds = v1Ids.stream().filter(id -> !v2Ids.contains(id)).collect(Collectors.toSet());

        Map<Long, Image> addedImages = imageRepository.findAllById(addedIds).stream()
                .collect(Collectors.toMap(Image::getId, i -> i));
        Map<Long, Image> removedImages = imageRepository.findAllById(removedIds).stream()
                .collect(Collectors.toMap(Image::getId, i -> i));

        List<ImageRef> added = addedIds.stream()
                .map(id -> addedImages.get(id))
                .filter(i -> i != null)
                .map(i -> new ImageRef(i.getId(), i.getFilename()))
                .toList();
        List<ImageRef> removed = removedIds.stream()
                .map(id -> removedImages.get(id))
                .filter(i -> i != null)
                .map(i -> new ImageRef(i.getId(), i.getFilename()))
                .toList();
        return new ImageDiff(added, removed);
    }

    private ClassDistributionResult diffClassDistribution(Long datasetId, DatasetVersion v1, DatasetVersion v2) {
        Map<String, ClassCount> v1Counts = classDistributionService.countByLabelUpToVersion(datasetId, v1);
        Map<String, ClassCount> v2Counts = classDistributionService.countByLabelUpToVersion(datasetId, v2);

        Set<String> allCodes = new TreeSet<>();
        allCodes.addAll(v1Counts.keySet());
        allCodes.addAll(v2Counts.keySet());

        List<ClassDistributionEntry> entries = new ArrayList<>();
        long annotationsModified = 0;
        for (String code : allCodes) {
            ClassCount inV1 = v1Counts.get(code);
            ClassCount inV2 = v2Counts.get(code);
            long countInV1 = inV1 != null ? inV1.count() : 0;
            long countInV2 = inV2 != null ? inV2.count() : 0;
            String labelName = inV2 != null ? inV2.labelName() : inV1.labelName();
            long delta = countInV2 - countInV1;
            entries.add(new ClassDistributionEntry(code, labelName, countInV1, countInV2, delta));
            annotationsModified += Math.abs(delta);
        }
        return new ClassDistributionResult(entries, annotationsModified);
    }

    private record ImageDiff(List<ImageRef> added, List<ImageRef> removed) {
    }

    private record ClassDistributionResult(List<ClassDistributionEntry> entries, long annotationsModified) {
    }
}
