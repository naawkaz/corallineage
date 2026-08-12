package com.corallineage.backend.datasets.importservice;

import java.nio.file.Path;

public record ParsedImage(String filename, Path sourcePath) {
}
