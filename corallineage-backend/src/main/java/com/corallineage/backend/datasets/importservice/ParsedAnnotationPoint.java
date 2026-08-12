package com.corallineage.backend.datasets.importservice;

public record ParsedAnnotationPoint(String imageFilename, int pointIndex, int row, int col, String labelCode) {
}
