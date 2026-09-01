package com.corallineage.backend.datasets.importservice;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * MLC format: one .txt file per image, each line "row col label_code" or "row; col; label_code"
 * (semicolon-delimited exports also carry a leading "# Row; Col; Label" comment line). The image
 * filename is matched first as the full image name with ".txt" appended ("foo.jpg" + "foo.jpg.txt"),
 * then as the .txt name with its extension swapped for an image extension ("foo.txt" + "foo.jpg").
 * A .txt with no matching image is skipped rather than aborting the import.
 */
@Component
public class MlcFormatParser implements DatasetFormatParser {

    private static final List<String> IMAGE_EXTENSIONS = List.of(".jpg", ".jpeg", ".png");

    @Override
    public ParseResult parse(Path extractedDir) {
        List<ParsedImage> images = new ArrayList<>();
        List<ParsedAnnotationPoint> points = new ArrayList<>();

        try (Stream<Path> walk = Files.walk(extractedDir)) {
            List<Path> txtFiles = walk
                    .filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".txt"))
                    .toList();

            for (Path txtFile : txtFiles) {
                Path imageFile = findMatchingImage(txtFile);
                if (imageFile == null) {
                    continue;
                }
                String imageFilename = imageFile.getFileName().toString();
                images.add(new ParsedImage(imageFilename, imageFile));

                List<String> lines = Files.readAllLines(txtFile);
                int pointIndex = 0;
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                    String[] parts = splitFields(trimmed);
                    if (parts.length < 3) {
                        throw new ImportParseException(
                                "Malformed line in " + txtFile.getFileName() + ": \"" + line + "\" (expected \"row col label_code\")");
                    }
                    int row = parseInt(parts[0], txtFile);
                    int col = parseInt(parts[1], txtFile);
                    String labelCode = parts[2];
                    points.add(new ParsedAnnotationPoint(imageFilename, pointIndex++, row, col, labelCode));
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read MLC archive contents", ex);
        }

        return new ParseResult(images, points);
    }

    /**
     * Tries "foo.jpg.txt" -> "foo.jpg" first, then "foo.txt" -> "foo.jpg"/"foo.jpeg"/"foo.png".
     */
    private Path findMatchingImage(Path txtFile) throws IOException {
        String txtFilename = txtFile.getFileName().toString();
        Path dir = txtFile.getParent();

        String appendedConventionName = txtFilename.substring(0, txtFilename.length() - ".txt".length());
        Path direct = dir.resolve(appendedConventionName);
        if (Files.exists(direct)) return direct;

        String baseName = stripExtension(appendedConventionName);
        for (String ext : IMAGE_EXTENSIONS) {
            Path candidate = dir.resolve(baseName + ext);
            if (Files.exists(candidate)) return candidate;
        }
        return null;
    }

    private String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    /** Splits on semicolons, commas, and/or whitespace, trimming empty tokens. */
    private String[] splitFields(String line) {
        return Stream.of(line.split("[;,\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    private int parseInt(String value, Path file) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new ImportParseException("Malformed row/col value \"" + value + "\" in " + file.getFileName());
        }
    }
}
