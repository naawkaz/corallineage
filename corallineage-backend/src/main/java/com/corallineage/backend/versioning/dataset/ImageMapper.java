package com.corallineage.backend.versioning.dataset;

import org.springframework.stereotype.Component;

@Component
public class ImageMapper {

    public ImageResponse toResponse(Image image) {
        ImageResponse response = new ImageResponse();
        response.setId(image.getId());
        response.setFilename(image.getFilename());
        response.setUrl("/images/" + image.getId() + "/file");
        response.setDvcFileHash(image.getDvcFileHash());
        response.setSite(image.getSite());
        response.setSurveyDate(image.getSurveyDate());
        response.setAddedAt(image.getAddedAt());
        return response;
    }

    public AnnotationPointSummaryResponse toPointSummary(AnnotationPoint point) {
        AnnotationPointSummaryResponse response = new AnnotationPointSummaryResponse();
        response.setId(point.getId());
        response.setImageId(point.getImage().getId());
        response.setPointIndex(point.getPointIndex());
        response.setRowPosition(point.getRowPosition());
        response.setColPosition(point.getColPosition());
        response.setLabelCode(point.getLabelDefinition().getLabelCode());
        return response;
    }
}
