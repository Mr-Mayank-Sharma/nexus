package com.nexus.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.core.ai.LlmProvider;
import com.nexus.core.ai.OllamaProvider;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** The Ollama provider against a local server: the Anthropic-shaped conversation goes out as Ollama chat and comes back. */
class OllamaProviderTest {

    static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void translatesToolCallsBothWays() throws Exception {
        // Step 1: a fake Ollama that records the request and answers with one tool call.
        AtomicReference<JsonNode> sentBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            sentBody.set(JSON.readTree(exchange.getRequestBody().readAllBytes()));
            byte[] reply = ("{\"message\":{\"role\":\"assistant\",\"content\":\"\",\"tool_calls\":[{\"function\":{\"name\":\"find_order\","
                    + "\"arguments\":{\"orderId\":\"10001\"}}}]},\"done_reason\":\"stop\",\"prompt_eval_count\":40,\"eval_count\":9}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, reply.length);
            exchange.getResponseBody().write(reply);
            exchange.close();
        });
        server.start();
        try {
            // Step 2: a conversation with an earlier tool call and its result.
            ArrayNode messages = JSON.createArrayNode();
            messages.addObject().put("role", "user").put("content", "Where is order 10000?");
            ObjectNode assistantTurn = messages.addObject().put("role", "assistant");
            ObjectNode earlierCall = assistantTurn.putArray("content").addObject().put("type", "tool_use").put("id", "toolu_1").put("name", "find_order");
            earlierCall.putObject("input").put("orderId", "10000");
            ObjectNode userTurn = messages.addObject().put("role", "user");
            userTurn.putArray("content").addObject().put("type", "tool_result").put("tool_use_id", "toolu_1").put("content", "{\"status\":\"SHIPPED\"}");
            LlmProvider.Tool findOrder = new LlmProvider.Tool("find_order", "Finds an order", JSON.readTree("{\"type\":\"object\",\"properties\":{\"orderId\":{\"type\":\"string\"}}}"));

            OllamaProvider provider = new OllamaProvider("http://127.0.0.1:" + server.getAddress().getPort());
            LlmProvider.Response response = provider.complete(new LlmProvider.Request("qwen2.5-coder:3b", "You help.", List.of(findOrder), messages, 300));

            // Step 3: what went out.
            JsonNode sent = sentBody.get();
            assertEquals("qwen2.5-coder:3b", sent.path("model").asText());
            assertFalse(sent.path("stream").asBoolean());
            assertEquals(300, sent.at("/options/num_predict").asInt());
            assertEquals("system", sent.at("/messages/0/role").asText());
            assertEquals("find_order", sent.at("/messages/2/tool_calls/0/function/name").asText());
            assertEquals("10000", sent.at("/messages/2/tool_calls/0/function/arguments/orderId").asText());
            assertEquals("tool", sent.at("/messages/3/role").asText());
            assertEquals("find_order", sent.at("/messages/3/tool_name").asText());
            assertEquals("function", sent.at("/tools/0/type").asText());

            // Step 4: what came back, in the Anthropic shape the agents read.
            assertEquals("tool_use", response.stopReason());
            JsonNode toolUse = response.content().get(0);
            assertEquals("tool_use", toolUse.path("type").asText());
            assertEquals("find_order", toolUse.path("name").asText());
            assertEquals("10001", toolUse.at("/input/orderId").asText());
            assertTrue(toolUse.path("id").asText().startsWith("toolu_local_"));
            assertEquals(40, response.usage().inputTokens());
            assertEquals(9, response.usage().outputTokens());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void readsAToolCallWrittenAsTextButNotOrdinaryJsonAnswers() throws Exception {
        LlmProvider.Tool orderCounts = new LlmProvider.Tool("order_counts", "Counts orders", JSON.readTree("{\"type\":\"object\",\"properties\":{}}"));
        String writtenCall = "```json\n{\"function\": \"order_counts\", \"arguments\": {\"sinceDays\": 30}}\n```";
        LlmProvider.Response call = answerWith(writtenCall, orderCounts);
        assertEquals("tool_use", call.stopReason());
        assertEquals("order_counts", call.content().get(0).path("name").asText());
        assertEquals(30, call.content().get(0).at("/input/sinceDays").asInt());

        LlmProvider.Response unquotedName = answerWith("Not JSON? {\"name\": order_counts, \"arguments\": {}}".substring(10), orderCounts);
        assertEquals("tool_use", unquotedName.stopReason(), "qwen2.5-coder:3b sometimes leaves the tool name unquoted");
        assertEquals("order_counts", unquotedName.content().get(0).path("name").asText());

        LlmProvider.Response callAfterProse = answerWith("To find out, I call the tool.\n```json\n{\"name\": \"order_counts\", \"arguments\": {}}\n```", orderCounts);
        assertEquals("tool_use", callAfterProse.stopReason(), "a sentence before the block is common with small models");

        LlmProvider.Response inlineCall = answerWith("You can use it: {\"name\": \"order_counts\", \"arguments\": {}} and then read the result.", orderCounts);
        assertEquals("tool_use", inlineCall.stopReason(), "a call written inline in a sentence");

        LlmProvider.Response toolTag = answerWith("I'll call it.\n<order_counts>\n{\"sinceDays\": 7}\n</order_counts>", orderCounts);
        assertEquals("tool_use", toolTag.stopReason(), "the tool's name as a tag around its arguments");
        assertEquals(7, toolTag.content().get(0).at("/input/sinceDays").asInt());

        LlmProvider.Response unknownTool = answerWith("{\"name\": \"drop_tables\", \"arguments\": {}}", orderCounts);
        assertEquals("end_turn", unknownTool.stopReason(), "a tool that was not offered stays text");
        assertEquals("text", unknownTool.content().get(0).path("type").asText());
    }

    /** The provider's reading of a fake Ollama reply whose text is the given content. */
    private static LlmProvider.Response answerWith(String replyText, LlmProvider.Tool tool) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            ObjectNode reply = JSON.createObjectNode();
            reply.putObject("message").put("role", "assistant").put("content", replyText);
            reply.put("done_reason", "stop");
            byte[] bytes = JSON.writeValueAsBytes(reply);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            ArrayNode messages = JSON.createArrayNode();
            messages.addObject().put("role", "user").put("content", "How many orders?");
            return new OllamaProvider("http://127.0.0.1:" + server.getAddress().getPort())
                    .complete(new LlmProvider.Request("qwen2.5-coder:3b", "You help.", List.of(tool), messages, 100));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void unreachableServerIsAClearError() {
        OllamaProvider provider = new OllamaProvider("http://127.0.0.1:9");
        ArrayNode messages = JSON.createArrayNode();
        messages.addObject().put("role", "user").put("content", "hi");
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> provider.complete(new LlmProvider.Request("qwen2.5-coder:3b", "", List.of(), messages, 10)));
        assertTrue(failure.getMessage().startsWith("Could not reach Ollama"), failure.getMessage());
        assertFalse(new OllamaProvider("").available());
    }
}
