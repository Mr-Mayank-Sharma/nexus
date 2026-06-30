package com.nexus.oms.service.ai;

import com.nexus.oms.dto.OrderResponse;
import com.nexus.oms.dto.ai.AiActionHistoryDto;
import com.nexus.oms.dto.ai.AiExecuteRequest;
import com.nexus.oms.dto.ai.AiSuggestionDto;
import com.nexus.oms.entity.NxAuditLog;
import com.nexus.oms.repository.AuditLogRepository;
import com.nexus.oms.service.OrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AiOrderActionService {

    private final OrderService orderService;
    private final AuditLogRepository auditLogRepository;

    public AiOrderActionService(OrderService orderService, AuditLogRepository auditLogRepository) {
        this.orderService = orderService;
        this.auditLogRepository = auditLogRepository;
    }

    public List<AiSuggestionDto> getSuggestions(UUID orderId) {
        OrderResponse order = orderService.getOrder(orderId);
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
