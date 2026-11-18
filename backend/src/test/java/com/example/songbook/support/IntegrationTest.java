package com.example.songbook.support;

import com.example.songbook.auth.entity.OtpPurpose;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class: full application context, real PostgreSQL + Flyway, MockMvc, and helpers that drive the
 * public API exactly like a client would.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestInfrastructure.class)
public abstract class IntegrationTest {

    protected static final String ADMIN_EMAIL = "admin@test.local";
    protected static final String ADMIN_MOBILE = "+919000000001";
    protected static final String ADMIN_PASSWORD = "Admin-Password-123";
    protected static final String PASSWORD = "Member-Password-123";

    /** Random start so reruns against the same embedded database do not collide. */
    private static final AtomicLong MOBILE_SEQ =
            new AtomicLong(100_000_000L + ThreadLocalRandom.current().nextLong(800_000_000L));

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected TestInfrastructure.CapturingSmsSender sms;

    /** A fresh Indian mobile number in E.164 form, e.g. +919123456789. */
    protected static String uniqueMobile() {
        return "+919" + String.format("%09d", MOBILE_SEQ.incrementAndGet() % 1_000_000_000L);
    }

    protected static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
    }

    protected String toJson(Object value) throws Exception {
        return json.writeValueAsString(value);
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    protected static MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }

    // ------------------------------------------------------------------ users

    /** @param mobile any accepted spelling; the SMS is captured under the normalised (E.164) number */
    protected void register(String mobile, String password) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("fullName", "Test User", "mobile", mobile, "password", password))))
                .andExpect(status().isAccepted());
    }

    protected MvcResult verifyResult(String mobile, String code) throws Exception {
        return mvc.perform(post("/api/v1/auth/verify-mobile").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("mobile", mobile, "code", code)))).andReturn();
    }

    /** Confirms {@code mobile} (E.164) with the last verification code sent to it. */
    protected void verify(String mobile) throws Exception {
        MvcResult r = verifyResult(mobile, sms.lastCode(mobile, OtpPurpose.MOBILE_VERIFICATION));
        if (r.getResponse().getStatus() != 204) {
            throw new AssertionError("Verification failed: " + r.getResponse().getContentAsString());
        }
    }

    protected MvcResult loginResult(String mobile, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("mobile", mobile, "password", password)))).andReturn();
    }

    protected String login(String mobile, String password) throws Exception {
        MvcResult result = loginResult(mobile, password);
        if (result.getResponse().getStatus() != 200) {
            throw new AssertionError("Login failed: " + result.getResponse().getContentAsString());
        }
        return body(result).get("accessToken").asText();
    }

    /** Registers, verifies (which creates the default membership) and logs in a member. */
    protected Member newMember(String prefix) throws Exception {
        String mobile = uniqueMobile();
        register(mobile, PASSWORD);
        verify(mobile);
        String token = login(mobile, PASSWORD);
        UUID id = UUID.fromString(jdbc.queryForObject("select id::text from users where mobile = ?", String.class, mobile));
        return new Member(id, mobile, token);
    }

    protected record Member(UUID id, String mobile, String token) {
    }

    protected String adminToken() throws Exception {
        return login(ADMIN_MOBILE, ADMIN_PASSWORD);
    }

    // ------------------------------------------------------------------ books

    protected UUID createCategory(String adminToken, String name) throws Exception {
        MvcResult r = mvc.perform(bearer(post("/api/v1/admin/categories"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", name, "description", "Test category"))))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(body(r).get("id").asText());
    }

    protected MvcResult createBookResult(String adminToken, Map<String, Object> metadata, byte[] pdf, byte[] cover)
            throws Exception {
        var request = multipart("/api/v1/admin/books")
                .file(new MockMultipartFile("metadata", "", MediaType.APPLICATION_JSON_VALUE, toJson(metadata).getBytes()))
                .file(new MockMultipartFile("pdf", "book.pdf", "application/pdf", pdf));
        if (cover != null) {
            request.file(new MockMultipartFile("cover", "cover.png", "image/png", cover));
        }
        return mvc.perform(bearer(request, adminToken)).andReturn();
    }

    protected UUID createPublishedBook(String adminToken, String title, UUID categoryId) throws Exception {
        Map<String, Object> metadata = bookMetadata(title, categoryId, LocalDate.now().minusDays(1), null, "PUBLISHED");
        MvcResult r = createBookResult(adminToken, metadata, pdf(3), png());
        if (r.getResponse().getStatus() != 201) {
            throw new AssertionError("Book creation failed: " + r.getResponse().getContentAsString());
        }
        return UUID.fromString(body(r).get("id").asText());
    }

    protected static Map<String, Object> bookMetadata(String title, UUID categoryId, LocalDate effective,
                                                      LocalDate expiry, String status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", title);
        m.put("description", "Description of " + title);
        m.put("categoryId", categoryId);
        m.put("effectiveDate", effective.toString());
        m.put("expiryDate", expiry == null ? null : expiry.toString());
        m.put("status", status);
        return m;
    }

    protected static byte[] pdf(int pages) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                doc.addPage(new PDPage());
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    protected static byte[] png() throws IOException {
        BufferedImage image = new BufferedImage(40, 60, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
