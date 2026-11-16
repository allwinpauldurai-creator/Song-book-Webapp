package com.example.songbook.file.entity;

import com.example.songbook.common.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Metadata of a stored file. Never holds SAS tokens or URLs. */
@Entity
@Table(name = "file_assets")
public class FileAsset extends AssignedIdEntity {

    @Id
    private UUID id;

    @Column(name = "container_name", nullable = false, length = 63)
    private String containerName;

    @Column(name = "blob_name", nullable = false, length = 512)
    private String blobName;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    protected FileAsset() {
    }

    public FileAsset(UUID id, String containerName, String blobName, String originalFilename, String contentType,
                     long fileSize, String checksumSha256, Integer pageCount, Instant createdAt, UUID createdBy) {
        this.id = id;
        this.containerName = containerName;
        this.blobName = blobName;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.checksumSha256 = checksumSha256;
        this.pageCount = pageCount;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
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

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
