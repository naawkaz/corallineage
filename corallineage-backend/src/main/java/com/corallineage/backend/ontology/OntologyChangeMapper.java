package com.corallineage.backend.ontology;

import com.corallineage.backend.versioning.dataset.AnnotationPoint;
import com.corallineage.backend.versioning.dataset.AnnotationPointRepository;
import com.corallineage.backend.versioning.dataset.DatasetVersionService;
import org.springframework.stereotype.Component;

@Component
public class OntologyChangeMapper {

    private final AnnotationPointRepository annotationPointRepository;
    private final DatasetVersionService datasetVersionService;

    public OntologyChangeMapper(AnnotationPointRepository annotationPointRepository,
                                DatasetVersionService datasetVersionService) {
        this.annotationPointRepository = annotationPointRepository;
        this.datasetVersionService = datasetVersionService;
    }

    public OntologyChangeResponse toResponse(OntologyChange change, long dirtyAnnotationsFlagged, long resolvedCount) {
        OntologyChangeResponse response = new OntologyChangeResponse();
        response.setId(change.getId());
        response.setChangeType(ChangeType.valueOf(change.getChangeType()));
        response.setOldLabelCode(change.getOldLabelCode());
        response.setNewLabelCodes(change.getNewLabelCodes());
        response.setAffectedDatasetVersionId(
                change.getAffectedDatasetVersion() != null ? change.getAffectedDatasetVersion().getId() : null);
        response.setAffectedDatasetVersionTag(
                change.getAffectedDatasetVersion() != null ? change.getAffectedDatasetVersion().getVersionTag() : null);
        response.setAffectedDatasetVersionNumber(
                change.getAffectedDatasetVersion() != null
                        ? datasetVersionService.getVersionNumber(change.getAffectedDatasetVersion()) : null);
        response.setChangedBy(change.getChangedBy());
        response.setChangedAt(change.getChangedAt());
        response.setNotes(change.getNotes());
        response.setReplacementLabelCode(change.getReplacementLabelCode());
        response.setDirtyAnnotationsFlagged(dirtyAnnotationsFlagged);
        response.setResolvedCount(resolvedCount);
        return response;
    }

    public DirtyAnnotationResponse toDirtyResponse(DirtyAnnotation dirty) {
        DirtyAnnotationResponse response = new DirtyAnnotationResponse();
        response.setId(dirty.getId());
        response.setImageId(dirty.getImage() != null ? dirty.getImage().getId() : null);
        response.setFilename(dirty.getImage() != null ? dirty.getImage().getFilename() : null);
        response.setPointIndex(dirty.getPointIndex());
        response.setCurrentLabelCode(dirty.getCurrentLabelCode());
        response.setOntologyChangeId(dirty.getOntologyChange() != null ? dirty.getOntologyChange().getId() : null);
        response.setFlaggedAt(dirty.getFlaggedAt());
        response.setResolved(dirty.getResolvedAt() != null);
        response.setResolvedAt(dirty.getResolvedAt());
        response.setResolvedBy(dirty.getResolvedBy());
        response.setNewLabelCode(dirty.getNewLabelCode());
        if (dirty.getImage() != null && dirty.getPointIndex() != null) {
            annotationPointRepository
                    .findByImageIdAndPointIndex(dirty.getImage().getId(), dirty.getPointIndex())
                    .ifPresent((AnnotationPoint point) -> {
                        response.setRowPosition(point.getRowPosition());
                        response.setColPosition(point.getColPosition());
                    });
        }
        return response;
    }
}
