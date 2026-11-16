package com.example.songbook.reader.service;

import com.example.songbook.audit.AuditActions;
import com.example.songbook.audit.AuditService;
import com.example.songbook.book.entity.Book;
import com.example.songbook.book.entity.BookVersion;
import com.example.songbook.book.repository.BookRepository;
import com.example.songbook.common.exception.ApiException;
import com.example.songbook.config.AppProperties;
import com.example.songbook.file.entity.FileAsset;
import com.example.songbook.file.storage.FileStorageService;
import com.example.songbook.membership.service.MembershipService;
import com.example.songbook.reader.service.AccessTicketService.Purpose;
import com.example.songbook.role.RoleName;
import com.example.songbook.user.entity.User;
import com.example.songbook.user.repository.UserRepository;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * The single place where PDF eligibility is decided (requirements 5.2 / 7):
 * active account, verified email, valid membership (admins may bypass, decision D3), and an available book.
 */
@Service
public class PdfAccessService {

    private final UserRepository users;
    private final BookRepository books;
    private final MembershipService memberships;
    private final AccessTicketService tickets;
    private final FileStorageService storage;
    private final AuditService audit;
    private final boolean adminsBypassMembership;
    private final Clock clock;

    public PdfAccessService(UserRepository users, BookRepository books, MembershipService memberships,
                            AccessTicketService tickets, FileStorageService storage, AuditService audit,
                            AppProperties properties, Clock clock) {
        this.users = users;
        this.books = books;
        this.memberships = memberships;
        this.tickets = tickets;
        this.storage = storage;
        this.audit = audit;
        this.adminsBypassMembership = properties.membership().adminsBypassMembership();
        this.clock = clock;
    }

    /** Checks eligibility and issues a ticket for the book's current version. */
    @Transactional
    public AccessGrant grant(UUID userId, UUID bookId, Purpose purpose) {
        Book book = requireEligible(userId, bookId);
        BookVersion version = book.getCurrentVersion();
        AccessTicketService.Ticket ticket = tickets.issue(userId, bookId, version.getId(), purpose);
        audit.record(userId, purpose == Purpose.DOWNLOAD ? AuditActions.PDF_DOWNLOAD_ACCESS : AuditActions.PDF_READ_ACCESS,
                "BOOK", bookId, Map.of("versionNumber", version.getVersionNumber()));
        return new AccessGrant(ticket.value(), ticket.expiresAt(), version.getId(), version.getVersionNumber(),
                version.getPdfAsset().getPageCount(),
                "/api/v1/books/" + bookId + "/content?ticket=" + ticket.value());
    }

    /** Validates a ticket, re-checks eligibility and opens the PDF. */
    @Transactional(readOnly = true)
    public PdfContent open(UUID bookId, String rawTicket) {
        AccessTicketService.Claims claims = tickets.verify(rawTicket)
                .filter(c -> c.bookId().equals(bookId))
                .orElseThrow(() -> ApiException.forbidden("INVALID_TICKET", "Access link is invalid or has expired"));
        Book book = requireEligible(claims.userId(), bookId);
        if (!book.getCurrentVersion().getId().equals(claims.versionId())) {
            throw ApiException.conflict("VERSION_CHANGED", "A newer version is available. Reopen the book.");
        }
        FileAsset pdf = book.getCurrentVersion().getPdfAsset();
        return new PdfContent(storage.load(pdf.getContainerName(), pdf.getBlobName()), book.getTitle(),
                claims.purpose() == Purpose.DOWNLOAD);
    }

    @Transactional(readOnly = true)
    public Book requireEligible(UUID userId, UUID bookId) {
        Instant now = Instant.now(clock);
        LocalDate today = LocalDate.now(clock);
        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHORIZED", "Authentication is required"));
        if (!user.canAuthenticate(now)) {
            throw ApiException.forbidden("ACCOUNT_INACTIVE", "Your account is not active");
        }
        if (!user.isEmailVerified()) {
            throw ApiException.forbidden("EMAIL_NOT_VERIFIED", "Please verify your email address");
        }
        boolean bypass = adminsBypassMembership && user.hasRole(RoleName.ADMIN);
        if (!bypass && !memberships.hasValidMembership(userId)) {
            throw ApiException.forbidden("MEMBERSHIP_INVALID", "Your membership is not active");
        }
        Book book = books.findDetailed(bookId).orElseThrow(() -> ApiException.notFound("Book not found"));
        if (!book.isAvailableOn(today)) {
            throw ApiException.notFound("Book not found");
        }
        return book;
    }

    public record AccessGrant(String ticket, Instant expiresAt, UUID versionId, int versionNumber,
                              Integer pageCount, String contentUrl) {
    }

    public record PdfContent(Resource resource, String title, boolean download) {
    }
}
