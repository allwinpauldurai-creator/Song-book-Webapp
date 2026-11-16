package com.example.songbook;

import com.example.songbook.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Requirements 14.1 / 14.2: registration, verification, login, refresh, reset, account state. */
class AuthFlowTests extends IntegrationTest {

    @Test
    void registrationCreatesUnverifiedMemberWithHashedPasswordOnly() throws Exception {
        String email = uniqueEmail("reg");
        register(email.toUpperCase(), PASSWORD); // email is normalized

        Map<String, Object> row = jdbc.queryForMap(
                "select email, password_hash, email_verified from users where email = ?", email);
        assertThat(row.get("email_verified")).isEqualTo(false);
        assertThat((String) row.get("password_hash")).startsWith("{bcrypt}").doesNotContain(PASSWORD);
        assertThat(jdbc.queryForList("""
                select r.name from user_roles ur join roles r on r.id = ur.role_id
                join users u on u.id = ur.user_id where u.email = ?""", String.class, email))
                .containsExactly("MEMBER");
        // token stored only as hash
        String token = mail.lastToken(email, "Verify");
        assertThat(jdbc.queryForObject("select count(*) from user_tokens where token_hash = ?", Integer.class, token))
                .isZero();
    }

    @Test
    void duplicateRegistrationReturnsSameResponseAndCreatesNoSecondUser() throws Exception {
        String email = uniqueEmail("dup");
        register(email, PASSWORD);
        register(email, "Other-Password-999");
        assertThat(jdbc.queryForObject("select count(*) from users where email = ?", Integer.class, email)).isEqualTo(1);
    }

    @Test
    void registrationCannotRequestAdminRole() throws Exception {
        String email = uniqueEmail("escalate");
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("fullName", "X", "email", email, "password", PASSWORD,
                                "roles", new String[]{"ADMIN"}, "accountStatus", "ACTIVE"))))
                .andExpect(status().isAccepted());
        assertThat(jdbc.queryForList("""
                select r.name from user_roles ur join roles r on r.id = ur.role_id
                join users u on u.id = ur.user_id where u.email = ?""", String.class, email))
                .containsExactly("MEMBER");
    }

    @Test
    void invalidRegistrationInputIsRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("fullName", "", "email", "not-an-email", "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(3));
    }

    @Test
    void unverifiedUserCannotLogIn() throws Exception {
        String email = uniqueEmail("unverified");
        register(email, PASSWORD);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void verificationCreatesMembershipAndTokenCannotBeReused() throws Exception {
        String email = uniqueEmail("verify");
        register(email, PASSWORD);
        String token = mail.lastToken(email, "Verify");
        verify(email);
        mvc.perform(post("/api/v1/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("token", token))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        String access = login(email, PASSWORD);
        mvc.perform(bearer(get("/api/v1/users/me"), access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.roles[0]").value("MEMBER"))
                .andExpect(jsonPath("$.membership.status").value("ACTIVE"))
                .andExpect(jsonPath("$.membership.valid").value(true));
    }

    @Test
    void expiredVerificationTokenIsRejected() throws Exception {
        String email = uniqueEmail("expired");
        register(email, PASSWORD);
        String token = mail.lastToken(email, "Verify");
        jdbc.update("update user_tokens set expires_at = now() - interval '1 minute' where user_id = "
                + "(select id from users where email = ?)", email);
        mvc.perform(post("/api/v1/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("token", token))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wrongPasswordIsRejectedAndRepeatedFailuresLockTheAccount() throws Exception {
        Member m = newMember("lock");
        for (int i = 0; i < 5; i++) {
            assertThat(loginResult(m.email(), "Wrong-Password-000").getResponse().getStatus()).isEqualTo(401);
        }
        MvcResult locked = loginResult(m.email(), PASSWORD);
        assertThat(locked.getResponse().getStatus()).isEqualTo(401);
        assertThat(body(locked).get("code").asText()).isEqualTo("ACCOUNT_LOCKED");
    }

    @Test
    void refreshRotatesTokenAndReuseRevokesTheFamily() throws Exception {
        Member m = newMember("refresh");
        MvcResult login = loginResult(m.email(), PASSWORD);
        Cookie first = login.getResponse().getCookie("sb_refresh");
        assertThat(first).isNotNull();
        assertThat(first.isHttpOnly()).isTrue();
        assertThat(login.getResponse().getHeader("Set-Cookie")).contains("SameSite=Strict").contains("Path=/api/v1/auth");

        MvcResult refreshed = mvc.perform(post("/api/v1/auth/refresh").cookie(first)
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        Cookie second = refreshed.getResponse().getCookie("sb_refresh");
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        // Replaying the rotated token => 401 and the whole family (including the new token) is revoked.
        mvc.perform(post("/api/v1/auth/refresh").cookie(first)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").cookie(second)).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshFromForeignOriginIsRejected() throws Exception {
        Member m = newMember("origin");
        Cookie cookie = loginResult(m.email(), PASSWORD).getResponse().getCookie("sb_refresh");
        mvc.perform(post("/api/v1/auth/refresh").cookie(cookie).header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        Member m = newMember("logout");
        Cookie cookie = loginResult(m.email(), PASSWORD).getResponse().getCookie("sb_refresh");
        mvc.perform(post("/api/v1/auth/logout").cookie(cookie))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
        mvc.perform(post("/api/v1/auth/refresh").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordResetWorksOnceAndInvalidatesOldPassword() throws Exception {
        Member m = newMember("reset");
        mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("email", m.email())))).andExpect(status().isAccepted());
        String token = mail.lastToken(m.email(), "Reset");
        String newPassword = "Brand-New-Password-1";

        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("token", token, "newPassword", newPassword)))).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("token", token, "newPassword", "Another-Password-2"))))
                .andExpect(status().isBadRequest());

        assertThat(loginResult(m.email(), PASSWORD).getResponse().getStatus()).isEqualTo(401);
        login(m.email(), newPassword);
    }

    @Test
    void forgotPasswordForUnknownEmailLooksIdentical() throws Exception {
        String unknown = uniqueEmail("nobody");
        mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("email", unknown))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("If the address can receive email, a message has been sent."));
        assertThat(mail.count(unknown)).isZero();
    }

    @Test
    void profileUpdateAndPasswordChange() throws Exception {
        Member m = newMember("profile");
        mvc.perform(bearer(put("/api/v1/users/me"), m.token()).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("fullName", "Renamed Member"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Renamed Member"));

        mvc.perform(bearer(put("/api/v1/users/me/password"), m.token()).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("currentPassword", "wrong", "newPassword", "Changed-Password-1"))))
                .andExpect(status().isBadRequest());
        mvc.perform(bearer(put("/api/v1/users/me/password"), m.token()).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("currentPassword", PASSWORD, "newPassword", "Changed-Password-1"))))
                .andExpect(status().isNoContent());
        login(m.email(), "Changed-Password-1");
    }

    @Test
    void disabledUserIsRejectedEvenWithValidAccessToken() throws Exception {
        Member m = newMember("disabled");
        String admin = adminToken();
        MvcResult detail = mvc.perform(bearer(get("/api/v1/admin/users/" + m.id()), admin)).andReturn();
        long version = body(detail).get("version").asLong();

        mvc.perform(bearer(put("/api/v1/admin/users/" + m.id()), admin).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("fullName", "Disabled", "accountStatus", "DISABLED",
                                "roles", new String[]{"MEMBER"}, "version", version))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("DISABLED"));

        mvc.perform(bearer(get("/api/v1/users/me"), m.token())).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidAndMissingTokensGet401() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(bearer(get("/api/v1/users/me"), "not.a.jwt")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/anything-unknown")).andExpect(status().isUnauthorized());
    }

    @Test
    void healthAndCorsPreflight() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/v1/books")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/v1/books")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownUserIdInPathCannotBeUsedByMembers() throws Exception {
        Member m = newMember("idor");
        mvc.perform(bearer(get("/api/v1/admin/users/" + UUID.randomUUID()), m.token()))
                .andExpect(status().isForbidden());
    }
}
