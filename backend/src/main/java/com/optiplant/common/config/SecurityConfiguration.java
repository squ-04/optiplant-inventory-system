package com.optiplant.common.config;

import com.optiplant.common.exception.ApiErrors;
import com.optiplant.identity.repository.UserAccountRepository;
import com.optiplant.identity.service.AccountService;
import com.optiplant.identity.service.SessionPolicyFilter;
import java.time.Clock;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.filter.CorsFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpResponse;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
    }

    @Bean
    SecurityContextRepository securityContextRepository() { return new HttpSessionSecurityContextRepository(); }

    @Bean
    CsrfTokenRepository csrfTokenRepository() { return new HttpSessionCsrfTokenRepository(); }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.frontend-origin}") String origin) {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(origin));
        cors.setAllowCredentials(true);
        cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        // Idempotency-Key protects critical commands against duplicated retries (ARQ-007).
        cors.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN", "Idempotency-Key"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    @Bean
    CorsFilter corsFilter(@Qualifier("corsConfigurationSource") CorsConfigurationSource source, JsonMapper json) {
        var filter = new CorsFilter(source);
        filter.setCorsProcessor(new DefaultCorsProcessor() {
            @Override
            protected void rejectRequest(ServerHttpResponse response) throws java.io.IOException {
                response.setStatusCode(HttpStatus.FORBIDDEN);
                response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
                response.getBody().write(json.writeValueAsString(ApiErrors.problem(403, "CORS_FORBIDDEN",
                    "El origen o la solicitud CORS no están autorizados.")).getBytes(StandardCharsets.UTF_8));
                response.flush();
            }
        });
        return filter;
    }

    @Bean
    org.springframework.boot.web.servlet.FilterRegistrationBean<CorsFilter> corsRegistration(CorsFilter filter) {
        // Security owns ordering; prevent Boot from installing a duplicate servlet filter.
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contexts,
        CsrfTokenRepository csrf, UserAccountRepository users, AccountService accounts, Clock clock,
        JsonMapper json) throws Exception {
        http.cors(cors -> {})
            .csrf(config -> config.csrfTokenRepository(csrf))
            .securityContext(config -> config.securityContextRepository(contexts))
            .requestCache(config -> config.disable())
            .formLogin(config -> config.disable())
            .httpBasic(config -> config.disable())
            .logout(config -> config.disable())
            .authorizeHttpRequests(config -> config
                .requestMatchers("/actuator/health", "/api/v1/auth/csrf", "/api/v1/auth/login",
                    "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(config -> config
                .authenticationEntryPoint((request, response, error) -> ApiErrors.write(response, json, 401,
                    "AUTHENTICATION_REQUIRED", "Debe autenticarse de nuevo."))
                .accessDeniedHandler((request, response, error) -> ApiErrors.write(response, json, 403,
                    error instanceof CsrfException ? "CSRF_INVALID" : "ACCESS_FORBIDDEN",
                    "La solicitud no está autorizada.")))
            .addFilterBefore(new SessionPolicyFilter(users, accounts, clock, json), AuthorizationFilter.class);
        return http.build();
    }
}
