package com.nexus.oms.service;

import com.nexus.oms.entity.IntegrationDLQ;
import com.nexus.oms.entity.IntegrationMessage;
import com.nexus.oms.repository.IntegrationDLQRepository;
import com.nexus.oms.repository.IntegrationMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DLQManagerTest {

    @Mock
    private IntegrationDLQRepository dlqRepository;
    @Mock
    private IntegrationMessageRepository messageRepository;

    private DLQManager dlqManager;
    private UUID flowId;
    private UUID dlqId;

    @BeforeEach
    void setUp() {
        dlqManager = new DLQManager(dlqRepository, messageRepository);
        flowId = UUID.randomUUID();
        dlqId = UUID.randomUUID();
    }

    @Test
    void moveToDLQ() {
        UUID tenantId = UUID.randomUUID();
        IntegrationMessage msg = IntegrationMessage.builder()
                .tenantId(tenantId)
                .flowId(flowId)
                .messageId("msg-1")
                .payload("{\"order\":123}")
                .maxRetries(5)
                .build();

        when(dlqRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        IntegrationDLQ dlq = dlqManager.moveToDLQ(msg, "timeout", "NETWORK");

        assertEquals(tenantId, dlq.getTenantId());
        assertEquals(flowId, dlq.getFlowId());
        assertEquals("msg-1", dlq.getMessageId());
        assertEquals("{\"order\":123}", dlq.getOriginalPayload());
        assertEquals("timeout", dlq.getErrorMessage());
        assertEquals("NETWORK", dlq.getErrorCategory());
        assertEquals("FAILED", dlq.getStatus());
        assertEquals(0, dlq.getRetryCount());
        assertEquals(5, dlq.getMaxRetries());
    }

    @Test
    void retryFromDLQ() {
        UUID tenantId = UUID.randomUUID();
        IntegrationDLQ dlq = IntegrationDLQ.builder()
                .id(dlqId)
                .tenantId(tenantId)
                .flowId(flowId)
                .messageId("msg-1")
                .originalPayload("{\"order\":123}")
                .status("FAILED")
                .maxRetries(3)
                .build();

        when(dlqRepository.findById(dlqId)).thenReturn(Optional.of(dlq));
        when(messageRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(dlqRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        dlqManager.retryFromDLQ(dlqId);

        verify(messageRepository).save(argThat(msg ->
                msg.getTenantId().equals(tenantId) &&
                msg.getFlowId().equals(flowId) &&
                msg.getPayload().equals("{\"order\":123}") &&
                "PENDING".equals(msg.getStatus())
        ));
        verify(dlqRepository).save(any());
        assertEquals("REPLAYED", dlq.getStatus());
    }

    @Test
    void retryFromDLQ_notFound_throws() {
        when(dlqRepository.findById(dlqId)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> dlqManager.retryFromDLQ(dlqId));
    }

    @Test
    void replayAllByFlow() {
        IntegrationDLQ d1 = IntegrationDLQ.builder().id(UUID.randomUUID()).status("FAILED").build();
        IntegrationDLQ d2 = IntegrationDLQ.builder().id(UUID.randomUUID()).status("REPLAYED").build();
        IntegrationDLQ d3 = IntegrationDLQ.builder().id(UUID.randomUUID()).status("FAILED").build();

        when(dlqRepository.findByFlowId(flowId)).thenReturn(List.of(d1, d2, d3));
        when(dlqRepository.findById(any())).thenReturn(Optional.of(d1), Optional.of(d3));
        when(messageRepository.save(any())).thenReturn(new IntegrationMessage());
        when(dlqRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        int count = dlqManager.replayAllByFlow(flowId);

        assertEquals(2, count);
        verify(messageRepository, times(2)).save(any());
    }

    @Test
    void getDLQStats() {
        when(dlqRepository.count()).thenReturn(10L);
        when(dlqRepository.findByStatus("FAILED")).thenReturn(List.of(
                IntegrationDLQ.builder().build(),
                IntegrationDLQ.builder().build()));
        when(dlqRepository.findByStatus("REPLAYED")).thenReturn(List.of(
                IntegrationDLQ.builder().build()));

        Map<String, Object> stats = dlqManager.getDLQStats();

        assertEquals(10L, stats.get("total"));
        assertEquals(2, stats.get("failed"));
        assertEquals(1, stats.get("replayed"));
    }
}
