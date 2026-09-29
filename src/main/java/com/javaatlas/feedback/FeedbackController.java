package com.javaatlas.feedback;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.javaatlas.AppProperties;
import com.javaatlas.analytics.AnalyticsService;
import com.javaatlas.common.ApiException;
import com.javaatlas.common.RateLimiter;
import com.javaatlas.user.MailService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Public feedback: the feedback form and the "was this lesson helpful?" buttons. */
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private static final Logger log = LoggerFactory.getLogger(FeedbackController.class);
    static final Set<String> TYPES = Set.of("idea", "bug", "content", "praise", "other", "lesson");
    static final Set<String> REASONS = Set.of("unclear", "too_long", "too_short", "mistake", "code", "outdated", "other");

    public record NewFeedback(
            @NotBlank @Size(max = 16) String type,
            @Min(1) @Max(5) Integer rating,
            Boolean helpful,
            @Size(max = 5) List<@Size(max = 20) String> reasons,
            @Size(max = 2000, message = "Please keep it under 2000 characters.") String message,
            @Size(max = 80) String name,
            @Email(message = "That email doesn’t look right.") @Size(max = 254) String email,
            @Size(max = 300) @Pattern(regexp = "/.*") String page,
            @Size(max = 80) @Pattern(regexp = "[a-z0-9-]*") String lessonId,
            /** Hidden field: people never fill it in, many bots do. */
            @Size(max = 200) String website,
            /** Milliseconds the form was open before sending. */
            Long elapsedMs) {
    }

    private final JdbcClient db;
    private final RateLimiter limiter;
    private final MailService mail;
    private final AppProperties props;
    private final boolean notify;

    public FeedbackController(JdbcClient db, RateLimiter limiter, MailService mail, AppProperties props,
                              @Value("${app.feedback-notify:true}") boolean notify) {
        this.db = db;
        this.limiter = limiter;
        this.mail = mail;
        this.props = props;
        this.notify = notify;
    }

    @PostMapping
    public ResponseEntity<Void> submit(@Valid @RequestBody NewFeedback f, HttpServletRequest req) {
        if (!TYPES.contains(f.type())) throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_request", "Choose what your feedback is about.");
        boolean lesson = f.type().equals("lesson");
        String message = f.message() == null ? "" : f.message().strip();
        if (!lesson && message.length() < 5) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_request", "Please write a few words so we understand.");
        }
        if (lesson && (f.lessonId() == null || f.lessonId().isBlank() || f.helpful() == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_request", "Missing lesson.");
        }
        // Bots: pretend it worked so they don't retry, but store nothing.
        boolean tooFast = !lesson && f.elapsedMs() != null && f.elapsedMs() < 1500;
        if ((f.website() != null && !f.website().isBlank()) || tooFast) {
            log.info("Ignored a feedback submission that looked automated from {}", req.getRemoteAddr());
            return ResponseEntity.noContent().build();
        }
        String ip = req.getRemoteAddr();
        boolean ok = lesson
                ? limiter.allow("feedback-lesson:" + ip, 60, Duration.ofHours(1))
                : limiter.allow("feedback:" + ip, 8, Duration.ofHours(1)) && limiter.allow("feedback-day:" + ip, 30, Duration.ofDays(1));
        if (!ok) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "rate_limited", "Thanks! You’ve sent a lot of feedback already; please try again later.");

        String reasons = f.reasons() == null ? null
                : String.join(",", f.reasons().stream().filter(REASONS::contains).distinct().toList());
        db.sql("""
                INSERT INTO feedback (type, rating, helpful, reasons, message, name, email, page, lesson_id, device)
                VALUES (:type, :rating, :helpful, :reasons, :message, :name, :email, :page, :lesson, :device)
                """)
                .param("type", f.type())
                .param("rating", f.rating())
                .param("helpful", f.helpful())
                .param("reasons", reasons == null || reasons.isEmpty() ? null : reasons)
                .param("message", message.isEmpty() ? null : message)
                .param("name", blank(f.name()))
                .param("email", blank(f.email()))
                .param("page", f.page())
                .param("lesson", blank(f.lessonId()))
                .param("device", AnalyticsService.device(req.getHeader("User-Agent")))
                .update();

        if (notify && !message.isEmpty() && mail.enabled() && limiter.allow("feedback-mail", 30, Duration.ofHours(1))) {
            String to = props.admin().email();
            String text = """
                    New %s feedback%s

                    %s

                    Page: %s
                    From: %s

                    Open the inbox: %s/admin/feedback
                    """.formatted(f.type(), f.rating() == null ? "" : " (" + f.rating() + "/5)", message,
                    f.page() == null ? "-" : f.page(),
                    f.email() == null || f.email().isBlank() ? "anonymous" : (blank(f.name()) == null ? "" : f.name().strip() + " ") + "<" + f.email().strip() + ">",
                    props.siteUrl());
            String replyTo = blank(f.email());
            Thread.ofVirtual().start(() -> mail.sendPlain(to, "JavaAtlas feedback: " + f.type(), text, replyTo));
        }
        return ResponseEntity.noContent().build();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
