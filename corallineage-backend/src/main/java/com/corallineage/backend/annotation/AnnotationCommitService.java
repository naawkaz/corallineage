package com.corallineage.backend.annotation;

import com.corallineage.backend.exception.ConflictException;
import com.corallineage.backend.exception.InvalidRequestException;
import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.versioning.dataset.AnnotationPoint;
import com.corallineage.backend.versioning.dataset.AnnotationPointRepository;
import com.corallineage.backend.versioning.dataset.DatasetVersion;
import com.corallineage.backend.versioning.dataset.DatasetVersionRepository;
import com.corallineage.backend.versioning.dataset.LabelDefinition;
import com.corallineage.backend.versioning.dataset.LabelDefinitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AnnotationCommitService {

    private final AnnotationJobRepository annotationJobRepository;
    private final AnnotationPredictionRepository annotationPredictionRepository;
    private final AnnotationPointRepository annotationPointRepository;
    private final DatasetVersionRepository datasetVersionRepository;
    private final LabelDefinitionRepository labelDefinitionRepository;

    public AnnotationCommitService(AnnotationJobRepository annotationJobRepository,
                                   AnnotationPredictionRepository annotationPredictionRepository,
                                   AnnotationPointRepository annotationPointRepository,
                                   DatasetVersionRepository datasetVersionRepository,
                                   LabelDefinitionRepository labelDefinitionRepository) {
        this.annotationJobRepository = annotationJobRepository;
        this.annotationPredictionRepository = annotationPredictionRepository;
        this.annotationPointRepository = annotationPointRepository;
        this.datasetVersionRepository = datasetVersionRepository;
        this.labelDefinitionRepository = labelDefinitionRepository;
    }

    @Transactional
    public AnnotationJob applyToExistingVersion(Long jobId, String appliedBy) {
        AnnotationJob job = annotationJobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Annotation job not found: " + jobId));
        if (!Boolean.TRUE.equals(job.getCommitInPlace())) {
            throw new InvalidRequestException("Only existing-version jobs can be applied in place");
        }
        if (!"COMPLETED".equals(job.getReviewStatus())) {
            throw new InvalidRequestException("All points must be reviewed (accepted or overridden) before applying to the version");
        }
        if (job.getAddedToProjectVersionId() != null) {
            throw new ConflictException("Job " + jobId + " was already applied to dataset version "
                    + job.getAddedToProjectVersionId());
        }
        DatasetVersion version = job.getDatasetVersion();
        if (version == null) {
            throw new InvalidRequestException("Job " + jobId + " has no target dataset version");
        }

        List<AnnotationPrediction> predictions = annotationPredictionRepository.findByAnnotationJobId(jobId);
        int written = 0;
        for (AnnotationPrediction prediction : predictions) {
            boolean accepted = Boolean.TRUE.equals(prediction.getAccepted());
            String finalCode = accepted ? prediction.getPredictedLabelCode() : prediction.getCorrectedLabelCode();
            if (finalCode == null) {
                continue; // should not happen once reviewStatus is COMPLETED
            }
            LabelDefinition label = resolveOrDeclareLabel(version, finalCode);

            AnnotationPoint point = annotationPointRepository
                    .findByImageIdAndPointIndex(prediction.getImage().getId(), prediction.getPointIndex())
                    .orElseGet(AnnotationPoint::new);
            point.setImage(prediction.getImage());
            point.setPointIndex(prediction.getPointIndex());
            point.setRowPosition(prediction.getRowPosition() != null ? prediction.getRowPosition() : 0);
            point.setColPosition(prediction.getColPosition() != null ? prediction.getColPosition() : 0);
            point.setLabelDefinition(label);
            point.setAnnotationSource(accepted ? "MODEL_PREDICTED" : "MODEL_CORRECTED");
            point.setConfidenceScore(accepted ? prediction.getConfidenceScore() : null);
            point.setAnnotatedBy(appliedBy);
            annotationPointRepository.save(point);
            written++;
        }

        version.setAnnotationCount(version.getAnnotationCount() + written);
        datasetVersionRepository.save(version);

        job.setAddedToProjectVersionId(version.getId());
        return annotationJobRepository.save(job);
    }

    private LabelDefinition resolveOrDeclareLabel(DatasetVersion version, String code) {
        return labelDefinitionRepository.findByDatasetVersionIdAndLabelCode(version.getId(), code)
                .orElseGet(() -> {
                    LabelDefinition label = new LabelDefinition();
                    label.setDatasetVersion(version);
                    label.setLabelCode(code);
                    label.setLabelName(code);
                    return labelDefinitionRepository.save(label);
                });
    }
}
