package com.example.songbook.file.service;

import com.example.songbook.file.entity.StorageCleanupItem;
import com.example.songbook.file.repository.StorageCleanupRepository;
import com.example.songbook.file.storage.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Deletes blobs recorded as orphaned. Items failing 10 times stay for manual investigation. */
@Component
public class StorageCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(StorageCleanupJob.class);
    private static final int MAX_ATTEMPTS = 10;

    private final StorageCleanupRepository repository;
    private final FileStorageService storage;
    private final Clock clock;

    public StorageCleanupJob(StorageCleanupRepository repository, FileStorageService storage, Clock clock) {
        this.repository = repository;
        this.storage = storage;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT2M")
    @Transactional
    public int processQueue() {
        List<StorageCleanupItem> items = repository.findPending(MAX_ATTEMPTS, PageRequest.of(0, 100));
        int done = 0;
        for (StorageCleanupItem item : items) {
            try {
                storage.delete(item.getContainerName(), item.getBlobName());
                item.markProcessed(Instant.now(clock));
                done++;
            } catch (RuntimeException e) {
                item.recordFailure(e.getMessage());
                log.warn("Storage cleanup failed for item {} (attempt {})", item.getId(), item.getAttempts());
            }
        }
        if (!items.isEmpty()) {
            log.info("Storage cleanup processed {}/{} items", done, items.size());
        }
        return done;
    }
}
