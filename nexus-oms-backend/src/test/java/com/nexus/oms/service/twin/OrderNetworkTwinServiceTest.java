package com.nexus.oms.service.twin;

import com.nexus.oms.entity.*;
import com.nexus.oms.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderNetworkTwinServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock OrderAllocationRepository allocationRepository;
    @Mock AddressRepository addressRepository;
    @Mock WarehouseRepository warehouseRepository;
    @Mock InventoryRepository inventoryRepository;
    @Mock BrokeringQueueRepository queueRepository;
    @Mock FulfillmentExceptionRepository exceptionRepository;

    private final LocalDateTime now = LocalDateTime.of(2026, 10, 9, 12, 0);
    private final UUID tenantId = UUID.randomUUID();
    private final UUID dc = UUID.randomUUID();
    private final UUID store = UUID.randomUUID();
    private OrderNetworkTwinService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(now.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        service = new OrderNetworkTwinService(orderRepository, allocationRepository, addressRepository, warehouseRepository,
                inventoryRepository, queueRepository, exceptionRepository, clock);
        Warehouse w1 = new Warehouse(); w1.setId(dc); w1.setCode("DC-1"); w1.setTenantId(tenantId);
        Warehouse w2 = new Warehouse(); w2.setId(store); w2.setCode("ST-9"); w2.setTenantId(tenantId);
        when(warehouseRepository.findByTenantId(eq(tenantId), any())).thenReturn(new PageImpl<>(List.of(w1, w2)));
        when(inventoryRepository.findByTenantId(tenantId)).thenReturn(List.of());
        when(queueRepository.findByTenantIdAndStatusIn(eq(tenantId), anyList())).thenReturn(List.of());
        when(exceptionRepository.findByTenantIdAndStatus(eq(tenantId), eq("OPEN"), any())).thenReturn(Page.empty());
    }

    @Test
    void emptyWindow_ReturnsNullRatiosNotZeros() {
        when(orderRepository.findTop5000ByTenantIdAndCreatedAtAfterOrderByCreatedAtDesc(eq(tenantId), any())).thenReturn(List.of());

        var snap = service.snapshot(tenantId, 24);

        assertEquals(0, snap.kpis().orders());
        assertNull(snap.kpis().routedPct());
        assertNull(snap.kpis().splitRatePct());
        assertNull(snap.kpis().avgEstimatedCostPerOrder());
        verify(allocationRepository, never()).findByTenantIdAndOrderIdIn(any(), any());
    }

    @Test
    void flowsAndKpis_ComeFromOrdersAndAllocations() {
        UUID addrNj = UUID.randomUUID();
        NxOrder single = order("SHOPIFY", "ALLOCATED", "100.00", addrNj);
        NxOrder splitOrder = order("shopify", "ALLOCATED", "50.00", addrNj);
        NxOrder backorder = order("AMAZON", "FAILED", "20.00", null);
        when(orderRepository.findTop5000ByTenantIdAndCreatedAtAfterOrderByCreatedAtDesc(eq(tenantId), eq(now.minusHours(24))))
                .thenReturn(List.of(single, splitOrder, backorder));
        Address nj = new Address(); nj.setId(addrNj); nj.setState("nj"); nj.setCountry("US");
        when(addressRepository.findAllById(anyCollection())).thenReturn(List.of(nj));
        when(allocationRepository.findByTenantIdAndOrderIdIn(eq(tenantId), anyCollection())).thenReturn(List.of(
                alloc(single, dc, "ALLOCATED", "6.40", "0.90"),
                alloc(splitOrder, dc, "ALLOCATED", "5.00", "0.80"),
                alloc(splitOrder, store, "ALLOCATED", "3.00", null),
                alloc(backorder, null, "FAILED", null, null)));

        var snap = service.snapshot(tenantId, 24);

        assertEquals(3, snap.kpis().orders());
        assertEquals(new BigDecimal("170.00"), snap.kpis().revenue());
        assertEquals(66.7, snap.kpis().routedPct());
        assertEquals(50.0, snap.kpis().splitRatePct(), "one of two routed orders went to two nodes");
        assertEquals(1, snap.kpis().backorders());
        assertEquals(new BigDecimal("7.20"), snap.kpis().avgEstimatedCostPerOrder(), "(6.40 + 8.00) / 2");
        assertEquals(85.0, snap.kpis().avgPromiseConfidencePct());

        assertEquals("SHOPIFY", snap.channels().get(0).name(), "channel names are normalised");
        assertEquals(2, snap.channels().get(0).orders());
        assertEquals("region:NJ", snap.regions().get(0).id());
        assertTrue(snap.flows().contains(new OrderNetworkTwinService.Flow("channel:SHOPIFY", "node:" + dc, "CHANNEL_TO_NODE", 2)));
        assertTrue(snap.flows().contains(new OrderNetworkTwinService.Flow("node:" + store, "region:NJ", "NODE_TO_REGION", 1)));
        assertEquals("DC-1", snap.nodes().get(0).code(), "busiest node first");
        assertEquals(2, snap.nodes().get(0).allocations());
    }

    @Test
    void windowIsClamped() {
        when(orderRepository.findTop5000ByTenantIdAndCreatedAtAfterOrderByCreatedAtDesc(eq(tenantId), any())).thenReturn(List.of());
        assertEquals(OrderNetworkTwinService.MAX_WINDOW_HOURS, service.snapshot(tenantId, 100_000).windowHours());
        assertEquals(1, service.snapshot(tenantId, -5).windowHours());
    }

    @Test
    void region_UsesStateThenCountry() {
        Address intl = new Address(); intl.setState("ON"); intl.setCountry("CA");
        Address countryOnly = new Address(); countryOnly.setCountry("de");
        assertEquals("ON, CA", OrderNetworkTwinService.region(intl));
        assertEquals("DE", OrderNetworkTwinService.region(countryOnly));
        assertEquals("Unknown", OrderNetworkTwinService.region(null));
    }

    private NxOrder order(String channel, String status, String total, UUID addressId) {
        NxOrder o = new NxOrder();
        o.setId(UUID.randomUUID());
        o.setTenantId(tenantId);
        o.setChannel(channel);
        o.setStatus(status);
        o.setTotal(new BigDecimal(total));
        o.setShipToAddressId(addressId);
        o.setCreatedAt(now.minusHours(1));
        return o;
    }

    private NxOrderAllocation alloc(NxOrder o, UUID node, String status, String cost, String confidence) {
        NxOrderAllocation a = new NxOrderAllocation();
        a.setOrderId(o.getId());
        a.setNodeId(node);
        a.setStatus(status);
        a.setQuantityAllocated(1);
        a.setCostEstimated(cost == null ? null : new BigDecimal(cost));
        a.setDeliveryPromiseConfidence(confidence == null ? null : new BigDecimal(confidence));
        return a;
    }
}
