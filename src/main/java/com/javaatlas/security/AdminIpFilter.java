package com.javaatlas.security;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.javaatlas.AppProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Optional: when ADMIN_ALLOWED_IPS is set (IPs or CIDR ranges, comma-separated), the admin sign-in and admin API
 * answer only from those addresses. Everyone else gets a plain 404, so the admin area doesn't appear to exist.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 11)
public class AdminIpFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AdminIpFilter.class);
    private final List<IpAddressMatcher> allowed;

    public AdminIpFilter(AppProperties props) {
        this.allowed = props.admin().allowedIps().stream().map(IpAddressMatcher::new).toList();
        if (!allowed.isEmpty()) log.info("Admin area limited to {}", props.admin().allowedIps());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return allowed.isEmpty() || !(uri.startsWith("/api/admin/") || uri.startsWith("/api/admin-auth/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        if (allowed.stream().noneMatch(m -> m.matches(ip))) {
            log.warn("Blocked admin request from {} to {}", ip, request.getRequestURI());
            response.setStatus(404);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"status\":404,\"code\":\"request_error\",\"detail\":\"Not found.\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
