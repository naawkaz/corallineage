package com.corallineage.backend.datasets.importservice;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Imports images with no annotation file, labels are declared upfront */
@Component
public class BlankFormatParser implements DatasetFormatParser {

    private static final List<String> IMAGE_EXTENSIONS = List.of(".jpg", ".jpeg", ".png");

    @Override
    public ParseResult parse(Path extractedDir) {
        List<ParsedImage> images = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(extractedDir)) {
            walk.filter(p -> Files.isRegularFile(p) && hasImageExtension(p))
                    .forEach(p -> images.add(new ParsedImage(p.getFileName().toString(), p)));
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read uploaded directory contents", ex);
        }
        return new ParseResult(images, List.of());
    }

    private boolean hasImageExtension(Path path) {
        String lower = path.getFileName().toString().toLowerCase();
        return IMAGE_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }
}
