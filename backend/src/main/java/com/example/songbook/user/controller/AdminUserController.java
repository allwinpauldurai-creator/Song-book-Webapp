package com.example.songbook.user.controller;

import com.example.songbook.common.response.PageResponse;
import com.example.songbook.role.RoleName;
import com.example.songbook.security.authentication.CurrentUser;
import com.example.songbook.user.dto.UserDtos.AdminCreateUserRequest;
import com.example.songbook.user.dto.UserDtos.AdminUpdateUserRequest;
import com.example.songbook.user.dto.UserDtos.AdminUserDetail;
import com.example.songbook.user.dto.UserDtos.AdminUserSummary;
import com.example.songbook.user.entity.AccountStatus;
import com.example.songbook.user.service.AdminUserService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - users")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {

    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<AdminUserSummary> list(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) AccountStatus status,
                                               @RequestParam(required = false) RoleName role,
                                               @ParameterObject Pageable pageable) {
        return service.search(q, status, role, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserDetail create(@Valid @RequestBody AdminCreateUserRequest request) {
        return service.create(request, CurrentUser.id());
    }

    @GetMapping("/{userId}")
    public AdminUserDetail get(@PathVariable UUID userId) {
        return service.get(userId);
    }

    @PutMapping("/{userId}")
    public AdminUserDetail update(@PathVariable UUID userId, @Valid @RequestBody AdminUpdateUserRequest request) {
        return service.update(userId, request, CurrentUser.id());
    }
}
