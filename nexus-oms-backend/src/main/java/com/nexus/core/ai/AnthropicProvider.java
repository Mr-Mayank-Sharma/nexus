package com.nexus.core.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;

/**
 * The Anthropic Messages API.
 * <ul>
 *   <li>The system prompt and the tool list are marked for prompt caching: they repeat on every call of an agent.</li>
 *   <li>429, 500, 502, 503 and 529 are retried with backoff, honouring retry-after; any other refusal is final.</li>
 *   <li>The key comes from {@code ANTHROPIC_API_KEY} (property {@code nexus.ai.anthropic.api-key}) and is never stored
 *       in the database.</li>
 * </ul>
 */
@Component("coreAnthropicProvider")
public class AnthropicProvider implements LlmProvider {

    static final String API_VERSION = "2023-06-01";
    static final Set<Integer> RETRYABLE_STATUSES = Set.of(429, 500, 502, 503, 529);
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);
    static final long MAX_BACKOFF_MILLIS = 30_000;
    static final long MAX_RETRY_AFTER_SECONDS = 60;
    static final long UNREADABLE_RETRY_AFTER_SECONDS = 2;

    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    private final String apiKey;
    private final String baseUrl;
    private final int maxAttempts;

    public AnthropicProvider(@Value("${nexus.ai.anthropic.api-key:${ANTHROPIC_API_KEY:}}") String apiKey,
                             @Value("${nexus.ai.anthropic.base-url:https://api.anthropic.com}") String baseUrl,
                             @Value("${nexus.ai.anthropic.attempts:4}") int maxAttempts) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    /** A refusal that retrying won't change (bad request, bad key). */
    static final class Refused extends IllegalStateException {
        Refused(String message) {
            super(message);
        }
    }

    @Override
    public boolean available() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public Response complete(Request request) {
        if (!available()) {
            throw new IllegalStateException("AI is not set up: ANTHROPIC_API_KEY is missing");
        }
        String requestJson;
        try {
            requestJson = JSON.writeValueAsString(requestBody(request));
        } catch (JsonProcessingException notSerializable) {
            throw new IllegalStateException(notSerializable);
        }

        IllegalStateException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(newHttpRequest(requestJson), HttpResponse.BodyHandlers.ofString());
                JsonNode body = response.body() == null || response.body().isBlank() ? JSON.createObjectNode() : JSON.readTree(response.body());
                if (response.statusCode() == 200) {
                    return parseResponse(body);
                }
                String message = body.at("/error/message").asText("HTTP " + response.statusCode());
                if (!retryable(response.statusCode())) {
                    throw new Refused("The AI provider refused the request: " + message);
                }
                lastFailure = new IllegalStateException("The AI provider is busy: " + message);
                long waitMillis = response.headers().firstValue("retry-after")
                        .map(retryAfter -> retryAfterSeconds(retryAfter) * 1000)
                        .orElse(backoffMillis(attempt));
                sleep(waitMillis);
            } catch (Refused refused) {
                throw refused;
            } catch (IOException unreachable) {
                lastFailure = new IllegalStateException("Could not reach the AI provider: " + unreachable.getMessage());
                sleep(backoffMillis(attempt));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted");
            }
        }
        throw lastFailure == null ? new IllegalStateException("The AI provider did not answer") : lastFailure;
    }

    private HttpRequest newHttpRequest(String requestJson) {
        return HttpRequest.newBuilder(URI.create(baseUrl + "/v1/messages"))
                .timeout(REQUEST_TIMEOUT)
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();
    }

    /** The Messages API body. Cache marks on the system prompt and on the last tool cache everything up to them. */
    static ObjectNode requestBody(Request request) {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", request.model());
        body.put("max_tokens", request.maxTokens());
        ArrayNode system = body.putArray("system");
        system.addObject().put("type", "text").put("text", request.system()).putObject("cache_control").put("type", "ephemeral");

        if (request.tools() != null && !request.tools().isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            for (int index = 0; index < request.tools().size(); index++) {
                Tool tool = request.tools().get(index);
                ObjectNode toolNode = tools.addObject();
                toolNode.put("name", tool.name());
                toolNode.put("description", tool.description());
                toolNode.set("input_schema", tool.inputSchema());
                boolean lastTool = index == request.tools().size() - 1;
                if (lastTool) {
                    toolNode.putObject("cache_control").put("type", "ephemeral");   // caches every tool before it too
                }
            }
        }
        body.set("messages", request.messages());
        return body;
    }

    static Response parseResponse(JsonNode body) {
        JsonNode usage = body.path("usage");
        Usage tokens = new Usage(usage.path("input_tokens").asLong(), usage.path("output_tokens").asLong(),
                usage.path("cache_read_input_tokens").asLong(), usage.path("cache_creation_input_tokens").asLong());
        return new Response((ArrayNode) body.path("content"), body.path("stop_reason").asText(null), tokens);
    }

    static boolean retryable(int status) {
        return RETRYABLE_STATUSES.contains(status);
    }

    /** 1, 2, 4, 8... seconds, at most 30. */
    private static long backoffMillis(int attempt) {
        return Math.min(MAX_BACKOFF_MILLIS, 1000L * (1L << (attempt - 1)));
    }

    /** The retry-after header in seconds, at most 60; 2 when it is not a number. */
    private static long retryAfterSeconds(String headerValue) {
        try {
            return Math.min(MAX_RETRY_AFTER_SECONDS, Long.parseLong(headerValue.trim()));
        } catch (NumberFormatException notSeconds) {
            return UNREADABLE_RETRY_AFTER_SECONDS;
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
