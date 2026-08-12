package com.corallineage.backend.datasets.importservice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MlcFormatParserTest {

    private final MlcFormatParser parser = new MlcFormatParser();

    @Test
    void parse_matchesTxtToImageAndParsesPoints(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("img001.jpg"));
        Files.writeString(dir.resolve("img001.txt"), "10 20 ACROPORA\n30 40 SAND\n");

        ParseResult result = parser.parse(dir);

        assertThat(result.images()).hasSize(1);
        assertThat(result.images().get(0).filename()).isEqualTo("img001.jpg");
        assertThat(result.points()).hasSize(2);
        assertThat(result.points().get(0)).isEqualTo(new ParsedAnnotationPoint("img001.jpg", 0, 10, 20, "ACROPORA"));
        assertThat(result.points().get(1)).isEqualTo(new ParsedAnnotationPoint("img001.jpg", 1, 30, 40, "SAND"));
    }

    @Test
    void parse_txtWithNoMatchingImage_isSkipped(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("orphan.txt"), "10 20 ACROPORA\n");

        ParseResult result = parser.parse(dir);

        assertThat(result.images()).isEmpty();
        assertThat(result.points()).isEmpty();
    }

    @Test
    void parse_malformedLine_throwsImportParseException(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("img001.png"));
        Files.writeString(dir.resolve("img001.txt"), "10 20\n");

        assertThatThrownBy(() -> parser.parse(dir)).isInstanceOf(ImportParseException.class);
    }

    @Test
    void parse_realWorldAppendedTxtConvention_matchesFullImageFilename(@TempDir Path dir) throws IOException {
        // e.g. Moorea Labeled Corals: "foo.jpg" + "foo.jpg.txt" (txt appended, not extension-replaced)
        Files.createFile(dir.resolve("mcr_lter1_pole1-2_qu1_20100411.jpg"));
        Files.writeString(dir.resolve("mcr_lter1_pole1-2_qu1_20100411.jpg.txt"), "1440; 1364; CCA\n");

        ParseResult result = parser.parse(dir);

        assertThat(result.images()).hasSize(1);
        assertThat(result.images().get(0).filename()).isEqualTo("mcr_lter1_pole1-2_qu1_20100411.jpg");
        assertThat(result.points()).containsExactly(
                new ParsedAnnotationPoint("mcr_lter1_pole1-2_qu1_20100411.jpg", 0, 1440, 1364, "CCA"));
    }

    @Test
    void parse_semicolonDelimitedRowsWithHeaderComment_areParsedCorrectly(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("img001.jpg"));
        Files.writeString(dir.resolve("img001.jpg.txt"),
                "# Row; Col; Label\n1440; 1364; CCA\n773; 1505; Sand\n");

        ParseResult result = parser.parse(dir);

        assertThat(result.points()).hasSize(2);
        assertThat(result.points().get(0)).isEqualTo(new ParsedAnnotationPoint("img001.jpg", 0, 1440, 1364, "CCA"));
        assertThat(result.points().get(1)).isEqualTo(new ParsedAnnotationPoint("img001.jpg", 1, 773, 1505, "Sand"));
    }

    @Test
    void parse_pngWithAppendedTxtConvention_matches(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("img002.png"));
        Files.writeString(dir.resolve("img002.png.txt"), "5; 6; Turf\n");

        ParseResult result = parser.parse(dir);

        assertThat(result.images()).hasSize(1);
        assertThat(result.images().get(0).filename()).isEqualTo("img002.png");
    }
}
