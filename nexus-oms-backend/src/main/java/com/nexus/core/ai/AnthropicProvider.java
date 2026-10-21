package com.nexus.core.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * The Anthropic Messages API. The system prompt and the tool list are marked for prompt caching (they repeat on every
 * call of an agent); 429, 500, 503 and 529 are retried with backoff, honouring retry-after. The key comes from
 * {@code ANTHROPIC_API_KEY} (property {@code nexus.ai.anthropic.api-key}) and is never stored in the database.
 */
@Component("coreAnthropicProvider")
public class AnthropicProvider implements LlmProvider {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String apiKey;
    private final String baseUrl;
    private final int attempts;

    public AnthropicProvider(@Value("${nexus.ai.anthropic.api-key:${ANTHROPIC_API_KEY:}}") String apiKey,
                             @Value("${nexus.ai.anthropic.base-url:https://api.anthropic.com}") String baseUrl,
                             @Value("${nexus.ai.anthropic.attempts:4}") int attempts) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.attempts = Math.max(1, attempts);
    }

    @Override
    public boolean available() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public Response complete(Request r) {
        if (!available()) throw new IllegalStateException("AI is not set up: ANTHROPIC_API_KEY is missing");
        ObjectNode body = body(r);
        String text;
        try {
            text = JSON.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        RuntimeException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/v1/messages")).timeout(Duration.ofSeconds(120))
                        .header("x-api-key", apiKey).header("anthropic-version", "2023-06-01").header("content-type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(text)).build(), HttpResponse.BodyHandlers.ofString());
                JsonNode n = res.body() == null || res.body().isBlank() ? JSON.createObjectNode() : JSON.readTree(res.body());
                if (res.statusCode() == 200) return parse(n);
                String message = n.at("/error/message").asText("HTTP " + res.statusCode());
                if (!retryable(res.statusCode())) throw new IllegalStateException("The AI provider refused the request: " + message);
                last = new IllegalStateException("The AI provider is busy: " + message);
                sleep(res.headers().firstValue("retry-after").map(v -> parseSeconds(v) * 1000).orElse(backoff(attempt)));
            } catch (IllegalStateException e) {
                if (e.getMessage() != null && e.getMessage().startsWith("The AI provider refused")) throw e;
                last = e;
            } catch (java.io.IOException e) {
                last = new IllegalStateException("Could not reach the AI provider: " + e.getMessage());
                sleep(backoff(attempt));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted");
            }
        }
        throw last == null ? new IllegalStateException("The AI provider did not answer") : last;
    }

    static ObjectNode body(Request r) {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", r.model());
        body.put("max_tokens", r.maxTokens());
        ArrayNode system = body.putArray("system");
        system.addObject().put("type", "text").put("text", r.system()).putObject("cache_control").put("type", "ephemeral");
        if (r.tools() != null && !r.tools().isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            for (int i = 0; i < r.tools().size(); i++) {
                Tool t = r.tools().get(i);
                ObjectNode n = tools.addObject();
                n.put("name", t.name());
                n.put("description", t.description());
                n.set("input_schema", t.inputSchema());
                if (i == r.tools().size() - 1) n.putObject("cache_control").put("type", "ephemeral");   // caches every tool before it too
            }
        }
        body.set("messages", r.messages());
        return body;
    }

    static Response parse(JsonNode n) {
        JsonNode u = n.path("usage");
        return new Response((ArrayNode) n.path("content"), n.path("stop_reason").asText(null), new Usage(u.path("input_tokens").asLong(),
                u.path("output_tokens").asLong(), u.path("cache_read_input_tokens").asLong(), u.path("cache_creation_input_tokens").asLong()));
    }

    static boolean retryable(int status) {
        return status == 429 || status == 500 || status == 502 || status == 503 || status == 529;
    }

    private static long backoff(int attempt) {
        return Math.min(30_000L, 1000L * (1L << (attempt - 1)));
    }

    private static long parseSeconds(String v) {
        try {
            return Math.min(60, Long.parseLong(v.trim()));
        } catch (NumberFormatException e) {
            return 2;
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
