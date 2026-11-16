package com.example.songbook.book.entity;

import com.example.songbook.common.persistence.AssignedIdEntity;
import com.example.songbook.file.entity.FileAsset;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Immutable PDF version of a book. Old versions are retained when a PDF is replaced. */
@Entity
@Table(name = "book_versions")
public class BookVersion extends AssignedIdEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id")
    private Book book;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pdf_asset_id")
    private FileAsset pdfAsset;

    @Column(name = "change_note", length = 500)
    private String changeNote;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected BookVersion() {
    }

    public BookVersion(Book book, int versionNumber, FileAsset pdfAsset, String changeNote, UUID uploadedBy, Instant now) {
        this.id = UUID.randomUUID();
        this.book = book;
        this.versionNumber = versionNumber;
        this.pdfAsset = pdfAsset;
        this.changeNote = changeNote;
        this.uploadedBy = uploadedBy;
        this.createdAt = now;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public Book getBook() {
        return book;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public FileAsset getPdfAsset() {
        return pdfAsset;
    }

    public String getChangeNote() {
        return changeNote;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
