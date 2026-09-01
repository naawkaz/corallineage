package com.corallineage.backend.datasets.importservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Imports COCO JSON annotations, maps category id to label code */
@Component
public class CocoJsonParser implements DatasetFormatParser {

    private static final String ANNOTATIONS_FILENAME = "annotations.json";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ParseResult parse(Path extractedDir) {
        Path jsonFile = findAnnotationsFile(extractedDir);
        if (jsonFile == null) {
            throw new ImportParseException("No " + ANNOTATIONS_FILENAME + " found in the uploaded archive");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(jsonFile.toFile());
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to parse " + jsonFile.getFileName(), ex);
        }

        Map<Long, String> categoryIdToLabelCode = new HashMap<>();
        for (JsonNode category : requireArray(root, "categories", jsonFile)) {
            categoryIdToLabelCode.put(category.get("id").asLong(), category.get("name").asText());
        }

        Map<Long, ParsedImage> imagesById = new HashMap<>();
        Map<Long, String> imageIdToFilename = new HashMap<>();
        for (JsonNode image : requireArray(root, "images", jsonFile)) {
            long imageId = image.get("id").asLong();
            String filename = image.get("file_name").asText();
            Path imagePath = extractedDir.resolve(filename);
            if (!Files.exists(imagePath)) {
                Path found = findByFilename(extractedDir, filename);
                imagePath = found;
            }
            if (imagePath == null || !Files.exists(imagePath)) {
                throw new ImportParseException("Image file \"" + filename + "\" referenced in " + ANNOTATIONS_FILENAME
                        + " not found in archive");
            }
            imagesById.put(imageId, new ParsedImage(filename, imagePath));
            imageIdToFilename.put(imageId, filename);
        }

        List<ParsedAnnotationPoint> points = new ArrayList<>();
        Map<Long, Integer> pointIndexByImage = new HashMap<>();
        for (JsonNode annotation : requireArray(root, "annotations", jsonFile)) {
            long imageId = annotation.get("image_id").asLong();
            long categoryId = annotation.get("category_id").asLong();
            String filename = imageIdToFilename.get(imageId);
            if (filename == null) {
                throw new ImportParseException("Annotation references unknown image_id " + imageId);
            }
            String labelCode = categoryIdToLabelCode.get(categoryId);
            if (labelCode == null) {
                throw new ImportParseException("Annotation references unknown category_id " + categoryId);
            }

            int[] rowCol = resolveCoordinates(annotation, jsonFile);
            int pointIndex = pointIndexByImage.merge(imageId, 1, Integer::sum) - 1;
            points.add(new ParsedAnnotationPoint(filename, pointIndex, rowCol[0], rowCol[1], labelCode));
        }

        return new ParseResult(new ArrayList<>(imagesById.values()), points);
    }

    private int[] resolveCoordinates(JsonNode annotation, Path jsonFile) {
        JsonNode point = annotation.get("point");
        if (point != null && point.has("x") && point.has("y")) {
            return new int[] { point.get("y").asInt(), point.get("x").asInt() };
        }
        JsonNode bbox = annotation.get("bbox");
        if (bbox != null && bbox.isArray() && bbox.size() == 4) {
            double x = bbox.get(0).asDouble();
            double y = bbox.get(1).asDouble();
            double width = bbox.get(2).asDouble();
            double height = bbox.get(3).asDouble();
            return new int[] { (int) Math.round(y + height / 2), (int) Math.round(x + width / 2) };
        }
        throw new ImportParseException(
                "Annotation in " + jsonFile.getFileName() + " has neither a \"point\" nor a \"bbox\" field");
    }

    private Path findAnnotationsFile(Path extractedDir) {
        Path direct = extractedDir.resolve(ANNOTATIONS_FILENAME);
        if (Files.exists(direct))
            return direct;
        return findByFilename(extractedDir, ANNOTATIONS_FILENAME);
    }

    private Path findByFilename(Path extractedDir, String filename) {
        try (var walk = Files.walk(extractedDir)) {
            return walk
                    .filter(p -> Files.isRegularFile(p) && p.getFileName().toString().equals(filename))
                    .findFirst()
                    .orElse(null);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to scan archive contents", ex);
        }
    }

    private Iterable<JsonNode> requireArray(JsonNode root, String field, Path jsonFile) {
        JsonNode node = root.get(field);
        if (node == null || !node.isArray()) {
            throw new ImportParseException(
                    jsonFile.getFileName() + " is missing required array field \"" + field + "\"");
        }
        return node;
    }
}
