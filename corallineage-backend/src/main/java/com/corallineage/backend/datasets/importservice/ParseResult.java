package com.corallineage.backend.datasets.importservice;

import java.util.List;

public record ParseResult(List<ParsedImage> images, List<ParsedAnnotationPoint> points) {
}
