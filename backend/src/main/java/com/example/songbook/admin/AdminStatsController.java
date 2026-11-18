package com.example.songbook.admin;

import com.example.songbook.admin.AdminStatsService.AdminStats;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/stats")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - dashboard")
@SecurityRequirement(name = "bearerAuth")
public class AdminStatsController {

    private final AdminStatsService service;

    public AdminStatsController(AdminStatsService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Counts for the admin dashboard: users, valid memberships, books by status, pending requests")
    public AdminStats stats() {
        return service.stats();
    }
}
