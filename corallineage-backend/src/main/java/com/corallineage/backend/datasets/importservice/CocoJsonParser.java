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
            String declared = image.get("file_name").asText();
            String filename = basename(declared);
            Path imagePath = extractedDir.resolve(declared);
            if (!Files.exists(imagePath)) {
                imagePath = findByFilename(extractedDir, filename);
            }
            if (imagePath == null || !Files.exists(imagePath)) {
                throw new ImportParseException("Image file \"" + declared + "\" referenced in " + ANNOTATIONS_FILENAME
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

            for (int[] rowCol : resolveCoordinates(annotation, jsonFile)) {
                int pointIndex = pointIndexByImage.merge(imageId, 1, Integer::sum) - 1;
                points.add(new ParsedAnnotationPoint(filename, pointIndex, rowCol[0], rowCol[1], labelCode));
            }
        }

        return new ParseResult(new ArrayList<>(imagesById.values()), points);
    }

    private List<int[]> resolveCoordinates(JsonNode annotation, Path jsonFile) {
        JsonNode keypoints = annotation.get("keypoints");
        if (keypoints != null && keypoints.isArray() && keypoints.size() >= 3) {
            List<int[]> result = new ArrayList<>();
            for (int i = 0; i + 2 < keypoints.size(); i += 3) {
                int visibility = keypoints.get(i + 2).asInt();
                if (visibility == 0) {
                    continue;
                }
                result.add(new int[] { keypoints.get(i + 1).asInt(), keypoints.get(i).asInt() });
            }
            if (!result.isEmpty()) {
                return result;
            }
        }
        JsonNode point = annotation.get("point");
        if (point != null && point.has("x") && point.has("y")) {
            return List.of(new int[] { point.get("y").asInt(), point.get("x").asInt() });
        }
        JsonNode bbox = annotation.get("bbox");
        if (bbox != null && bbox.isArray() && bbox.size() == 4) {
            double x = bbox.get(0).asDouble();
            double y = bbox.get(1).asDouble();
            double width = bbox.get(2).asDouble();
            double height = bbox.get(3).asDouble();
            return List.of(new int[] { (int) Math.round(y + height / 2), (int) Math.round(x + width / 2) });
        }
        throw new ImportParseException("Annotation in " + jsonFile.getFileName()
                + " has none of a \"keypoints\", \"point\" or \"bbox\" field");
    }

    private String basename(String filename) {
        String normalized = filename.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
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
