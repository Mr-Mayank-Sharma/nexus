package com.nexus.core.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.List;

/**
 * A language model behind one interface (doc 25, section 1): Anthropic by default, another provider or a scripted one
 * (tests, evaluation) when needed. Messages and content blocks follow the Anthropic Messages API shape.
 */
public interface LlmProvider {

    /** A tool the model may call: name, what it does, and the JSON schema of its input. */
    record Tool(String name, String description, JsonNode inputSchema) {
    }

    record Request(String model, String system, List<Tool> tools, ArrayNode messages, int maxTokens) {
    }

    record Usage(long inputTokens, long outputTokens, long cacheReadTokens, long cacheWriteTokens) {
        public static final Usage NONE = new Usage(0, 0, 0, 0);

        /** The two usages added together. */
        public Usage plus(Usage other) {
            return new Usage(inputTokens + other.inputTokens, outputTokens + other.outputTokens, cacheReadTokens + other.cacheReadTokens,
                    cacheWriteTokens + other.cacheWriteTokens);
        }
    }

    /** The model's turn: its content blocks (text and tool_use), why it stopped, and what it used. */
    record Response(ArrayNode content, String stopReason, Usage usage) {
    }

    Response complete(Request request);

    /** False when the provider can't be used (no key): the OMS then keeps working on rules. */
    boolean available();
}
