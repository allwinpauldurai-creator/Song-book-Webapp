package com.example.songbook.modification;

import com.example.songbook.common.response.PageResponse;
import com.example.songbook.modification.ModificationRequestService.CreateRequest;
import com.example.songbook.modification.ModificationRequestService.RequestResponse;
import com.example.songbook.security.authentication.CurrentUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Member")
@SecurityRequirement(name = "bearerAuth")
public class ModificationRequestController {

    private final ModificationRequestService service;

    public ModificationRequestController(ModificationRequestService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/modification-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public RequestResponse submit(@Valid @RequestBody CreateRequest request) {
        return service.submit(CurrentUser.id(), request);
    }

    @GetMapping("/api/v1/members/modification-requests")
    public PageResponse<RequestResponse> mine(@ParameterObject Pageable pageable) {
        return service.mine(CurrentUser.id(), pageable);
    }
}
