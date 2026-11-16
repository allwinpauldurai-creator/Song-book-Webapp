package com.example.songbook.user.controller;

import com.example.songbook.security.authentication.CurrentUser;
import com.example.songbook.user.dto.UserDtos.ChangePasswordRequest;
import com.example.songbook.user.dto.UserDtos.MeResponse;
import com.example.songbook.user.dto.UserDtos.UpdateProfileRequest;
import com.example.songbook.user.service.UserService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The authenticated user's own profile. The user id always comes from the token. */
@RestController
@RequestMapping("/api/v1/users/me")
@Tag(name = "Current user")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public MeResponse me() {
        return userService.me(CurrentUser.id());
    }

    @PutMapping
    public MeResponse update(@Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(CurrentUser.id(), request);
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(CurrentUser.id(), request);
        return ResponseEntity.noContent().build();
    }
}
