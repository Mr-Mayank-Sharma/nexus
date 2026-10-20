package com.nexus.oms.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.nexus.oms.dto.OrderResponse;
import com.nexus.oms.dto.ai.AiActionHistoryDto;
import com.nexus.oms.dto.ai.AiExecuteRequest;
import com.nexus.oms.dto.ai.AiSuggestionDto;
import com.nexus.oms.entity.NxAuditLog;
import com.nexus.oms.repository.AuditLogRepository;
import com.nexus.oms.service.OrderService;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AiOrderActionService {

    private static final Logger log = LoggerFactory.getLogger(AiOrderActionService.class);

    private final OrderService orderService;
    private final AuditLogRepository auditLogRepository;
    private final LlmChatService llmChatService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public AiOrderActionService(OrderService orderService,
                                 AuditLogRepository auditLogRepository,
                                 LlmChatService llmChatService,
                                 ObjectMapper objectMapper,
                                 MeterRegistry meterRegistry) {
        this.orderService = orderService;
        this.auditLogRepository = auditLogRepository;
        this.llmChatService = llmChatService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Get AI-powered suggestions for an order. Tries LLM first, falls back to rules.
     */
    public List<AiSuggestionDto> getSuggestions(UUID orderId) {
        OrderResponse order = orderService.getOrder(orderId);

        // Try LLM-enhanced suggestions first
        if (llmChatService.isEnabled()) {
            try {
                List<AiSuggestionDto> llmSuggestions = executableOnly(llmEnhancedSuggestions(orderId, order), order);
                if (!llmSuggestions.isEmpty()) {
                    meterRegistry.counter("nexus.ai.order_action.llm_suggestions").increment();
                    return llmSuggestions;
                }
            } catch (Exception e) {
                log.warn("LLM suggestion generation failed for order {}: {}", orderId, e.getMessage());
                meterRegistry.counter("nexus.ai.order_action.llm_fallback").increment();
            }
        }

        // Fall back to rule-based suggestions
        return ruleBasedSuggestions(orderId, order);
    }

    /**
     * Use LLM to generate contextual order action suggestions.
     */
    private List<AiSuggestionDto> llmEnhancedSuggestions(UUID orderId, OrderResponse order) {
        String systemPrompt = """
            You are an order management AI assistant. Given an order's current state and details,
            suggest the most appropriate actions. Return a JSON array of suggestions, each with:
            - actionType: CONFIRM, ALLOCATE, SHIP or CANCEL
            - label: Human-readable action label
            - description: Why this action is recommended (1 sentence)
            - confidence: 0.0-1.0 confidence score
            Consider the order status, value, and any risk signals.
            """;

        String userPrompt = String.format(
            "Order ID: %s\nStatus: %s\nTotal: $%.2f\nCustomer: %s\nItems: %d\nShipping: %s\nPayment: %s",
            orderId,
            order.getStatus(),
            order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO,
            order.getCustomerName() != null ? order.getCustomerName() : "Unknown",
            order.getItems() != null ? order.getItems().size() : 0,
            order.getShipTo() != null ? String.valueOf(order.getShipTo()) : "N/A",
            order.getPaymentStatus() != null ? order.getPaymentStatus() : "N/A"
        );

        var messages = List.of(Map.of("role", "user", "content", userPrompt));
        JsonNode llmResult = llmChatService.chatJson(systemPrompt, messages);

        List<AiSuggestionDto> suggestions = new ArrayList<>();
        if (llmResult != null && llmResult.isArray()) {
            for (JsonNode item : llmResult) {
                suggestions.add(AiSuggestionDto.builder()
                        .actionType(item.hasNonNull("actionType") ? item.get("actionType").asText() : null)
                        .label(item.hasNonNull("label") ? item.get("label").asText() : "AI Suggestion")
                        .description(item.hasNonNull("description") ? item.get("description").asText() : "LLM-recommended action")
                        .confidence(item.has("confidence") ? item.get("confidence").asDouble() : 0.7)
                        .orderId(orderId.toString())
                        .build());
            }
        }

        return suggestions;
    }

    /** The actions {@link #executeAction} can carry out, by the order status they apply to. */
    private static final Map<String, Set<String>> EXECUTABLE = Map.of(
            "PENDING", Set.of("CONFIRM", "CANCEL"),
            "CONFIRMED", Set.of("ALLOCATE", "CANCEL"),
            "ALLOCATED", Set.of("SHIP", "CANCEL"));

    /** Drops model suggestions this service cannot carry out for the order's current status. */
    private List<AiSuggestionDto> executableOnly(List<AiSuggestionDto> suggestions, OrderResponse order) {
        Set<String> allowed = EXECUTABLE.getOrDefault(order.getStatus() == null ? "" : order.getStatus().toUpperCase(), Set.of());
        return suggestions.stream().filter(sg -> sg.getActionType() != null && allowed.contains(sg.getActionType().toUpperCase())).toList();
    }

    /**
     * Rule-based suggestions. Each one is grounded in a check this service actually makes (stock at a
     * location, the recorded payment status) and only offers actions that {@link #executeAction} supports.
     */
    private List<AiSuggestionDto> ruleBasedSuggestions(UUID orderId, OrderResponse order) {
        List<AiSuggestionDto> suggestions = new ArrayList<>();
        String payment = order.getPaymentStatus() == null ? "" : order.getPaymentStatus().toUpperCase();
        boolean paid = Set.of("PAID", "CAPTURED", "AUTHORIZED", "PARTIALLY_PAID").contains(payment);

        switch (order.getStatus() != null ? order.getStatus().toUpperCase() : "") {
            case "PENDING" -> suggestions.add(suggestion(orderId, "CONFIRM", "Confirm Order", paid
                    ? "Payment is recorded as " + payment.toLowerCase() + ", so the order can go ahead"
                    : "No payment is recorded as paid yet. Check the payment before confirming", paid ? 0.92 : 0.55));
            case "CONFIRMED" -> {
                boolean stock;
                try {
                    stock = orderService.canAllocate(orderId);
                } catch (Exception e) {
                    stock = false;
                }
                if (stock) {
                    suggestions.add(suggestion(orderId, "ALLOCATE", "Allocate Inventory",
                            "One location has enough stock for every line", 0.9));
                } else {
                    suggestions.add(suggestion(orderId, "CANCEL", "Cancel Order",
                            "No single location has enough stock for every line. Wait for a restock or a transfer; cancel only if the customer agrees", 0.3));
                }
            }
            case "ALLOCATED" -> suggestions.add(suggestion(orderId, "SHIP", "Ship Order",
                    "Stock is reserved" + (order.getAllocatedNode() != null ? " at the allocated location" : "") + ", so the order is ready to ship", 0.85));
            default -> {
            }
        }
        return suggestions;
    }

    private static AiSuggestionDto suggestion(UUID orderId, String type, String label, String why, double confidence) {
        return AiSuggestionDto.builder().actionType(type).label(label).description(why)
                .confidence(confidence).orderId(orderId.toString()).build();
    }

    /**
     * Not transactional on purpose: each order operation commits or rolls back on its own. If this joined one
     * transaction, a refused step (say, not enough stock to allocate) would mark it rollback-only and the
     * request would fail with a 500 instead of reporting the reason.
     */
    public AiActionHistoryDto executeAction(UUID orderId, AiExecuteRequest request, UUID tenantId) {
        String actionType = request.getActionType() != null ? request.getActionType().toUpperCase() : "";
        String result = "SUCCESS";
        String details = "";

        try {
            switch (actionType) {
                case "CONFIRM" -> {
                    orderService.confirmOrder(orderId);
                    details = "Order confirmed by AI action";
                }
                case "ALLOCATE" -> {
                    orderService.allocateOrder(orderId);
                    details = "Inventory allocated by AI action";
                }
                case "SHIP" -> {
                    orderService.shipOrder(orderId, "auto", "AI-TN-" + System.currentTimeMillis());
                    details = "Order shipped by AI action (auto carrier)";
                }
                case "CANCEL" -> {
                    orderService.cancelOrder(orderId);
                    details = "Order cancelled by AI action";
                }
                default -> {
                    result = "FAILED";
                    details = "Unknown action: " + actionType;
                }
            }
        } catch (Exception e) {
            result = "FAILED";
            details = e.getMessage() != null ? e.getMessage() : "Execution failed";
        }

        meterRegistry.counter("nexus.ai.order_action.executed",
                "action", actionType, "result", result).increment();

        NxAuditLog auditLog = NxAuditLog.builder()
                .tenantId(tenantId)
                .entityType("ORDER")
                .entityId(orderId)
                .eventType("AI_" + actionType)
                .actorType("AI")
                .data(objectMapper.createObjectNode().put("action", actionType).put("autoExecute", request.isAutoExecute())
                        .put("result", result).put("details", details).toString())
                .build();
        auditLogRepository.save(auditLog);

        return AiActionHistoryDto.builder()
                .id(auditLog.getId().toString())
                .actionType(actionType)
                .label(getActionLabel(actionType))
                .status(result)
                .actor("AI")
                .details(details)
                .timestamp(auditLog.getCreatedAt())
                .build();
    }

    public List<AiActionHistoryDto> getActionHistory(UUID orderId) {
        List<NxAuditLog> logs = auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("ORDER", orderId);
        return logs.stream()
                .filter(log -> log.getEventType() != null && log.getEventType().startsWith("AI_"))
                .map(log -> {
                    String actionType = log.getEventType().replace("AI_", "");
                    boolean failed = log.getData() != null && log.getData().contains("\"FAILED\"");
                    return AiActionHistoryDto.builder()
                            .id(log.getId().toString())
                            .actionType(actionType)
                            .label(getActionLabel(actionType))
                            .status(failed ? "FAILED" : "SUCCESS")
                            .actor(log.getActorType() != null ? log.getActorType() : "AI")
                            .details(log.getData() != null ? log.getData() : "")
                            .timestamp(log.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    private String getActionLabel(String actionType) {
        return switch (actionType.toUpperCase()) {
            case "CONFIRM" -> "Confirm Order";
            case "ALLOCATE" -> "Allocate Inventory";
            case "SHIP" -> "Ship Order";
            case "CANCEL" -> "Cancel Order";
            default -> actionType;
        };
    }
}
