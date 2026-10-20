package com.nexus.oms.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.dto.OrderResponse;
import com.nexus.oms.dto.ai.AiActionHistoryDto;
import com.nexus.oms.dto.ai.AiExecuteRequest;
import com.nexus.oms.dto.ai.AiSuggestionDto;
import com.nexus.oms.entity.NxAuditLog;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.repository.AuditLogRepository;
import com.nexus.oms.service.OrderService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiOrderActionServiceTest {

    private final OrderService orderService = mock(OrderService.class);
    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final LlmChatService llm = mock(LlmChatService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private AiOrderActionService service;
    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AiOrderActionService(orderService, auditLogRepository, llm, mapper, new SimpleMeterRegistry());
        when(auditLogRepository.save(any())).thenAnswer(i -> {
            NxAuditLog log = i.getArgument(0);
            log.setId(UUID.randomUUID());
            return log;
        });
    }

    private void orderIs(String status, String payment) {
        when(orderService.getOrder(orderId)).thenReturn(OrderResponse.builder().id(orderId).status(status).paymentStatus(payment).build());
    }

    @Test
    void confirmedOrderWithStock_suggestsAllocate() {
        orderIs("CONFIRMED", "paid");
        when(orderService.canAllocate(orderId)).thenReturn(true);

        List<AiSuggestionDto> s = service.getSuggestions(orderId);

        assertEquals("ALLOCATE", s.get(0).getActionType());
    }

    @Test
    void confirmedOrderWithoutStock_doesNotSuggestAllocate() {
        orderIs("CONFIRMED", "paid");
        when(orderService.canAllocate(orderId)).thenReturn(false);

        List<AiSuggestionDto> s = service.getSuggestions(orderId);

        assertTrue(s.stream().noneMatch(x -> "ALLOCATE".equals(x.getActionType())));
    }

    @Test
    void unpaidPendingOrder_saysSoInsteadOfClaimingPaymentIsVerified() {
        orderIs("PENDING", null);

        AiSuggestionDto s = service.getSuggestions(orderId).get(0);

        assertEquals("CONFIRM", s.getActionType());
        assertTrue(s.getDescription().contains("No payment is recorded"));
    }

    @Test
    void modelSuggestionsThatCannotBeExecuted_areDropped() throws Exception {
        orderIs("PENDING", "paid");
        when(llm.isEnabled()).thenReturn(true);
        when(llm.chatJson(any(), any())).thenReturn(mapper.readTree(
                "[{\"actionType\":\"ESCALATE\",\"label\":\"Escalate\"},{\"actionType\":\"CONFIRM\",\"label\":\"Confirm\"}]"));

        List<AiSuggestionDto> s = service.getSuggestions(orderId);

        assertEquals(1, s.size());
        assertEquals("CONFIRM", s.get(0).getActionType());
    }

    @Test
    void refusedAction_isReportedWithTheReason() {
        when(orderService.allocateOrder(orderId)).thenThrow(new BadRequestException("Insufficient inventory to allocate order"));
        AiExecuteRequest request = new AiExecuteRequest();
        request.setActionType("ALLOCATE");

        AiActionHistoryDto result = service.executeAction(orderId, request, UUID.randomUUID());

        assertEquals("FAILED", result.getStatus());
        assertEquals("Insufficient inventory to allocate order", result.getDetails());
    }
}
