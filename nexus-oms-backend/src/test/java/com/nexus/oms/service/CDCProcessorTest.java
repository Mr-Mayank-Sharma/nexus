package com.nexus.oms.service;

import com.nexus.oms.entity.IntegrationCDCEvent;
import com.nexus.oms.repository.IntegrationCDCEventRepository;
import com.nexus.oms.security.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CDCProcessorTest {

    @Mock
    private IntegrationCDCEventRepository cdcEventRepository;

    private CDCProcessor cdcProcessor;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        cdcProcessor = new CDCProcessor(cdcEventRepository);
        tenantId = UUID.randomUUID();
    }

    @Test
    void captureEvent() {
        UUID entityId = UUID.randomUUID();
        when(cdcEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getCurrentTenantId).thenReturn(tenantId);

            IntegrationCDCEvent event = cdcProcessor.captureEvent(
                    "orders", "Order", entityId, "UPDATE", "{}", "{\"status\":\"SHIPPED\"}");

            assertEquals(tenantId, event.getTenantId());
            assertEquals("orders", event.getSource());
            assertEquals("Order", event.getEntityType());
            assertEquals(entityId, event.getEntityId());
            assertEquals("UPDATE", event.getEventType());
            assertEquals("{}", event.getBeforeSnapshot());
            assertEquals("{\"status\":\"SHIPPED\"}", event.getAfterSnapshot());
            assertFalse(event.getProcessed());
        }
    }

    @Test
    void processPendingEvents() {
        IntegrationCDCEvent e1 = IntegrationCDCEvent.builder().tenantId(tenantId).processed(false).build();
        IntegrationCDCEvent e2 = IntegrationCDCEvent.builder().tenantId(tenantId).processed(false).build();

        when(cdcEventRepository.findByTenantIdAndProcessed(tenantId, false)).thenReturn(List.of(e1, e2));
        when(cdcEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getCurrentTenantId).thenReturn(tenantId);

            cdcProcessor.processPendingEvents();

            assertTrue(e1.getProcessed());
            assertNotNull(e1.getProcessedAt());
            assertTrue(e2.getProcessed());
            assertNotNull(e2.getProcessedAt());
            verify(cdcEventRepository, times(2)).save(any());
        }
    }

    @Test
    void getUnprocessedCount() {
        when(cdcEventRepository.countByTenantIdAndProcessed(tenantId, false)).thenReturn(5L);

        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getCurrentTenantId).thenReturn(tenantId);

            assertEquals(5L, cdcProcessor.getUnprocessedCount());
        }
    }
}
