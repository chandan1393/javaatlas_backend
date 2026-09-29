package com.javaatlas.security;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.javaatlas.AppProperties;
import com.javaatlas.user.AppUser;

/** Issues the session cookie that carries a signed JWT. */
@Service
public class TokenService {

    public static final String COOKIE = "ja_session";

    private final JwtEncoder encoder;
    private final AppProperties props;

    public TokenService(JwtEncoder encoder, AppProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    /** A learner session. Admin accounts get sessions only through {@link #adminCookie}. */
    public ResponseCookie sessionCookie(AppUser user) {
        if (user.isAdmin()) {
            throw new IllegalStateException("Admin sessions must be issued by the admin sign-in");
        }
        return cookie(user, props.jwt().ttl(), false);
    }

    /** An admin session: shorter-lived, and marked with whether the second factor was checked. */
    public ResponseCookie adminCookie(AppUser user, boolean mfa) {
        return cookie(user, Duration.ofHours(props.admin().sessionHours()), mfa);
    }

    private ResponseCookie cookie(AppUser user, Duration ttl, boolean mfa) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("javaatlas")
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("name", user.getName())
                .claim("roles", List.of(user.getRole()))
                .claim("tv", user.getTokenVersion())
                .claim("mfa", mfa)
                .build();
        String token = encoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return base(token).maxAge(ttl).build();
    }

    public ResponseCookie clearedCookie() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE, value)
                .httpOnly(true)
                .secure(props.cookieSecure())
                .sameSite("Strict")
                .path("/");
    }
}
