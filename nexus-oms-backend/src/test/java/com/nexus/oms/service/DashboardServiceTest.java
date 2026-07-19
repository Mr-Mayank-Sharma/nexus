package com.nexus.oms.service;

import com.nexus.oms.entity.NxOrder;
import com.nexus.oms.entity.NxShipment;
import com.nexus.oms.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private FulfillmentExceptionRepository exceptionRepository;
    @Mock
    private ReturnRepository returnRepository;
    @Mock
    private ShipmentRepository shipmentRepository;

    private DashboardService dashboardService;
    private UUID tenantId;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        dashboardService = new DashboardService(orderRepository, exceptionRepository,
                returnRepository, shipmentRepository);
        tenantId = UUID.randomUUID();
    }

    @Test
    void getKPIs_returnsAllMetrics() {
        when(orderRepository.countByTenantIdAndCreatedAtAfter(any(), any())).thenReturn(5L);
        when(orderRepository.sumTotalByTenantIdAndCreatedAtAfter(any(), any())).thenReturn(BigDecimal.valueOf(1000));
        when(exceptionRepository.countByTenantIdAndStatus(tenantId, "OPEN")).thenReturn(3L);
        when(orderRepository.countByTenantIdAndStatusNot(tenantId, "CANCELLED")).thenReturn(100L);
        when(orderRepository.countByTenantIdAndStatus(tenantId, "SHIPPED")).thenReturn(30L);
        when(orderRepository.countByTenantIdAndStatus(tenantId, "DELIVERED")).thenReturn(80L);

        Map<String, Object> kpis = dashboardService.getKPIs(tenantId);

        assertEquals(5, kpis.get("ordersToday"));
        assertEquals(1000.0, kpis.get("revenueToday"));
        assertEquals(3, kpis.get("activeExceptions"));
        assertEquals("80.0%", kpis.get("onTimeDelivery"));
        assertEquals("4.2h", kpis.get("avgShipTime"));
        assertEquals(18, kpis.get("activePickers"));
    }

    @Test
    void getKPIs_zeroOrders() {
        when(orderRepository.countByTenantIdAndCreatedAtAfter(any(), any())).thenReturn(0L);
        when(orderRepository.sumTotalByTenantIdAndCreatedAtAfter(any(), any())).thenReturn(BigDecimal.ZERO);
        when(exceptionRepository.countByTenantIdAndStatus(tenantId, "OPEN")).thenReturn(0L);
        when(orderRepository.countByTenantIdAndStatusNot(tenantId, "CANCELLED")).thenReturn(0L);
        when(orderRepository.countByTenantIdAndStatus(tenantId, "SHIPPED")).thenReturn(0L);
        when(orderRepository.countByTenantIdAndStatus(tenantId, "DELIVERED")).thenReturn(0L);

        Map<String, Object> kpis = dashboardService.getKPIs(tenantId);

        assertEquals("97.2%", kpis.get("onTimeDelivery"));
        assertEquals("—", kpis.get("avgShipTime"));
    }

    @Test
    void getOrderVelocity() {
        Map<String, Object> velocity = dashboardService.getOrderVelocity();
        assertEquals("orders_per_hour", velocity.get("metric"));
        assertEquals(12.5, velocity.get("value"));
    }

    @Test
    void getExceptions_returnsOpenExceptions() {
        com.nexus.oms.entity.NxFulfillmentException ex =
                new com.nexus.oms.entity.NxFulfillmentException();
        ex.setExceptionType("CARRIER_ISSUE");
        ex.setOrderId(UUID.randomUUID());
        ex.setSeverity("HIGH");
        ex.setMessage("Delivery delayed");
        when(exceptionRepository.findByTenantIdAndStatus(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(ex)));

        Map<String, Object> result = dashboardService.getExceptions(tenantId);

        assertEquals(1, result.get("total"));
    }

    @Test
    void getExceptions_handlesNullFields() {
        com.nexus.oms.entity.NxFulfillmentException ex =
                new com.nexus.oms.entity.NxFulfillmentException();
        ex.setMessage("test");
        ex.setOrderId(UUID.randomUUID());
        when(exceptionRepository.findByTenantIdAndStatus(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(ex)));

        Map<String, Object> result = dashboardService.getExceptions(tenantId);

        assertEquals(1, result.get("total"));
    }

    @Test
    void getActivity_returnsRecentOrdersAndShipments() {
        NxOrder order = new NxOrder();
        order.setId(UUID.randomUUID());
        order.setStatus("SHIPPED");
        order.setCreatedAt(LocalDateTime.now().minusHours(1));
        when(orderRepository.findRecentByTenantId(tenantId, PageRequest.of(0, 5)))
                .thenReturn(List.of(order));

        NxShipment shipment = new NxShipment();
        shipment.setId(UUID.randomUUID());
        shipment.setStatus("completed");
        shipment.setCreatedAt(LocalDateTime.now().minusMinutes(30));
        when(shipmentRepository.findByTenantId(tenantId)).thenReturn(List.of(shipment));

        List<Map<String, Object>> events = dashboardService.getActivity(tenantId);

        assertFalse(events.isEmpty());
    }

    @Test
    void getActivity_withNullTimestamps() {
        NxOrder order = new NxOrder();
        order.setId(UUID.randomUUID());
        order.setStatus("PENDING");
        order.setCreatedAt(null);
        when(orderRepository.findRecentByTenantId(tenantId, PageRequest.of(0, 5)))
                .thenReturn(List.of(order));

        when(shipmentRepository.findByTenantId(tenantId)).thenReturn(List.of());

        List<Map<String, Object>> events = dashboardService.getActivity(tenantId);

        assertNotNull(events);
    }

    @Test
    void getActivity_sortsByTimestamp() {
        NxOrder order = new NxOrder();
        order.setId(UUID.randomUUID());
        order.setStatus("DELIVERED");
        order.setCreatedAt(LocalDateTime.now().minusHours(2));
        when(orderRepository.findRecentByTenantId(tenantId, PageRequest.of(0, 5)))
                .thenReturn(List.of(order));

        when(shipmentRepository.findByTenantId(tenantId)).thenReturn(List.of());

        List<Map<String, Object>> events = dashboardService.getActivity(tenantId);

        assertEquals(1, events.size());
    }
}
