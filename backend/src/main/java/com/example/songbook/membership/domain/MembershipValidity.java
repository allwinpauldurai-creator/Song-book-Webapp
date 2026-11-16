package com.example.songbook.membership.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Membership validity rule (requirements section 4.4). Dates are inclusive; a null end date
 * means the membership does not end automatically (whether new memberships get one is a
 * configuration decision, see {@code songbook.membership.default-duration-days}).
 */
public final class MembershipValidity {

    private MembershipValidity() {
    }

    public static boolean isValid(MembershipStatus status, LocalDate startDate, LocalDate endDate, LocalDate today) {
        Objects.requireNonNull(today, "today");
        if (status != MembershipStatus.ACTIVE || startDate == null) {
            return false;
        }
        if (today.isBefore(startDate)) {
            return false;
        }
        return endDate == null || !today.isAfter(endDate);
    }
}
