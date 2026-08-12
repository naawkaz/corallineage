package com.corallineage.backend.datasets.importservice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoralNetCsvParserTest {

    private final CoralNetCsvParser parser = new CoralNetCsvParser();

    @Test
    void parse_readsRequiredColumnsAndIgnoresExtras(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("img001.jpg"));
        Files.writeString(dir.resolve("annotations.csv"),
                "annotator,image_filename,row,col,label_code,date\n"
                        + "researcher_01,img001.jpg,10,20,ACROPORA,2025-01-01\n"
                        + "researcher_01,img001.jpg,30,40,SAND,2025-01-01\n");

        ParseResult result = parser.parse(dir);

        assertThat(result.images()).hasSize(1);
        assertThat(result.points()).hasSize(2);
        assertThat(result.points().get(0)).isEqualTo(new ParsedAnnotationPoint("img001.jpg", 0, 10, 20, "ACROPORA"));
        assertThat(result.points().get(1)).isEqualTo(new ParsedAnnotationPoint("img001.jpg", 1, 30, 40, "SAND"));
    }

    @Test
    void parse_missingRequiredColumn_throwsImportParseException(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("annotations.csv"), "image_filename,row,col\nimg001.jpg,10,20\n");

        assertThatThrownBy(() -> parser.parse(dir)).isInstanceOf(ImportParseException.class);
    }

    @Test
    void parse_referencedImageMissingFromArchive_throwsImportParseException(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("annotations.csv"), "image_filename,row,col,label_code\nghost.jpg,10,20,ACROPORA\n");

        assertThatThrownBy(() -> parser.parse(dir)).isInstanceOf(ImportParseException.class);
    }

    @Test
    void parse_noCsvFile_throwsImportParseException(@TempDir Path dir) {
        assertThatThrownBy(() -> parser.parse(dir)).isInstanceOf(ImportParseException.class);
    }
}
