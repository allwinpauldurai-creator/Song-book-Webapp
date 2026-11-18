package com.example.songbook.book.service;

import com.example.songbook.book.dto.BookDtos.BookDetail;
import com.example.songbook.book.dto.BookDtos.BookSummary;
import com.example.songbook.book.entity.Book;
import com.example.songbook.book.repository.BookRepository;
import com.example.songbook.book.repository.BookSpecifications;
import com.example.songbook.common.exception.ApiException;
import com.example.songbook.common.response.PageResponse;
import com.example.songbook.common.util.PageableUtils;
import com.example.songbook.file.entity.FileAsset;
import com.example.songbook.file.storage.FileStorageService;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/** Public catalogue: only books available today are visible; unavailable books are reported as 404. */
@Service
public class BookCatalogueService {

    private static final Map<String, String> SORTS = Map.of(
            "title", "title", "effectiveDate", "effectiveDate", "updatedAt", "updatedAt");

    private final BookRepository books;
    private final FileStorageService storage;
    private final Clock clock;

    public BookCatalogueService(BookRepository books, FileStorageService storage, Clock clock) {
        this.books = books;
        this.storage = storage;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<BookSummary> search(String q, UUID categoryId, Pageable pageable) {
        Specification<Book> spec = Specification.where(BookSpecifications.availableOn(LocalDate.now(clock)))
                .and(BookSpecifications.matches(q))
                .and(BookSpecifications.inCategory(categoryId));
        Pageable safe = PageableUtils.sanitize(pageable, SORTS, Sort.by("title").ascending());
        return PageResponse.from(books.findAll(spec, safe), BookSummary::from);
    }

    @Transactional(readOnly = true)
    public BookDetail get(UUID id) {
        return BookDetail.from(requireAvailable(id));
    }

    @Transactional(readOnly = true)
    public CoverImage cover(UUID id) {
        Book book = requireAvailable(id);
        FileAsset cover = book.getCoverAsset();
        if (cover == null) {
            throw ApiException.notFound("Book has no cover");
        }
        return new CoverImage(storage.load(cover), cover.getContentType(),
                cover.getChecksumSha256());
    }

    @Transactional(readOnly = true)
    public Book requireAvailable(UUID id) {
        return books.findDetailed(id)
                .filter(b -> b.isAvailableOn(LocalDate.now(clock)))
                .orElseThrow(() -> ApiException.notFound("Book not found"));
    }

    public record CoverImage(Resource resource, String contentType, String etag) {
    }
}
