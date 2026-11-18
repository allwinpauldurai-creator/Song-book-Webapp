package com.example.songbook.sms;

import com.example.songbook.auth.entity.OtpPurpose;
import com.example.songbook.config.AppProperties;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * MSG91 Flow API: {@code POST /api/v5/flow} with the {@code authkey} header. Each purpose has its own
 * DLT-approved template whose text contains the {@code ##otp##} variable. MSG91 expects the number without
 * the leading {@code +} (e.g. {@code 919876543210}).
 */
public class Msg91SmsSender implements SmsSender {

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
                "recipients", List.of(Map.of("mobiles", mobile.substring(1), "otp", code)));
        client.post()
                .uri("/api/v5/flow")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private String templateFor(OtpPurpose purpose) {
        return switch (purpose) {
            case MOBILE_VERIFICATION -> config.templateVerify();
            case PASSWORD_RESET -> config.templateReset();
            case MOBILE_CHANGE -> config.templateChange();
        };
    }
}
