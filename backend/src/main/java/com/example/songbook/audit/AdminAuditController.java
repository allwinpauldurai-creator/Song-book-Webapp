package com.example.songbook.audit;

import com.example.songbook.audit.AuditService.AuditLogResponse;
import com.example.songbook.common.response.PageResponse;
import com.example.songbook.common.util.PageableUtils;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - audit")
@SecurityRequirement(name = "bearerAuth")
public class AdminAuditController {

    private final AuditService service;

    public AdminAuditController(AuditService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<AuditLogResponse> search(
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @ParameterObject Pageable pageable) {
        Pageable safe = PageableUtils.sanitize(pageable, Map.of("createdAt", "createdAt", "action", "action"),
                Sort.by("createdAt").descending());
        return service.search(actorId, action, entityType, entityId, from, to, safe);
    }
}
