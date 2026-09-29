package com.javaatlas.user;

import java.time.Instant;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.javaatlas.AppProperties;
import com.javaatlas.common.ApiException;
import com.javaatlas.security.Totp;
import com.javaatlas.security.TokenService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The admin Security page: two-factor setup, password change and "sign out everywhere". */
@RestController
@RequestMapping("/api/admin/security")
public class AdminSecurityController {

    private static final Logger log = LoggerFactory.getLogger(AdminSecurityController.class);

    public record TotpSetup(String secret, String uri) {
    }

    public record CodeRequest(@NotBlank @Size(max = 12) String code) {
    }

    public record DisableRequest(@NotBlank @Size(max = 100) String password, @NotBlank @Size(max = 12) String code) {
    }

    public record AdminPasswordRequest(
            @NotBlank @Size(max = 100) String currentPassword,
            @NotBlank @Size(min = 12, max = 100, message = "Admin passwords need at least 12 characters.") String newPassword) {
    }

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final AppProperties props;
    private final AdminAuthController auth;

    public AdminSecurityController(UserRepository users, PasswordEncoder encoder, TokenService tokens, AppProperties props, AdminAuthController auth) {
        this.users = users;
        this.encoder = encoder;
        this.tokens = tokens;
        this.props = props;
        this.auth = auth;
    }

    /** Starts two-factor setup: a new secret to scan. It's only active after /totp/enable. */
    @PostMapping("/totp/setup")
    @Transactional
    public TotpSetup setup(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = admin(jwt);
        if (user.isTotpEnabled()) {
            throw new ApiException(HttpStatus.CONFLICT, "totp_enabled", "Two-factor sign-in is already on.");
        }
        String secret = Totp.newSecret();
        user.startTotpSetup(secret);
        return new TotpSetup(secret, Totp.uri("JavaAtlas Admin", user.getEmail(), secret));
    }

    @PostMapping("/totp/enable")
    @Transactional
    public ResponseEntity<AdminAuthController.AdminMe> enable(@Valid @RequestBody CodeRequest req, @AuthenticationPrincipal Jwt jwt) {
        AppUser user = admin(jwt);
        if (user.isTotpEnabled() || user.getTotpSecret() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "totp_state", "Start the setup again.");
        }
        OptionalLong step = Totp.verify(user.getTotpSecret(), req.code(), Instant.now());
        if (step.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_totp", "That code isn’t right. Check the time on your phone and try the next code.");
        }
        user.enableTotp(step.getAsLong());
        user.revokeSessions();   // any session without the second factor ends; this one is renewed below
        log.info("Two-factor sign-in turned on for {}", user.getEmail());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, tokens.adminCookie(user, true).toString())
                .body(auth.me(user, true));
    }

    @PostMapping("/totp/disable")
    @Transactional
    public ResponseEntity<AdminAuthController.AdminMe> disable(@Valid @RequestBody DisableRequest req, @AuthenticationPrincipal Jwt jwt) {
        AppUser user = admin(jwt);
        if (props.admin().require2fa()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "totp_required", "Two-factor sign-in is required on this site (ADMIN_REQUIRE_2FA).");
        }
        OptionalLong step = Totp.verify(user.getTotpSecret(), req.code(), Instant.now());
        if (!encoder.matches(req.password(), user.getPasswordHash()) || step.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_credentials", "Password or code is incorrect.");
        }
        user.disableTotp();
        user.revokeSessions();
        log.warn("Two-factor sign-in turned off for {}", user.getEmail());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, tokens.adminCookie(user, false).toString())
                .body(auth.me(user, false));
    }

    @PostMapping("/password")
    @Transactional
    public ResponseEntity<Void> password(@Valid @RequestBody AdminPasswordRequest req, @AuthenticationPrincipal Jwt jwt) {
        AppUser user = admin(jwt);
        if (!encoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_credentials", "Your current password is incorrect.");
        }
        user.changePasswordHash(encoder.encode(req.newPassword()));
        user.revokeSessions();
        log.info("Admin password changed for {}", user.getEmail());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, tokens.adminCookie(user, Boolean.TRUE.equals(jwt.getClaimAsBoolean("mfa"))).toString())
                .build();
    }

    /** Signs out every other session (for example a laptop you left signed in). */
    @PostMapping("/logout-everywhere")
    @Transactional
    public ResponseEntity<Void> logoutEverywhere(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = admin(jwt);
        user.revokeSessions();
        log.info("Admin signed out everywhere: {}", user.getEmail());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, tokens.adminCookie(user, Boolean.TRUE.equals(jwt.getClaimAsBoolean("mfa"))).toString())
                .build();
    }

    private AppUser admin(Jwt jwt) {
        return users.findById(Long.valueOf(jwt.getSubject())).filter(AppUser::isAdmin)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "not_admin", "This isn’t an admin session."));
    }
}
