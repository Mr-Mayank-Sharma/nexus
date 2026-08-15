package com.nexus.oms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.entity.IntegrationEndpoint;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.integration.protocol.RestProtocolAdapter;
import com.nexus.oms.repository.*;
import com.nexus.oms.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IntegrationPlatformServiceTest {

    @Mock private IntegrationEndpointRepository endpointRepository;
    @Mock private IntegrationFlowRepository flowRepository;
    @Mock private IntegrationFlowStepRepository flowStepRepository;
    @Mock private IntegrationMessageRepository messageRepository;
    @Mock private IntegrationDLQRepository dlqRepository;
    @Mock private IntegrationTransformMappingRepository mappingRepository;
    @Mock private IntegrationValidationRuleRepository ruleRepository;
    @Mock private IntegrationImportJobRepository importJobRepository;
    @Mock private IntegrationExportJobRepository exportJobRepository;
    @Mock private IntegrationCDCEventRepository cdcEventRepository;
    @Mock private IntegrationAuditLogRepository auditLogRepository;
    @Mock private ImportExportEngine importExportEngine;
    @Mock private DLQManager dlqManager;
    @Mock private CDCProcessor cdcProcessor;
    @Mock private RestProtocolAdapter restProtocolAdapter;

    private IntegrationPlatformService service;
    private UUID tenantId;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new IntegrationPlatformService(endpointRepository, flowRepository, flowStepRepository,
                messageRepository, dlqRepository, mappingRepository, ruleRepository, importJobRepository,
                exportJobRepository, cdcEventRepository, auditLogRepository,
                importExportEngine, dlqManager, cdcProcessor, restProtocolAdapter);
        tenantId = UUID.randomUUID();
        TenantContext.setCurrentTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private IntegrationEndpoint endpoint(UUID id, Integer retries, Integer delayMs) {
        return IntegrationEndpoint.builder().id(id).tenantId(tenantId).name("webhook")
                .endpointType("WEBHOOK").protocol("HTTP").host("example.com")
                .path("/hooks/order").method("POST").retryCount(retries).retryDelayMs(delayMs).build();
    }

    private JsonNode ok(String body) throws Exception {
        return MAPPER.readTree(body);
    }

    @Test
    void sendOutbound_successSendsIdempotencyKeyAndReturnsOnFirstAttempt() throws Exception {
        UUID id = UUID.randomUUID();
        when(endpointRepository.findById(id)).thenReturn(Optional.of(endpoint(id, 2, 5)));
        when(restProtocolAdapter.post(any(), any(), any(), any()))
                .thenReturn(ok("{\"statusCode\":200}"));

        Map<String, Object> result = service.sendOutbound(id, Map.of("orderId", "1"), "key-abc");

        assertEquals("SUCCESS", result.get("status"));
        assertEquals(1, result.get("attempts"));
        assertEquals("key-abc", result.get("idempotencyKey"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headerCaptor = ArgumentCaptor.forClass(Map.class);
        verify(restProtocolAdapter).post(eq("http://example.com"), eq("/hooks/order"),
                headerCaptor.capture(), any());
        assertEquals("key-abc", headerCaptor.getValue().get("Idempotency-Key"));
        verify(dlqManager, never()).moveToDLQ(any(), any(), any());
    }

    @Test
    void sendOutbound_retriesUntilSuccessWithBackoff() throws Exception {
        UUID id = UUID.randomUUID();
        when(endpointRepository.findById(id)).thenReturn(Optional.of(endpoint(id, 2, 1)));
        when(restProtocolAdapter.post(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("connection refused"))
                .thenThrow(new RuntimeException("connection refused"))
                .thenReturn(ok("{\"statusCode\":200}"));

        Map<String, Object> result = service.sendOutbound(id, Map.of(), null);

        assertEquals("SUCCESS", result.get("status"));
        assertEquals(3, result.get("attempts"));
        assertNotNull(result.get("idempotencyKey"));
        verify(restProtocolAdapter, times(3)).post(any(), any(), any(), any());
        verify(dlqManager, never()).moveToDLQ(any(), any(), any());
    }

    @Test
    void sendOutbound_exhaustsRetriesAndQueuesToDLQ() throws Exception {
        UUID id = UUID.randomUUID();
        when(endpointRepository.findById(id)).thenReturn(Optional.of(endpoint(id, 2, 1)));
        when(restProtocolAdapter.post(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("connection refused"));

        Map<String, Object> result = service.sendOutbound(id, Map.of("orderId", "9"), "key-xyz");

        assertEquals("FAILED", result.get("status"));
        assertEquals(3, result.get("attempts"));
        assertEquals("key-xyz", result.get("idempotencyKey"));
        assertEquals(true, result.get("queuedToDLQ"));
        verify(restProtocolAdapter, times(3)).post(any(), any(), any(), any());
        verify(dlqManager).moveToDLQ(any(), any(), eq("OUTBOUND_DISPATCH"));
    }

    @Test
    void sendOutbound_requiresHost() {
        UUID id = UUID.randomUUID();
        IntegrationEndpoint noHost = endpoint(id, 1, 1);
        noHost.setHost(null);
        when(endpointRepository.findById(id)).thenReturn(Optional.of(noHost));

        assertThrows(BadRequestException.class, () -> service.sendOutbound(id, Map.of(), null));
        verify(restProtocolAdapter, never()).post(any(), any(), any(), any());
    }
}
