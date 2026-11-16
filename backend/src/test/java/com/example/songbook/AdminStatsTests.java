package com.example.songbook;

import com.example.songbook.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/v1/admin/stats. The DB is shared between test classes, so assertions use deltas. */
class AdminStatsTests extends IntegrationTest {

    private JsonNode stats(String token) throws Exception {
        return body(mvc.perform(bearer(get("/api/v1/admin/stats"), token)).andExpect(status().isOk()).andReturn());
    }

    @Test
    void countsReflectNewMembersBooksAndRequests() throws Exception {
        String admin = adminToken();
        JsonNode before = stats(admin);

        Member member = newMember("stats"); // verified -> default ACTIVE membership
        register(uniqueEmail("stats-unverified"), PASSWORD);
        UUID category = createCategory(admin, "Stats " + UUID.randomUUID());
        UUID book = createPublishedBook(admin, "Stats book", category);
        mvc.perform(bearer(post("/api/v1/modification-requests"), member.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("requestType", "CORRECTION", "bookId", book, "description", "Fix line 2"))))
                .andExpect(status().isCreated());

        JsonNode after = stats(admin);

        assertThat(after.at("/users/total").asLong() - before.at("/users/total").asLong()).isEqualTo(2);
        assertThat(after.at("/users/active").asLong() - before.at("/users/active").asLong()).isEqualTo(2);
        assertThat(after.at("/users/unverified").asLong() - before.at("/users/unverified").asLong()).isEqualTo(1);
        assertThat(after.at("/users/activeAdmins").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(after.get("membershipsValidToday").asLong() - before.get("membershipsValidToday").asLong()).isEqualTo(1);
        assertThat(after.at("/booksByStatus/PUBLISHED").asLong() - before.at("/booksByStatus/PUBLISHED").asLong()).isEqualTo(1);
        assertThat(after.get("pendingRequests").asLong() - before.get("pendingRequests").asLong()).isEqualTo(1);
        // Every status is present even when zero, so the UI never sees a missing key.
        assertThat(after.get("booksByStatus").fieldNames()).toIterable()
                .containsExactlyInAnyOrder("DRAFT", "PUBLISHED", "INACTIVE", "ARCHIVED");
        assertThat(after.get("asOf").asText()).matches("\\d{4}-\\d{2}-\\d{2}");
    }

    @Test
    void onlyAdminsCanReadStats() throws Exception {
        mvc.perform(get("/api/v1/admin/stats")).andExpect(status().isUnauthorized());
        Member member = newMember("stats-member");
        mvc.perform(bearer(get("/api/v1/admin/stats"), member.token())).andExpect(status().isForbidden());
    }
}
