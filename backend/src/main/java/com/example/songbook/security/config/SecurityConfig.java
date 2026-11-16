package com.example.songbook.security.config;

import com.example.songbook.config.AppProperties;
import com.example.songbook.security.authentication.DatabaseJwtAuthenticationConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Stateless, deny-by-default API security.
 * <ul>
 *   <li>Access: {@code Authorization: Bearer <JWT>} validated by the resource-server filter.</li>
 *   <li>Refresh: HttpOnly SameSite=Strict cookie, only read by {@code /auth/refresh} and {@code /auth/logout},
 *       which additionally verify the Origin header (see AuthController).</li>
 *   <li>PDF content: authorized by a short-lived signed ticket (see ReaderController).</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String API = "/api/v1";

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http, JsonSecurityErrorHandlers errorHandlers,
                                           DatabaseJwtAuthenticationConverter jwtConverter) throws Exception {
        http
                // Bearer tokens are not sent automatically by browsers, so CSRF tokens are unnecessary.
                // Cookie-based endpoints (refresh/logout) are protected by SameSite=Strict + Origin check.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {
                })
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .contentTypeOptions(c -> {
                        })
                        .frameOptions(f -> f.deny())
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(errorHandlers.authenticationEntryPoint())
                        .accessDeniedHandler(errorHandlers.accessDeniedHandler()))
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
                        .authenticationEntryPoint(errorHandlers.authenticationEntryPoint())
                        .accessDeniedHandler(errorHandlers.accessDeniedHandler()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                API + "/auth/register",
                                API + "/auth/verify-email",
                                API + "/auth/resend-verification",
                                API + "/auth/login",
                                API + "/auth/refresh",
                                API + "/auth/logout",
                                API + "/auth/forgot-password",
                                API + "/auth/reset-password").permitAll()
                        .requestMatchers(HttpMethod.GET, API + "/categories").permitAll()
                        // Public catalogue: list, detail and cover only.
                        .requestMatchers(HttpMethod.GET,
                                API + "/books",
                                API + "/books/{bookId}",
                                API + "/books/{bookId}/cover").permitAll()
                        // PDF bytes: authorized by a signed, short-lived ticket checked in the controller.
                        .requestMatchers(HttpMethod.GET, API + "/books/{bookId}/content").permitAll()
                        .requestMatchers(API + "/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated());
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Range"));
        config.setExposedHeaders(List.of("Content-Disposition", "Content-Range", "Accept-Ranges", "Content-Length"));
        config.setAllowCredentials(true); // needed for the HttpOnly refresh cookie
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(API + "/**", config);
        return source;
    }

    /** Delegating encoder defaulting to BCrypt; allows a later move to Argon2 without forcing resets. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** Disables Spring Boot's generated in-memory user. Authentication is handled by AuthService + JWT. */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("Not supported");
        };
    }
}
