package com.example.songbook.book.service;

import com.example.songbook.audit.AuditActions;
import com.example.songbook.audit.AuditService;
import com.example.songbook.book.domain.BookStatus;
import com.example.songbook.book.dto.BookDtos.AdminBookDetail;
import com.example.songbook.book.dto.BookDtos.AdminBookSummary;
import com.example.songbook.book.dto.BookDtos.BookVersionResponse;
import com.example.songbook.book.dto.BookDtos.CreateBookRequest;
import com.example.songbook.book.dto.BookDtos.UpdateBookRequest;
import com.example.songbook.book.entity.Book;
import com.example.songbook.book.entity.BookVersion;
import com.example.songbook.book.repository.BookRepository;
import com.example.songbook.book.repository.BookSpecifications;
import com.example.songbook.book.repository.BookVersionRepository;
import com.example.songbook.category.BookCategory;
import com.example.songbook.category.CategoryService;
import com.example.songbook.common.exception.ApiException;
import com.example.songbook.common.response.PageResponse;
import com.example.songbook.common.util.PageableUtils;
import com.example.songbook.config.AppProperties;
import com.example.songbook.file.entity.FileAsset;
import com.example.songbook.file.repository.FileAssetRepository;
import com.example.songbook.file.service.FileAssetService;
import com.example.songbook.file.storage.FileStorageService;
import com.example.songbook.file.validation.FileValidator;
import com.example.songbook.file.validation.ValidatedFile;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin book management. File uploads follow the "upload, then commit metadata, compensate on failure"
 * pattern (see {@link FileAssetService}). Old PDF versions are never deleted.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminBookService {

    private static final Map<String, String> SORTS = Map.of(
            "title", "title", "status", "status", "effectiveDate", "effectiveDate",
            "createdAt", "createdAt", "updatedAt", "updatedAt");

    private final BookRepository books;
    private final BookVersionRepository versions;
    private final FileAssetRepository assets;
    private final CategoryService categories;
    private final FileValidator validator;
    private final FileAssetService fileAssets;
    private final FileStorageService storage;
    private final AuditService audit;
    private final AppProperties.Storage storageConfig;
    private final TransactionTemplate tx;
    private final Clock clock;

    public AdminBookService(BookRepository books, BookVersionRepository versions, FileAssetRepository assets,
                            CategoryService categories, FileValidator validator, FileAssetService fileAssets,
                            FileStorageService storage, AuditService audit, AppProperties properties,
                            PlatformTransactionManager txManager, Clock clock) {
        this.books = books;
        this.versions = versions;
        this.assets = assets;
        this.categories = categories;
        this.validator = validator;
        this.fileAssets = fileAssets;
        this.storage = storage;
        this.audit = audit;
        this.storageConfig = properties.storage();
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public PageResponse<AdminBookSummary> search(String q, BookStatus status, UUID categoryId, Pageable pageable) {
        Specification<Book> spec = Specification.where(BookSpecifications.matches(q))
                .and(BookSpecifications.hasStatus(status))
                .and(BookSpecifications.inCategory(categoryId));
        Pageable safe = PageableUtils.sanitize(pageable, SORTS, Sort.by("updatedAt").descending());
        LocalDate today = LocalDate.now(clock);
        return PageResponse.from(books.findAll(spec, safe), b -> AdminBookSummary.from(b, today));
    }

    @Transactional(readOnly = true)
    public AdminBookDetail get(UUID id) {
        return AdminBookDetail.from(requireDetailed(id), LocalDate.now(clock));
    }

    @Transactional(readOnly = true)
    public List<BookVersionResponse> versions(UUID bookId) {
        Book book = requireDetailed(bookId);
        UUID currentId = book.getCurrentVersion() == null ? null : book.getCurrentVersion().getId();
        return versions.findHistory(bookId).stream()
                .map(v -> BookVersionResponse.from(v, v.getId().equals(currentId)))
                .toList();
    }

    /** Any version (including old ones) for admin review/download. */
    @Transactional(readOnly = true)
    public VersionFile versionFile(UUID bookId, UUID versionId) {
        BookVersion v = versions.findForBook(versionId, bookId)
                .orElseThrow(() -> ApiException.notFound("Version not found"));
        FileAsset pdf = v.getPdfAsset();
        return new VersionFile(storage.load(pdf.getContainerName(), pdf.getBlobName()),
                v.getBook().getTitle() + "-v" + v.getVersionNumber());
    }

    public record VersionFile(Resource resource, String baseName) {
    }

    // ------------------------------------------------------------------ create

    public AdminBookDetail create(CreateBookRequest req, MultipartFile pdf, MultipartFile cover, UUID actorId) {
        validateDates(req.effectiveDate(), req.expiryDate());
        rejectArchived(req.status());
        BookCategory category = categories.requireAssignable(req.categoryId(), null);
        if (pdf == null || pdf.isEmpty()) {
            throw ApiException.badRequest("INVALID_FILE", "A PDF file is required");
        }

        UUID bookId = UUID.randomUUID();
        FileAsset pdfAsset = null;
        FileAsset coverAsset = null;
        try (ValidatedFile pdfFile = validator.validatePdf(pdf);
             ValidatedFile coverFile = cover == null || cover.isEmpty() ? null : validator.validateImage(cover)) {

            pdfAsset = fileAssets.upload(pdfFile, storageConfig.pdfsContainer(), pdfKey(bookId, 1), actorId);
            if (coverFile != null) {
                coverAsset = fileAssets.upload(coverFile, storageConfig.coversContainer(),
                        coverKey(bookId, coverFile.extension()), actorId);
            }

            FileAsset finalPdf = pdfAsset;
            FileAsset finalCover = coverAsset;
            tx.executeWithoutResult(s -> {
                Instant now = Instant.now(clock);
                assets.save(finalPdf);
                Book book = new Book(bookId, req.title(), blankToNull(req.description()), category,
                        req.effectiveDate(), req.expiryDate(), actorId);
                if (finalCover != null) {
                    book.setCoverAsset(assets.save(finalCover));
                }
                // Insert as DRAFT first: a PUBLISHED row must reference its version (CHECK constraint).
                books.saveAndFlush(book);
                BookVersion v1 = versions.save(new BookVersion(book, 1, finalPdf, "Initial upload", actorId, now));
                book.setCurrentVersion(v1);
                book.setStatus(req.status());
                books.flush();
                audit.record(actorId, AuditActions.BOOK_CREATED, "BOOK", bookId, Map.of(
                        "title", book.getTitle(), "status", book.getStatus(), "pdfSha256", finalPdf.getChecksumSha256()));
            });
        } catch (RuntimeException e) {
            fileAssets.discard(pdfAsset, "book-create-failed");
            fileAssets.discard(coverAsset, "book-create-failed");
            throw e;
        }
        return get(bookId);
    }

    // ------------------------------------------------------------------ update metadata

    @Transactional
    public AdminBookDetail update(UUID id, UpdateBookRequest req, UUID actorId) {
        validateDates(req.effectiveDate(), req.expiryDate());
        rejectArchived(req.status());
        Book book = requireDetailed(id);
        ApiException.checkVersion(req.version(), book.getVersion());
        if (book.getStatus() == BookStatus.ARCHIVED) {
            throw ApiException.conflict("BOOK_ARCHIVED", "Archived books cannot be edited");
        }
        UUID currentCategory = book.getCategory() == null ? null : book.getCategory().getId();
        Map<String, Object> changes = new HashMap<>();
        changes.put("statusBefore", book.getStatus());
        book.setTitle(req.title());
        book.setDescription(blankToNull(req.description()));
        book.setCategory(categories.requireAssignable(req.categoryId(), currentCategory));
        book.setEffectiveDate(req.effectiveDate());
        book.setExpiryDate(req.expiryDate());
        book.setStatus(req.status());
        books.flush();
        changes.put("statusAfter", book.getStatus());
        changes.put("effectiveDate", book.getEffectiveDate());
        changes.put("expiryDate", book.getExpiryDate() == null ? "none" : book.getExpiryDate());
        audit.record(actorId, AuditActions.BOOK_UPDATED, "BOOK", id, changes);
        return AdminBookDetail.from(book, LocalDate.now(clock));
    }

    @Transactional
    public void archive(UUID id, UUID actorId) {
        Book book = books.findById(id).orElseThrow(() -> ApiException.notFound("Book not found"));
        if (book.getStatus() == BookStatus.ARCHIVED) {
            return; // idempotent
        }
        BookStatus before = book.getStatus();
        book.setStatus(BookStatus.ARCHIVED);
        audit.record(actorId, AuditActions.BOOK_ARCHIVED, "BOOK", id, Map.of("statusBefore", before));
    }

    // ------------------------------------------------------------------ files

    /** Uploads a new PDF version and makes it current. Previous versions and their files are retained. */
    public BookVersionResponse addVersion(UUID bookId, MultipartFile pdf, String changeNote, UUID actorId) {
        if (changeNote != null && changeNote.length() > 500) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Change note must be at most 500 characters");
        }
        Book existing = books.findById(bookId).orElseThrow(() -> ApiException.notFound("Book not found"));
        if (existing.getStatus() == BookStatus.ARCHIVED) {
            throw ApiException.conflict("BOOK_ARCHIVED", "Archived books cannot be changed");
        }
        FileAsset pdfAsset = null;
        try (ValidatedFile file = validator.validatePdf(pdf)) {
            // Unique key per upload: the version number is only final inside the transaction.
            pdfAsset = fileAssets.upload(file, storageConfig.pdfsContainer(),
                    bookId + "/upload-" + UUID.randomUUID() + ".pdf", actorId);
            FileAsset finalPdf = pdfAsset;
            return tx.execute(s -> {
                Book book = books.lockById(bookId).orElseThrow(() -> ApiException.notFound("Book not found"));
                int next = versions.maxVersionNumber(bookId) + 1;
                assets.save(finalPdf);
                BookVersion version = versions.save(new BookVersion(book, next, finalPdf, blankToNull(changeNote),
                        actorId, Instant.now(clock)));
                book.setCurrentVersion(version);
                books.flush();
                audit.record(actorId, AuditActions.BOOK_VERSION_UPLOADED, "BOOK", bookId, Map.of(
                        "versionNumber", next, "pdfSha256", finalPdf.getChecksumSha256()));
                return BookVersionResponse.from(version, true);
            });
        } catch (RuntimeException e) {
            fileAssets.discard(pdfAsset, "book-version-failed");
            throw e;
        }
    }

    /** Replaces the cover. The previous cover file is kept (referenced by audit history). */
    public AdminBookDetail replaceCover(UUID bookId, MultipartFile cover, UUID actorId) {
        books.findById(bookId).orElseThrow(() -> ApiException.notFound("Book not found"));
        FileAsset coverAsset = null;
        try (ValidatedFile file = validator.validateImage(cover)) {
            coverAsset = fileAssets.upload(file, storageConfig.coversContainer(), coverKey(bookId, file.extension()),
                    actorId);
            FileAsset finalCover = coverAsset;
            tx.executeWithoutResult(s -> {
                Book book = books.lockById(bookId).orElseThrow(() -> ApiException.notFound("Book not found"));
                book.setCoverAsset(assets.save(finalCover));
                audit.record(actorId, AuditActions.BOOK_COVER_REPLACED, "BOOK", bookId,
                        Map.of("coverSha256", finalCover.getChecksumSha256()));
            });
        } catch (RuntimeException e) {
            fileAssets.discard(coverAsset, "book-cover-failed");
            throw e;
        }
        return get(bookId);
    }

    // ------------------------------------------------------------------ helpers

    private Book requireDetailed(UUID id) {
        return books.findDetailed(id).orElseThrow(() -> ApiException.notFound("Book not found"));
    }

    private static String pdfKey(UUID bookId, int version) {
        return bookId + "/version-" + version + "/" + UUID.randomUUID() + ".pdf";
    }

    private static String coverKey(UUID bookId, String extension) {
        return bookId + "/" + UUID.randomUUID() + "." + extension;
    }

    private static void validateDates(LocalDate effective, LocalDate expiry) {
        if (expiry != null && expiry.isBefore(effective)) {
            throw ApiException.badRequest("INVALID_DATE_RANGE", "Expiry date must be on or after the effective date");
        }
    }

    private static void rejectArchived(BookStatus status) {
        if (status == BookStatus.ARCHIVED) {
            throw ApiException.badRequest("INVALID_STATUS", "Use the archive endpoint to archive a book");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
