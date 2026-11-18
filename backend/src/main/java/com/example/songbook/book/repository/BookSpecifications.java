package com.example.songbook.book.repository;

import com.example.songbook.book.domain.BookStatus;
import com.example.songbook.book.entity.Book;
import com.example.songbook.common.util.PageableUtils;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.UUID;

public final class BookSpecifications {

    private BookSpecifications() {
    }

    /** Mirrors {@link com.example.songbook.book.domain.BookAvailability} in SQL. */
    public static Specification<Book> availableOn(LocalDate today) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), BookStatus.PUBLISHED),
                cb.isNotNull(root.get("currentVersion")),
                cb.lessThanOrEqualTo(root.get("effectiveDate"), today),
                cb.or(cb.isNull(root.get("expiryDate")), cb.greaterThanOrEqualTo(root.get("expiryDate"), today)));
    }

    public static Specification<Book> matches(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String pattern = PageableUtils.likePattern(q);
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), pattern, '\\'),
                cb.like(cb.lower(root.get("description")), pattern, '\\'));
    }

    public static Specification<Book> inCategory(UUID categoryId) {
        return categoryId == null ? null : (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Book> uploadedBy(UUID userId) {
        return (root, query, cb) -> cb.equal(root.get("uploadedBy"), userId);
    }

    public static Specification<Book> hasStatus(BookStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
