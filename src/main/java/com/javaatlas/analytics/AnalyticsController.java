package com.javaatlas.analytics;

import java.time.Duration;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.javaatlas.common.RateLimiter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Receives anonymous page views and events from the website. Always answers 204 so it never affects visitors. */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private static final Set<String> TYPES = Set.of("pageview", "search", "lesson_complete", "checkout_start", "signup_start");

    public record Collect(
            @NotBlank @Size(max = 24) String type,
            @Size(max = 300) @Pattern(regexp = "/.*") String path,
            @Size(max = 120) @Pattern(regexp = "[a-zA-Z0-9.-]*") String ref,
            @Size(max = 120) String query,
            @Min(0) @Max(10_000) Integer results,
            @Size(max = 60) String source,
            @Size(max = 80) String campaign,
            @Min(0) @Max(10_000) Integer width) {
    }

    private final AnalyticsService analytics;
    private final RateLimiter limiter;

    public AnalyticsController(AnalyticsService analytics, RateLimiter limiter) {
        this.analytics = analytics;
        this.limiter = limiter;
    }

    @PostMapping("/collect")
    public ResponseEntity<Void> collect(@Valid @RequestBody Collect c, HttpServletRequest req) {
        boolean countable = TYPES.contains(c.type())
                && (c.path() == null || !c.path().startsWith("/admin"))
                && limiter.allow("analytics:" + req.getRemoteAddr(), 600, Duration.ofHours(1));
        if (countable) {
            String device = c.width() == null ? null : c.width() < 768 ? "mobile" : c.width() < 1100 ? "tablet" : "desktop";
            analytics.record(req, new AnalyticsService.Event(c.type(), stripQuery(c.path()), c.ref(), c.query(), c.results(), c.source(), c.campaign(), device));
        }
        return ResponseEntity.noContent().build();
    }

    private static String stripQuery(String path) {
        if (path == null) return null;
        int q = path.indexOf('?');
        int h = path.indexOf('#');
        int cut = q < 0 ? h : h < 0 ? q : Math.min(q, h);
        return cut < 0 ? path : path.substring(0, cut);
    }
}
