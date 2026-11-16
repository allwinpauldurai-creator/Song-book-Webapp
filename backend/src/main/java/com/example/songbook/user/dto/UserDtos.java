package com.example.songbook.user.dto;

import com.example.songbook.membership.dto.MembershipDtos.MembershipResponse;
import com.example.songbook.membership.dto.MembershipDtos.MembershipSummary;
import com.example.songbook.role.RoleName;
import com.example.songbook.user.entity.AccountStatus;
import com.example.songbook.user.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class UserDtos {

    private UserDtos() {
    }

    public record MeResponse(UUID id, String fullName, String email, Set<RoleName> roles, boolean emailVerified,
                             MembershipSummary membership) {
        public static MeResponse from(User u, MembershipSummary membership) {
            return new MeResponse(u.getId(), u.getFullName(), u.getEmail(), u.roleNames(), u.isEmailVerified(),
                    membership);
        }
    }

    /** Only the name is editable by members; roles/status/email cannot be set here. */
    public record UpdateProfileRequest(@NotBlank @Size(max = 150) String fullName) {
    }

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(min = 10, max = 128) String newPassword) {
        @Override
        public String toString() {
            return "ChangePasswordRequest[***]";
        }
    }

    public record AdminUserSummary(UUID id, String fullName, String email, AccountStatus accountStatus,
                                   boolean emailVerified, Set<RoleName> roles, Instant createdAt) {
        public static AdminUserSummary from(User u) {
            return new AdminUserSummary(u.getId(), u.getFullName(), u.getEmail(), u.getAccountStatus(),
                    u.isEmailVerified(), u.roleNames(), u.getCreatedAt());
        }
    }

    public record AdminUserDetail(UUID id, String fullName, String email, AccountStatus accountStatus,
                                  boolean emailVerified, Instant emailVerifiedAt, Instant lockedUntil,
                                  Set<RoleName> roles, Instant createdAt, Instant updatedAt, Long version,
                                  List<MembershipResponse> memberships) {
        public static AdminUserDetail from(User u, List<MembershipResponse> memberships) {
            return new AdminUserDetail(u.getId(), u.getFullName(), u.getEmail(), u.getAccountStatus(),
                    u.isEmailVerified(), u.getEmailVerifiedAt(), u.getLockedUntil(), u.roleNames(),
                    u.getCreatedAt(), u.getUpdatedAt(), u.getVersion(), memberships);
        }
    }

    /**
     * @param emailVerified admin-created accounts may be marked verified (the admin vouches for the address)
     */
    public record AdminCreateUserRequest(
            @NotBlank @Size(max = 150) String fullName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 10, max = 128) String password,
            @NotEmpty Set<RoleName> roles,
            boolean emailVerified) {
        @Override
        public String toString() {
            return "AdminCreateUserRequest[email=" + email + "]";
        }
    }

    public record AdminUpdateUserRequest(
            @NotBlank @Size(max = 150) String fullName,
            @NotNull AccountStatus accountStatus,
            @NotEmpty Set<RoleName> roles,
            @NotNull Long version) {
    }
}
