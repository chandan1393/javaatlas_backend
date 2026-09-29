package com.javaatlas.security;

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.javaatlas.AppProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Extra cross-site request protection on top of the SameSite=Strict cookie: requests that change data
 * must come from this site (or an allowed origin). Server-to-server calls such as the Razorpay webhook
 * send no Origin header and aren't affected.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class OriginCheckFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private final Set<String> allowed = new HashSet<>();

    public OriginCheckFilter(AppProperties props) {
        allowed.add(origin(props.siteUrl()));
        props.corsOrigins().forEach(o -> allowed.add(origin(o)));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return SAFE.contains(request.getMethod()) || !request.getRequestURI().startsWith("/api/")
                || request.getRequestURI().equals("/api/payments/webhook");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String originHeader = request.getHeader("Origin");
        if (originHeader != null && !originHeader.isBlank() && !"null".equals(originHeader)) {
            String origin = origin(originHeader);
            String host = request.getServerName().toLowerCase(Locale.ROOT);
            String originHost = hostOf(originHeader);
            if (!allowed.contains(origin) && !host.equals(originHost)) {
                response.setStatus(403);
                response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                response.getWriter().write("{\"status\":403,\"code\":\"bad_origin\",\"detail\":\"This request isn't allowed from another website.\"}");
                return;
            }
        } else if ("null".equals(originHeader)) {
            response.setStatus(403);
            return;
        }
        chain.doFilter(request, response);
    }

    static String origin(String url) {
        try {
            URI u = URI.create(url.trim());
            String scheme = u.getScheme() == null ? "https" : u.getScheme().toLowerCase(Locale.ROOT);
            int port = u.getPort();
            boolean defaultPort = port == -1 || (port == 443 && scheme.equals("https")) || (port == 80 && scheme.equals("http"));
            return scheme + "://" + String.valueOf(u.getHost()).toLowerCase(Locale.ROOT) + (defaultPort ? "" : ":" + port);
        } catch (IllegalArgumentException e) {
            return url.trim().toLowerCase(Locale.ROOT);
        }
    }

    private static String hostOf(String url) {
        try {
            return String.valueOf(URI.create(url.trim()).getHost()).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
