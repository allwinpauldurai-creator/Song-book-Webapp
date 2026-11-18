package com.example.songbook.reader.entity;

import com.example.songbook.common.persistence.AssignedIdEntity;
import com.example.songbook.book.entity.Book;
import com.example.songbook.book.entity.BookVersion;
import com.example.songbook.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reading_progress")
public class ReadingProgress extends AssignedIdEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id")
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_version_id")
    private BookVersion bookVersion;

    @Column(name = "last_page", nullable = false)
    private int lastPage;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ReadingProgress() {
    }

    public ReadingProgress(User user, Book book) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.book = book;
    }

    public void update(int lastPage, BookVersion version, Instant now) {
        this.lastPage = lastPage;
        this.bookVersion = version;
        this.updatedAt = now;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public Book getBook() {
        return book;
    }

    public BookVersion getBookVersion() {
        return bookVersion;
    }

    public int getLastPage() {
        return lastPage;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
