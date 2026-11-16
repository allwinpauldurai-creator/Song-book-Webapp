package com.example.songbook.admin;

import com.example.songbook.book.domain.BookStatus;
import com.example.songbook.membership.domain.MembershipStatus;
import com.example.songbook.modification.entity.ModificationRequest;
import com.example.songbook.role.RoleName;
import com.example.songbook.user.entity.AccountStatus;
import com.example.songbook.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only counts for the admin dashboard. Uses grouped JPQL counts rather than loading rows.
 * "Valid today" mirrors {@code MembershipValidity.isValid}: ACTIVE, started, and not past its end date,
 * evaluated with the business-timezone {@link Clock}.
 */
@Service
public class AdminStatsService {

    private final EntityManager em;
    private final UserRepository users;
    private final Clock clock;

    public AdminStatsService(EntityManager em, UserRepository users, Clock clock) {
        this.em = em;
        this.users = users;
        this.clock = clock;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public AdminStats stats() {
        LocalDate today = LocalDate.now(clock);

        Map<AccountStatus, Long> usersByStatus = grouped(AccountStatus.class,
                "select u.accountStatus, count(u) from User u group by u.accountStatus");
        long totalUsers = usersByStatus.values().stream().mapToLong(Long::longValue).sum();
        long unverified = em.createQuery("select count(u) from User u where u.emailVerified = false", Long.class)
                .getSingleResult();

        long validMemberships = em.createQuery("""
                        select count(m) from Membership m
                        where m.status = :active and m.startDate <= :today
                          and (m.endDate is null or m.endDate >= :today)""", Long.class)
                .setParameter("active", MembershipStatus.ACTIVE)
                .setParameter("today", today)
                .getSingleResult();

        Map<BookStatus, Long> booksByStatus = grouped(BookStatus.class,
                "select b.status, count(b) from Book b group by b.status");

        long pendingRequests = em.createQuery(
                        "select count(r) from ModificationRequest r where r.status = :pending", Long.class)
                .setParameter("pending", ModificationRequest.Status.PENDING)
                .getSingleResult();

        return new AdminStats(
                new UserCounts(totalUsers, usersByStatus.get(AccountStatus.ACTIVE), usersByStatus.get(AccountStatus.DISABLED),
                        usersByStatus.get(AccountStatus.LOCKED), unverified, users.countActiveWithRole(RoleName.ADMIN)),
                validMemberships,
                booksByStatus,
                pendingRequests,
                today);
    }

    /** Runs a "select enumField, count(x) ... group by enumField" query; every enum constant gets a value. */
    private <E extends Enum<E>> Map<E, Long> grouped(Class<E> type, String jpql) {
        Map<E, Long> result = new EnumMap<>(type);
        for (E constant : type.getEnumConstants()) {
            result.put(constant, 0L);
        }
        List<Object[]> rows = em.createQuery(jpql, Object[].class).getResultList();
        for (Object[] row : rows) {
            result.put(type.cast(row[0]), (Long) row[1]);
        }
        return result;
    }

    public record UserCounts(long total, long active, long disabled, long locked, long unverified, long activeAdmins) {
    }

    /**
     * @param membershipsValidToday ACTIVE memberships whose date range includes {@code asOf}
     * @param booksByStatus         every {@link BookStatus} with its count (zero when none)
     * @param asOf                  the business-timezone date the counts were evaluated for
     */
    public record AdminStats(UserCounts users, long membershipsValidToday, Map<BookStatus, Long> booksByStatus,
                             long pendingRequests, LocalDate asOf) {
    }
}
