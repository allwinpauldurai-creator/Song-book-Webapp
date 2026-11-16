package com.example.songbook.file.entity;

import com.example.songbook.common.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A blob that may be orphaned and should be deleted by the reconciliation job. */
@Entity
@Table(name = "storage_cleanup_queue")
public class StorageCleanupItem extends AssignedIdEntity {

    @Id
    private UUID id;

    @Column(name = "container_name", nullable = false, length = 63)
    private String containerName;

    @Column(name = "blob_name", nullable = false, length = 512)
    private String blobName;

    @Column(nullable = false, length = 100)
    private String reason;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected StorageCleanupItem() {
    }

    public StorageCleanupItem(String containerName, String blobName, String reason, Instant now) {
        this.id = UUID.randomUUID();
        this.containerName = containerName;
        this.blobName = blobName;
        this.reason = reason;
        this.createdAt = now;
    }

    public void markProcessed(Instant now) {
        this.processedAt = now;
    }

    public void recordFailure(String error) {
        this.attempts++;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 1000));
    }

    @Override
    public UUID getId() {
        return id;
    }

    public String getContainerName() {
        return containerName;
    }

    public String getBlobName() {
        return blobName;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
