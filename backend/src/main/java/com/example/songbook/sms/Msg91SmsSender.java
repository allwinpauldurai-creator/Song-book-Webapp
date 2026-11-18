package com.example.songbook.sms;

import com.example.songbook.auth.entity.OtpPurpose;
import com.example.songbook.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * MSG91 Flow API: {@code POST /api/v5/flow} with the {@code authkey} header. The DLT-approved template's text
 * contains the OTP variable ({@code ##otp##} by default, see {@code msg91.otp-variable}). Password reset and
 * mobile change use their own templates when configured, otherwise the verification template. MSG91 expects the
 * number without the leading {@code +} (e.g. {@code 919876543210}).
 *
 * <p>MSG91 can answer HTTP 200 with {@code {"type":"error"}}; both that and HTTP errors become
 * {@link SmsSendException} carrying MSG91's message (never the code), so failures are visible in the log.
 */
public class Msg91SmsSender implements SmsSender {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_DETAIL = 200;

    private final RestClient client;
    private final AppProperties.Msg91 config;

    public Msg91SmsSender(RestClient.Builder builder, AppProperties.Msg91 config) {
        this.config = config;
        this.client = builder
                .baseUrl(config.baseUrl())
                .defaultHeader("authkey", config.authKey())
                .build();
    }

    @Override
    public void sendOtp(String mobile, OtpPurpose purpose, String code) {
        Map<String, Object> body = Map.of(
                "template_id", templateFor(purpose),
                "short_url", "0",
                "recipients", List.of(Map.of("mobiles", mobile.substring(1), otpVariable(), code)));
        String response;
        try {
            response = client.post()
                    .uri("/api/v5/flow")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw new SmsSendException("MSG91 HTTP " + e.getStatusCode().value() + ": "
                    + detail(e.getResponseBodyAsString()));
        }
        JsonNode json = parse(response);
        if (json != null && "error".equalsIgnoreCase(json.path("type").asText())) {
            throw new SmsSendException("MSG91 error: " + detail(response));
        }
    }

    String templateFor(OtpPurpose purpose) {
        String specific = switch (purpose) {
            case MOBILE_VERIFICATION -> config.templateVerify();
            case PASSWORD_RESET -> config.templateReset();
            case MOBILE_CHANGE -> config.templateChange();
        };
        return specific == null || specific.isBlank() ? config.templateVerify() : specific;
    }

    private String otpVariable() {
        String v = config.otpVariable();
        return v == null || v.isBlank() ? "otp" : v;
    }

    private static JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return JSON.readTree(body);
        } catch (Exception e) {
            return null;
        }
    }

    /** MSG91's own message when the body is JSON, otherwise the (shortened) body. */
    private static String detail(String body) {
        JsonNode json = parse(body);
        String text = json != null && json.hasNonNull("message") ? json.get("message").asText() : body;
        if (text == null || text.isBlank()) {
            return "(empty response)";
        }
        return text.length() > MAX_DETAIL ? text.substring(0, MAX_DETAIL) + "…" : text;
    }
}
