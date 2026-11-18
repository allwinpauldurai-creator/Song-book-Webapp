package com.example.songbook.sms;

import com.example.songbook.auth.entity.OtpPurpose;
import com.example.songbook.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SmsProviderTest {

    private static final AppProperties.Msg91 MSG91 = new AppProperties.Msg91(
            "https://msg91.test", "test-auth-key", "tpl-otp", "tpl-reset", "tpl-change", "otp");

    private static AppProperties.Sms sms(String provider, boolean allowLog, AppProperties.Msg91 msg91) {
        return new AppProperties.Sms(provider, allowLog, Duration.ofMinutes(10), 5, Duration.ofSeconds(60), 500, msg91);
    }

    private static AppProperties props(AppProperties.Sms sms) {
        return new AppProperties("UTC", "http://localhost", null, null, null, null, sms, null, true);
    }

    @Test
    void msg91SendsTheCodeWithTheTemplateOfThePurpose() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://msg91.test/api/v5/flow"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("authkey", "test-auth-key"))
                .andExpect(content().json("""
                        {"template_id":"tpl-reset","short_url":"0",
                         "recipients":[{"mobiles":"919876543210","otp":"123456"}]}""", true))
                .andRespond(withSuccess("{\"type\":\"success\"}", MediaType.APPLICATION_JSON));

        new Msg91SmsSender(builder, MSG91).sendOtp("+919876543210", OtpPurpose.PASSWORD_RESET, "123456");
        server.verify();
    }

    @Test
    void msg91SingleTemplateServesEveryPurposeWithACustomVariableName() {
        AppProperties.Msg91 single = new AppProperties.Msg91(
                "https://msg91.test", "test-auth-key", "tpl-only", "", null, "OTP");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://msg91.test/api/v5/flow"))
                .andExpect(content().json("""
                        {"template_id":"tpl-only","short_url":"0",
                         "recipients":[{"mobiles":"919876543210","OTP":"654321"}]}""", true))
                .andRespond(withSuccess("{\"type\":\"success\"}", MediaType.APPLICATION_JSON));

        new Msg91SmsSender(builder, single).sendOtp("+919876543210", OtpPurpose.MOBILE_CHANGE, "654321");
        server.verify();
    }

    @Test
    void msg91ErrorReplyBecomesSmsSendExceptionWithoutTheCode() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://msg91.test/api/v5/flow"))
                .andRespond(withSuccess("{\"type\":\"error\",\"message\":\"Template not approved\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://msg91.test/api/v5/flow"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"type\":\"error\",\"message\":\"Invalid authkey\"}"));
        Msg91SmsSender sender = new Msg91SmsSender(builder, MSG91);

        assertThatThrownBy(() -> sender.sendOtp("+919876543210", OtpPurpose.PASSWORD_RESET, "111222"))
                .isInstanceOf(SmsSendException.class)
                .hasMessageContaining("Template not approved")
                .hasMessageNotContaining("111222");
        assertThatThrownBy(() -> sender.sendOtp("+919876543210", OtpPurpose.PASSWORD_RESET, "111222"))
                .isInstanceOf(SmsSendException.class)
                .hasMessageContaining("401")
                .hasMessageContaining("Invalid authkey");
        server.verify();
    }

    @Test
    void logProviderIsRefusedUnlessExplicitlyAllowed() {
        SmsConfig config = new SmsConfig();
        assertThatThrownBy(() -> config.smsSender(props(sms("log", false, MSG91)), RestClient.builder()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("allow-log-provider");
        assertThat(config.smsSender(props(sms("log", true, MSG91)), RestClient.builder()))
                .isInstanceOf(LoggingSmsSender.class);
    }

    @Test
    void msg91NeedsAuthKeyAndTheOtpTemplate() {
        SmsConfig config = new SmsConfig();
        AppProperties.Msg91 incomplete = new AppProperties.Msg91("https://msg91.test", "key", "", "tpl", "tpl", "otp");
        assertThatThrownBy(() -> config.smsSender(props(sms("msg91", false, incomplete)), RestClient.builder()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("template");
        assertThat(config.smsSender(props(sms("msg91", false, MSG91)), RestClient.builder()))
                .isInstanceOf(Msg91SmsSender.class);
    }

    @Test
    void unknownProviderFailsFast() {
        assertThatThrownBy(() -> new SmsConfig().smsSender(props(sms("pigeon", true, MSG91)), RestClient.builder()))
                .isInstanceOf(IllegalStateException.class);
    }
}
