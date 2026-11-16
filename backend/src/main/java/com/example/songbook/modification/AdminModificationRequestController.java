package com.example.songbook.modification;

import com.example.songbook.common.response.PageResponse;
import com.example.songbook.modification.ModificationRequestService.RequestResponse;
import com.example.songbook.modification.ModificationRequestService.ReviewRequest;
import com.example.songbook.modification.entity.ModificationRequest;
import com.example.songbook.security.authentication.CurrentUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/modification-requests")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - modification requests")
@SecurityRequirement(name = "bearerAuth")
public class AdminModificationRequestController {

    private final ModificationRequestService service;

    public AdminModificationRequestController(ModificationRequestService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<RequestResponse> list(@RequestParam(required = false) ModificationRequest.Status status,
                                              @ParameterObject Pageable pageable) {
        return service.search(status, pageable);
    }

    @PutMapping("/{requestId}")
    public RequestResponse review(@PathVariable UUID requestId, @Valid @RequestBody ReviewRequest request) {
        return service.review(requestId, request, CurrentUser.id());
    }
}
