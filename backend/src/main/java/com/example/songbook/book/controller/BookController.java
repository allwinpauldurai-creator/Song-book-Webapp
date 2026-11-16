package com.example.songbook.book.controller;

import com.example.songbook.book.dto.BookDtos.BookDetail;
import com.example.songbook.book.dto.BookDtos.BookSummary;
import com.example.songbook.book.service.BookCatalogueService;
import com.example.songbook.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

/** Public catalogue. Exposes metadata and covers only - never PDF locations. */
@RestController
@RequestMapping("/api/v1/books")
@Tag(name = "Catalogue")
public class BookController {

    private final BookCatalogueService catalogue;

    public BookController(BookCatalogueService catalogue) {
        this.catalogue = catalogue;
    }

    @GetMapping
    @Operation(summary = "Search the public catalogue (sort: title, effectiveDate, updatedAt)")
    public PageResponse<BookSummary> list(@RequestParam(required = false) String q,
                                          @RequestParam(required = false) UUID categoryId,
                                          @ParameterObject Pageable pageable) {
        return catalogue.search(q, categoryId, pageable);
    }

    @GetMapping("/{bookId}")
    public BookDetail get(@PathVariable UUID bookId) {
        return catalogue.get(bookId);
    }

    @GetMapping("/{bookId}/cover")
    public ResponseEntity<Resource> cover(@PathVariable UUID bookId) {
        BookCatalogueService.CoverImage cover = catalogue.cover(bookId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(cover.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .eTag("\"" + cover.etag() + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(cover.resource());
    }
}
