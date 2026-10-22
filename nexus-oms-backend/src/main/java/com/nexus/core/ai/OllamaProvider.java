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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A model served by Ollama on this machine or the local network ({@code /api/chat}), for development and testing
 * without a cloud key. The agents speak the Anthropic message shape; this class translates it to Ollama's chat shape
 * and back:
 * <ul>
 *   <li>the system prompt becomes a {@code system} message;</li>
 *   <li>assistant {@code tool_use} blocks become {@code tool_calls}, and {@code tool_result} blocks become
 *       {@code tool} messages;</li>
 *   <li>the reply's tool calls become {@code tool_use} blocks with fresh ids. Small models (qwen2.5-coder) often
 *       write the call as JSON text instead; a reply that is only such a call, naming an offered tool, is read as one.</li>
 * </ul>
 * Nothing leaves the machine unless {@code nexus.ai.ollama.base-url} points elsewhere.
 */
@Component("coreOllamaProvider")
public class OllamaProvider implements LlmProvider {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    /** Small local models on a laptop can take minutes on a long prompt. */
    static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(5);

    private static final ObjectMapper JSON = new ObjectMapper();
    /** A JSON object inside ```json fences or <tool_call> tags, or the whole reply. */
    private static final Pattern WRAPPED_JSON = Pattern.compile("(?s)^\\s*(?:```(?:json)?|<tool_call>)?\\s*(\\{.*\\})\\s*(?:```|</tool_call>)?\\s*$");
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    private final String baseUrl;

    public OllamaProvider(@Value("${nexus.ai.ollama.base-url:http://localhost:11434}") String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    @Override
    public boolean available() {
        return !baseUrl.isBlank();
    }

    @Override
    public Response complete(Request request) {
        if (!available()) {
            throw new IllegalStateException("AI is not set up: nexus.ai.ollama.base-url is empty");
        }
        String requestJson;
        try {
            requestJson = JSON.writeValueAsString(requestBody(request));
        } catch (JsonProcessingException notSerializable) {
            throw new IllegalStateException(notSerializable);
        }
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/api/chat"))
                .timeout(REQUEST_TIMEOUT)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            JsonNode body = response.body() == null || response.body().isBlank() ? JSON.createObjectNode() : JSON.readTree(response.body());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("The local model refused the request: " + body.path("error").asText("HTTP " + response.statusCode()));
            }
            Set<String> offeredTools = request.tools() == null ? Set.of()
                    : request.tools().stream().map(Tool::name).collect(Collectors.toSet());
            return parseResponse(body, offeredTools);
        } catch (IOException unreachable) {
            throw new IllegalStateException("Could not reach Ollama at " + baseUrl + ": " + unreachable.getMessage());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted");
        }
    }

    /** The {@code /api/chat} body: system prompt, translated conversation, tools, no streaming. */
    static ObjectNode requestBody(Request request) {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", request.model());
        body.put("stream", false);
        body.putObject("options").put("num_predict", request.maxTokens());

        ArrayNode messages = body.putArray("messages");
        if (request.system() != null && !request.system().isBlank()) {
            messages.addObject().put("role", "system").put("content", request.system());
        }
        Map<String, String> toolNamesById = new HashMap<>();
        for (JsonNode message : request.messages()) {
            addMessage(messages, message, toolNamesById);
        }

        if (request.tools() != null && !request.tools().isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            for (Tool tool : request.tools()) {
                ObjectNode function = tools.addObject().put("type", "function").putObject("function");
                function.put("name", tool.name());
                function.put("description", tool.description());
                function.set("parameters", tool.inputSchema());
            }
        }
        return body;
    }

    /** One Anthropic-shaped message as one or more Ollama messages. */
    private static void addMessage(ArrayNode messages, JsonNode message, Map<String, String> toolNamesById) {
        String role = message.path("role").asText();
        JsonNode content = message.path("content");
        if (content.isTextual()) {
            messages.addObject().put("role", role).put("content", content.asText());
            return;
        }

        // Step 1: text blocks are joined; tool calls and tool results are collected.
        StringBuilder text = new StringBuilder();
        ArrayNode toolCalls = JSON.createArrayNode();
        ArrayNode toolResults = JSON.createArrayNode();
        for (JsonNode block : content) {
            switch (block.path("type").asText()) {
                case "text" -> text.append(text.isEmpty() ? "" : "\n").append(block.path("text").asText());
                case "tool_use" -> {
                    toolNamesById.put(block.path("id").asText(), block.path("name").asText());
                    ObjectNode function = toolCalls.addObject().putObject("function");
                    function.put("name", block.path("name").asText());
                    function.set("arguments", block.path("input").isMissingNode() ? JSON.createObjectNode() : block.path("input"));
                }
                case "tool_result" -> toolResults.addObject()
                        .put("role", "tool")
                        .put("tool_name", toolNamesById.getOrDefault(block.path("tool_use_id").asText(), ""))
                        .put("content", resultText(block.path("content")));
                default -> {
                    // Other block kinds (images, thinking) are not sent to the local model.
                }
            }
        }

        // Step 2: tool results answer the previous turn's calls, so they come first; then any text or calls.
        messages.addAll(toolResults);
        if (!text.isEmpty() || !toolCalls.isEmpty()) {
            ObjectNode translated = messages.addObject().put("role", role).put("content", text.toString());
            if (!toolCalls.isEmpty()) {
                translated.set("tool_calls", toolCalls);
            }
        }
    }

    /** A tool result's content: plain text, or the text of its text blocks. */
    private static String resultText(JsonNode content) {
        if (content.isTextual()) {
            return content.asText();
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode block : content) {
            text.append(block.path("text").asText());
        }
        return text.toString();
    }

    /** The reply as Anthropic content blocks: its text, then a {@code tool_use} block per tool call. */
    static Response parseResponse(JsonNode body, Set<String> offeredTools) {
        JsonNode message = body.path("message");
        ArrayNode content = JSON.createArrayNode();
        String text = message.path("content").asText("");
        JsonNode toolCalls = message.path("tool_calls");
        boolean anyToolCall = false;

        // Step 1: a reply that is only a tool call written as text counts as that call.
        Optional<JsonNode> callInText = toolCalls.isEmpty() ? toolCallWrittenAsText(text, offeredTools) : Optional.empty();
        if (callInText.isPresent()) {
            addToolUse(content, callInText.get());
            anyToolCall = true;
        } else if (!text.isBlank()) {
            content.addObject().put("type", "text").put("text", text);
        }

        // Step 2: proper tool calls.
        for (JsonNode call : toolCalls) {
            addToolUse(content, call.path("function"));
            anyToolCall = true;
        }

        String stopReason = anyToolCall ? "tool_use" : ("length".equals(body.path("done_reason").asText()) ? "max_tokens" : "end_turn");
        Usage usage = new Usage(body.path("prompt_eval_count").asLong(), body.path("eval_count").asLong(), 0, 0);
        return new Response(content, stopReason, usage);
    }

    /** One {@code tool_use} block from a function call ({@code name} and {@code arguments}). */
    private static void addToolUse(ArrayNode content, JsonNode function) {
        ObjectNode toolUse = content.addObject()
                .put("type", "tool_use")
                .put("id", "toolu_local_" + UUID.randomUUID().toString().replace("-", ""))
                .put("name", function.path("name").asText());
        toolUse.set("input", argumentsObject(function.path("arguments")));
    }

    /**
     * The call when the whole reply is one JSON object naming an offered tool, as {"name"|"function": ...,
     * "arguments"|"parameters": {...}}; anything else is an ordinary answer.
     */
    private static Optional<JsonNode> toolCallWrittenAsText(String text, Set<String> offeredTools) {
        Matcher wrapped = WRAPPED_JSON.matcher(text);
        if (offeredTools.isEmpty() || !wrapped.matches()) {
            return Optional.empty();
        }
        try {
            JsonNode written = JSON.readTree(wrapped.group(1));
            String name = written.path("name").asText(written.path("function").asText(""));
            if (!offeredTools.contains(name)) {
                return Optional.empty();
            }
            JsonNode arguments = written.has("arguments") ? written.path("arguments") : written.path("parameters");
            ObjectNode function = JSON.createObjectNode().put("name", name);
            function.set("arguments", arguments.isMissingNode() ? JSON.createObjectNode() : arguments);
            return Optional.of(function);
        } catch (JsonProcessingException notJson) {
            return Optional.empty();
        }
    }

    /** Tool arguments as an object; some models send them as a JSON string. */
    private static JsonNode argumentsObject(JsonNode arguments) {
        if (arguments.isObject()) {
            return arguments;
        }
        if (arguments.isTextual()) {
            try {
                JsonNode parsed = JSON.readTree(arguments.asText());
                if (parsed != null && parsed.isObject()) {
                    return parsed;
                }
            } catch (JsonProcessingException notJson) {
                // falls through to an empty input; the tool then reports what is missing
            }
        }
        return JSON.createObjectNode();
    }
}
