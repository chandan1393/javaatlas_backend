package com.javaatlas.security;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import com.javaatlas.AppProperties;
import com.javaatlas.user.AppUser;
import com.javaatlas.user.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Runs after the session JWT is verified. Rejects sessions that were revoked (password changed,
 * "sign out everywhere"), whose role changed, or whose account was deleted; and keeps admins out of
 * the admin API until they've passed two-factor sign-in (when ADMIN_REQUIRE_2FA is on).
 */
public class SessionValidationFilter extends OncePerRequestFilter {

    private final UserRepository users;
    private final TokenService tokens;
    private final AppProperties props;

    public SessionValidationFilter(UserRepository users, TokenService tokens, AppProperties props) {
        this.users = users;
        this.tokens = tokens;
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            Jwt jwt = token.getToken();
            AppUser user = users.findById(Long.parseLong(jwt.getSubject())).orElse(null);
            List<String> roles = jwt.getClaimAsStringList("roles");
            Number tv = jwt.getClaim("tv");
            boolean valid = user != null
                    && tv != null && tv.intValue() == user.getTokenVersion()
                    && roles != null && roles.contains(user.getRole());
            if (!valid) {
                SecurityContextHolder.clearContext();
                response.addHeader(HttpHeaders.SET_COOKIE, tokens.clearedCookie().toString());
                problem(response, 401, "session_expired", "Your session has ended. Please sign in again.");
                return;
            }
            String uri = request.getRequestURI();
            boolean adminApi = uri.startsWith("/api/admin/") && !uri.startsWith("/api/admin/security/");
            if (adminApi && user.isAdmin() && props.admin().require2fa() && !Objects.equals(jwt.getClaimAsBoolean("mfa"), Boolean.TRUE)) {
                problem(response, 403, "mfa_required", "Set up two-factor sign-in to use the admin area.");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static void problem(HttpServletResponse response, int status, String code, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"status\":" + status + ",\"code\":\"" + code + "\",\"detail\":\"" + detail + "\"}");
    }
}
