package com.nexus.oms.service.shopify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.entity.NxChannelLocationMapping;
import com.nexus.oms.entity.NxIntegrationStore;
import com.nexus.oms.entity.NxInventory;
import com.nexus.oms.entity.NxProductMapping;
import com.nexus.oms.repository.InventoryRepository;
import com.nexus.oms.repository.NxChannelLocationMappingRepository;
import com.nexus.oms.repository.NxProductMappingRepository;
import com.nexus.oms.service.IntegrationStoreService;
import com.nexus.oms.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** A new merchant's first question: "where is my stock?". The import must be safe to run against a live shop. */
class ShopifyInventoryBaselineServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ShopifyClient client = mock(ShopifyClient.class);
    private final IntegrationStoreService stores = mock(IntegrationStoreService.class);
    private final ShopifyTokenService tokens = mock(ShopifyTokenService.class);
    private final ShopifyInventorySyncService sync = mock(ShopifyInventorySyncService.class);
    private final NxChannelLocationMappingRepository mappings = mock(NxChannelLocationMappingRepository.class);
    private final NxProductMappingRepository products = mock(NxProductMappingRepository.class);
    private final InventoryRepository inventory = mock(InventoryRepository.class);
    private final InventoryService inventoryService = mock(InventoryService.class);

    private final UUID tenant = UUID.randomUUID();
    private final UUID storeId = UUID.randomUUID();
    private final UUID node = UUID.randomUUID();
    private ShopifyInventoryBaselineService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new ShopifyInventoryBaselineService(client, stores, tokens, sync, mappings, products, inventory, inventoryService);
        when(stores.getStore(storeId)).thenReturn(NxIntegrationStore.builder().id(storeId).tenantId(tenant).storeCode("acme").build());
        when(stores.getSetting(storeId, "shop_domain")).thenReturn("acme.myshopify.com");
        when(tokens.getAccessToken(storeId)).thenReturn("tok");
        when(mappings.findByIntegrationStoreId(storeId)).thenReturn(List.of(NxChannelLocationMapping.builder()
                .tenantId(tenant).integrationStoreId(storeId).nodeId(node).externalLocationId("500").enabled(true).bufferUnits(0).build()));
        when(sync.fetchInventoryItems("acme.myshopify.com", "tok")).thenReturn(Map.of(10L, 100L, 20L, 200L));
        when(products.findByTenantId(tenant)).thenReturn(List.of(product("SKU-1", 10L), product("SKU-2", 20L)));
        // item 100 holds 12 at location 500 (on_hand); item 200 is not stocked at 500 at all
        when(client.graphql(eq("acme.myshopify.com"), eq("tok"), anyString(), anyMap())).thenReturn(data(
                "{\"nodes\":[{\"id\":\"gid://shopify/InventoryItem/100\",\"inventoryLevels\":{\"nodes\":[{\"location\":{\"id\":\"gid://shopify/Location/500\"},"
                        + "\"quantities\":[{\"name\":\"on_hand\",\"quantity\":12}]}]}},"
                        + "{\"id\":\"gid://shopify/InventoryItem/200\",\"inventoryLevels\":{\"nodes\":[]}}]}"));
        when(inventory.findByTenantIdAndSkuAndNodeId(any(), any(), any())).thenReturn(Optional.empty());
    }

    private static JsonNode data(String json) throws Exception {
        return JSON.readTree(json);
    }

    private NxProductMapping product(String sku, Long variant) {
        return NxProductMapping.builder().tenantId(tenant).nexusSku(sku).bcVariantId(variant).bcProductId(1L).build();
    }

    private NxInventory row(String sku, int onHand) {
        return NxInventory.builder().id(UUID.randomUUID()).tenantId(tenant).sku(sku).nodeId(node).quantityOnHand(onHand).build();
    }

    @Test
    void withoutALocationMapping_itSaysSo_andDoesNotCallShopify() {
        when(mappings.findByIntegrationStoreId(storeId)).thenReturn(List.of());

        var report = service.run(storeId, true, false);

        assertEquals("BLOCKED", report.status());
        assertTrue(report.message().contains("Map each Shopify location"));
        verifyNoInteractions(client, inventoryService);
    }

    @Test
    void aDryRun_reportsWhatWouldHappen_andWritesNothing() {
        var report = service.run(storeId, false, false);

        assertTrue(report.dryRun());
        assertEquals(1, report.created());
        assertEquals(1, report.untracked());   // SKU-2 is not stocked at the mapped location
        assertTrue(report.message().startsWith("Dry run: nothing was written"));
        verify(inventoryService, never()).importOpeningBalance(any(), any());
        verify(inventoryService, never()).adjustInventoryBySku(any(), any(), any(), anyInt(), any());
    }

    @Test
    void applying_createsTheRowFromPhysicalOnHand_notFromAvailable() {
        var report = service.run(storeId, true, false);

        ArgumentCaptor<NxInventory> created = ArgumentCaptor.forClass(NxInventory.class);
        verify(inventoryService).importOpeningBalance(created.capture(), contains("Shopify on-hand"));
        assertEquals("SKU-1", created.getValue().getSku());
        assertEquals(node, created.getValue().getNodeId());
        assertEquals(12, created.getValue().getQuantityOnHand());
        assertEquals(0, created.getValue().getQuantityAllocated(), "units already committed in Shopify are not pre-reserved here");
        assertEquals(1, report.created());
        // The query asks for on_hand, so a later open-order import cannot double-count committed units.
        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        verify(client).graphql(any(), any(), query.capture(), anyMap());
        assertTrue(query.getValue().contains("on_hand"));
    }

    @Test
    void stockNexusAlreadyManages_isReportedButNotOverwritten_unlessAskedFor() {
        when(inventory.findByTenantIdAndSkuAndNodeId(tenant, "SKU-1", node)).thenReturn(Optional.of(row("SKU-1", 7)));

        var report = service.run(storeId, true, false);

        assertEquals(1, report.differs());
        assertEquals(0, report.created());
        assertEquals(7, report.differences().get(0).nexusOnHand());
        assertEquals(12, report.differences().get(0).shopifyOnHand());
        verify(inventoryService, never()).importOpeningBalance(any(), any());
        verify(inventoryService, never()).adjustInventoryBySku(any(), any(), any(), anyInt(), any());
    }

    @Test
    void overwrite_adjustsThroughTheLedger_byTheDifference() {
        when(inventory.findByTenantIdAndSkuAndNodeId(tenant, "SKU-1", node)).thenReturn(Optional.of(row("SKU-1", 7)));

        var report = service.run(storeId, true, true);

        verify(inventoryService).adjustInventoryBySku(eq(tenant), eq("SKU-1"), eq(node), eq(5), contains("overwrite"));
        assertEquals(1, report.updated());
    }

    @Test
    void aMatchingRow_isLeftAlone() {
        when(inventory.findByTenantIdAndSkuAndNodeId(tenant, "SKU-1", node)).thenReturn(Optional.of(row("SKU-1", 12)));

        var report = service.run(storeId, true, true);

        assertEquals(1, report.unchanged());
        verify(inventoryService, never()).adjustInventoryBySku(any(), any(), any(), anyInt(), any());
    }

    @Test
    void itemsAreReadInBatches_andGlobalIdsAreParsed() throws Exception {
        var many = new java.util.ArrayList<Long>();
        for (long i = 1; i <= 120; i++) many.add(i);
        when(client.graphql(any(), any(), anyString(), anyMap())).thenReturn(data("{\"nodes\":[]}"));

        var result = service.readOnHand("acme.myshopify.com", "tok", many);

        assertTrue(result.isEmpty());
        verify(client, times(3)).graphql(any(), any(), anyString(), anyMap());   // 50 + 50 + 20
    }
}
