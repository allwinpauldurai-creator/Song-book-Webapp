package com.example.songbook.reader.controller;

import com.example.songbook.common.response.PageResponse;
import com.example.songbook.reader.service.ReadingProgressService;
import com.example.songbook.reader.service.ReadingProgressService.HistoryItem;
import com.example.songbook.reader.service.ReadingProgressService.ProgressResponse;
import com.example.songbook.reader.service.ReadingProgressService.SaveProgressRequest;
import com.example.songbook.security.authentication.CurrentUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/members")
@Tag(name = "Member")
@SecurityRequirement(name = "bearerAuth")
public class ReadingProgressController {

    private final ReadingProgressService service;

    public ReadingProgressController(ReadingProgressService service) {
        this.service = service;
    }

    @GetMapping("/books/{bookId}/progress")
    public ProgressResponse get(@PathVariable UUID bookId) {
        return service.get(CurrentUser.id(), bookId);
    }

    @PutMapping("/books/{bookId}/progress")
    public ProgressResponse save(@PathVariable UUID bookId, @Valid @RequestBody SaveProgressRequest request) {
        return service.save(CurrentUser.id(), bookId, request.lastPage());
    }

    @GetMapping("/progress")
    public PageResponse<HistoryItem> history(@ParameterObject Pageable pageable) {
        return service.history(CurrentUser.id(), pageable);
    }
}
