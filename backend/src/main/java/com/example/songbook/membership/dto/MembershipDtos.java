package com.example.songbook.membership.dto;

import com.example.songbook.membership.domain.MembershipStatus;
import com.example.songbook.membership.entity.Membership;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class MembershipDtos {

    private MembershipDtos() {
    }

    public record MembershipSummary(UUID id, MembershipStatus status, LocalDate startDate, LocalDate endDate,
                                    boolean valid) {
        public static MembershipSummary from(Membership m, LocalDate today) {
            return new MembershipSummary(m.getId(), m.getStatus(), m.getStartDate(), m.getEndDate(), m.isValidOn(today));
        }
    }

    public record MembershipResponse(UUID id, UUID userId, String userEmail, String userFullName,
                                     MembershipStatus status, LocalDate startDate, LocalDate endDate,
                                     boolean validToday, String notes, UUID createdBy,
                                     Instant createdAt, Instant updatedAt, Long version) {
        public static MembershipResponse from(Membership m, LocalDate today) {
            return new MembershipResponse(m.getId(), m.getUser().getId(), m.getUser().getEmail(),
                    m.getUser().getFullName(), m.getStatus(), m.getStartDate(), m.getEndDate(), m.isValidOn(today),
                    m.getNotes(), m.getCreatedBy(), m.getCreatedAt(), m.getUpdatedAt(), m.getVersion());
        }
    }

    /** A blank endDate means "no end date". */
    public record CreateMembershipRequest(
            @NotNull UUID userId,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            @NotNull MembershipStatus status,
            @Size(max = 500) String notes) {
    }

    public record UpdateMembershipRequest(
            @NotNull LocalDate startDate,
            LocalDate endDate,
            @NotNull MembershipStatus status,
            @Size(max = 500) String notes,
            @NotNull Long version) {
    }
}
