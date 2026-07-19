package com.nexus.oms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.entity.Address;
import com.nexus.oms.entity.NxOrder;
import com.nexus.oms.entity.NxRoutingRule;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

public class RuleConditionEvaluator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public boolean evaluate(NxRoutingRule rule, NxOrder order) {
        JsonNode conditions = parseJson(rule.getConditions());
        if (conditions == null) return true;

        if (conditions.has("channels")) {
            Set<String> channels = new HashSet<>();
            conditions.get("channels").forEach(c -> channels.add(c.asText().toUpperCase()));
            if (!channels.contains(order.getChannel() != null ? order.getChannel().toUpperCase() : "")) {
                return false;
            }
        }

        if (conditions.has("fulfillmentTypes")) {
            Set<String> types = new HashSet<>();
            conditions.get("fulfillmentTypes").forEach(t -> types.add(t.asText().toUpperCase()));
            if (!types.contains(order.getFulfillmentType() != null ? order.getFulfillmentType().toUpperCase() : "")) {
                return false;
            }
        }

        if (conditions.has("maxTotal")) {
            BigDecimal maxTotal = new BigDecimal(conditions.get("maxTotal").asText());
            if (order.getTotal() != null && order.getTotal().compareTo(maxTotal) > 0) {
                return false;
            }
        }

        if (conditions.has("minTotal")) {
            BigDecimal minTotal = new BigDecimal(conditions.get("minTotal").asText());
            if (order.getTotal() != null && order.getTotal().compareTo(minTotal) < 0) {
                return false;
            }
        }

        if (conditions.has("regions")) {
            String shipRegion = extractRegion(order.getShipToAddress());
            Set<String> regions = new HashSet<>();
            conditions.get("regions").forEach(r -> regions.add(r.asText().toUpperCase()));
            if (shipRegion != null && !regions.contains(shipRegion.toUpperCase())) {
                return false;
            }
        }

        if (conditions.has("priority")) {
            Set<String> priorities = new HashSet<>();
            conditions.get("priority").forEach(p -> priorities.add(p.asText().toUpperCase()));
            String orderPriority = extractPriority(order.getMetadata());
            if (orderPriority != null && !priorities.contains(orderPriority.toUpperCase())) {
                return false;
            }
        }

        return true;
    }

    public String extractRegion(Address address) {
        if (address == null) return null;
        if (address.getState() != null) return address.getState();
        if (address.getCountry() != null) return address.getCountry();
        return null;
    }

    public String extractPriority(String metadataJson) {
        if (metadataJson == null) return null;
        try {
            JsonNode node = MAPPER.readTree(metadataJson);
            return node.has("priority") ? node.get("priority").asText() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
