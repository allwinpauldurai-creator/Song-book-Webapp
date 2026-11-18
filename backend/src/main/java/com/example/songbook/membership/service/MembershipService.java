package com.example.songbook.membership.service;

import com.example.songbook.audit.AuditActions;
import com.example.songbook.audit.AuditService;
import com.example.songbook.common.exception.ApiException;
import com.example.songbook.common.response.PageResponse;
import com.example.songbook.common.util.PageableUtils;
import com.example.songbook.config.AppProperties;
import com.example.songbook.membership.domain.MembershipStatus;
import com.example.songbook.membership.dto.MembershipDtos.CreateMembershipRequest;
import com.example.songbook.membership.dto.MembershipDtos.MembershipResponse;
import com.example.songbook.membership.dto.MembershipDtos.MembershipSummary;
import com.example.songbook.membership.dto.MembershipDtos.UpdateMembershipRequest;
import com.example.songbook.membership.entity.Membership;
import com.example.songbook.membership.repository.MembershipRepository;
import com.example.songbook.user.entity.User;
import com.example.songbook.user.repository.UserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Membership records. History is preserved: a user may have many rows, at most one ACTIVE.
 */
@Service
public class MembershipService {

    private static final Map<String, String> SORTS = Map.of(
            "startDate", "startDate", "endDate", "endDate", "createdAt", "createdAt", "status", "status");

    private final MembershipRepository memberships;
    private final UserRepository users;
    private final AuditService audit;
    private final AppProperties.Membership config;
    private final Clock clock;

    public MembershipService(MembershipRepository memberships, UserRepository users, AuditService audit,
                             AppProperties properties, Clock clock) {
        this.memberships = memberships;
        this.users = users;
        this.audit = audit;
        this.config = properties.membership();
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    @Transactional(readOnly = true)
    public boolean hasValidMembership(UUID userId) {
        return memberships.findByUserIdAndStatus(userId, MembershipStatus.ACTIVE)
                .map(m -> m.isValidOn(today()))
                .orElse(false);
    }

    /** The ACTIVE membership if any, otherwise the most recent record, otherwise null. */
    @Transactional(readOnly = true)
    public MembershipSummary summaryFor(UUID userId) {
        LocalDate today = today();
        return memberships.findByUserIdAndStatus(userId, MembershipStatus.ACTIVE)
                .or(() -> memberships.findHistory(userId).stream().findFirst())
                .map(m -> MembershipSummary.from(m, today))
                .orElse(null);
    }

    /** Creates the configured default membership (at registration), unless one is already active. */
    @Transactional
    public void createDefaultIfAbsent(User user) {
        if (!config.autoCreateOnRegistration()
                || memberships.findByUserIdAndStatus(user.getId(), MembershipStatus.ACTIVE).isPresent()) {
            return;
        }
        LocalDate start = today();
        LocalDate end = config.defaultDurationDays() == 0 ? null : start.plusDays(config.defaultDurationDays());
        Membership m = memberships.save(new Membership(user, start, end, MembershipStatus.ACTIVE,
                "Created automatically at registration", null));
        audit.record(null, AuditActions.MEMBERSHIP_CREATED, "MEMBERSHIP", m.getId(),
                Map.of("userId", user.getId(), "automatic", true));
    }

    @Transactional(readOnly = true)
    public PageResponse<MembershipResponse> search(UUID userId, MembershipStatus status, Pageable pageable) {
        Specification<Membership> spec = (root, query, cb) -> cb.conjunction();
        if (userId != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("user").get("id"), userId));
        }
        if (status != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status));
        }
        LocalDate today = today();
        Pageable safe = PageableUtils.sanitize(pageable, SORTS, Sort.by(Sort.Direction.DESC, "startDate"));
        return PageResponse.from(memberships.findAll(spec, safe), m -> MembershipResponse.from(m, today));
    }

    @Transactional(readOnly = true)
    public List<MembershipResponse> historyFor(UUID userId) {
        LocalDate today = today();
        return memberships.findHistory(userId).stream().map(m -> MembershipResponse.from(m, today)).toList();
    }

    /** Creating a new ACTIVE membership cancels the user's previous ACTIVE one (history kept). */
    @Transactional
    public MembershipResponse create(CreateMembershipRequest req, UUID actorId) {
        validateDates(req.startDate(), req.endDate());
        User user = users.findById(req.userId()).orElseThrow(() -> ApiException.notFound("User not found"));
        if (req.status() == MembershipStatus.ACTIVE) {
            memberships.findByUserIdAndStatus(user.getId(), MembershipStatus.ACTIVE).ifPresent(previous -> {
                previous.setStatus(MembershipStatus.CANCELLED);
                memberships.saveAndFlush(previous); // flush before insert: one-ACTIVE unique index
                audit.record(actorId, AuditActions.MEMBERSHIP_UPDATED, "MEMBERSHIP", previous.getId(),
                        Map.of("status", "CANCELLED", "reason", "superseded"));
            });
        }
        Membership m = memberships.save(new Membership(user, req.startDate(), req.endDate(), req.status(),
                blankToNull(req.notes()), actorId));
        audit.record(actorId, AuditActions.MEMBERSHIP_CREATED, "MEMBERSHIP", m.getId(),
                details(user.getId(), m));
        return MembershipResponse.from(m, today());
    }

    @Transactional
    public MembershipResponse update(UUID id, UpdateMembershipRequest req, UUID actorId) {
        validateDates(req.startDate(), req.endDate());
        Membership m = memberships.findById(id).orElseThrow(() -> ApiException.notFound("Membership not found"));
        ApiException.checkVersion(req.version(), m.getVersion());
        if (req.status() == MembershipStatus.ACTIVE && m.getStatus() != MembershipStatus.ACTIVE
                && memberships.findByUserIdAndStatus(m.getUser().getId(), MembershipStatus.ACTIVE).isPresent()) {
            throw ApiException.conflict("ACTIVE_MEMBERSHIP_EXISTS",
                    "The user already has an active membership. End it first.");
        }
        m.setStartDate(req.startDate());
        m.setEndDate(req.endDate());
        m.setStatus(req.status());
        m.setNotes(blankToNull(req.notes()));
        memberships.flush();
        audit.record(actorId, AuditActions.MEMBERSHIP_UPDATED, "MEMBERSHIP", m.getId(), details(m.getUser().getId(), m));
        return MembershipResponse.from(m, today());
    }

    private static Map<String, Object> details(UUID userId, Membership m) {
        return Map.of("userId", userId, "status", m.getStatus(), "startDate", m.getStartDate(),
                "endDate", m.getEndDate() == null ? "none" : m.getEndDate());
    }

    private static void validateDates(LocalDate start, LocalDate end) {
        if (end != null && end.isBefore(start)) {
            throw ApiException.badRequest("INVALID_DATE_RANGE", "End date must be on or after start date");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
