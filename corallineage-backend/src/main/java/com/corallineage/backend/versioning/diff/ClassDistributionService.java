package com.corallineage.backend.versioning.diff;

import com.corallineage.backend.versioning.dataset.AnnotationPointRepository;
import com.corallineage.backend.versioning.dataset.DatasetVersion;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Queries per-label annotation point counts for a dataset as of a given version's snapshot time. */
@Component
public class ClassDistributionService {

    private final AnnotationPointRepository annotationPointRepository;

    public ClassDistributionService(AnnotationPointRepository annotationPointRepository) {
        this.annotationPointRepository = annotationPointRepository;
    }

    public Map<String, ClassCount> countByLabelUpToVersion(Long datasetId, DatasetVersion cutoffVersion) {
        Map<String, ClassCount> counts = new LinkedHashMap<>();
        for (AnnotationPointRepository.LabelCountRow row
                : annotationPointRepository.countByLabelUpToVersion(datasetId, cutoffVersion.getCreatedAt())) {
            counts.put(row.getLabelCode(), new ClassCount(row.getLabelCode(), row.getLabelName(), row.getPointCount()));
        }
        return counts;
    }
}
