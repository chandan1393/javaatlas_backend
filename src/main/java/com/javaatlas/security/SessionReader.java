package com.javaatlas.security;

import java.util.Optional;

import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * For public pages that show more to signed-in learners (course pages, lectures):
 * reads the session cookie if there is a valid one, and ignores it otherwise.
 */
@Component
public class SessionReader {

    private final JwtDecoder decoder;

    public SessionReader(JwtDecoder decoder) {
        this.decoder = decoder;
    }

    public Optional<Session> read(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        for (Cookie c : request.getCookies()) {
            if (TokenService.COOKIE.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                try {
                    return Optional.of(Session.of(decoder.decode(c.getValue())));
                } catch (JwtException | NumberFormatException e) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }
}
