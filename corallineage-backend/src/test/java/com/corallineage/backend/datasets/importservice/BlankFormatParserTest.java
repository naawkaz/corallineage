package com.corallineage.backend.datasets.importservice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BlankFormatParserTest {

    private final BlankFormatParser parser = new BlankFormatParser();

    @Test
    void parse_collectsEveryImageFile_andNoPoints(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("img001.jpg"));
        Files.createFile(dir.resolve("img002.PNG"));
        Files.createDirectory(dir.resolve("sub"));
        Files.createFile(dir.resolve("sub/img003.jpeg"));

        ParseResult result = parser.parse(dir);

        assertThat(result.images()).hasSize(3);
        assertThat(result.images()).extracting("filename")
                .containsExactlyInAnyOrder("img001.jpg", "img002.PNG", "img003.jpeg");
        assertThat(result.points()).isEmpty();
    }

    @Test
    void parse_ignoresNonImageFiles(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("img001.jpg"));
        Files.createFile(dir.resolve("readme.txt"));
        Files.createFile(dir.resolve("annotations.json"));

        ParseResult result = parser.parse(dir);

        assertThat(result.images()).hasSize(1);
        assertThat(result.images().get(0).filename()).isEqualTo("img001.jpg");
    }

    @Test
    void parse_noImages_returnsEmptyResult(@TempDir Path dir) {
        ParseResult result = parser.parse(dir);

        assertThat(result.images()).isEmpty();
        assertThat(result.points()).isEmpty();
    }
}
