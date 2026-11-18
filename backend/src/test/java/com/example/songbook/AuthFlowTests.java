package com.example.songbook;

import com.example.songbook.auth.entity.OtpPurpose;
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

/** Requirements 14.1 / 14.2: registration, mobile verification, login, refresh, reset, account state. */
class AuthFlowTests extends IntegrationTest {

    private static final String GENERIC_SMS = "If the number is registered, a code has been sent by SMS.";

    /** "+919123456789" -> "91234 56789", how people often type it. */
    private static String loosely(String e164) {
        String national = e164.substring(3);
        return national.substring(0, 5) + " " + national.substring(5);
    }

    private static String wrong(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    @Test
    void registrationCreatesUnverifiedMemberWithHashedPasswordAndCodeOnly() throws Exception {
        String mobile = uniqueMobile();
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("fullName", "Reg User", "mobile", loosely(mobile),
                                "email", "Reg.User@Example.ORG", "password", PASSWORD))))
                .andExpect(status().isAccepted());

        Map<String, Object> row = jdbc.queryForMap(
                "select mobile, email, password_hash, mobile_verified from users where mobile = ?", mobile);
        assertThat(row.get("mobile_verified")).isEqualTo(false);
        assertThat(row.get("email")).isEqualTo("reg.user@example.org");
        assertThat((String) row.get("password_hash")).startsWith("{bcrypt}").doesNotContain(PASSWORD);
        assertThat(jdbc.queryForList("""
                select r.name from user_roles ur join roles r on r.id = ur.role_id
                join users u on u.id = ur.user_id where u.mobile = ?""", String.class, mobile))
                .containsExactly("MEMBER");
        // The code is stored only as a hash.
        String code = sms.lastCode(mobile, OtpPurpose.MOBILE_VERIFICATION);
        assertThat(code).matches("\\d{6}");
        assertThat(jdbc.queryForObject("select count(*) from otp_codes where code_hash = ?", Integer.class, code))
                .isZero();
    }

    @Test
    void mobileSpellingsAreNormalisedToOneAccount() throws Exception {
        String mobile = uniqueMobile();
        String national = mobile.substring(3);
        register(national, PASSWORD);
        register("+91 " + national.substring(0, 5) + "-" + national.substring(5), PASSWORD);
        register("0" + national, PASSWORD);
        assertThat(jdbc.queryForObject("select count(*) from users where mobile = ?", Integer.class, mobile))
                .isEqualTo(1);
        verify(mobile);
        login(loosely(mobile), PASSWORD);
    }

    @Test
    void invalidMobileIsRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("fullName", "X", "mobile", "12345", "password", PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MOBILE"));
    }

    @Test
    void duplicateRegistrationReturnsSameResponseAndCreatesNoSecondUser() throws Exception {
        String mobile = uniqueMobile();
        register(mobile, PASSWORD);
        register(mobile, "Other-Password-999");
        assertThat(jdbc.queryForObject("select count(*) from users where mobile = ?", Integer.class, mobile))
                .isEqualTo(1);
        // Once verified, registering again sends nothing.
        verify(mobile);
        long sent = sms.count(mobile);
        register(mobile, "Other-Password-999");
        assertThat(sms.count(mobile)).isEqualTo(sent);
        login(mobile, PASSWORD);
    }

    @Test
    void emailAlreadyUsedByAnotherAccountIsDroppedSilently() throws Exception {
        String email = uniqueEmail("shared");
        String first = uniqueMobile();
        String second = uniqueMobile();
        for (String mobile : new String[]{first, second}) {
            mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("fullName", "X", "mobile", mobile, "email", email,
                                    "password", PASSWORD))))
                    .andExpect(status().isAccepted());
        }
        assertThat(jdbc.queryForObject("select email from users where mobile = ?", String.class, first))
                .isEqualTo(email);
        assertThat(jdbc.queryForObject("select email from users where mobile = ?", String.class, second)).isNull();
    }

    @Test
    void registrationCannotRequestAdminRole() throws Exception {
        String mobile = uniqueMobile();
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("fullName", "X", "mobile", mobile, "password", PASSWORD,
                                "roles", new String[]{"ADMIN"}, "accountStatus", "ACTIVE"))))
                .andExpect(status().isAccepted());
        assertThat(jdbc.queryForList("""
                select r.name from user_roles ur join roles r on r.id = ur.role_id
                join users u on u.id = ur.user_id where u.mobile = ?""", String.class, mobile))
                .containsExactly("MEMBER");
    }

    @Test
    void invalidRegistrationInputIsRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("fullName", "", "mobile", "", "email", "not-an-email",
                                "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(4));
    }

    @Test
    void unverifiedUserCannotLogIn() throws Exception {
        String mobile = uniqueMobile();
        register(mobile, PASSWORD);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("mobile", mobile, "password", PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MOBILE_NOT_VERIFIED"));
    }

    @Test
    void verificationCreatesMembershipAndCodeCannotBeReused() throws Exception {
        String mobile = uniqueMobile();
        register(mobile, PASSWORD);
        String code = sms.lastCode(mobile, OtpPurpose.MOBILE_VERIFICATION);
        verify(mobile);
        MvcResult reused = verifyResult(mobile, code);
        assertThat(reused.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(reused).get("code").asText()).isEqualTo("INVALID_CODE");

        String access = login(mobile, PASSWORD);
        mvc.perform(bearer(get("/api/v1/users/me"), access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mobile").value(mobile))
                .andExpect(jsonPath("$.mobileVerified").value(true))
                .andExpect(jsonPath("$.roles[0]").value("MEMBER"))
                .andExpect(jsonPath("$.membership.status").value("ACTIVE"))
                .andExpect(jsonPath("$.membership.valid").value(true));
    }

    @Test
    void expiredCodeIsRejected() throws Exception {
        String mobile = uniqueMobile();
        register(mobile, PASSWORD);
        String code = sms.lastCode(mobile, OtpPurpose.MOBILE_VERIFICATION);
        jdbc.update("update otp_codes set expires_at = now() - interval '1 minute' where user_id = "
                + "(select id from users where mobile = ?)", mobile);
        assertThat(verifyResult(mobile, code).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void codeIsBurnedAfterFiveWrongAttempts() throws Exception {
        String mobile = uniqueMobile();
        register(mobile, PASSWORD);
        String code = sms.lastCode(mobile, OtpPurpose.MOBILE_VERIFICATION);
        for (int i = 0; i < 5; i++) {
            assertThat(verifyResult(mobile, wrong(code)).getResponse().getStatus()).isEqualTo(400);
        }
        // The right code no longer works: attempts were counted even though each request failed.
        assertThat(verifyResult(mobile, code).getResponse().getStatus()).isEqualTo(400);
        assertThat(jdbc.queryForObject("select mobile_verified from users where mobile = ?", Boolean.class, mobile))
                .isFalse();

        // A new code works.
        mvc.perform(post("/api/v1/auth/resend-verification").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("mobile", mobile))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(GENERIC_SMS));
        verify(mobile);
    }

    @Test
    void codeForUnknownMobileIsRejectedLikeAWrongCode() throws Exception {
        MvcResult r = verifyResult(uniqueMobile(), "123456");
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(r).get("code").asText()).isEqualTo("INVALID_CODE");
    }

    @Test
    void wrongPasswordIsRejectedAndRepeatedFailuresLockTheAccount() throws Exception {
        Member m = newMember("lock");
        for (int i = 0; i < 5; i++) {
            assertThat(loginResult(m.mobile(), "Wrong-Password-000").getResponse().getStatus()).isEqualTo(401);
        }
        MvcResult locked = loginResult(m.mobile(), PASSWORD);
        assertThat(locked.getResponse().getStatus()).isEqualTo(401);
        assertThat(body(locked).get("code").asText()).isEqualTo("ACCOUNT_LOCKED");
    }

    @Test
    void unparseableLoginNumberLooksLikeWrongCredentials() throws Exception {
        MvcResult r = loginResult("not-a-number", PASSWORD);
        assertThat(r.getResponse().getStatus()).isEqualTo(401);
        assertThat(body(r).get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void refreshRotatesTokenAndReuseRevokesTheFamily() throws Exception {
        Member m = newMember("refresh");
        MvcResult login = loginResult(m.mobile(), PASSWORD);
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
        Cookie cookie = loginResult(m.mobile(), PASSWORD).getResponse().getCookie("sb_refresh");
        mvc.perform(post("/api/v1/auth/refresh").cookie(cookie).header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        Member m = newMember("logout");
        Cookie cookie = loginResult(m.mobile(), PASSWORD).getResponse().getCookie("sb_refresh");
        mvc.perform(post("/api/v1/auth/logout").cookie(cookie))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
        mvc.perform(post("/api/v1/auth/refresh").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordResetWorksOnceAndInvalidatesOldPassword() throws Exception {
        Member m = newMember("reset");
        mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("mobile", loosely(m.mobile()))))).andExpect(status().isAccepted());
        String code = sms.lastCode(m.mobile(), OtpPurpose.PASSWORD_RESET);
        String newPassword = "Brand-New-Password-1";

        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("mobile", m.mobile(), "code", code, "newPassword", newPassword))))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("mobile", m.mobile(), "code", code,
                                "newPassword", "Another-Password-2"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CODE"));

        assertThat(loginResult(m.mobile(), PASSWORD).getResponse().getStatus()).isEqualTo(401);
        login(m.mobile(), newPassword);
    }

    @Test
    void verificationCodeCannotResetThePassword() throws Exception {
        String mobile = uniqueMobile();
        register(mobile, PASSWORD);
        String verifyCode = sms.lastCode(mobile, OtpPurpose.MOBILE_VERIFICATION);
        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("mobile", mobile, "code", verifyCode,
                                "newPassword", "Brand-New-Password-1"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forgotPasswordForUnknownMobileLooksIdentical() throws Exception {
        String unknown = uniqueMobile();
        mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("mobile", unknown))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(GENERIC_SMS));
        assertThat(sms.count(unknown)).isZero();
    }

    @Test
    void profileUpdateAndPasswordChange() throws Exception {
        Member m = newMember("profile");
        String email = uniqueEmail("contact");
        mvc.perform(bearer(put("/api/v1/users/me"), m.token()).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("fullName", "Renamed Member", "email", email.toUpperCase()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Renamed Member"))
                .andExpect(jsonPath("$.email").value(email));
        mvc.perform(bearer(put("/api/v1/users/me"), m.token()).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("fullName", "Renamed Member", "email", ""))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").doesNotExist());

        mvc.perform(bearer(put("/api/v1/users/me/password"), m.token()).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("currentPassword", "wrong", "newPassword", "Changed-Password-1"))))
                .andExpect(status().isBadRequest());
        mvc.perform(bearer(put("/api/v1/users/me/password"), m.token()).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("currentPassword", PASSWORD, "newPassword", "Changed-Password-1"))))
                .andExpect(status().isNoContent());
        login(m.mobile(), "Changed-Password-1");
    }

    @Test
    void mobileChangeNeedsPasswordAndCodeFromTheNewNumber() throws Exception {
        Member m = newMember("change");
        String newMobile = uniqueMobile();

        mvc.perform(bearer(post("/api/v1/users/me/mobile"), m.token()).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("newMobile", newMobile, "currentPassword", "wrong"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
        mvc.perform(bearer(post("/api/v1/users/me/mobile"), m.token()).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("newMobile", loosely(newMobile), "currentPassword", PASSWORD))))
                .andExpect(status().isAccepted());

        // The code went to the new number; the old number still signs in until it is confirmed.
        String code = sms.lastCode(newMobile, OtpPurpose.MOBILE_CHANGE);
        login(m.mobile(), PASSWORD);

        mvc.perform(bearer(post("/api/v1/users/me/mobile/confirm"), m.token()).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("code", wrong(code)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CODE"));
        mvc.perform(bearer(post("/api/v1/users/me/mobile/confirm"), m.token()).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("code", code))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mobile").value(newMobile))
                .andExpect(jsonPath("$.mobileVerified").value(true));

        assertThat(loginResult(m.mobile(), PASSWORD).getResponse().getStatus()).isEqualTo(401);
        login(newMobile, PASSWORD);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'MOBILE_CHANGED' "
                + "and entity_id = ?", Integer.class, m.id())).isEqualTo(1);
    }

    @Test
    void mobileChangeToANumberInUseIsRefused() throws Exception {
        Member a = newMember("taken-a");
        Member b = newMember("taken-b");
        mvc.perform(bearer(post("/api/v1/users/me/mobile"), a.token()).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("newMobile", b.mobile(), "currentPassword", PASSWORD))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MOBILE_IN_USE"));
        mvc.perform(bearer(post("/api/v1/users/me/mobile"), a.token()).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("newMobile", a.mobile(), "currentPassword", PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAME_MOBILE"));
    }

    @Test
    void disabledUserIsRejectedEvenWithValidAccessToken() throws Exception {
        Member m = newMember("disabled");
        String admin = adminToken();
        MvcResult detail = mvc.perform(bearer(get("/api/v1/admin/users/" + m.id()), admin)).andReturn();
        long version = body(detail).get("version").asLong();

        mvc.perform(bearer(put("/api/v1/admin/users/" + m.id()), admin).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("fullName", "Disabled", "mobile", m.mobile(), "mobileVerified", true,
                                "accountStatus", "DISABLED", "roles", new String[]{"MEMBER"}, "version", version))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("DISABLED"));

        mvc.perform(bearer(get("/api/v1/users/me"), m.token())).andExpect(status().isUnauthorized());
    }

    @Test
    void bootstrapAdminSignsInWithMobileAndIsSearchableByIt() throws Exception {
        String admin = adminToken();
        mvc.perform(bearer(get("/api/v1/users/me"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mobile").value(ADMIN_MOBILE))
                .andExpect(jsonPath("$.mobileVerified").value(true))
                .andExpect(jsonPath("$.email").value(ADMIN_EMAIL));
        mvc.perform(bearer(get("/api/v1/admin/users").param("q", "90000 00001"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].mobile").value(ADMIN_MOBILE));
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
