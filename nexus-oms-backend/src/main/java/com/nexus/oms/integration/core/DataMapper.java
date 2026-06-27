package com.nexus.oms.integration.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DataMapper {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public JsonNode transform(JsonNode source, Map<String, String> mapping, String direction) {
        ObjectNode result = objectMapper.createObjectNode();

        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            String sourceField = direction.equals("TO_EXTERNAL") ? entry.getKey() : entry.getValue();
            String targetField = direction.equals("TO_EXTERNAL") ? entry.getValue() : entry.getKey();

            JsonNode value = resolveValue(source, sourceField);
            if (value != null && !value.isNull()) {
                setValue(result, targetField, value);
            }
        }

        return result;
    }

    public List<JsonNode> transformBatch(List<JsonNode> sources, Map<String, String> mapping, String direction) {
        return sources.stream().map(s -> transform(s, mapping, direction)).toList();
    }

    private JsonNode resolveValue(JsonNode root, String path) {
        String[] parts = path.split("\\.");
        JsonNode current = root;
        for (String part : parts) {
            if (current == null || current.isNull()) return null;
            if (part.contains("[") && part.endsWith("]")) {
                int idx = Integer.parseInt(part.substring(part.indexOf('[') + 1, part.indexOf(']')));
                String arrayField = part.substring(0, part.indexOf('['));
                current = current.get(arrayField);
                if (current != null && current.isArray() && idx < current.size()) {
                    current = current.get(idx);
                } else return null;
            } else {
                current = current.get(part);
            }
        }
        return current;
    }

    private void setValue(ObjectNode root, String path, JsonNode value) {
        String[] parts = path.split("\\.");
        ObjectNode current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            if (current.get(part) == null) {
                current.set(part, objectMapper.createObjectNode());
            }
            current = (ObjectNode) current.get(part);
        }
        current.set(parts[parts.length - 1], value);
    }

    public Map<String, String> parseMappingString(String mappingJson) {
        try {
            Map<String, String> mapping = new LinkedHashMap<>();
            JsonNode json = objectMapper.readTree(mappingJson);
            json.fields().forEachRemaining(e -> mapping.put(e.getKey(), e.getValue().asText()));
            return mapping;
        } catch (Exception e) {
            return Map.of();
        }
    }

    public static final Map<String, String> ORDER_TO_OMS = Map.ofEntries(
        Map.entry("id", "externalId"),
        Map.entry("order_number", "channelOrderId"),
        Map.entry("created_at", "createdAt"),
        Map.entry("currency", "currency"),
        Map.entry("subtotal_price", "subtotal"),
        Map.entry("total_price", "total"),
        Map.entry("shipping_lines[0].price", "shippingCost"),
        Map.entry("customer.email", "customerEmail"),
        Map.entry("customer.first_name", "customerFirstName"),
        Map.entry("customer.last_name", "customerLastName"),
        Map.entry("shipping_address.address1", "shipTo.address1"),
        Map.entry("shipping_address.city", "shipTo.city"),
        Map.entry("shipping_address.zip", "shipTo.zip"),
        Map.entry("shipping_address.country", "shipTo.country"),
        Map.entry("shipping_address.phone", "shipTo.phone")
    );

    public static final Map<String, String> PRODUCT_TO_OMS = Map.ofEntries(
        Map.entry("id", "externalId"),
        Map.entry("sku", "sku"),
        Map.entry("title", "productName"),
        Map.entry("price", "unitPrice"),
        Map.entry("weight", "weight"),
        Map.entry("inventory_quantity", "quantityOnHand")
    );
}
