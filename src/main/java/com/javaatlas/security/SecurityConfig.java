package com.javaatlas.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import com.javaatlas.user.UserRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.javaatlas.AppProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Lessons, the course catalog, course pages and free preview lectures are public.
 * Checkout, "my" pages and the admin API need a signed-in user (admin API: role ADMIN).
 * The session is a signed JWT in an HttpOnly, SameSite=Strict cookie.
 */
@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /** Paths where the security filter reads the session cookie. Everything else ignores it. */
    private static final List<String> SIGNED_IN_PATHS = List.of(
            "/api/payments/order", "/api/payments/verify", "/api/me/", "/api/auth/me", "/api/auth/password", "/api/admin/", "/api/admin-auth/me");

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, UserRepository users, TokenService tokens, AppProperties props) throws Exception {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(roles);

        http
            // CSRF: the session cookie is SameSite=Strict and state-changing endpoints only accept JSON,
            // which a cross-site form cannot send.
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(h -> h
                .frameOptions(f -> f.deny())
                .contentTypeOptions(Customizer.withDefaults())
                .referrerPolicy(r -> r.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .contentSecurityPolicy(c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000)))
            .addFilterAfter(new SessionValidationFilter(users, tokens, props), BearerTokenAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/payments/order", "/api/payments/verify", "/api/me/**", "/api/auth/me", "/api/auth/password", "/api/admin-auth/me").authenticated()
                .anyRequest().permitAll())
            .oauth2ResourceServer(o -> o
                .bearerTokenResolver(SecurityConfig::tokenFromCookie)
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)));
        return http.build();
    }

    static String tokenFromCookie(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (SIGNED_IN_PATHS.stream().noneMatch(uri::startsWith) || request.getCookies() == null) {
            return null;
        }
        for (Cookie c : request.getCookies()) {
            if (TokenService.COOKIE.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                return c.getValue();
            }
        }
        return null;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (!props.corsOrigins().isEmpty()) {
            CorsConfiguration cfg = new CorsConfiguration();
            cfg.setAllowedOrigins(props.corsOrigins());
            cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
            cfg.setAllowedHeaders(List.of("Content-Type"));
            cfg.setAllowCredentials(true);
            source.registerCorsConfiguration("/api/**", cfg);
        }
        return source;
    }

    @Bean
    SecretKey jwtSigningKey(AppProperties props) {
        String secret = props.jwt().secret();
        byte[] bytes;
        if (!AppProperties.has(secret)) {
            if (props.cookieSecure()) {
                throw new IllegalStateException("JWT_SECRET must be set in production (COOKIE_SECURE=true). Generate one with: openssl rand -base64 48");
            }
            bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            log.warn("JWT_SECRET is not set. Using a random key, so everyone is signed out when the app restarts. Set JWT_SECRET in production.");
        } else {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < 32) {
                throw new IllegalStateException("JWT_SECRET must be at least 32 characters long.");
            }
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
