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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
                List<AiSuggestionDto> llmSuggestions = llmEnhancedSuggestions(orderId, order);
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
            - actionType: CONFIRM, ALLOCATE, SHIP, CANCEL, HOLD, ESCALATE, or REPROCESS_PAYMENT
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
                        .actionType(item.has("actionType") ? item.get("actionType").asText() : "CONFIRM")
                        .label(item.has("label") ? item.get("label").asText() : "AI Suggestion")
                        .description(item.has("description") ? item.get("description").asText() : "LLM-recommended action")
                        .confidence(item.has("confidence") ? item.get("confidence").asDouble() : 0.7)
                        .orderId(orderId.toString())
                        .build());
            }
        }

        return suggestions;
    }

    /**
     * Rule-based suggestions (original logic, preserved as fallback).
     */
    private List<AiSuggestionDto> ruleBasedSuggestions(UUID orderId, OrderResponse order) {
        List<AiSuggestionDto> suggestions = new ArrayList<>();

        switch (order.getStatus() != null ? order.getStatus().toUpperCase() : "") {
            case "PENDING" -> {
                suggestions.add(AiSuggestionDto.builder()
                        .actionType("CONFIRM")
                        .label("Confirm Order")
                        .description("AI recommends confirming this order — payment appears verified")
                        .confidence(0.92)
                        .orderId(orderId.toString())
                        .build());
                suggestions.add(AiSuggestionDto.builder()
                        .actionType("CANCEL")
                        .label("Cancel Order")
                        .description("Cancel if order is no longer needed")
                        .confidence(0.15)
                        .orderId(orderId.toString())
                        .build());
            }
            case "CONFIRMED" -> {
                suggestions.add(AiSuggestionDto.builder()
                        .actionType("ALLOCATE")
                        .label("Allocate Inventory")
                        .description("AI suggests allocating inventory — stock appears available")
                        .confidence(0.88)
                        .orderId(orderId.toString())
                        .build());
                suggestions.add(AiSuggestionDto.builder()
                        .actionType("CANCEL")
                        .label("Cancel Order")
                        .description("Cancel if inventory cannot be fulfilled")
                        .confidence(0.10)
                        .orderId(orderId.toString())
                        .build());
            }
            case "ALLOCATED" -> {
                suggestions.add(AiSuggestionDto.builder()
                        .actionType("SHIP")
                        .label("Ship Order")
                        .description("AI suggests shipping — allocation complete, carrier ready")
                        .confidence(0.85)
                        .orderId(orderId.toString())
                        .build());
            }
            default -> {
            }
        }

        return suggestions;
    }

    @Transactional
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
                .data("{\"action\":\"" + actionType + "\",\"autoExecute\":" + request.isAutoExecute() + ",\"result\":\"" + result + "\"}")
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
