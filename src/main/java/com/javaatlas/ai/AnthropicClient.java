package com.javaatlas.ai;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.javaatlas.AppProperties;

/** Calls the Claude Messages API. The API key never leaves the server. */
@Component
public class AnthropicClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicClient.class);
    /** Streaming events passed through to the browser; everything else is dropped. */
    private static final Set<String> FORWARD = Set.of("content_block_delta", "message_delta", "message_stop", "error");

    public record Result(String text, boolean truncated) {
    }

    private final AppProperties.Ai cfg;
    private final RestClient http;

    public AnthropicClient(AppProperties props) {
        this.cfg = props.ai();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(90));
        this.http = RestClient.builder()
                .baseUrl("https://api.anthropic.com")
                .requestFactory(requestFactory)
                .defaultHeader("anthropic-version", "2023-06-01")
                .build();
    }

    /** A complete (non-streaming) answer, used for JSON tools such as quizzes and study plans. */
    public Result complete(String system, List<Map<String, String>> messages, int maxTokens) {
        Map<String, Object> body = Map.of(
                "model", cfg.model(),
                "max_tokens", maxTokens,
                "system", system,
                "messages", messages);
        Map<?, ?> res = http.post()
                .uri("/v1/messages")
                .header("x-api-key", cfg.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
        StringBuilder text = new StringBuilder();
        if (res != null && res.get("content") instanceof List<?> parts) {
            for (Object part : parts) {
                if (part instanceof Map<?, ?> block && "text".equals(block.get("type")) && block.get("text") != null) {
                    text.append(block.get("text"));
                }
            }
        }
        boolean truncated = res != null && "max_tokens".equals(res.get("stop_reason"));
        return new Result(text.toString(), truncated);
    }

    /**
     * Streams an answer as server-sent events. Claude's own events are forwarded as they arrive
     * (text deltas, the stop reason and errors), so the browser can show the answer word by word.
     */
    public void stream(String system, List<Map<String, String>> messages, int maxTokens, OutputStream out) throws IOException {
        Map<String, Object> body = Map.of(
                "model", cfg.model(),
                "max_tokens", maxTokens,
                "system", system,
                "messages", messages,
                "stream", true);
        http.post()
                .uri("/v1/messages")
                .header("x-api-key", cfg.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .body(body)
                .exchange((request, response) -> {
                    if (response.getStatusCode().isError()) {
                        log.warn("AI request failed with status {}", response.getStatusCode().value());
                        writeError(out, response.getStatusCode().value() == 429
                                ? "The AI is busy right now. Please try again in a minute."
                                : "The AI didn’t respond. Please try again.");
                        return null;
                    }
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                        String line;
                        String event = null;
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("event:")) {
                                event = line.substring(6).trim();
                            } else if (line.startsWith("data:") && event != null && FORWARD.contains(event)) {
                                out.write(("event: " + event + "\n" + line + "\n\n").getBytes(StandardCharsets.UTF_8));
                                out.flush();
                            }
                        }
                    }
                    return null;
                });
    }

    static void writeError(OutputStream out, String message) throws IOException {
        String safe = message.replace("\\", "\\\\").replace("\"", "\\\"");
        out.write(("event: error\ndata: {\"type\":\"error\",\"error\":{\"message\":\"" + safe + "\"}}\n\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
}
