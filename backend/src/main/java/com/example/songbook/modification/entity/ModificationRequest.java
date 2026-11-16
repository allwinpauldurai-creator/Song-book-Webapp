package com.example.songbook.modification.entity;

import com.example.songbook.book.entity.Book;
import com.example.songbook.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "modification_requests")
public class ModificationRequest {

    public enum Type { CORRECTION, ADDITION, OTHER }

    public enum Status { PENDING, APPROVED, REJECTED }

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by")
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id")
    private Book book;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 30)
    private Type requestType;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version;

    protected ModificationRequest() {
    }

    public ModificationRequest(User requestedBy, Book book, Type requestType, String description) {
        this.id = UUID.randomUUID();
        this.requestedBy = requestedBy;
        this.book = book;
        this.requestType = requestType;
        this.description = description.trim();
    }

    public void review(Status decision, String note, UUID reviewer, Instant now) {
        this.status = decision;
        this.reviewNote = note;
        this.reviewedBy = reviewer;
        this.reviewedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public User getRequestedBy() {
        return requestedBy;
    }

    public Book getBook() {
        return book;
    }

    public Type getRequestType() {
        return requestType;
    }

    public String getDescription() {
        return description;
    }

    public Status getStatus() {
        return status;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getVersion() {
        return version;
    }
}
