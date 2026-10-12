package com.corallineage.backend.datasets.importservice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CocoJsonParserTest {

    private final CocoJsonParser parser = new CocoJsonParser();

    @Test
    void parse_usesPointFieldWhenPresent(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("img001.jpg"));
        Files.writeString(dir.resolve("annotations.json"), """
                {
                  "images": [{"id": 1, "file_name": "img001.jpg"}],
                  "categories": [{"id": 5, "name": "ACROPORA"}],
                  "annotations": [{"id": 1, "image_id": 1, "category_id": 5, "point": {"x": 20, "y": 10}}]
                }
                """);

        ParseResult result = parser.parse(dir);

        assertThat(result.images()).hasSize(1);
        assertThat(result.images().get(0).filename()).isEqualTo("img001.jpg");
        assertThat(result.points()).hasSize(1);
        assertThat(result.points().get(0)).isEqualTo(new ParsedAnnotationPoint("img001.jpg", 0, 10, 20, "ACROPORA"));
    }

    @Test
    void parse_readsKeypointsArray(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("img001.jpg"));
        Files.writeString(dir.resolve("annotations.json"), """
                {
                  "images": [{"id": 1, "file_name": "img001.jpg", "width": 100, "height": 100}],
                  "categories": [{"id": 5, "name": "ACROPORA", "supercategory": "coral"}],
                  "annotations": [
                    {"id": 1, "image_id": 1, "category_id": 5, "num_keypoints": 2,
                     "keypoints": [20, 10, 2, 0, 0, 0, 40, 30, 1]}
                  ]
                }
                """);

        ParseResult result = parser.parse(dir);

        assertThat(result.points()).containsExactly(
                new ParsedAnnotationPoint("img001.jpg", 0, 10, 20, "ACROPORA"),
                new ParsedAnnotationPoint("img001.jpg", 1, 30, 40, "ACROPORA"));
    }

    @Test
    void parse_stripsPathFromFileName(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("img001.jpg"));
        Files.writeString(dir.resolve("annotations.json"), """
                {
                  "images": [{"id": 1, "file_name": "images/img001.jpg"}],
                  "categories": [{"id": 5, "name": "SAND"}],
                  "annotations": [{"id": 1, "image_id": 1, "category_id": 5, "point": {"x": 1, "y": 2}}]
                }
                """);

        ParseResult result = parser.parse(dir);

        assertThat(result.images().get(0).filename()).isEqualTo("img001.jpg");
        assertThat(result.points().get(0).imageFilename()).isEqualTo("img001.jpg");
    }

    @Test
    void parse_fallsBackToBboxCentroidWhenNoPoint(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("img001.jpg"));
        Files.writeString(dir.resolve("annotations.json"), """
                {
                  "images": [{"id": 1, "file_name": "img001.jpg"}],
                  "categories": [{"id": 5, "name": "SAND"}],
                  "annotations": [{"id": 1, "image_id": 1, "category_id": 5, "bbox": [10, 20, 4, 6]}]
                }
                """);

        ParseResult result = parser.parse(dir);

        assertThat(result.points()).hasSize(1);
        assertThat(result.points().get(0)).isEqualTo(new ParsedAnnotationPoint("img001.jpg", 0, 23, 12, "SAND"));
    }

    @Test
    void parse_unknownCategoryId_throwsImportParseException(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("img001.jpg"));
        Files.writeString(dir.resolve("annotations.json"), """
                {
                  "images": [{"id": 1, "file_name": "img001.jpg"}],
                  "categories": [{"id": 5, "name": "SAND"}],
                  "annotations": [{"id": 1, "image_id": 1, "category_id": 99, "point": {"x": 1, "y": 1}}]
                }
                """);

        assertThatThrownBy(() -> parser.parse(dir)).isInstanceOf(ImportParseException.class);
    }

    @Test
    void parse_imageFileMissingFromArchive_throwsImportParseException(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("annotations.json"), """
                {
                  "images": [{"id": 1, "file_name": "ghost.jpg"}],
                  "categories": [],
                  "annotations": []
                }
                """);

        assertThatThrownBy(() -> parser.parse(dir)).isInstanceOf(ImportParseException.class);
    }
}
