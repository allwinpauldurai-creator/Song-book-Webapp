package com.example.songbook.user.dto;

import com.example.songbook.auth.dto.AuthDtos;
import com.example.songbook.membership.dto.MembershipDtos.MembershipResponse;
import com.example.songbook.membership.dto.MembershipDtos.MembershipSummary;
import com.example.songbook.role.RoleName;
import com.example.songbook.user.entity.AccountStatus;
import com.example.songbook.user.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class UserDtos {

    private UserDtos() {
    }

    public record MeResponse(UUID id, String fullName, String mobile, boolean mobileVerified, String email,
                             Set<RoleName> roles, MembershipSummary membership) {
        public static MeResponse from(User u, MembershipSummary membership) {
            return new MeResponse(u.getId(), u.getFullName(), u.getMobile(), u.isMobileVerified(), u.getEmail(),
                    u.roleNames(), membership);
        }
    }

    /**
     * Members may edit their name and optional contact email. The mobile number changes only through the SMS
     * confirmation flow; roles and status cannot be set here.
     *
     * @param email blank or null removes the address
     */
    public record UpdateProfileRequest(
            @NotBlank @Size(max = 150) String fullName,
            @Email @Size(max = 254) String email) {
    }

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(min = 10, max = 128) String newPassword) {
        @Override
        public String toString() {
            return "ChangePasswordRequest[***]";
        }
    }

    /** Step 1 of a mobile change: an SMS code is sent to {@code newMobile}. */
    public record ChangeMobileRequest(
            @NotBlank @Size(max = 25) String newMobile,
            @NotBlank @Size(max = 128) String currentPassword) {
        @Override
        public String toString() {
            return "ChangeMobileRequest[***]";
        }
    }

    /** Step 2 of a mobile change: the code received on the new number. */
    public record ConfirmMobileRequest(@NotBlank @Pattern(regexp = AuthDtos.CODE_PATTERN) String code) {
        @Override
        public String toString() {
            return "ConfirmMobileRequest[***]";
        }
    }

    public record AdminUserSummary(UUID id, String fullName, String mobile, boolean mobileVerified, String email,
                                   AccountStatus accountStatus, Set<RoleName> roles, Instant createdAt) {
        public static AdminUserSummary from(User u) {
            return new AdminUserSummary(u.getId(), u.getFullName(), u.getMobile(), u.isMobileVerified(),
                    u.getEmail(), u.getAccountStatus(), u.roleNames(), u.getCreatedAt());
        }
    }

    public record AdminUserDetail(UUID id, String fullName, String mobile, boolean mobileVerified,
                                  Instant mobileVerifiedAt, String email, AccountStatus accountStatus,
                                  Instant lockedUntil, Set<RoleName> roles, Instant createdAt, Instant updatedAt,
                                  Long version, List<MembershipResponse> memberships) {
        public static AdminUserDetail from(User u, List<MembershipResponse> memberships) {
            return new AdminUserDetail(u.getId(), u.getFullName(), u.getMobile(), u.isMobileVerified(),
                    u.getMobileVerifiedAt(), u.getEmail(), u.getAccountStatus(), u.getLockedUntil(), u.roleNames(),
                    u.getCreatedAt(), u.getUpdatedAt(), u.getVersion(), memberships);
        }
    }

    /**
     * @param mobileVerified admin-created accounts may be marked verified (the admin vouches for the number)
     * @param email          optional contact address
     */
    public record AdminCreateUserRequest(
            @NotBlank @Size(max = 150) String fullName,
            @NotBlank @Size(max = 25) String mobile,
            @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 10, max = 128) String password,
            @NotEmpty Set<RoleName> roles,
            boolean mobileVerified) {
        @Override
        public String toString() {
            return "AdminCreateUserRequest[***]";
        }
    }

    /** @param mobileVerified the admin vouches for {@code mobile}; false makes the user confirm it by SMS */
    public record AdminUpdateUserRequest(
            @NotBlank @Size(max = 150) String fullName,
            @NotBlank @Size(max = 25) String mobile,
            boolean mobileVerified,
            @Email @Size(max = 254) String email,
            @NotNull AccountStatus accountStatus,
            @NotEmpty Set<RoleName> roles,
            @NotNull Long version) {
    }
}
