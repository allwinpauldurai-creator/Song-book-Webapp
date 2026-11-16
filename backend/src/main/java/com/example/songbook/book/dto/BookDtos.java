package com.example.songbook.book.dto;

import com.example.songbook.book.domain.BookStatus;
import com.example.songbook.book.entity.Book;
import com.example.songbook.book.entity.BookVersion;
import com.example.songbook.category.CategoryDtos.CategoryResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Book DTOs. Public DTOs never contain storage container/blob names or any file URL other than the
 * public cover endpoint.
 */
public final class BookDtos {

    private BookDtos() {
    }

    static String coverUrlOf(Book b) {
        return b.getCoverAsset() == null ? null : "/api/v1/books/" + b.getId() + "/cover";
    }

    static CategoryResponse categoryOf(Book b) {
        return b.getCategory() == null ? null : CategoryResponse.from(b.getCategory());
    }

    // ------------------------------------------------------------------ public

    public record BookSummary(UUID id, String title, String description, CategoryResponse category, String coverUrl) {
        public static BookSummary from(Book b) {
            return new BookSummary(b.getId(), b.getTitle(), b.getDescription(), categoryOf(b), coverUrlOf(b));
        }
    }

    public record BookDetail(UUID id, String title, String description, CategoryResponse category, String coverUrl,
                             LocalDate effectiveDate, LocalDate expiryDate, Integer pageCount, Instant updatedAt) {
        public static BookDetail from(Book b) {
            Integer pages = b.getCurrentVersion() == null ? null : b.getCurrentVersion().getPdfAsset().getPageCount();
            return new BookDetail(b.getId(), b.getTitle(), b.getDescription(), categoryOf(b), coverUrlOf(b),
                    b.getEffectiveDate(), b.getExpiryDate(), pages, b.getUpdatedAt());
        }
    }

    // ------------------------------------------------------------------ admin

    public record AdminBookSummary(UUID id, String title, CategoryResponse category, BookStatus status,
                                   LocalDate effectiveDate, LocalDate expiryDate, boolean availableToday,
                                   String coverUrl, Instant updatedAt) {
        public static AdminBookSummary from(Book b, LocalDate today) {
            return new AdminBookSummary(b.getId(), b.getTitle(), categoryOf(b), b.getStatus(), b.getEffectiveDate(),
                    b.getExpiryDate(), b.isAvailableOn(today), coverUrlOf(b), b.getUpdatedAt());
        }
    }

    public record AdminBookDetail(UUID id, String title, String description, CategoryResponse category,
                                  BookStatus status, LocalDate effectiveDate, LocalDate expiryDate,
                                  boolean availableToday, String coverUrl, BookVersionResponse currentVersion,
                                  UUID uploadedBy, Instant createdAt, Instant updatedAt, Long version) {
        public static AdminBookDetail from(Book b, LocalDate today) {
            return new AdminBookDetail(b.getId(), b.getTitle(), b.getDescription(), categoryOf(b), b.getStatus(),
                    b.getEffectiveDate(), b.getExpiryDate(), b.isAvailableOn(today), coverUrlOf(b),
                    b.getCurrentVersion() == null ? null : BookVersionResponse.from(b.getCurrentVersion(), true),
                    b.getUploadedBy(), b.getCreatedAt(), b.getUpdatedAt(), b.getVersion());
        }
    }

    public record BookVersionResponse(UUID id, int versionNumber, String originalFilename, long fileSize,
                                      Integer pageCount, String checksumSha256, String changeNote, UUID uploadedBy,
                                      Instant createdAt, boolean current) {
        public static BookVersionResponse from(BookVersion v, boolean current) {
            return new BookVersionResponse(v.getId(), v.getVersionNumber(), v.getPdfAsset().getOriginalFilename(),
                    v.getPdfAsset().getFileSize(), v.getPdfAsset().getPageCount(), v.getPdfAsset().getChecksumSha256(),
                    v.getChangeNote(), v.getUploadedBy(), v.getCreatedAt(), current);
        }
    }

    /**
     * Metadata part of the create-book multipart request. A null expiryDate means "no expiry".
     * Status may be DRAFT, PUBLISHED or INACTIVE (archiving has its own endpoint).
     */
    public record CreateBookRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 10000) String description,
            UUID categoryId,
            @NotNull LocalDate effectiveDate,
            LocalDate expiryDate,
            @NotNull BookStatus status) {
    }

    public record UpdateBookRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 10000) String description,
            UUID categoryId,
            @NotNull LocalDate effectiveDate,
            LocalDate expiryDate,
            @NotNull BookStatus status,
            @NotNull Long version) {
    }
}
