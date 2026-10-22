package com.nexus.core.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Builds a tool's JSON input schema: an object with named fields, some required.
 * <pre>
 * ToolSchema.of().required("orderId", "string", "The order id").optional("limit", "integer", "At most this many")
 * </pre>
 */
final class ToolSchema {

    private final ObjectNode schema = AgentRunner.JSON.createObjectNode().put("type", "object");
    private final ObjectNode properties = schema.putObject("properties");

    private ToolSchema() {
    }

    static ToolSchema of() {
        return new ToolSchema();
    }

    /** A schema whose fields are all required strings, with no descriptions. */
    static ToolSchema requiredStrings(String... fieldNames) {
        ToolSchema toolSchema = new ToolSchema();
        for (String fieldName : fieldNames) {
            toolSchema.required(fieldName, "string", null);
        }
        return toolSchema;
    }

    ToolSchema required(String field, String type, String description) {
        optional(field, type, description);
        ArrayNode requiredFields = schema.has("required") ? (ArrayNode) schema.get("required") : schema.putArray("required");
        requiredFields.add(field);
        return this;
    }

    ToolSchema optional(String field, String type, String description) {
        ObjectNode property = properties.putObject(field).put("type", type);
        if (description != null) {
            property.put("description", description);
        }
        return this;
    }

    JsonNode json() {
        return schema;
    }
}
