package com.corallineage.backend.datasets.importservice;

import java.nio.file.Path;

public interface DatasetFormatParser {

    ParseResult parse(Path extractedDir);
}
