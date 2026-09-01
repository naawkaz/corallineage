package com.corallineage.backend.versioning.diff;

import com.corallineage.backend.versioning.dataset.AnnotationPointRepository;
import com.corallineage.backend.versioning.dataset.DatasetVersion;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Per label annotation point counts as of a version snapshot */
@Component
public class ClassDistributionService {

    private final AnnotationPointRepository annotationPointRepository;

    public ClassDistributionService(AnnotationPointRepository annotationPointRepository) {
        this.annotationPointRepository = annotationPointRepository;
    }

    public Map<String, ClassCount> countByLabelUpToVersion(Long datasetId, DatasetVersion cutoffVersion) {
        Map<String, ClassCount> counts = new LinkedHashMap<>();
        for (AnnotationPointRepository.LabelCountRow row
                : annotationPointRepository.countByLabelForVersionMembership(cutoffVersion.getId())) {
            counts.put(row.getLabelCode(), new ClassCount(row.getLabelCode(), row.getLabelName(), row.getPointCount()));
        }
        return counts;
    }
}
