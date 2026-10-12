package com.corallineage.backend.datasets.importservice;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/** Imports a CoralNet CSV, accepting the site export header and the shorthand column names */
@Component
public class CoralNetCsvParser implements DatasetFormatParser {

    private static final Map<String, List<String>> COLUMN_ALIASES = Map.of(
            "image_filename", List.of("image_filename", "name"),
            "row", List.of("row"),
            "col", List.of("col", "column"),
            "label_code", List.of("label_code", "label"));

    @Override
    public ParseResult parse(Path extractedDir) {
        Path csvFile = findCsvFile(extractedDir);
        if (csvFile == null) {
            throw new ImportParseException("No .csv file found in the uploaded archive");
        }

        try {
            List<String> lines = Files.readAllLines(csvFile);
            if (lines.isEmpty()) {
                throw new ImportParseException("CSV file " + csvFile.getFileName() + " is empty");
            }

            Map<String, Integer> columnIndex = indexColumns(splitCsvLine(lines.get(0)), csvFile);

            List<ParsedAnnotationPoint> points = new ArrayList<>();
            Map<String, Integer> pointIndexByImage = new HashMap<>();
            Map<String, ParsedImage> imagesByFilename = new LinkedHashMap<>();

            for (int lineNo = 1; lineNo < lines.size(); lineNo++) {
                String raw = lines.get(lineNo);
                if (raw.isBlank())
                    continue;
                String[] fields = splitCsvLine(raw);

                String imageFilename = basename(field(fields, columnIndex, "image_filename", csvFile, lineNo));
                int row = parseInt(field(fields, columnIndex, "row", csvFile, lineNo), csvFile, lineNo);
                int col = parseInt(field(fields, columnIndex, "col", csvFile, lineNo), csvFile, lineNo);
                String labelCode = field(fields, columnIndex, "label_code", csvFile, lineNo);

                int currentLineNo = lineNo;
                imagesByFilename.computeIfAbsent(imageFilename, filename -> {
                    Path imagePath = findImageFile(extractedDir, filename);
                    if (imagePath == null) {
                        throw new ImportParseException(
                                "Line " + (currentLineNo + 1) + " in " + csvFile.getFileName()
                                        + ": image file \"" + filename + "\" not found in archive");
                    }
                    return new ParsedImage(filename, imagePath);
                });

                int pointIndex = pointIndexByImage.merge(imageFilename, 1, Integer::sum) - 1;
                points.add(new ParsedAnnotationPoint(imageFilename, pointIndex, row, col, labelCode));
            }

            return new ParseResult(new ArrayList<>(imagesByFilename.values()), points);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read CoralNet CSV archive contents", ex);
        }
    }

    private Path findCsvFile(Path extractedDir) {
        try (Stream<Path> walk = Files.walk(extractedDir)) {
            return walk
                    .filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".csv"))
                    .findFirst()
                    .orElse(null);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to scan archive contents", ex);
        }
    }

    private Path findImageFile(Path extractedDir, String filename) {
        try (Stream<Path> walk = Files.walk(extractedDir)) {
            return walk
                    .filter(p -> Files.isRegularFile(p) && p.getFileName().toString().equals(filename))
                    .findFirst()
                    .orElse(null);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to scan archive contents", ex);
        }
    }

    private Map<String, Integer> indexColumns(String[] header, Path csvFile) {
        Map<String, Integer> headerIndex = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            headerIndex.put(header[i].trim().toLowerCase(), i);
        }
        Map<String, Integer> index = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : COLUMN_ALIASES.entrySet()) {
            Integer position = entry.getValue().stream()
                    .map(headerIndex::get)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            if (position == null) {
                throw new ImportParseException("CSV file " + csvFile.getFileName()
                        + " is missing required column \"" + entry.getValue().get(0) + "\"");
            }
            index.put(entry.getKey(), position);
        }
        return index;
    }

    private String field(String[] fields, Map<String, Integer> columnIndex, String column, Path csvFile, int lineNo) {
        int idx = columnIndex.get(column);
        if (idx >= fields.length) {
            throw new ImportParseException("Line " + (lineNo + 1) + " in " + csvFile.getFileName()
                    + " is missing a value for \"" + column + "\"");
        }
        return fields[idx].trim();
    }

    private String basename(String filename) {
        String normalized = filename.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    private int parseInt(String value, Path csvFile, int lineNo) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new ImportParseException("Line " + (lineNo + 1) + " in " + csvFile.getFileName()
                    + ": malformed numeric value \"" + value + "\"");
        }
    }

    /** Splits a CSV line, supports quoted fields */
    private String[] splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else if (c == '"') {
                    inQuotes = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }
}
