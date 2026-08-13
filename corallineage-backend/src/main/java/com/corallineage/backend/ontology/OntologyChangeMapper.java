package com.corallineage.backend.ontology;

import org.springframework.stereotype.Component;

@Component
public class OntologyChangeMapper {

    public OntologyChangeResponse toResponse(OntologyChange change, long dirtyAnnotationsFlagged, long resolvedCount) {
        OntologyChangeResponse response = new OntologyChangeResponse();
        response.setId(change.getId());
        response.setChangeType(ChangeType.valueOf(change.getChangeType()));
        response.setOldLabelCode(change.getOldLabelCode());
        response.setNewLabelCodes(change.getNewLabelCodes());
        response.setAffectedDatasetVersionId(
                change.getAffectedDatasetVersion() != null ? change.getAffectedDatasetVersion().getId() : null);
        response.setChangedBy(change.getChangedBy());
        response.setChangedAt(change.getChangedAt());
        response.setNotes(change.getNotes());
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
        return response;
    }
}
