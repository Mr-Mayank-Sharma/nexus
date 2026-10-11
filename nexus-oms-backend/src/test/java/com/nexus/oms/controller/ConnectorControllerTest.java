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
import com.nexus.oms.service.IntegrationStoreService;
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
    @Mock private IntegrationStoreService storeService;

    private ConnectorController controller;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        controller = new ConnectorController(storeRepository, settingRepository, syncLogRepository,
                orderRepository, orderItemRepository, inventoryRepository, productRepository, storeService);
        tenantId = UUID.randomUUID();
        TenantContext.setCurrentTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void status_neverClaimsAConnection_evenWhenCredentialsAreSaved() {
        NxIntegrationStore store = new NxIntegrationStore();
        store.setId(UUID.randomUUID());
        store.setIsActive(true);
        store.setStatus("ACTIVE");
        NxIntegrationStoreSetting marker = new NxIntegrationStoreSetting();
        marker.setSettingType("credentials");
        marker.setSettingValue("saved");
        when(storeRepository.findByTenantIdAndStoreCode(any(), eq("amazon"))).thenReturn(Optional.of(store));
        when(storeRepository.findByTenantIdAndStoreCode(any(), argThat(m -> !"amazon".equals(m)))).thenReturn(Optional.empty());
        when(settingRepository.findByStoreId(store.getId())).thenReturn(List.of(marker));
        when(orderRepository.countByTenantIdAndChannelIgnoreCase(any(), any())).thenReturn(3L);

        Map<String, Object> res = controller.status().getBody();

        Map<?, ?> connectors = (Map<?, ?>) res.get("connectors");
        assertEquals(4, connectors.size());
        Map<?, ?> amazon = (Map<?, ?>) connectors.get("amazon");
        assertEquals(false, amazon.get("connected"), "nothing can verify a marketplace connection");
        assertEquals(true, amazon.get("hasCredentials"));
        assertEquals("CREDENTIALS_SAVED_UNVERIFIED", amazon.get("state"));
        assertEquals(false, amazon.get("liveSync"));
        assertEquals(3L, amazon.get("ordersInNexus"));
        Map<?, ?> ebay = (Map<?, ?>) connectors.get("ebay");
        assertEquals("NOT_CONFIGURED", ebay.get("state"));
        assertEquals(false, ebay.get("hasCredentials"));
    }

    @Test
    void configure_sealsSecrets_andKeepsThemOutOfTheStoredConfig() {
        when(storeRepository.findByTenantIdAndStoreCode(any(), any())).thenReturn(Optional.empty());
        when(storeRepository.save(any())).thenAnswer(inv -> {
            NxIntegrationStore s = inv.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            return s;
        });
        when(settingRepository.findByStoreIdAndSettingType(any(), any())).thenReturn(Optional.empty());
        when(settingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<Map<String, Object>> resp = controller.configure(Map.of(
                "marketplace", "amazon", "endpoint", "https://api.amazon.test", "marketplaceId", "ATVPDKIKX0DER",
                "clientSecret", "super-secret", "refreshToken", "rt-123"));

        assertTrue((Boolean) resp.getBody().get("success"));
        assertEquals(false, resp.getBody().get("verified"));
        org.mockito.ArgumentCaptor<NxIntegrationStore> saved = org.mockito.ArgumentCaptor.forClass(NxIntegrationStore.class);
        verify(storeRepository).save(saved.capture());
        String config = saved.getValue().getConfigJson();
        assertTrue(config.contains("ATVPDKIKX0DER"));
        assertFalse(config.contains("super-secret"), "a secret must not be stored in the plain config");
        assertFalse(config.contains("rt-123"));
        verify(storeService).updateSetting(any(), eq("clientSecret"), eq("super-secret"));
        verify(storeService).updateSetting(any(), eq("refreshToken"), eq("rt-123"));
        assertEquals("PENDING_VERIFICATION", saved.getValue().getStatus());
    }

    @Test
    void configure_withoutSecrets_doesNotPretendCredentialsExist() {
        when(storeRepository.findByTenantIdAndStoreCode(any(), any())).thenReturn(Optional.empty());
        when(storeRepository.save(any())).thenAnswer(inv -> {
            NxIntegrationStore s = inv.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            return s;
        });

        controller.configure(Map.of("marketplace", "walmart", "channelType", "WFS"));

        verify(storeService, never()).updateSetting(any(), any(), any());
        verify(settingRepository, never()).save(any(NxIntegrationStoreSetting.class));
    }

    @Test
    void configure_unknownMarketplaceReturnsBadRequest() {
        ResponseEntity<Map<String, Object>> resp = controller.configure(Map.of("marketplace", "etsy"));

        assertEquals(400, resp.getStatusCode().value());
        assertEquals(false, resp.getBody().get("success"));
        verify(storeRepository, never()).save(any());
    }

    @Test
    void authorize_savesTheTokenSealed_butDoesNotMarkItConnectedOrVerified() {
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
        assertEquals(false, resp.getBody().get("verified"));
        assertEquals("PENDING_VERIFICATION", store.getStatus(), "saving a token is not a verified connection");
        verify(storeService).updateSetting(store.getId(), "access_token", "tok-123");
        org.mockito.ArgumentCaptor<NxIntegrationStoreSetting> marker = org.mockito.ArgumentCaptor.forClass(NxIntegrationStoreSetting.class);
        verify(settingRepository).save(marker.capture());
        assertEquals("saved", marker.getValue().getSettingValue(), "the marker is a flag, never the token");
    }

    @Test
    void authorize_requiresAToken_insteadOfRecordingAFictionalOne() {
        ResponseEntity<Map<String, Object>> resp = controller.authorize("amazon", Map.of());

        assertEquals(400, resp.getStatusCode().value());
        verify(storeService, never()).updateSetting(any(), any(), any());
        verify(storeRepository, never()).save(any());
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
    void sync_isHonestlyUnavailable_andRecordsNothing() {
        ResponseEntity<Map<String, Object>> resp = controller.sync("amazon");

        assertEquals(501, resp.getStatusCode().value());
        assertEquals(false, resp.getBody().get("success"));
        assertEquals(false, resp.getBody().get("implemented"));
        assertTrue(String.valueOf(resp.getBody().get("error")).contains("not available"));
        verifyNoInteractions(syncLogRepository);      // no fabricated SUCCESS history
        verify(storeRepository, never()).save(any());  // and no connector record created by a sync
        verifyNoInteractions(orderRepository);
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
        assertEquals("NEXUS_RECORDS", resp.getBody().get("source"), "these are Nexus's own orders, and the response says so");
        assertEquals(false, resp.getBody().get("live"));
    }

    @Test
    void inventoryAndListings_areLabelledAsNexusOwnData() {
        when(inventoryRepository.findByTenantId(tenantId)).thenReturn(List.of());
        when(productRepository.findByTenantId(any(), any())).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

        Map<String, Object> inventory = controller.getInventory("amazon").getBody();
        Map<String, Object> listings = controller.getListings("amazon").getBody();

        assertEquals("NEXUS_RECORDS", inventory.get("source"));
        assertEquals(false, inventory.get("live"));
        assertEquals("NEXUS_RECORDS", listings.get("source"));
        assertEquals(false, listings.get("live"));
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
