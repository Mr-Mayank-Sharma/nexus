package com.nexus.core.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A model that plays a fixed script, for tests and the offline evaluation: each turn calls tools or answers. The final
 * answer can be built from what the tools returned, so the evaluation checks the tools and the plumbing without a
 * network call. It also keeps every request, so a test can check what would have left the OMS (masking, caching).
 */
public class ScriptedProvider implements LlmProvider {

    static final ObjectMapper JSON = new ObjectMapper();

    /** One turn: tool calls (name and input), or a final answer built from the conversation so far. */
    public record Turn(List<String[]> toolCalls, Function<ArrayNode, String> answer) {
        public static Turn tools(String... nameAndJsonInput) {
            List<String[]> calls = new ArrayList<>();
            for (int i = 0; i < nameAndJsonInput.length; i += 2) calls.add(new String[]{nameAndJsonInput[i], nameAndJsonInput[i + 1]});
            return new Turn(calls, null);
        }

        public static Turn answer(Function<ArrayNode, String> answer) {
            return new Turn(List.of(), answer);
        }
    }

    private final List<Turn> turns;
    private final List<Request> requests = new ArrayList<>();
    private int next;

    public ScriptedProvider(List<Turn> turns) {
        this.turns = turns;
    }

    @Override
    public boolean available() {
        return true;
    }

    public List<Request> requests() {
        return requests;
    }

    @Override
    public Response complete(Request r) {
        requests.add(new Request(r.model(), r.system(), r.tools(), r.messages().deepCopy(), r.maxTokens()));
        if (next >= turns.size()) throw new IllegalStateException("The script has no more turns");
        Turn t = turns.get(next++);
        ArrayNode content = JSON.createArrayNode();
        if (t.answer() != null) {
            content.addObject().put("type", "text").put("text", t.answer().apply(r.messages()));
            return new Response(content, "end_turn", new Usage(100, 50, 0, 0));
        }
        int n = 0;
        for (String[] c : t.toolCalls()) {
            ObjectNode use = content.addObject().put("type", "tool_use").put("id", "toolu_" + next + "_" + n++).put("name", c[0]);
            try {
                use.set("input", JSON.readTree(c[1]));
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
        }
        return new Response(content, "tool_use", new Usage(100, 20, 0, 0));
    }

    /** The text of every tool result sent back so far, joined: what the model would have seen. */
    public static String toolResults(ArrayNode messages) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode m : messages) {
            if (!m.path("content").isArray()) continue;
            for (JsonNode b : m.path("content")) if ("tool_result".equals(b.path("type").asText())) sb.append(b.path("content").asText()).append('\n');
        }
        return sb.toString();
    }
}
