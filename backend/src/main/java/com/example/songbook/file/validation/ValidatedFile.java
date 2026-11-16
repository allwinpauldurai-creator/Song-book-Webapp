package com.example.songbook.file.validation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A validated upload copied to a temporary file. Close it to delete the temp file.
 *
 * @param extension file extension derived from the detected type (not from the client filename)
 */
public record ValidatedFile(
        Path path,
        String contentType,
        String extension,
        long size,
        String sha256,
        Integer pageCount,
        String originalFilename) implements AutoCloseable {

    @Override
    public void close() {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // temp file; OS cleans up eventually
        }
    }
}
