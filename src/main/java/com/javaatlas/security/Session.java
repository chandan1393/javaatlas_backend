package com.javaatlas.security;

import java.util.List;

import org.springframework.security.oauth2.jwt.Jwt;

import com.javaatlas.user.AppUser;

/** The signed-in learner, taken from the session JWT. */
public record Session(long userId, boolean admin, boolean mfa) {

    public static Session of(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        Boolean mfa = jwt.getClaimAsBoolean("mfa");
        return new Session(Long.parseLong(jwt.getSubject()), roles != null && roles.contains(AppUser.ROLE_ADMIN), Boolean.TRUE.equals(mfa));
    }
}
