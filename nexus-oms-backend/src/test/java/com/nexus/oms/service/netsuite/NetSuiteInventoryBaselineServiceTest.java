package com.nexus.oms.service.netsuite;

import com.nexus.oms.entity.NxChannelLocationMapping;
import com.nexus.oms.entity.NxIntegrationStore;
import com.nexus.oms.entity.NxInventory;
import com.nexus.oms.entity.Product;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.repository.InventoryRepository;
import com.nexus.oms.repository.NxChannelLocationMappingRepository;
import com.nexus.oms.repository.ProductRepository;
import com.nexus.oms.service.IntegrationStoreService;
import com.nexus.oms.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NetSuiteInventoryBaselineServiceTest {

    private final NetSuiteClient client = mock(NetSuiteClient.class);
    private final IntegrationStoreService stores = mock(IntegrationStoreService.class);
    private final NxChannelLocationMappingRepository mappings = mock(NxChannelLocationMappingRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final InventoryRepository inventory = mock(InventoryRepository.class);
    private final InventoryService inventoryService = mock(InventoryService.class);
    private final UUID tenant = UUID.randomUUID(), storeId = UUID.randomUUID(), node = UUID.randomUUID();
    private NetSuiteInventoryBaselineService service;

    @BeforeEach
    void setUp() {
        service = new NetSuiteInventoryBaselineService(client, stores, mappings, products, inventory, inventoryService);
        when(stores.getStore(storeId)).thenReturn(NxIntegrationStore.builder().id(storeId).tenantId(tenant).storeCode("ns").build());
        for (String k : List.of("account_id", "consumer_key", "consumer_secret", "token_id", "token_secret")) {
            when(stores.getSetting(storeId, k)).thenReturn(k + "-value");
        }
        when(mappings.findByIntegrationStoreId(storeId)).thenReturn(List.of(NxChannelLocationMapping.builder()
                .tenantId(tenant).integrationStoreId(storeId).nodeId(node).externalLocationId("12").enabled(true).bufferUnits(0).build()));
        when(products.findByTenantIdAndSku(eq(tenant), eq("SKU-1"))).thenReturn(Optional.of(new Product()));
        when(products.findByTenantIdAndSku(eq(tenant), eq("GHOST"))).thenReturn(Optional.empty());
        when(inventory.findByTenantIdAndSkuAndNodeId(any(), any(), any())).thenReturn(Optional.empty());
        rows(row("SKU-1", 12, 40), row("SKU-1", 99, 5), row("GHOST", 12, 3));
    }

    private static Map<String, Object> row(String sku, Object loc, Object qty) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("sku", sku);
        r.put("location_id", loc);
        r.put("on_hand", qty);
        return r;
    }

    @SafeVarargs
    private void rows(Map<String, Object>... rows) {
        when(client.query(any(), anyString())).thenReturn(List.of(rows));
    }

    @Test
    void withoutAMapping_itIsBlocked_andNetSuiteIsNotCalled() {
        when(mappings.findByIntegrationStoreId(storeId)).thenReturn(List.of());
        var report = service.run(storeId, true, false);
        assertEquals("BLOCKED", report.status());
        verify(client, never()).query(any(), anyString());
    }

    @Test
    void aDryRun_countsEverything_andWritesNothing() {
        var report = service.run(storeId, false, false);

        assertEquals(1, report.created());          // SKU-1 at mapped location 12
        assertEquals(1, report.unknownSku());       // GHOST is not a Nexus product
        assertEquals(1, report.unmappedLocation()); // SKU-1 at location 99 is not mapped
        assertEquals(List.of("GHOST"), report.unknownSkus());
        verify(inventoryService, never()).importOpeningBalance(any(), any());
    }

    @Test
    void applying_createsOnlyKnownSkusAtMappedLocations_throughTheLedgerPath() {
        service.run(storeId, true, false);

        var captor = org.mockito.ArgumentCaptor.forClass(NxInventory.class);
        verify(inventoryService, times(1)).importOpeningBalance(captor.capture(), contains("NetSuite"));
        assertEquals("SKU-1", captor.getValue().getSku());
        assertEquals(40, captor.getValue().getQuantityOnHand());
        assertEquals(node, captor.getValue().getNodeId());
    }

    @Test
    void existingStockIsKeptUnlessOverwriteIsAskedFor() {
        when(inventory.findByTenantIdAndSkuAndNodeId(tenant, "SKU-1", node))
                .thenReturn(Optional.of(NxInventory.builder().tenantId(tenant).sku("SKU-1").nodeId(node).quantityOnHand(25).build()));

        var kept = service.run(storeId, true, false);
        assertEquals(1, kept.differs());
        verify(inventoryService, never()).adjustInventoryBySku(any(), any(), any(), anyInt(), any());

        var overwritten = service.run(storeId, true, true);
        verify(inventoryService).adjustInventoryBySku(eq(tenant), eq("SKU-1"), eq(node), eq(15), contains("overwrite"));
        assertEquals(1, overwritten.updated());
    }

    @Test
    void ungroupedRowsAreSummed_andFloatingLocationIdsAreNormalised() {
        rows(row("SKU-1", 12.0, 10), row("SKU-1", "12", 5.0));

        service.run(storeId, true, false);

        var captor = org.mockito.ArgumentCaptor.forClass(NxInventory.class);
        verify(inventoryService).importOpeningBalance(captor.capture(), anyString());
        assertEquals(15, captor.getValue().getQuantityOnHand());
    }

    @Test
    void aQueryThatLacksTheRequiredColumns_failsLoudly() {
        Map<String, Object> bad = new LinkedHashMap<>();
        bad.put("itemid", "SKU-1");
        rows(bad);

        BadRequestException e = assertThrows(BadRequestException.class, () -> service.run(storeId, false, false));
        assertTrue(e.getMessage().contains("sku, location_id and on_hand"), e.getMessage());
    }

    @Test
    void aConfiguredQueryReplacesTheDefault() {
        when(stores.getSetting(storeId, "inventory_query")).thenReturn("SELECT 1 AS sku");
        service.run(storeId, false, false);
        verify(client).query(any(), eq("SELECT 1 AS sku"));
    }

    @Test
    void theDefaultQueryAsksForTheThreeColumnsItNeeds() {
        service.run(storeId, false, false);
        verify(client).query(any(), argThat(q -> q.contains("AS sku") && q.contains("AS location_id") && q.contains("AS on_hand")));
    }
}
