package com.example.songbook.reader.controller;

import com.example.songbook.common.util.ContentDispositionUtils;
import com.example.songbook.reader.service.AccessTicketService.Purpose;
import com.example.songbook.reader.service.PdfAccessService;
import com.example.songbook.reader.service.PdfAccessService.AccessGrant;
import com.example.songbook.security.authentication.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Member PDF access. Step 1: an authenticated, eligible member requests a 60-second ticket.
 * Step 2: PDF.js (or a download link) fetches {@code /content?ticket=...}; HTTP Range is supported.
 */
@RestController
@RequestMapping("/api/v1/books/{bookId}")
@Tag(name = "Reader")
public class ReaderController {

    private final PdfAccessService access;

    public ReaderController(PdfAccessService access) {
        this.access = access;
    }

    @GetMapping("/read-access")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get a short-lived ticket to read the PDF online")
    public AccessGrant readAccess(@PathVariable UUID bookId) {
        return access.grant(CurrentUser.id(), bookId, Purpose.READ);
    }

    @GetMapping("/download-access")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get a short-lived ticket to download the PDF")
    public AccessGrant downloadAccess(@PathVariable UUID bookId) {
        return access.grant(CurrentUser.id(), bookId, Purpose.DOWNLOAD);
    }

    @GetMapping("/content")
    @Operation(summary = "Stream PDF bytes (ticket-authorized, supports Range requests)")
    public ResponseEntity<Resource> content(@PathVariable UUID bookId, @RequestParam String ticket) {
        PdfAccessService.PdfContent pdf = access.open(bookId, ticket);
        String fileName = ContentDispositionUtils.safeBaseName(pdf.title()) + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header("X-Content-Type-Options", "nosniff")
                .headers(h -> h.setContentDisposition(ContentDispositionUtils.build(pdf.download(), fileName)))
                .body(pdf.resource());
    }
}
