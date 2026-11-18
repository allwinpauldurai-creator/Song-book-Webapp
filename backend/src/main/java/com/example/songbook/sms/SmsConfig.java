package com.example.songbook.sms;

import com.example.songbook.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.stream.Stream;

/** Chooses the SMS provider from {@code songbook.sms.provider}. Misconfiguration fails at startup. */
@Configuration(proxyBeanMethods = false)
public class SmsConfig {

    @Bean
    SmsSender smsSender(AppProperties properties, RestClient.Builder restClientBuilder) {
        AppProperties.Sms sms = properties.sms();
        return switch (sms.provider()) {
            case "msg91" -> {
                AppProperties.Msg91 m = sms.msg91();
                if (Stream.of(m.authKey(), m.templateVerify(), m.templateReset(), m.templateChange())
                        .anyMatch(v -> v == null || v.isBlank())) {
                    throw new IllegalStateException("songbook.sms.provider=msg91 requires msg91.auth-key and the "
                            + "template-verify, template-reset and template-change ids");
                }
                yield new Msg91SmsSender(restClientBuilder, m);
            }
            case "log" -> {
                if (!sms.allowLogProvider()) {
                    throw new IllegalStateException("songbook.sms.provider=log writes one-time codes to the log; "
                            + "set songbook.sms.allow-log-provider=true only for local or dev environments");
                }
                yield new LoggingSmsSender();
            }
            default -> throw new IllegalStateException("Unknown songbook.sms.provider: " + sms.provider());
        };
    }
}
