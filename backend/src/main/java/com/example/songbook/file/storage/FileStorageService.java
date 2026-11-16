package com.example.songbook.file.storage;

import org.springframework.core.io.Resource;

import java.nio.file.Path;

/**
 * Storage abstraction for private book files. Implementations: {@link LocalFileStorageService}
 * (local/test) and {@link AzureBlobFileStorageService} (Azure, Managed Identity).
 * <p>
 * Keys are never exposed to clients. Writes never overwrite an existing object.
 */
public interface FileStorageService {

    /** Uploads {@code source} to {@code container/blobName}. Fails if the object already exists. */
    void upload(String container, String blobName, Path source, String contentType);

    /**
     * Returns a re-readable resource with a known content length, so Spring MVC can serve
     * HTTP Range requests from it.
     */
    Resource load(String container, String blobName);

    /** Deletes the object if present. */
    void delete(String container, String blobName);

    boolean exists(String container, String blobName);
}
