package com.example.songbook.file.storage;

import com.example.songbook.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/** File-system storage for local development and tests. */
@Service
@ConditionalOnProperty(prefix = "songbook.storage", name = "provider", havingValue = "local")
public class LocalFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageService.class);

    private final Path root;

    public LocalFileStorageService(AppProperties properties) {
        String dir = properties.storage().localRootDir();
        if (dir == null || dir.isBlank()) {
            throw new IllegalStateException("songbook.storage.local-root-dir must be set for the local provider");
        }
        this.root = Path.of(dir).toAbsolutePath().normalize();
        log.info("Local file storage root: {}", root);
    }

    @Override
    public void upload(String container, String blobName, Path source, String contentType) {
        Path target = resolve(container, blobName);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(source, target); // no REPLACE_EXISTING: never overwrite
        } catch (FileAlreadyExistsException e) {
            throw new StorageException("Storage object already exists", e);
        } catch (IOException e) {
            throw new StorageException("Failed to store file", e);
        }
    }

    @Override
    public Resource load(String container, String blobName) {
        Path path = resolve(container, blobName);
        if (!Files.isRegularFile(path)) {
            throw new StorageException("Storage object not found");
        }
        return new FileSystemResource(path);
    }

    @Override
    public void delete(String container, String blobName) {
        try {
            Files.deleteIfExists(resolve(container, blobName));
        } catch (NoSuchFileException ignored) {
            // already gone
        } catch (IOException e) {
            throw new StorageException("Failed to delete file", e);
        }
    }

    @Override
    public boolean exists(String container, String blobName) {
        return Files.isRegularFile(resolve(container, blobName));
    }

    /** Resolves inside the root and rejects any path traversal. */
    private Path resolve(String container, String blobName) {
        Path path = root.resolve(container).resolve(blobName).normalize();
        if (!path.startsWith(root.resolve(container))) {
            throw new StorageException("Invalid storage key");
        }
        return path;
    }
}
