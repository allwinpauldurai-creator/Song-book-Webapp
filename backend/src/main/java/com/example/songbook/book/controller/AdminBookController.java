package com.example.songbook.book.controller;

import com.example.songbook.book.domain.BookStatus;
import com.example.songbook.book.dto.BookDtos.AdminBookDetail;
import com.example.songbook.book.dto.BookDtos.AdminBookSummary;
import com.example.songbook.book.dto.BookDtos.BookVersionResponse;
import com.example.songbook.book.dto.BookDtos.CreateBookRequest;
import com.example.songbook.book.dto.BookDtos.UpdateBookRequest;
import com.example.songbook.book.service.AdminBookService;
import com.example.songbook.common.response.PageResponse;
import com.example.songbook.common.util.ContentDispositionUtils;
import com.example.songbook.security.authentication.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/books")
@PreAuthorize("hasAnyRole('ADMIN', 'PUBLISHER')")
@Tag(name = "Admin - books", description = "Admins manage every book; publishers only the books they uploaded")
@SecurityRequirement(name = "bearerAuth")
public class AdminBookController {

    private final AdminBookService service;

    public AdminBookController(AdminBookService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<AdminBookSummary> list(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) BookStatus status,
                                               @RequestParam(required = false) UUID categoryId,
                                               @ParameterObject Pageable pageable) {
        return service.search(q, status, categoryId, pageable, CurrentUser.id());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a book: 'metadata' JSON part, required 'pdf' part, optional 'cover' part")
    public AdminBookDetail create(@Valid @RequestPart("metadata") CreateBookRequest metadata,
                                  @RequestPart("pdf") MultipartFile pdf,
                                  @RequestPart(value = "cover", required = false) MultipartFile cover) {
        return service.create(metadata, pdf, cover, CurrentUser.id());
    }

    @GetMapping("/{bookId}")
    public AdminBookDetail get(@PathVariable UUID bookId) {
        return service.get(bookId, CurrentUser.id());
    }

    @PutMapping("/{bookId}")
    public AdminBookDetail update(@PathVariable UUID bookId, @Valid @RequestBody UpdateBookRequest request) {
        return service.update(bookId, request, CurrentUser.id());
    }

    @PutMapping(value = "/{bookId}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AdminBookDetail replaceCover(@PathVariable UUID bookId, @RequestPart("cover") MultipartFile cover) {
        return service.replaceCover(bookId, cover, CurrentUser.id());
    }

    @GetMapping("/{bookId}/versions")
    public List<BookVersionResponse> versions(@PathVariable UUID bookId) {
        return service.versions(bookId, CurrentUser.id());
    }

    @PostMapping(value = "/{bookId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload a replacement PDF; the previous version is retained")
    public BookVersionResponse addVersion(@PathVariable UUID bookId,
                                          @RequestPart("pdf") MultipartFile pdf,
                                          @RequestParam(required = false) String changeNote) {
        return service.addVersion(bookId, pdf, changeNote, CurrentUser.id());
    }

    @GetMapping("/{bookId}/versions/{versionId}/content")
    @Operation(summary = "Download any version (including historical) for review")
    public ResponseEntity<Resource> versionContent(@PathVariable UUID bookId, @PathVariable UUID versionId) {
        AdminBookService.VersionFile file = service.versionFile(bookId, versionId, CurrentUser.id());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .headers(h -> h.setContentDisposition(ContentDispositionUtils.build(true,
                        ContentDispositionUtils.safeBaseName(file.baseName()) + ".pdf")))
                .body(file.resource());
    }

    @PostMapping("/{bookId}/archive")
    public ResponseEntity<Void> archive(@PathVariable UUID bookId) {
        service.archive(bookId, CurrentUser.id());
        return ResponseEntity.noContent().build();
    }
}
