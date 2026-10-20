package com.nexus.oms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.entity.NxAuditLog;
import com.nexus.oms.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * What actually happened to an order, oldest first: creation and status changes (recorded by the V87
 * database trigger) and AI actions (recorded by AiOrderActionService). Nothing is inferred from the
 * order's current status.
 */
@Service
public class OrderHistoryService {

    public record OrderEvent(String id, String type, String title, String description, String actor, LocalDateTime timestamp) {}

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public OrderHistoryService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    public List<OrderEvent> history(UUID orderId) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("ORDER", orderId).stream()
                .map(this::toEvent)
                .sorted(Comparator.comparing(OrderEvent::timestamp, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
    }

    private OrderEvent toEvent(NxAuditLog log) {
        JsonNode data = parse(log.getData());
        String type = log.getEventType() == null ? "EVENT" : log.getEventType();
        String actor = text(data, "user");
        if (actor == null) actor = log.getActorType() == null ? "System" : pretty(log.getActorType());
        String title;
        String description;
        if ("ORDER_CREATED".equals(type)) {
            title = "Order created";
            String channel = text(data, "channel");
            description = channel == null ? "Status " + pretty(text(data, "to")) : "Received from " + channel;
        } else if ("STATUS_CHANGED".equals(type)) {
            title = pretty(text(data, "to"));
            description = "From " + pretty(text(data, "from")) + (text(data, "subStatus") != null ? " (" + pretty(text(data, "subStatus")) + ")" : "");
        } else if (type.startsWith("AI_")) {
            String action = type.substring(3);
            boolean failed = "FAILED".equals(text(data, "result"));
            title = "AI action: " + pretty(action) + (failed ? " (failed)" : "");
            String details = text(data, "details");
            description = details != null ? details : failed ? "The action was refused" : "Carried out";
            actor = "AI";
        } else {
            title = pretty(type);
            description = "";
        }
        return new OrderEvent(log.getId() == null ? null : log.getId().toString(), type, title, description, actor, log.getCreatedAt());
    }

    private JsonNode parse(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) && !node.get(field).asText().isBlank() ? node.get(field).asText() : null;
    }

    /** PARTIALLY_ALLOCATED -> Partially allocated */
    private static String pretty(String code) {
        if (code == null) return "unknown";
        String s = code.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
