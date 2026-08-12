package com.corallineage.backend.training;

import java.math.BigDecimal;
import java.util.List;

public record AnnotateResult(List<AnnotatedImage> predictions) {

    public record AnnotatedImage(Long imageId, List<AnnotatedPoint> points) {
    }

    public record AnnotatedPoint(Integer pointIndex, String predictedLabelCode, BigDecimal confidenceScore) {
    }
}
