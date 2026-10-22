package com.nexus.core.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
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
    private static final Usage ANSWER_USAGE = new Usage(100, 50, 0, 0);
    private static final Usage TOOL_TURN_USAGE = new Usage(100, 20, 0, 0);

    /** One tool call in a turn: the tool's name and its input as JSON text. */
    public record ToolUse(String name, String jsonInput) {
    }

    /**
     * One turn: fixed tool calls, tool calls built from the conversation so far, or a final answer built from it.
     * Exactly one of the three is used: the answer when it is set, else the dynamic calls when set, else the fixed ones.
     */
    public record Turn(List<ToolUse> toolCalls, Function<ArrayNode, String> answer, Function<ArrayNode, String[]> dynamicTools) {

        /** Tool calls built from the conversation so far, as name, JSON input, name, JSON input...: use an id a tool returned. */
        public static Turn toolsFrom(Function<ArrayNode, String[]> namesAndInputs) {
            return new Turn(List.of(), null, namesAndInputs);
        }

        /** Fixed tool calls, as name, JSON input, name, JSON input... */
        public static Turn tools(String... namesAndInputs) {
            return new Turn(toolUses(namesAndInputs), null, null);
        }

        public static Turn answer(Function<ArrayNode, String> answer) {
            return new Turn(List.of(), answer, null);
        }
    }

    private final List<Turn> turns;
    private final List<Request> requests = new ArrayList<>();
    private int nextTurn;

    public ScriptedProvider(List<Turn> turns) {
        this.turns = turns;
    }

    @Override
    public boolean available() {
        return true;
    }

    /** Every request the agent sent, with a copy of the conversation as it was then. */
    public List<Request> requests() {
        return requests;
    }

    @Override
    public Response complete(Request request) {
        requests.add(new Request(request.model(), request.system(), request.tools(), request.messages().deepCopy(), request.maxTokens()));
        if (nextTurn >= turns.size()) {
            throw new IllegalStateException("The script has no more turns");
        }
        Turn turn = turns.get(nextTurn++);
        ArrayNode content = JSON.createArrayNode();

        if (turn.answer() != null) {
            content.addObject().put("type", "text").put("text", turn.answer().apply(request.messages()));
            return new Response(content, "end_turn", ANSWER_USAGE);
        }

        List<ToolUse> toolUses = turn.dynamicTools() != null ? toolUses(turn.dynamicTools().apply(request.messages())) : turn.toolCalls();
        int callNumber = 0;
        for (ToolUse toolUse : toolUses) {
            ObjectNode block = content.addObject()
                    .put("type", "tool_use")
                    .put("id", "toolu_" + nextTurn + "_" + callNumber++)
                    .put("name", toolUse.name());
            block.set("input", parse(toolUse.jsonInput()));
        }
        return new Response(content, "tool_use", TOOL_TURN_USAGE);
    }

    /** The text of every tool result sent back so far, joined: what the model would have seen. */
    public static String toolResults(ArrayNode messages) {
        StringBuilder results = new StringBuilder();
        for (JsonNode message : messages) {
            if (!message.path("content").isArray()) {
                continue;
            }
            for (JsonNode block : message.path("content")) {
                if ("tool_result".equals(block.path("type").asText())) {
                    results.append(block.path("content").asText()).append('\n');
                }
            }
        }
        return results.toString();
    }

    private static List<ToolUse> toolUses(String[] namesAndInputs) {
        List<ToolUse> toolUses = new ArrayList<>();
        for (int index = 0; index + 1 < namesAndInputs.length; index += 2) {
            toolUses.add(new ToolUse(namesAndInputs[index], namesAndInputs[index + 1]));
        }
        return toolUses;
    }

    private static JsonNode parse(String json) {
        try {
            return JSON.readTree(json);
        } catch (JsonProcessingException badJson) {
            throw new IllegalArgumentException(badJson);
        }
    }
}
