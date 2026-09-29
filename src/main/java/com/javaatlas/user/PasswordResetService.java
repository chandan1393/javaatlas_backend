package com.javaatlas.user;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.javaatlas.AppProperties;
import com.javaatlas.common.ApiException;

/**
 * "Forgot password": emails a single-use link that expires after 30 minutes.
 * The response never reveals whether an email address has an account.
 */
@Service
public class PasswordResetService {

    static final Duration VALID_FOR = Duration.ofMinutes(30);
    private static final int MAX_LINKS_PER_HOUR = 3;

    private final UserRepository users;
    private final PasswordResetRepository resets;
    private final PasswordEncoder passwordEncoder;
    private final MailService mail;
    private final AppProperties props;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(UserRepository users, PasswordResetRepository resets, PasswordEncoder passwordEncoder,
                                MailService mail, AppProperties props) {
        this.users = users;
        this.resets = resets;
        this.passwordEncoder = passwordEncoder;
        this.mail = mail;
        this.props = props;
    }

    public void requestReset(String rawEmail) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        users.findByEmail(email).ifPresent(user -> {
            Instant now = Instant.now();
            if (resets.countByUserIdAndCreatedAtAfter(user.getId(), now.minus(Duration.ofHours(1))) >= MAX_LINKS_PER_HOUR) {
                return;   // quietly limit how many emails one account can receive
            }
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            resets.save(new PasswordReset(user.getId(), sha256(token), now.plus(VALID_FOR)));
            String link = props.siteUrl() + "/reset-password?token=" + token;
            // Send in the background so the response time doesn't reveal whether the account exists.
            Thread.startVirtualThread(() -> mail.sendPasswordReset(user.getEmail(), user.getName(), link, VALID_FOR));
        });
    }

    @Transactional
    public AppUser reset(String token, String newPassword) {
        PasswordReset reset = resets.findByTokenHash(sha256(token.trim()))
                .filter(r -> r.isValidAt(Instant.now()))
                .orElseThrow(PasswordResetService::invalidLink);
        AppUser user = users.findById(reset.getUserId()).orElseThrow(PasswordResetService::invalidLink);
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        user.revokeSessions();                   // every existing session is signed out
        resets.deleteByUserId(user.getId());   // this link and any other open links stop working
        return user;
    }

    static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ApiException invalidLink() {
        return new ApiException(HttpStatus.BAD_REQUEST, "invalid_token", "This reset link is invalid or has expired. Request a new one.");
    }
}
