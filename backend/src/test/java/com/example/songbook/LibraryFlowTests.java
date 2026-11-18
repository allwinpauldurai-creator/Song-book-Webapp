package com.example.songbook;

import com.example.songbook.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Requirements 14.2-14.5: catalogue, availability, PDF access, files, versions, admin features. */
class LibraryFlowTests extends IntegrationTest {

    @Test
    void publishedBookIsVisibleToGuestsWithoutStorageDetails() throws Exception {
        String admin = adminToken();
        UUID category = createCategory(admin, "Hymns " + UUID.randomUUID());
        String title = "Public Book " + UUID.randomUUID();
        UUID bookId = createPublishedBook(admin, title, category);

        MvcResult list = mvc.perform(get("/api/v1/books").param("q", title).param("categoryId", category.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value(title))
                .andExpect(jsonPath("$.content[0].coverUrl").value(org.hamcrest.Matchers.startsWith("/api/v1/books/" + bookId + "/cover?v=")))
                .andReturn();
        String raw = list.getResponse().getContentAsString();
        assertThat(raw).doesNotContain("book-pdfs").doesNotContain("blob").doesNotContain(".pdf");

        mvc.perform(get("/api/v1/books/" + bookId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageCount").value(3));
        mvc.perform(get("/api/v1/books/" + bookId + "/cover"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"));
        mvc.perform(get("/api/v1/categories")).andExpect(status().isOk());
    }

    @Test
    void guestCannotReadOrDownloadPdfs() throws Exception {
        UUID bookId = createPublishedBook(adminToken(), "Guest Denied " + UUID.randomUUID(), null);
        mvc.perform(get("/api/v1/books/" + bookId + "/read-access")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/books/" + bookId + "/download-access")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/books/" + bookId + "/content").param("ticket", "forged.ticket"))
                .andExpect(status().isForbidden());
    }

    @Test
    void eligibleMemberReadsAndDownloadsWithRangeSupport() throws Exception {
        UUID bookId = createPublishedBook(adminToken(), "Readable " + UUID.randomUUID(), null);
        Member m = newMember("reader");

        JsonNode read = body(mvc.perform(bearer(get("/api/v1/books/" + bookId + "/read-access"), m.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageCount").value(3))
                .andReturn());
        String ticket = read.get("ticket").asText();

        MvcResult full = mvc.perform(get("/api/v1/books/" + bookId + "/content").param("ticket", ticket))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")))
                .andReturn();
        assertThat(new String(full.getResponse().getContentAsByteArray(), 0, 5)).isEqualTo("%PDF-");

        mvc.perform(get("/api/v1/books/" + bookId + "/content").param("ticket", ticket).header("Range", "bytes=0-9"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", org.hamcrest.Matchers.startsWith("bytes 0-9/")));

        String downloadTicket = body(mvc.perform(bearer(get("/api/v1/books/" + bookId + "/download-access"), m.token()))
                .andExpect(status().isOk()).andReturn()).get("ticket").asText();
        mvc.perform(get("/api/v1/books/" + bookId + "/content").param("ticket", downloadTicket))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")));

        // Ticket for one book cannot be used for another
        UUID other = createPublishedBook(adminToken(), "Other " + UUID.randomUUID(), null);
        mvc.perform(get("/api/v1/books/" + other + "/content").param("ticket", ticket)).andExpect(status().isForbidden());
    }

    @Test
    void suspendedOrExpiredMembershipDeniesAccess() throws Exception {
        String admin = adminToken();
        UUID bookId = createPublishedBook(admin, "Membership Gate " + UUID.randomUUID(), null);
        Member m = newMember("suspend");

        JsonNode membership = body(mvc.perform(bearer(get("/api/v1/admin/memberships"), admin)
                .param("userId", m.id().toString())).andExpect(status().isOk()).andReturn()).get("content").get(0);
        String membershipId = membership.get("id").asText();

        mvc.perform(bearer(put("/api/v1/admin/memberships/" + membershipId), admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("startDate", membership.get("startDate").asText(),
                                "status", "SUSPENDED", "version", membership.get("version").asLong()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validToday").value(false));
        mvc.perform(bearer(get("/api/v1/books/" + bookId + "/read-access"), m.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBERSHIP_INVALID"));

        // A new ACTIVE membership that ended yesterday is still invalid
        LocalDate today = LocalDate.now();
        mvc.perform(bearer(post("/api/v1/admin/memberships"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("userId", m.id(), "startDate", today.minusDays(30).toString(),
                                "endDate", today.minusDays(2).toString(), "status", "ACTIVE"))))
                .andExpect(status().isCreated());
        mvc.perform(bearer(get("/api/v1/books/" + bookId + "/read-access"), m.token()))
                .andExpect(status().isForbidden());

        // Future start date also denies; then a current one grants
        mvc.perform(bearer(post("/api/v1/admin/memberships"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("userId", m.id(), "startDate", today.plusDays(5).toString(),
                                "status", "ACTIVE"))))
                .andExpect(status().isCreated());
        mvc.perform(bearer(get("/api/v1/books/" + bookId + "/read-access"), m.token()))
                .andExpect(status().isForbidden());
        mvc.perform(bearer(post("/api/v1/admin/memberships"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("userId", m.id(), "startDate", today.minusDays(2).toString(),
                                "status", "ACTIVE"))))
                .andExpect(status().isCreated());
        mvc.perform(bearer(get("/api/v1/books/" + bookId + "/read-access"), m.token()))
                .andExpect(status().isOk());

        // History preserved: 4 rows, exactly one ACTIVE
        assertThat(jdbc.queryForObject("select count(*) from memberships where user_id = ?", Integer.class, m.id()))
                .isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from memberships where user_id = ? and status = 'ACTIVE'",
                Integer.class, m.id())).isEqualTo(1);
    }

    @Test
    void futureExpiredDraftAndArchivedBooksAreUnavailable() throws Exception {
        String admin = adminToken();
        LocalDate today = LocalDate.now();
        String tag = UUID.randomUUID().toString();

        UUID future = UUID.fromString(body(createBookResult(admin,
                bookMetadata("Future " + tag, null, today.plusDays(10), null, "PUBLISHED"), pdf(1), null)).get("id").asText());
        UUID expired = UUID.fromString(body(createBookResult(admin,
                bookMetadata("Expired " + tag, null, today.minusDays(10), today.minusDays(2), "PUBLISHED"), pdf(1), null))
                .get("id").asText());
        UUID draft = UUID.fromString(body(createBookResult(admin,
                bookMetadata("Draft " + tag, null, today.minusDays(1), null, "DRAFT"), pdf(1), null)).get("id").asText());
        UUID archived = createPublishedBook(admin, "Archived " + tag, null);
        mvc.perform(bearer(post("/api/v1/admin/books/" + archived + "/archive"), admin)).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/books").param("q", tag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        Member m = newMember("unavailable");
        for (UUID id : new UUID[]{future, expired, draft, archived}) {
            mvc.perform(get("/api/v1/books/" + id)).andExpect(status().isNotFound());
            mvc.perform(bearer(get("/api/v1/books/" + id + "/read-access"), m.token())).andExpect(status().isNotFound());
        }
        // Admin still sees them
        mvc.perform(bearer(get("/api/v1/admin/books"), admin).param("q", tag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void invalidUploadsAreRejected() throws Exception {
        String admin = adminToken();
        Map<String, Object> meta = bookMetadata("Bad " + UUID.randomUUID(), null, LocalDate.now(), null, "DRAFT");

        // Text file disguised as PDF
        MvcResult fake = createBookResult(admin, meta, "<html>not a pdf</html>".getBytes(), null);
        assertThat(fake.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(fake).get("code").asText()).isEqualTo("INVALID_FILE");

        // Truncated PDF (correct magic bytes, broken structure)
        MvcResult truncated = createBookResult(admin, meta, "%PDF-1.7\n garbage".getBytes(), null);
        assertThat(truncated.getResponse().getStatus()).isEqualTo(400);

        // Invalid image
        MvcResult badCover = createBookResult(admin, meta, pdf(1), "GIF89a....".getBytes());
        assertThat(badCover.getResponse().getStatus()).isEqualTo(400);

        // Oversized PDF (test limit is 2 MB)
        MvcResult big = createBookResult(admin, meta, new byte[3 * 1024 * 1024], null);
        assertThat(big.getResponse().getStatus()).isEqualTo(413);

        // Expiry before effective date
        MvcResult dates = createBookResult(admin,
                bookMetadata("Dates", null, LocalDate.now(), LocalDate.now().minusDays(1), "DRAFT"), pdf(1), null);
        assertThat(dates.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(dates).get("code").asText()).isEqualTo("INVALID_DATE_RANGE");

        assertThat(jdbc.queryForObject("select count(*) from books where title like 'Bad %' or title = 'Dates'",
                Integer.class)).isZero();
    }

    @Test
    void replacingPdfKeepsPreviousVersion() throws Exception {
        String admin = adminToken();
        UUID bookId = createPublishedBook(admin, "Versioned " + UUID.randomUUID(), null);
        Member m = newMember("versions");
        String oldTicket = body(mvc.perform(bearer(get("/api/v1/books/" + bookId + "/read-access"), m.token()))
                .andReturn()).get("ticket").asText();

        mvc.perform(bearer(multipart("/api/v1/admin/books/" + bookId + "/versions")
                                .file(new MockMultipartFile("pdf", "v2.pdf", "application/pdf", pdf(5)))
                                .param("changeNote", "Added hymns"), admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value(2))
                .andExpect(jsonPath("$.pageCount").value(5));

        JsonNode versions = body(mvc.perform(bearer(get("/api/v1/admin/books/" + bookId + "/versions"), admin))
                .andExpect(status().isOk()).andReturn());
        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).get("current").asBoolean()).isTrue();
        assertThat(versions.get(1).get("versionNumber").asInt()).isEqualTo(1);

        // Old version file still downloadable by admin
        String v1 = versions.get(1).get("id").asText();
        mvc.perform(bearer(get("/api/v1/admin/books/" + bookId + "/versions/" + v1 + "/content"), admin))
                .andExpect(status().isOk());

        // Member now sees the new version; old ticket is rejected
        mvc.perform(get("/api/v1/books/" + bookId)).andExpect(jsonPath("$.pageCount").value(5));
        mvc.perform(get("/api/v1/books/" + bookId + "/content").param("ticket", oldTicket))
                .andExpect(status().isConflict());
    }

    @Test
    void memberCannotUseAdminEndpoints() throws Exception {
        Member m = newMember("notadmin");
        mvc.perform(bearer(get("/api/v1/admin/users"), m.token())).andExpect(status().isForbidden());
        mvc.perform(bearer(get("/api/v1/admin/memberships"), m.token())).andExpect(status().isForbidden());
        mvc.perform(bearer(post("/api/v1/admin/categories"), m.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\"}")).andExpect(status().isForbidden());
        MvcResult upload = createBookResult(m.token(),
                bookMetadata("Hack", null, LocalDate.now(), null, "PUBLISHED"), pdf(1), null);
        assertThat(upload.getResponse().getStatus()).isEqualTo(403);
        mvc.perform(bearer(get("/api/v1/admin/audit-logs"), m.token())).andExpect(status().isForbidden());
    }

    @Test
    void lastAdminCannotBeDemoted() throws Exception {
        String admin = adminToken();
        JsonNode me = body(mvc.perform(bearer(get("/api/v1/users/me"), admin)).andReturn());
        JsonNode detail = body(mvc.perform(bearer(get("/api/v1/admin/users/" + me.get("id").asText()), admin)).andReturn());
        mvc.perform(bearer(put("/api/v1/admin/users/" + me.get("id").asText()), admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("fullName", "Admin", "mobile", ADMIN_MOBILE, "mobileVerified", true,
                                "accountStatus", "ACTIVE", "roles", new String[]{"MEMBER"},
                                "version", detail.get("version").asLong()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ADMIN"));
    }

    @Test
    void staleVersionIsRejected() throws Exception {
        String admin = adminToken();
        UUID category = createCategory(admin, "Stale " + UUID.randomUUID());
        mvc.perform(bearer(put("/api/v1/admin/categories/" + category), admin).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "Renamed " + UUID.randomUUID(), "active", true, "version", 99))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void adminCreatesUserAndManagesBookMetadata() throws Exception {
        String admin = adminToken();
        String email = uniqueEmail("created");
        String mobile = uniqueMobile();
        mvc.perform(bearer(post("/api/v1/admin/users"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("fullName", "Created User", "mobile", mobile.substring(3),
                                "email", email, "password", PASSWORD, "roles", new String[]{"MEMBER"},
                                "mobileVerified", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mobile").value(mobile))
                .andExpect(jsonPath("$.mobileVerified").value(true));
        mvc.perform(bearer(get("/api/v1/admin/users"), admin).param("q", email))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(bearer(get("/api/v1/admin/users"), admin).param("q", mobile.substring(3)))
                .andExpect(jsonPath("$.totalElements").value(1));
        login(mobile, PASSWORD); // admin vouched for the number: no SMS needed
        mvc.perform(bearer(post("/api/v1/admin/users"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("fullName", "Duplicate", "mobile", mobile, "password", PASSWORD,
                                "roles", new String[]{"MEMBER"}, "mobileVerified", true))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MOBILE_IN_USE"));

        UUID bookId = createPublishedBook(admin, "Editable " + UUID.randomUUID(), null);
        JsonNode book = body(mvc.perform(bearer(get("/api/v1/admin/books/" + bookId), admin)).andReturn());
        mvc.perform(bearer(put("/api/v1/admin/books/" + bookId), admin).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("title", "Edited title", "effectiveDate", LocalDate.now().toString(),
                                "status", "INACTIVE", "version", book.get("version").asLong()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.availableToday").value(false));
        mvc.perform(get("/api/v1/books/" + bookId)).andExpect(status().isNotFound());

        mvc.perform(bearer(multipart("/api/v1/admin/books/" + bookId + "/cover")
                                .file(new MockMultipartFile("cover", "c.png", "image/png", png()))
                                .with(r -> {
                                    r.setMethod("PUT");
                                    return r;
                                }), admin))
                .andExpect(status().isOk())
                // A replaced cover gets a new URL, so the 24 h public cache cannot serve the old image.
                .andExpect(jsonPath("$.coverUrl").value(org.hamcrest.Matchers.startsWith("/api/v1/books/" + bookId + "/cover?v=")))
                .andExpect(jsonPath("$.coverUrl").value(org.hamcrest.Matchers.not(book.get("coverUrl").asText())));
    }

    @Test
    void readingProgressModificationRequestsAndAudit() throws Exception {
        String admin = adminToken();
        UUID bookId = createPublishedBook(admin, "Progress " + UUID.randomUUID(), null);
        Member m = newMember("progress");

        mvc.perform(bearer(get("/api/v1/members/books/" + bookId + "/progress"), m.token()))
                .andExpect(status().isNotFound());
        mvc.perform(bearer(put("/api/v1/members/books/" + bookId + "/progress"), m.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lastPage\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastPage").value(2));
        mvc.perform(bearer(put("/api/v1/members/books/" + bookId + "/progress"), m.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lastPage\":99}"))
                .andExpect(status().isBadRequest());
        mvc.perform(bearer(get("/api/v1/members/progress"), m.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].bookId").value(bookId.toString()));

        // Another member does not see this progress
        Member other = newMember("other");
        mvc.perform(bearer(get("/api/v1/members/books/" + bookId + "/progress"), other.token()))
                .andExpect(status().isNotFound());

        JsonNode request = body(mvc.perform(bearer(post("/api/v1/modification-requests"), m.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("bookId", bookId, "requestType", "CORRECTION",
                                "description", "Typo in hymn 12"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn());
        mvc.perform(bearer(get("/api/v1/members/modification-requests"), other.token()))
                .andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(bearer(put("/api/v1/admin/modification-requests/" + request.get("id").asText()), admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("status", "APPROVED", "reviewNote", "Fixed",
                                "version", request.get("version").asLong()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(bearer(put("/api/v1/admin/modification-requests/" + request.get("id").asText()), admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("status", "REJECTED", "version", request.get("version").asLong() + 1))))
                .andExpect(status().isConflict());

        mvc.perform(bearer(get("/api/v1/admin/audit-logs"), admin).param("entityId", bookId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.action == 'BOOK_CREATED')]").exists());

        // Audit details never contain secrets
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where details::text ilike '%password%' "
                + "or details::text ilike '%token%'", Integer.class)).isZero();
    }

    @Test
    void invalidSortIsRejected() throws Exception {
        mvc.perform(get("/api/v1/books").param("sort", "passwordHash,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SORT"));
    }
}
