package com.nexus.oms.controller;

import com.nexus.oms.entity.NxIntegrationStore;
import com.nexus.oms.entity.NxIntegrationStoreSetting;
import com.nexus.oms.entity.NxOrder;
import com.nexus.oms.entity.NxSyncLog;
import com.nexus.oms.entity.Product;
import com.nexus.oms.repository.InventoryRepository;
import com.nexus.oms.repository.NxIntegrationStoreRepository;
import com.nexus.oms.repository.NxIntegrationStoreSettingRepository;
import com.nexus.oms.repository.NxSyncLogRepository;
import com.nexus.oms.repository.OrderItemRepository;
import com.nexus.oms.repository.OrderRepository;
import com.nexus.oms.repository.ProductRepository;
import com.nexus.oms.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConnectorControllerTest {

    @Mock private NxIntegrationStoreRepository storeRepository;
    @Mock private NxIntegrationStoreSettingRepository settingRepository;
    @Mock private NxSyncLogRepository syncLogRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private ProductRepository productRepository;

    private ConnectorController controller;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        controller = new ConnectorController(storeRepository, settingRepository, syncLogRepository,
                orderRepository, orderItemRepository, inventoryRepository, productRepository);
        tenantId = UUID.randomUUID();
        TenantContext.setCurrentTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void status_unconfiguredConnectorReportsInactive() {
        when(storeRepository.findByTenantIdAndStoreCode(any(), any())).thenReturn(Optional.empty());
        when(orderRepository.countByTenantIdAndChannelIgnoreCase(any(), any())).thenReturn(0L);

        Map<String, Object> res = controller.status().getBody();
        assertTrue((Boolean) res.get("success"));
        Map<?, ?> connectors = (Map<?, ?>) res.get("connectors");
        assertTrue(connectors.containsKey("amazon"));
        assertTrue(connectors.containsKey("ebay"));
        assertTrue(connectors.containsKey("walmart"));
        assertTrue(connectors.containsKey("bigcommerce"));
        Map<?, ?> amazon = (Map<?, ?>) connectors.get("amazon");
        assertEquals(false, amazon.get("active"));
        assertEquals(false, amazon.get("hasCredentials"));
    }

    @Test
    void configure_upsertsStoreAndAddsCredentialSetting() {
        when(storeRepository.findByTenantIdAndStoreCode(any(), any())).thenReturn(Optional.empty());
        when(storeRepository.save(any())).thenAnswer(inv -> {
            NxIntegrationStore s = inv.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            return s;
        });
        when(settingRepository.findByStoreIdAndSettingType(any(), any())).thenReturn(Optional.empty());
        when(settingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<Map<String, Object>> resp = controller.configure(Map.of("marketplace", "amazon", "endpoint", "https://api.amazon.test"));

        assertTrue((Boolean) resp.getBody().get("success"));
        verify(storeRepository).save(any());
        verify(settingRepository).save(any(NxIntegrationStoreSetting.class));
    }

    @Test
    void configure_unknownMarketplaceReturnsBadRequest() {
        ResponseEntity<Map<String, Object>> resp = controller.configure(Map.of("marketplace", "etsy"));

        assertEquals(400, resp.getStatusCode().value());
        assertEquals(false, resp.getBody().get("success"));
        verify(storeRepository, never()).save(any());
    }

    @Test
    void authorize_setsActiveAndStoresCredentials() {
        NxIntegrationStore store = new NxIntegrationStore();
        store.setId(UUID.randomUUID());
        store.setTenantId(tenantId);
        store.setStoreCode("amazon");
        store.setStatus("INACTIVE");
        store.setIsActive(false);
        when(storeRepository.findByTenantIdAndStoreCode(tenantId, "amazon")).thenReturn(Optional.of(store));
        when(storeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(settingRepository.findByStoreIdAndSettingType(store.getId(), "credentials")).thenReturn(Optional.empty());
        when(settingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<Map<String, Object>> resp = controller.authorize("amazon", Map.of("accessToken", "tok-123"));

        assertEquals(true, resp.getBody().get("success"));
        assertEquals(true, store.getIsActive());
        assertEquals("ACTIVE", store.getStatus());
        assertEquals(true, resp.getBody().get("hasCredentials"));
        verify(settingRepository).save(any(NxIntegrationStoreSetting.class));
    }

    @Test
    void disconnect_marksStoreInactive() {
        NxIntegrationStore store = new NxIntegrationStore();
        store.setId(UUID.randomUUID());
        store.setTenantId(tenantId);
        store.setStoreCode("ebay");
        store.setStatus("ACTIVE");
        store.setIsActive(true);
        when(storeRepository.findByTenantIdAndStoreCode(tenantId, "ebay")).thenReturn(Optional.of(store));
        when(storeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<Map<String, Object>> resp = controller.disconnect("ebay");

        assertEquals(true, resp.getBody().get("success"));
        assertEquals(false, store.getIsActive());
        assertEquals("DISCONNECTED", store.getStatus());
    }

    @Test
    void sync_recordsRealSyncLogAndCountsChannelOrders() {
        NxOrder o1 = new NxOrder();
        o1.setId(UUID.randomUUID());
        o1.setTenantId(tenantId);
        o1.setChannel("amazon");
        o1.setChannelOrderId("amz-1");
        o1.setStatus("ACTIVE");
        o1.setTotal(new BigDecimal("12.50"));
        when(orderRepository.findByTenantIdAndChannelIgnoreCase(tenantId, "amazon")).thenReturn(List.of(o1));
        when(storeRepository.findByTenantIdAndStoreCode(tenantId, "amazon")).thenReturn(Optional.empty());
        when(storeRepository.save(any())).thenAnswer(inv -> {
            NxIntegrationStore s = inv.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            return s;
        });
        when(syncLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<Map<String, Object>> resp = controller.sync("amazon");

        assertEquals(true, resp.getBody().get("success"));
        assertEquals(1, resp.getBody().get("orderCount"));
        verify(syncLogRepository).save(any(NxSyncLog.class));
    }

    @Test
    void getOrders_returnsRealOrdersWithItems() {
        NxOrder o = new NxOrder();
        o.setId(UUID.randomUUID());
        o.setTenantId(tenantId);
        o.setChannel("walmart");
        o.setChannelOrderId("wmt-301");
        o.setStatus("SHIPPED");
        o.setTotal(new BigDecimal("58.75"));
        o.setCustomerEmail("c@example.com");
        when(orderRepository.findByTenantIdAndChannelIgnoreCase(tenantId, "walmart")).thenReturn(List.of(o));
        when(orderItemRepository.findByOrderId(o.getId())).thenReturn(List.of());

        ResponseEntity<Map<String, Object>> resp = controller.getOrders("walmart");

        assertEquals(true, resp.getBody().get("success"));
        assertEquals(1, resp.getBody().get("orderCount"));
        List<?> orders = (List<?>) resp.getBody().get("orders");
        Map<?, ?> first = (Map<?, ?>) orders.get(0);
        assertEquals("wmt-301", first.get("orderId"));
        assertEquals("SHIPPED", first.get("status"));
        assertEquals(o.getId(), first.get("id"));
    }

    @Test
    void getOrderDetail_missingOrderReturnsNotFound() {
        when(orderRepository.findByTenantIdAndChannelOrderId(tenantId, "nope")).thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> resp = controller.getOrderDetail("amazon", "nope");

        assertEquals(404, resp.getStatusCode().value());
    }

    @Test
    void unknownMarketplaceReturnsNotFound() {
        ResponseEntity<Map<String, Object>> resp = controller.getOrders("etsy");

        assertEquals(404, resp.getStatusCode().value());
    }
}
