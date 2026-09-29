package com.javaatlas.ai;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.javaatlas.AppProperties;
import com.javaatlas.common.ApiException;
import com.javaatlas.common.RateLimiter;
import com.javaatlas.analytics.AnalyticsService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * AI tools: the lesson tutor, code explainer, code reviewer, error explainer, modernizer and mock interviewer
 * (streamed), plus quiz and study-plan generators (JSON). Public, so protected by size limits,
 * a per-visitor hourly limit and a site-wide daily limit.
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);
    private static final int MAX_TOTAL_CHARS = 24_000;
    private static final Set<AiMode> CHAT_MODES = Set.of(AiMode.TUTOR, AiMode.EXPLAIN, AiMode.REVIEW, AiMode.ERROR, AiMode.MODERNIZE, AiMode.INTERVIEW);
    private static final Set<String> CONTEXT_KEYS = Set.of("lesson", "level", "target", "topic", "count", "goal", "hours", "weeks", "catalog");

    public record Message(
            @NotBlank @Pattern(regexp = "user|assistant") String role,
            @NotBlank @Size(max = 12_000, message = "That text is too long.") String content) {
    }

    public record ChatRequest(
            @NotBlank String mode,
            @Size(max = 24) List<@Valid Message> messages,
            Map<String, String> context) {
    }

    public record GenerateRequest(@NotBlank String mode, Map<String, String> context) {
    }

    public record GenerateResponse(String text, boolean truncated) {
    }

    private final AppProperties props;
    private final AnthropicClient client;
    private final RateLimiter limiter;
    private final AnalyticsService analytics;

    public AiController(AppProperties props, AnthropicClient client, RateLimiter limiter, AnalyticsService analytics) {
        this.props = props;
        this.client = client;
        this.limiter = limiter;
        this.analytics = analytics;
    }

    private void count(HttpServletRequest http, AiMode mode) {
        analytics.record(http, new AnalyticsService.Event("ai", null, null, null, null, mode.name().toLowerCase(java.util.Locale.ROOT), null, null));
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        boolean on = props.ai().enabled();
        return Map.of("enabled", on, "tools", on ? List.of("tutor", "explain", "review", "error", "modernize", "interview", "quiz", "plan") : List.of());
    }

    /** Streams the answer as server-sent events. */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> chat(@Valid @RequestBody ChatRequest req, HttpServletRequest http) {
        AiMode mode = AiMode.from(req.mode()).filter(CHAT_MODES::contains)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "invalid_request", "Unknown AI tool."));
        List<Message> messages = req.messages() == null ? List.of() : req.messages();
        if (mode == AiMode.INTERVIEW && messages.isEmpty()) {
            messages = List.of(new Message("user", "I'm ready. Please start the interview."));
        }
        if (messages.isEmpty() || !"user".equals(messages.getFirst().role()) || !"user".equals(messages.getLast().role())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_request", "The conversation must start and end with your message.");
        }
        Map<String, String> context = cleanContext(req.context());
        checkLimits(http, messages.stream().mapToInt(m -> m.content().length()).sum());

        count(http, mode);
        String system = mode.system(context);
        List<Map<String, String>> turns = messages.stream().map(m -> Map.of("role", m.role(), "content", m.content())).toList();
        StreamingResponseBody body = out -> {
            try {
                client.stream(system, turns, mode.maxTokens(), out);
            } catch (RestClientException e) {
                log.warn("AI stream failed: {}", e.getMessage());
                AnthropicClient.writeError(out, "The AI didn’t respond. Please try again.");
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("Cache-Control", "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(body);
    }

    /** Quiz and study-plan generators: the answer is JSON text for the page to show. */
    @PostMapping("/generate")
    public GenerateResponse generate(@Valid @RequestBody GenerateRequest req, HttpServletRequest http) {
        AiMode mode = AiMode.from(req.mode()).filter(AiMode::json)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "invalid_request", "Unknown AI tool."));
        Map<String, String> context = cleanContext(req.context());
        checkLimits(http, context.values().stream().mapToInt(String::length).sum());
        count(http, mode);
        try {
            AnthropicClient.Result r = client.complete(mode.system(context),
                    List.of(Map.of("role", "user", "content", "Create it now. Reply with the JSON only.")), mode.maxTokens());
            return new GenerateResponse(r.text(), r.truncated());
        } catch (RestClientException e) {
            log.warn("AI generate failed: {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "upstream_error", "The AI didn’t respond. Please try again.");
        }
    }

    private Map<String, String> cleanContext(Map<String, String> raw) {
        if (raw == null) return Map.of();
        return raw.entrySet().stream()
                .filter(e -> CONTEXT_KEYS.contains(e.getKey()) && e.getValue() != null)
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> {
                            int max = "catalog".equals(e.getKey()) ? 9_000 : 200;
                            String v = e.getValue().replaceAll("[\\r\\n]+", " ").strip();
                            if ("catalog".equals(e.getKey())) v = e.getValue().strip();
                            return v.length() > max ? v.substring(0, max) : v;
                        }));
    }

    private void checkLimits(HttpServletRequest http, int chars) {
        AppProperties.Ai ai = props.ai();
        if (!ai.enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ai_disabled", "AI tools aren’t available on this site.");
        }
        if (chars > MAX_TOTAL_CHARS) {
            throw new ApiException(HttpStatus.valueOf(413), "prompt_too_large", "That’s too much text. Shorten it and try again.");
        }
        if (!limiter.allow("ai:" + http.getRemoteAddr(), ai.perIpPerHour(), Duration.ofHours(1))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "rate_limited", "You’ve used the AI a lot this hour. Please try again later.");
        }
        if (!limiter.allow("ai:site", ai.dailyLimit(), Duration.ofDays(1))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "rate_limited", "The AI has reached today’s limit. It resets within 24 hours.");
        }
    }
}
