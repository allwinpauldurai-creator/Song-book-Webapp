package com.example.songbook.file.service;

import com.example.songbook.file.entity.FileAsset;
import com.example.songbook.file.entity.StorageCleanupItem;
import com.example.songbook.file.repository.StorageCleanupRepository;
import com.example.songbook.file.storage.FileStorageService;
import com.example.songbook.file.validation.ValidatedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Coordinates storage uploads with database metadata.
 * <p>
 * Blob storage and PostgreSQL do not share a transaction, so: upload first under a unique key,
 * then commit the metadata; on DB failure delete the blob, and if that fails enqueue it in
 * {@code storage_cleanup_queue} for the reconciliation job.
 */
@Service
public class FileAssetService {

    private static final Logger log = LoggerFactory.getLogger(FileAssetService.class);

    private final FileStorageService storage;
    private final StorageCleanupRepository cleanupRepository;
    private final TransactionTemplate requiresNew;
    private final Clock clock;

    public FileAssetService(FileStorageService storage, StorageCleanupRepository cleanupRepository,
                            PlatformTransactionManager txManager, Clock clock) {
        this.storage = storage;
        this.cleanupRepository = cleanupRepository;
        this.requiresNew = new TransactionTemplate(txManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    /** Uploads the file and returns an unsaved {@link FileAsset} describing it. */
    public FileAsset upload(ValidatedFile file, String container, String blobName, UUID actorId) {
        storage.upload(container, blobName, file.path(), file.contentType());
        return new FileAsset(UUID.randomUUID(), container, blobName, file.originalFilename(), file.contentType(),
                file.size(), file.sha256(), file.pageCount(), Instant.now(clock), actorId);
    }

    /** Compensates an upload whose database transaction failed. Never throws. */
    public void discard(FileAsset asset, String reason) {
        if (asset == null) {
            return;
        }
        try {
            storage.delete(asset.getContainerName(), asset.getBlobName());
        } catch (RuntimeException e) {
            log.warn("Could not delete orphaned blob {}/{}; queuing for cleanup", asset.getContainerName(),
                    asset.getBlobName(), e);
            try {
                requiresNew.executeWithoutResult(s -> cleanupRepository.save(new StorageCleanupItem(
                        asset.getContainerName(), asset.getBlobName(), reason, Instant.now(clock))));
            } catch (RuntimeException e2) {
                log.error("Could not queue orphaned blob {}/{} for cleanup", asset.getContainerName(),
                        asset.getBlobName(), e2);
            }
        }
    }
}
