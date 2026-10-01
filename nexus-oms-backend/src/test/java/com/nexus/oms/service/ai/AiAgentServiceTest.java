package com.nexus.oms.service.ai;

import com.nexus.oms.entity.NxOrder;
import com.nexus.oms.repository.InventoryRepository;
import com.nexus.oms.repository.ai.AiModelMetricRepository;
import com.nexus.oms.repository.ai.AiModelRepository;
import com.nexus.oms.repository.OrderItemRepository;
import com.nexus.oms.repository.OrderRepository;
import com.nexus.oms.repository.PackageRepository;
import com.nexus.oms.repository.ShipmentRepository;
import com.nexus.oms.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The AI agent's routing queue is the surface a human uses to decide what to do next. Filtering
 * PARTIALLY_ALLOCATED and FAILED out of it was backwards: those are the orders most likely to need
 * a decision, and hiding them made a real shortfall look like an empty queue.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiAgentServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private PackageRepository packageRepository;
    @Mock
    private ShipmentRepository shipmentRepository;
    @Mock
    private AiModelRepository modelRepository;
    @Mock
    private AiModelMetricRepository metricRepository;
    @Mock
    private SupplierRepository supplierRepository;
    @Mock
    private InventoryRepository inventoryRepository;

    private AiAgentService service;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        service = new AiAgentService(orderRepository, orderItemRepository, packageRepository,
                shipmentRepository, modelRepository, metricRepository, supplierRepository,
                inventoryRepository);
        tenantId = UUID.randomUUID();
        when(orderItemRepository.findByOrderId(any())).thenReturn(List.of());
    }

    @Test
    void getRoutingQueue_IncludesPartiallyAllocatedOrderAndFlagsIt() {
        NxOrder order = orderWithStatus("PARTIALLY_ALLOCATED");
        queueContains(order);

        Map<String, Object> entry = singleEntry();

        assertEquals("PARTIALLY_ALLOCATED", entry.get("status"));
        assertEquals(true, entry.get("needsAttention"));
        assertNotNull(entry.get("attentionReason"));
    }

    @Test
    void getRoutingQueue_IncludesFailedAllocationOrderAndFlagsIt() {
        NxOrder order = orderWithStatus("FAILED");
        queueContains(order);

        Map<String, Object> entry = singleEntry();

        assertEquals("FAILED", entry.get("status"));
        assertEquals(true, entry.get("needsAttention"));
    }

    @Test
    void getRoutingQueue_HealthyAllocatedOrderIsPresentButNotFlagged() {
        NxOrder order = orderWithStatus("ALLOCATED");
        queueContains(order);

        Map<String, Object> entry = singleEntry();

        assertEquals("ALLOCATED", entry.get("status"));
        assertEquals(false, entry.get("needsAttention"));
        assertFalse(entry.containsKey("attentionReason"));
    }

    @Test
    void getRoutingQueue_ExcludesOrdersThatAreNoLongerOpen() {
        NxOrder shipped = orderWithStatus("SHIPPED");
        NxOrder delivered = orderWithStatus("DELIVERED");
        when(orderRepository.findRecentByTenantId(any(), any(Pageable.class)))
                .thenReturn(List.of(shipped, delivered));

        assertTrue(service.getRoutingQueue(tenantId).isEmpty());
    }

    @Test
    void getRoutingQueue_KeepsBothHealthyAndShortAllocatedOrdersTogether() {
        NxOrder healthy = orderWithStatus("ALLOCATED");
        NxOrder partial = orderWithStatus("PARTIALLY_ALLOCATED");
        NxOrder failed = orderWithStatus("FAILED");
        NxOrder shipped = orderWithStatus("SHIPPED");
        when(orderRepository.findRecentByTenantId(any(), any(Pageable.class)))
                .thenReturn(List.of(healthy, partial, failed, shipped));

        List<Map<String, Object>> queue = service.getRoutingQueue(tenantId);

        assertEquals(3, queue.size());
        assertEquals(2, queue.stream().filter(e -> Boolean.TRUE.equals(e.get("needsAttention"))).count());
    }

    private void queueContains(NxOrder... orders) {
        when(orderRepository.findRecentByTenantId(any(), any(Pageable.class))).thenReturn(List.of(orders));
    }

    private Map<String, Object> singleEntry() {
        List<Map<String, Object>> queue = service.getRoutingQueue(tenantId);
        assertEquals(1, queue.size());
        return queue.get(0);
    }

    private NxOrder orderWithStatus(String status) {
        return NxOrder.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .status(status)
                .build();
    }
}
