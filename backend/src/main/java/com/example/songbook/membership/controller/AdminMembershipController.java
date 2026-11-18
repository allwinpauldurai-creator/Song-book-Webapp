package com.example.songbook.membership.controller;

import com.example.songbook.common.response.PageResponse;
import com.example.songbook.membership.domain.MembershipStatus;
import com.example.songbook.membership.dto.MembershipDtos.CreateMembershipRequest;
import com.example.songbook.membership.dto.MembershipDtos.MembershipResponse;
import com.example.songbook.membership.dto.MembershipDtos.UpdateMembershipRequest;
import com.example.songbook.membership.service.MembershipService;
import com.example.songbook.security.authentication.CurrentUser;
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
@RequestMapping("/api/v1/admin/memberships")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - memberships")
@SecurityRequirement(name = "bearerAuth")
public class AdminMembershipController {

    private final MembershipService service;

    public AdminMembershipController(MembershipService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<MembershipResponse> list(@RequestParam(required = false) UUID userId,
                                                 @RequestParam(required = false) MembershipStatus status,
                                                 @ParameterObject Pageable pageable) {
        return service.search(userId, status, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MembershipResponse create(@Valid @RequestBody CreateMembershipRequest request) {
        return service.create(request, CurrentUser.id());
    }

    @PutMapping("/{membershipId}")
    public MembershipResponse update(@PathVariable UUID membershipId,
                                     @Valid @RequestBody UpdateMembershipRequest request) {
        return service.update(membershipId, request, CurrentUser.id());
    }
}
