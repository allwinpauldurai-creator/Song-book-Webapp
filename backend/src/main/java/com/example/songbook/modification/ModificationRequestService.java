package com.example.songbook.modification;

import com.example.songbook.audit.AuditActions;
import com.example.songbook.audit.AuditService;
import com.example.songbook.book.entity.Book;
import com.example.songbook.book.repository.BookRepository;
import com.example.songbook.common.exception.ApiException;
import com.example.songbook.common.response.PageResponse;
import com.example.songbook.common.util.PageableUtils;
import com.example.songbook.modification.entity.ModificationRequest;
import com.example.songbook.modification.repository.ModificationRequestRepository;
import com.example.songbook.user.repository.UserRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class ModificationRequestService {

    private static final Map<String, String> SORTS = Map.of("createdAt", "createdAt", "status", "status");

    private final ModificationRequestRepository repository;
    private final UserRepository users;
    private final BookRepository books;
    private final AuditService audit;
    private final Clock clock;

    public ModificationRequestService(ModificationRequestRepository repository, UserRepository users,
                                      BookRepository books, AuditService audit, Clock clock) {
        this.repository = repository;
        this.users = users;
        this.books = books;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public RequestResponse submit(UUID userId, CreateRequest req) {
        Book book = null;
        if (req.bookId() != null) {
            book = books.findById(req.bookId())
                    .orElseThrow(() -> ApiException.badRequest("INVALID_BOOK", "Book not found"));
        }
        ModificationRequest request = repository.save(new ModificationRequest(users.getReferenceById(userId), book,
                req.requestType(), req.description()));
        audit.record(userId, AuditActions.MODIFICATION_REQUESTED, "MODIFICATION_REQUEST", request.getId(),
                Map.of("type", req.requestType()));
        return RequestResponse.from(repository.findById(request.getId()).orElseThrow());
    }

    @Transactional(readOnly = true)
    public PageResponse<RequestResponse> mine(UUID userId, Pageable pageable) {
        Pageable safe = PageableUtils.sanitize(pageable, SORTS, Sort.by("createdAt").descending());
        return PageResponse.from(repository.findByRequester(userId, safe), RequestResponse::from);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public PageResponse<RequestResponse> search(ModificationRequest.Status status, Pageable pageable) {
        Pageable safe = PageableUtils.sanitize(pageable, SORTS, Sort.by("createdAt").descending());
        return PageResponse.from(repository.search(status, safe), RequestResponse::from);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public RequestResponse review(UUID id, ReviewRequest req, UUID adminId) {
        ModificationRequest request = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Request not found"));
        ApiException.checkVersion(req.version(), request.getVersion());
        if (request.getStatus() != ModificationRequest.Status.PENDING) {
            throw ApiException.conflict("ALREADY_REVIEWED", "This request has already been reviewed");
        }
        if (req.status() == ModificationRequest.Status.PENDING) {
            throw ApiException.badRequest("INVALID_STATUS", "Decision must be APPROVED or REJECTED");
        }
        request.review(req.status(), blankToNull(req.reviewNote()), adminId, Instant.now(clock));
        repository.flush();
        audit.record(adminId, AuditActions.MODIFICATION_REVIEWED, "MODIFICATION_REQUEST", id,
                Map.of("decision", req.status()));
        return RequestResponse.from(request);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public record CreateRequest(UUID bookId, @NotNull ModificationRequest.Type requestType,
                                @NotBlank @Size(max = 5000) String description) {
    }

    public record ReviewRequest(@NotNull ModificationRequest.Status status, @Size(max = 1000) String reviewNote,
                                @NotNull Long version) {
    }

    public record RequestResponse(UUID id, UUID requestedBy, String requesterName, UUID bookId, String bookTitle,
                                  ModificationRequest.Type requestType, String description,
                                  ModificationRequest.Status status, String reviewNote, UUID reviewedBy,
                                  Instant reviewedAt, Instant createdAt, Long version) {
        static RequestResponse from(ModificationRequest r) {
            Book b = r.getBook();
            return new RequestResponse(r.getId(), r.getRequestedBy().getId(), r.getRequestedBy().getFullName(),
                    b == null ? null : b.getId(), b == null ? null : b.getTitle(), r.getRequestType(),
                    r.getDescription(), r.getStatus(), r.getReviewNote(), r.getReviewedBy(), r.getReviewedAt(),
                    r.getCreatedAt(), r.getVersion());
        }
    }
}
