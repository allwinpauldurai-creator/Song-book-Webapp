package com.example.songbook.book.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Catalogue availability rule (requirements section 5.2). Dates are inclusive on both ends;
 * a null expiry date means the book never expires automatically.
 */
public final class BookAvailability {

    private BookAvailability() {
    }

    public static boolean isAvailable(BookStatus status, LocalDate effectiveDate, LocalDate expiryDate, LocalDate today) {
        Objects.requireNonNull(today, "today");
        if (status != BookStatus.PUBLISHED || effectiveDate == null) {
            return false;
        }
        if (today.isBefore(effectiveDate)) {
            return false;
        }
        return expiryDate == null || !today.isAfter(expiryDate);
    }
}
