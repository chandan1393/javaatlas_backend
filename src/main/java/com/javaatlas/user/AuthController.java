package com.javaatlas.user;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
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
import com.javaatlas.security.TokenService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Sign-up, log-in, log-out and password change. Learning never needs an account. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record SignupRequest(
            @NotBlank(message = "Enter your name.") @Size(max = 120, message = "That name is too long.") String name,
            @NotBlank(message = "Enter your email.") @Email(message = "Enter a valid email address.") @Size(max = 254, message = "That email is too long.") String email,
            @NotBlank(message = "Choose a password.") @Size(min = 8, max = 100, message = "Passwords need 8 to 100 characters.") String password) {
    }

    public record LoginRequest(
            @NotBlank(message = "Enter your email.") String email,
            @NotBlank(message = "Enter your password.") String password) {
    }

    public record PasswordRequest(
            @NotBlank(message = "Enter your current password.") String currentPassword,
            @NotBlank(message = "Choose a new password.") @Size(min = 8, max = 100, message = "Passwords need 8 to 100 characters.") String newPassword) {
    }

    public record UserDto(String name, String email, String role) {
    }

    public record ForgotRequest(
            @NotBlank(message = "Enter your email.") @Email(message = "Enter a valid email address.") String email) {
    }

    public record ResetRequest(
            @NotBlank(message = "This reset link is incomplete.") String token,
            @NotBlank(message = "Choose a new password.") @Size(min = 8, max = 100, message = "Passwords need 8 to 100 characters.") String newPassword) {
    }

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final RateLimiter limiter;
    private final AppProperties props;
    private final PasswordResetService resets;
    static final int MAX_FAILED_LOGINS = 8;
    static final Duration LOCKOUT = Duration.ofMinutes(15);

    public AuthController(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens,
                          RateLimiter limiter, AppProperties props, PasswordResetService resets) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.limiter = limiter;
        this.props = props;
        this.resets = resets;
    }

    /** Always answers 204, whether or not the email has an account. */
    @PostMapping("/forgot")
    public ResponseEntity<Void> forgot(@Valid @RequestBody ForgotRequest req, HttpServletRequest http) {
        guard(http);
        resets.requestReset(req.email());
        return ResponseEntity.noContent().build();
    }

    /** Sets the new password and signs the learner in. */
    @PostMapping("/reset")
    public ResponseEntity<UserDto> reset(@Valid @RequestBody ResetRequest req, HttpServletRequest http) {
        guard(http);
        AppUser user = resets.reset(req.token(), req.newPassword());
        if (user.isAdmin()) {
            // Admins still need their second factor: no session here, they sign in at /admin/login.
            return ResponseEntity.ok(new UserDto(user.getName(), user.getEmail(), user.getRole()));
        }
        return withSession(user);
    }

    @PostMapping("/signup")
    public ResponseEntity<UserDto> signup(@Valid @RequestBody SignupRequest req, HttpServletRequest http) {
        guard(http);
        String email = normalize(req.email());
        if (users.existsByEmail(email)) {
            throw emailTaken();
        }
        AppUser user = new AppUser(email, req.name().trim(), passwordEncoder.encode(req.password()));
        try {
            user = users.save(user);
        } catch (DataIntegrityViolationException e) {
            throw emailTaken();
        }
        return withSession(user);
    }

    @PostMapping("/login")
    @Transactional
    public ResponseEntity<UserDto> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        guard(http);
        String email = normalize(req.email());
        String lockKey = "login-fail:" + email;
        if (limiter.blocked(lockKey, MAX_FAILED_LOGINS, LOCKOUT)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "locked", "Too many wrong passwords for this account. Try again in 15 minutes, or reset your password.");
        }
        AppUser user = users.findByEmail(email)
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElse(null);
        if (user == null) {
            limiter.hit(lockKey, LOCKOUT);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "bad_credentials", "Email or password is incorrect.");
        }
        limiter.reset(lockKey);
        if (user.isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "admin_login_required", "This is an admin account. Sign in at /admin/login.");
        }
        return withSession(user);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, tokens.clearedCookie().toString()).build();
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        String role = roles != null && roles.contains(AppUser.ROLE_ADMIN) ? AppUser.ROLE_ADMIN : AppUser.ROLE_USER;
        return new UserDto(jwt.getClaimAsString("name"), jwt.getClaimAsString("email"), role);
    }

    @PostMapping("/password")
    @Transactional
    public ResponseEntity<Void> changePassword(@Valid @RequestBody PasswordRequest req, @AuthenticationPrincipal Jwt jwt,
                                               HttpServletRequest http) {
        guard(http);
        AppUser user = users.findById(Long.valueOf(jwt.getSubject()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "bad_credentials", "Please log in again."));
        if (user.isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "admin_security_page", "Change the admin password on the admin Security page.");
        }
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_credentials", "Your current password is incorrect.");
        }
        user.changePasswordHash(passwordEncoder.encode(req.newPassword()));
        user.revokeSessions();   // other devices are signed out; this one gets a fresh session below
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, tokens.sessionCookie(user).toString()).build();
    }

    private ResponseEntity<UserDto> withSession(AppUser user) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, tokens.sessionCookie(user).toString())
                .body(new UserDto(user.getName(), user.getEmail(), user.getRole()));
    }

    private void guard(HttpServletRequest http) {
        if (!limiter.allow("auth:" + http.getRemoteAddr(), 20, Duration.ofMinutes(15))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "rate_limited", "Too many attempts. Wait 15 minutes, then try again.");
        }
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static ApiException emailTaken() {
        return new ApiException(HttpStatus.CONFLICT, "email_taken", "An account with this email already exists. Log in instead.");
    }
}
