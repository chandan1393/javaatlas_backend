package com.javaatlas.user;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.OptionalLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.javaatlas.AppProperties;
import com.javaatlas.common.ApiException;
import com.javaatlas.common.RateLimiter;
import com.javaatlas.security.Totp;
import com.javaatlas.security.TokenService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Admin sign-in at /admin/login: password, then a code from an authenticator app once 2FA is set up. */
@RestController
@RequestMapping("/api/admin-auth")
public class AdminAuthController {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthController.class);
    private static final Duration WINDOW = Duration.ofMinutes(15);

    public record AdminLoginRequest(
            @NotBlank @Size(max = 254) String email,
            @NotBlank @Size(max = 100) String password,
            @Pattern(regexp = "\\s*\\d{3}\\s?\\d{3}\\s*|", message = "Enter the 6-digit code.") String code) {
    }

    public record AdminMe(String name, String email, String role, boolean totpEnabled, boolean mfa, boolean require2fa) {
    }

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final RateLimiter limiter;
    private final AppProperties props;

    public AdminAuthController(UserRepository users, PasswordEncoder encoder, TokenService tokens, RateLimiter limiter, AppProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.tokens = tokens;
        this.limiter = limiter;
        this.props = props;
    }

    @PostMapping("/login")
    @Transactional
    public ResponseEntity<AdminMe> login(@Valid @RequestBody AdminLoginRequest req, HttpServletRequest http) {
        String ip = http.getRemoteAddr();
        if (!limiter.allow("admin-login:" + ip, 10, WINDOW)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "rate_limited", "Too many attempts. Wait 15 minutes, then try again.");
        }
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        String lockKey = "admin-fail:" + email;
        if (limiter.blocked(lockKey, 5, WINDOW)) {
            log.warn("Admin sign-in locked for {} (from {})", email, ip);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "locked", "This account is locked for 15 minutes after too many failed attempts.");
        }
        AppUser user = users.findByEmail(email)
                .filter(AppUser::isAdmin)
                .filter(u -> encoder.matches(req.password(), u.getPasswordHash()))
                .orElse(null);
        if (user == null) {
            limiter.hit(lockKey, WINDOW);
            log.warn("Failed admin sign-in for {} from {}", email, ip);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "bad_credentials", "Email or password is incorrect.");
        }
        boolean mfa = false;
        if (user.isTotpEnabled()) {
            if (req.code() == null || req.code().isBlank()) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "totp_required", "Enter the 6-digit code from your authenticator app.");
            }
            OptionalLong step = Totp.verify(user.getTotpSecret(), req.code(), Instant.now());
            if (step.isEmpty() || !user.acceptTotpStep(step.getAsLong())) {
                limiter.hit(lockKey, WINDOW);
                log.warn("Wrong two-factor code for {} from {}", email, ip);
                throw new ApiException(HttpStatus.UNAUTHORIZED, "bad_totp", "That code isn’t right, or was already used. Wait for the next code and try again.");
            }
            mfa = true;
        }
        limiter.reset(lockKey);
        log.info("Admin signed in: {} from {} (2FA: {})", email, ip, mfa);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, tokens.adminCookie(user, mfa).toString())
                .body(me(user, mfa));
    }

    @GetMapping("/me")
    public AdminMe me(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = users.findById(Long.valueOf(jwt.getSubject())).filter(AppUser::isAdmin)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "not_admin", "This isn’t an admin session."));
        return me(user, Boolean.TRUE.equals(jwt.getClaimAsBoolean("mfa")));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, tokens.clearedCookie().toString()).build();
    }

    AdminMe me(AppUser user, boolean mfa) {
        return new AdminMe(user.getName(), user.getEmail(), user.getRole(), user.isTotpEnabled(), mfa, props.admin().require2fa());
    }
}
