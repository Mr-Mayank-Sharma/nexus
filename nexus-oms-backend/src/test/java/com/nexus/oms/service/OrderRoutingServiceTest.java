package com.nexus.oms.service;

import com.nexus.oms.dto.AllocationRequest;
import com.nexus.oms.entity.*;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.repository.*;
import com.nexus.oms.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderRoutingServiceTest {

    @Mock private OrderAllocationRepository allocationRepository;
    @Mock private FulfillmentExceptionRepository exceptionRepository;
    @Mock private NxRoutingRuleRepository routingRuleRepository;
    @Mock private RoutingConfigRepository routingConfigRepository;
    @Mock private RoutingLogRepository routingLogRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private InventoryService inventoryService;

    private OrderRoutingService service;
    private UUID tenantId;
    private NxOrder order;
    private Warehouse stocked;
    private Warehouse empty;

    @BeforeEach
    void setUp() {
        service = new OrderRoutingService(allocationRepository, exceptionRepository, routingRuleRepository,
                routingConfigRepository, routingLogRepository, warehouseRepository, orderRepository,
                orderItemRepository, inventoryService);
        tenantId = UUID.randomUUID();
        TenantContext.setCurrentTenantId(tenantId);

        order = NxOrder.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .channel("WEB")
                .status("PENDING")
                .subtotal(new BigDecimal("20.00"))
                .total(new BigDecimal("27.00"))
                .build();

        NxOrderItem item = NxOrderItem.builder()
                .id(UUID.randomUUID())
                .orderId(order.getId())
                .sku("SKU-001")
                .quantity(2)
                .build();
        when(orderItemRepository.findByOrderId(order.getId())).thenReturn(List.of(item));

        stocked = Warehouse.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .name("Seattle DC")
                .status("ACTIVE")
                .type("WAREHOUSE")
                .state("WA")
                .country("US")
                .totalCapacitySqm(new BigDecimal("1000"))
                .usedCapacitySqm(new BigDecimal("100"))
                .build();

        empty = Warehouse.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .name("Miami DC")
                .status("ACTIVE")
                .type("WAREHOUSE")
                .state("FL")
                .country("US")
                .totalCapacitySqm(new BigDecimal("1000"))
                .usedCapacitySqm(new BigDecimal("100"))
                .build();

        when(warehouseRepository.findByTenantIdAndStatus(tenantId, "ACTIVE"))
                .thenReturn(List.of(empty, stocked));
        when(warehouseRepository.findById(stocked.getId())).thenReturn(Optional.of(stocked));
        when(warehouseRepository.findById(empty.getId())).thenReturn(Optional.of(empty));

        when(inventoryService.checkAvailability(tenantId, "SKU-001", stocked.getId(), 2)).thenReturn(true);
        when(inventoryService.checkAvailability(tenantId, "SKU-001", empty.getId(), 2)).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void ruleBasedAllocation_prefersWarehouseWithStock() {
        when(routingRuleRepository.findByTenantIdAndIsActiveTrue(tenantId)).thenReturn(List.of());

        List<NxOrderAllocation> allocations = service.ruleBasedAllocation(order, tenantId);

        assertFalse(allocations.isEmpty());
        assertEquals(stocked.getId(), allocations.get(0).getNodeId());
        assertEquals("ALLOCATED", allocations.get(0).getStatus());
    }

    @Test
    void aiOptimizedAllocation_picksStockedWarehouseOverEmptyOne() {
        List<NxOrderAllocation> allocations = service.aiOptimizedAllocation(order, tenantId);

        assertFalse(allocations.isEmpty());
        assertEquals(stocked.getId(), allocations.get(0).getNodeId());
    }

    @Test
    void allocateOrder_dryRun_doesNotPersistOrChangeStatus() {
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(routingConfigRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());

        AllocationRequest request = new AllocationRequest();
        request.setOrderId(order.getId());
        request.setStrategy("AI_OPTIMIZED");
        request.setDryRun(true);

        var result = service.allocateOrder(request);

        assertEquals("SIMULATED", result.getStatus());
        assertEquals(stocked.getId(), result.getAllocations().get(0).getNodeId());
        verify(allocationRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
        assertEquals("PENDING", order.getStatus());
    }

    @Test
    void allocateOrder_throwsWhenNotPending() {
        NxOrder confirmed = NxOrder.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .status("CONFIRMED")
                .build();
        when(orderRepository.findById(confirmed.getId())).thenReturn(Optional.of(confirmed));

        AllocationRequest request = new AllocationRequest();
        request.setOrderId(confirmed.getId());
        request.setStrategy("HYBRID");

        assertThrows(BadRequestException.class, () -> service.allocateOrder(request));
    }

    @Test
    void allocateOrder_persistsAllocationAndSetsOrderState() {
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(routingConfigRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());

        AllocationRequest request = new AllocationRequest();
        request.setOrderId(order.getId());
        request.setStrategy("AI_OPTIMIZED");

        var result = service.allocateOrder(request);

        assertEquals("ALLOCATED", result.getStatus());
        assertEquals("ALLOCATED", order.getStatus());
        assertEquals(stocked.getId(), order.getAllocatedNode());
        verify(allocationRepository, atLeastOnce()).save(any());
        verify(orderRepository, atLeastOnce()).save(order);
    }
}
