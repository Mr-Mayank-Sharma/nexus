package com.nexus.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.core.ai.AnthropicProvider;
import com.nexus.core.ai.LlmProvider;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** The Anthropic provider against a local server: headers, prompt caching marks, retry on overload, no retry on a bad request. */
class AnthropicProviderTest {

    static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void sendsCachedSystemAndToolsRetriesWhenBusyAndReadsUsage() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<JsonNode> body = new AtomicReference<>();
        AtomicReference<String> key = new AtomicReference<>(), version = new AtomicReference<>();
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        s.createContext("/v1/messages", ex -> {
            body.set(JSON.readTree(ex.getRequestBody().readAllBytes()));
            key.set(ex.getRequestHeaders().getFirst("x-api-key"));
            version.set(ex.getRequestHeaders().getFirst("anthropic-version"));
            byte[] b;
            int status;
            if (calls.incrementAndGet() == 1) {
                status = 529;
                b = "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}".getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("retry-after", "0");
            } else {
                status = 200;
                b = ("{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}],\"stop_reason\":\"end_turn\","
                        + "\"usage\":{\"input_tokens\":12,\"output_tokens\":3,\"cache_read_input_tokens\":900,\"cache_creation_input_tokens\":0}}").getBytes(StandardCharsets.UTF_8);
            }
            ex.sendResponseHeaders(status, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        s.start();
        try {
            AnthropicProvider p = new AnthropicProvider("sk-test", "http://127.0.0.1:" + s.getAddress().getPort(), 3);
            LlmProvider.Tool t1 = new LlmProvider.Tool("a", "first", JSON.readTree("{\"type\":\"object\",\"properties\":{}}"));
            LlmProvider.Tool t2 = new LlmProvider.Tool("b", "second", JSON.readTree("{\"type\":\"object\",\"properties\":{}}"));
            var messages = JSON.createArrayNode();
            messages.addObject().put("role", "user").put("content", "hi");
            LlmProvider.Response r = p.complete(new LlmProvider.Request("claude-sonnet-5-5", "You help.", List.of(t1, t2), messages, 256));
            assertEquals(2, calls.get(), "retried after the 529");
            assertEquals("ok", r.content().get(0).path("text").asText());
            assertEquals(900, r.usage().cacheReadTokens());
            assertEquals("sk-test", key.get());
            assertEquals("2023-06-01", version.get());
            JsonNode sent = body.get();
            assertEquals("ephemeral", sent.at("/system/0/cache_control/type").asText());
            assertTrue(sent.at("/tools/0/cache_control").isMissingNode(), "only the last tool carries the mark");
            assertEquals("ephemeral", sent.at("/tools/1/cache_control/type").asText());
            assertEquals("claude-sonnet-5-5", sent.path("model").asText());
        } finally {
            s.stop(0);
        }
    }

    @Test
    void aBadRequestIsNotRetriedAndNoKeyMeansNotAvailable() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        s.createContext("/v1/messages", ex -> {
            calls.incrementAndGet();
            byte[] b = "{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"max_tokens: must be positive\"}}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(400, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        s.start();
        try {
            AnthropicProvider p = new AnthropicProvider("sk-test", "http://127.0.0.1:" + s.getAddress().getPort(), 3);
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> p.complete(new LlmProvider.Request("m", "s", List.of(), JSON.createArrayNode(), 0)));
            assertTrue(e.getMessage().contains("max_tokens: must be positive"), e.getMessage());
            assertEquals(1, calls.get());
        } finally {
            s.stop(0);
        }
        assertFalse(new AnthropicProvider("", "http://x", 1).available());
    }
}
