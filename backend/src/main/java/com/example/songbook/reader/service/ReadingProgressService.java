package com.example.songbook.reader.service;

import com.example.songbook.book.entity.Book;
import com.example.songbook.common.exception.ApiException;
import com.example.songbook.common.response.PageResponse;
import com.example.songbook.common.util.PageableUtils;
import com.example.songbook.reader.entity.ReadingProgress;
import com.example.songbook.reader.repository.ReadingProgressRepository;
import com.example.songbook.user.entity.User;
import com.example.songbook.user.repository.UserRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Last-read page per member and book. Always scoped to the authenticated user (no IDOR). */
@Service
public class ReadingProgressService {

    private static final Map<String, String> SORTS = Map.of("updatedAt", "updatedAt");

    private final ReadingProgressRepository repository;
    private final UserRepository users;
    private final PdfAccessService access;
    private final Clock clock;

    public ReadingProgressService(ReadingProgressRepository repository, UserRepository users,
                                  PdfAccessService access, Clock clock) {
        this.repository = repository;
        this.users = users;
        this.access = access;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProgressResponse get(UUID userId, UUID bookId) {
        return repository.findByUserAndBook(userId, bookId)
                .map(ProgressResponse::from)
                .orElseThrow(() -> ApiException.notFound("No reading progress for this book"));
    }

    /** Only books the member may currently read can have progress recorded. */
    @Transactional
    public ProgressResponse save(UUID userId, UUID bookId, int lastPage) {
        Book book = access.requireEligible(userId, bookId);
        Integer pages = book.getCurrentVersion().getPdfAsset().getPageCount();
        if (pages != null && lastPage > pages) {
            throw ApiException.badRequest("INVALID_PAGE", "Page must be between 1 and " + pages);
        }
        ReadingProgress progress = repository.findByUserAndBook(userId, bookId).orElseGet(() -> {
            User user = users.getReferenceById(userId);
            return new ReadingProgress(user, book);
        });
        progress.update(lastPage, book.getCurrentVersion(), Instant.now(clock));
        repository.save(progress);
        return ProgressResponse.from(progress);
    }

    @Transactional(readOnly = true)
    public PageResponse<HistoryItem> history(UUID userId, Pageable pageable) {
        Pageable safe = PageableUtils.sanitize(pageable, SORTS, Sort.by("updatedAt").descending());
        return PageResponse.from(repository.findHistory(userId, safe), HistoryItem::from);
    }

    public record SaveProgressRequest(@NotNull @Min(1) @Max(100000) Integer lastPage) {
    }

    public record ProgressResponse(UUID bookId, int lastPage, UUID versionId, Instant updatedAt) {
        static ProgressResponse from(ReadingProgress p) {
            return new ProgressResponse(p.getBook().getId(), p.getLastPage(),
                    p.getBookVersion() == null ? null : p.getBookVersion().getId(), p.getUpdatedAt());
        }
    }

    public record HistoryItem(UUID bookId, String title, String coverUrl, int lastPage, Instant updatedAt) {
        static HistoryItem from(ReadingProgress p) {
            Book b = p.getBook();
            return new HistoryItem(b.getId(), b.getTitle(),
                    b.getCoverAsset() == null ? null : "/api/v1/books/" + b.getId() + "/cover",
                    p.getLastPage(), p.getUpdatedAt());
        }
    }
}
