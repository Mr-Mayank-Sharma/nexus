package com.nexus.oms.service.shopify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.entity.*;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.repository.*;
import com.nexus.oms.service.IntegrationStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ShopifyInventoryPublishTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final UUID tenant = UUID.randomUUID();
    private final UUID storeId = UUID.randomUUID();
    private final UUID storeA = UUID.randomUUID();
    private final UUID storeB = UUID.randomUUID();

    private final ShopifyClient client = mock(ShopifyClient.class);
    private final IntegrationStoreService stores = mock(IntegrationStoreService.class);
    private final ShopifyTokenService tokens = mock(ShopifyTokenService.class);
    private final NxIntegrationSyncConfigRepository syncConfigs = mock(NxIntegrationSyncConfigRepository.class);
    private final NxSyncLogRepository syncLogs = mock(NxSyncLogRepository.class);
    private final NxProductMappingRepository products = mock(NxProductMappingRepository.class);
    private final InventoryRepository inventory = mock(InventoryRepository.class);
    private final WarehouseRepository warehouses = mock(WarehouseRepository.class);
    private final NxChannelLocationMappingRepository mappings = mock(NxChannelLocationMappingRepository.class);
    private final NxInventoryPublishRunRepository runs = mock(NxInventoryPublishRunRepository.class);
    private final NxInventoryPublishLineRepository lines = mock(NxInventoryPublishLineRepository.class);
    private ShopifyInventorySyncService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new ShopifyInventorySyncService(client, stores, tokens, syncConfigs, syncLogs, products, inventory,
                warehouses, mappings, runs, lines, mock(PlatformTransactionManager.class), JSON);
        NxIntegrationStore store = NxIntegrationStore.builder().id(storeId).tenantId(tenant).storeCode("acme").build();
        when(stores.getStore(storeId)).thenReturn(store);
        when(stores.getSetting(storeId, "shop_domain")).thenReturn("acme.myshopify.com");
        when(tokens.getAccessToken(storeId)).thenReturn("tok");
        when(runs.saveAndFlush(any())).thenAnswer(inv -> {
            NxInventoryPublishRun r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
        when(runs.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(runs.findFirstByIntegrationStoreIdAndDryRunFalseAndStatusInOrderByStartedAtDesc(any(), any())).thenReturn(Optional.empty());
        when(warehouses.findAllById(any())).thenReturn(List.of(wh(storeA, "Store A"), wh(storeB, "Store B")));

        when(client.getLocations(any(), any())).thenReturn(json("{\"locations\":[{\"id\":111,\"active\":true},{\"id\":222,\"active\":true},{\"id\":333,\"active\":false}]}"));
        when(client.getProductsPage(any(), any(), any())).thenReturn(new ShopifyClient.ProductPage(
                json("{\"products\":[{\"id\":1,\"variants\":[{\"id\":10,\"inventory_item_id\":100}]}]}"), null));
        when(products.findByTenantId(tenant)).thenReturn(List.of(product("SKU-1", 10L)));
        when(inventory.findByTenantId(tenant)).thenReturn(List.of(stock(storeA, "SKU-1", 30), stock(storeB, "SKU-1", 50)));
        mapTwoStores();
    }

    private void mapTwoStores() {
        when(mappings.findByIntegrationStoreId(storeId)).thenReturn(List.of(map(storeA, "111"), map(storeB, "222")));
    }

    private static JsonNode json(String s) throws Exception {
        return JSON.readTree(s);
    }

    private Warehouse wh(UUID id, String name) {
        Warehouse w = new Warehouse();
        w.setId(id);
        w.setTenantId(tenant);
        w.setName(name);
        w.setIsActive(true);
        return w;
    }

    private NxProductMapping product(String sku, Long variant) {
        return NxProductMapping.builder().tenantId(tenant).nexusSku(sku).bcVariantId(variant).bcProductId(1L).build();
    }

    private NxInventory stock(UUID node, String sku, int onHand) {
        return NxInventory.builder().id(UUID.randomUUID()).tenantId(tenant).sku(sku).nodeId(node).quantityOnHand(onHand)
                .quantityAllocated(0).quantityReserved(0).quantityDamaged(0).safetyStock(0).build();
    }

    private NxChannelLocationMapping map(UUID node, String location) {
        return NxChannelLocationMapping.builder().tenantId(tenant).integrationStoreId(storeId).nodeId(node)
                .externalLocationId(location).enabled(true).bufferUnits(0).build();
    }

    private void shopifyHolds(String levelsJson) throws Exception {
        when(client.getInventoryLevels(any(), any(), any())).thenReturn(json(levelsJson));
    }

    @SuppressWarnings("unchecked")
    private List<NxInventoryPublishLine> savedLines() {
        ArgumentCaptor<List<NxInventoryPublishLine>> captor = ArgumentCaptor.forClass(List.class);
        verify(lines).saveAll(captor.capture());
        return captor.getValue();
    }

    private NxInventoryPublishLine line(List<NxInventoryPublishLine> all, String location) {
        return all.stream().filter(l -> l.getExternalLocationId().equals(location)).findFirst().orElseThrow();
    }

    @Test
    void withoutMappings_nothingIsPublished_andShopifyIsNeverCalled() {
        when(mappings.findByIntegrationStoreId(storeId)).thenReturn(List.of());

        NxInventoryPublishRun run = service.publish(storeId, false, "alice");

        assertEquals(NxInventoryPublishRun.BLOCKED, run.getStatus());
        assertTrue(run.getMessage().contains("No inventory location mapping"));
        verifyNoInteractions(client);
    }

    @Test
    void repeatedBlockedAttempts_updateOneRecordInsteadOfAddingOnePerAttempt() {
        when(mappings.findByIntegrationStoreId(storeId)).thenReturn(List.of());
        NxInventoryPublishRun first = service.publish(storeId, false, "scheduler");
        when(runs.findFirstByIntegrationStoreIdOrderByStartedAtDesc(storeId)).thenReturn(Optional.of(first));

        service.publish(storeId, false, "scheduler");

        ArgumentCaptor<NxInventoryPublishRun> saved = ArgumentCaptor.forClass(NxInventoryPublishRun.class);
        verify(runs, times(2)).save(saved.capture());
        assertSame(saved.getAllValues().get(0), saved.getAllValues().get(1), "the second attempt updated the same record");
        verify(runs, never()).saveAndFlush(any());
    }

    @Test
    void eachStorePublishesToItsOwnShopifyLocation_notTheSum() throws Exception {
        shopifyHolds("{\"inventory_levels\":[{\"inventory_item_id\":100,\"location_id\":111,\"available\":5}]}");

        NxInventoryPublishRun run = service.publish(storeId, false, "alice");

        verify(client).setInventoryLevel(eq("acme.myshopify.com"), eq("tok"), argThat(m ->
                Long.valueOf(100).equals(m.get("inventory_item_id")) && Long.valueOf(111).equals(m.get("location_id")) && Integer.valueOf(30).equals(m.get("available"))));
        verify(client).setInventoryLevel(eq("acme.myshopify.com"), eq("tok"), argThat(m ->
                Long.valueOf(222).equals(m.get("location_id")) && Integer.valueOf(50).equals(m.get("available"))));
        verify(client, never()).setInventoryLevel(any(), any(), argThat(m -> Integer.valueOf(80).equals(m.get("available"))));
        assertEquals(NxInventoryPublishRun.COMPLETED, run.getStatus());
        assertEquals(2, run.getItemsPublished());
    }

    @Test
    void anItemNotYetStockedAtTheLocation_isConnectedBeforeItsQuantityIsSet() throws Exception {
        shopifyHolds("{\"inventory_levels\":[{\"inventory_item_id\":100,\"location_id\":111,\"available\":5}]}");

        service.publish(storeId, false, "alice");

        verify(client).connectInventoryLevel(any(), any(), argThat(m -> Long.valueOf(222).equals(m.get("location_id"))));
        verify(client, never()).connectInventoryLevel(any(), any(), argThat(m -> Long.valueOf(111).equals(m.get("location_id"))));
    }

    @Test
    void aDryRun_writesNothingToShopify_butRecordsThePlan() throws Exception {
        shopifyHolds("{\"inventory_levels\":[{\"inventory_item_id\":100,\"location_id\":111,\"available\":30},{\"inventory_item_id\":100,\"location_id\":222,\"available\":10}]}");

        NxInventoryPublishRun run = service.publish(storeId, true, "alice");

        verify(client, never()).setInventoryLevel(any(), any(), any());
        verify(client, never()).connectInventoryLevel(any(), any(), any());
        List<NxInventoryPublishLine> saved = savedLines();
        assertEquals(NxInventoryPublishLine.UNCHANGED, line(saved, "111").getOutcome());
        assertEquals(NxInventoryPublishLine.PLANNED, line(saved, "222").getOutcome());
        assertEquals(10, line(saved, "222").getChannelQtyBefore());
        assertEquals(50, line(saved, "222").getCalculatedQty());
        assertTrue(line(saved, "222").getCalculation().contains("Store B"), "the per-node arithmetic is recorded");
        assertTrue(run.getMessage().startsWith("Dry run"));
        assertEquals(1, run.getItemsPlanned());
    }

    @Test
    void quantitiesAlreadyCorrect_areNotRewritten() throws Exception {
        shopifyHolds("{\"inventory_levels\":[{\"inventory_item_id\":100,\"location_id\":111,\"available\":30},{\"inventory_item_id\":100,\"location_id\":222,\"available\":50}]}");

        NxInventoryPublishRun run = service.publish(storeId, false, "alice");

        verify(client, never()).setInventoryLevel(any(), any(), any());
        assertEquals(2, run.getItemsUnchanged());
    }

    @Test
    void aQuantityChangedInShopifyAdmin_isFlaggedAsDrift_andOverwritten() throws Exception {
        NxInventoryPublishRun last = NxInventoryPublishRun.builder().id(UUID.randomUUID()).build();
        when(runs.findFirstByIntegrationStoreIdAndDryRunFalseAndStatusInOrderByStartedAtDesc(any(), any())).thenReturn(Optional.of(last));
        when(lines.findByRunIdOrderBySkuAscExternalLocationIdAsc(eq(last.getId()), any(Pageable.class))).thenReturn(List.of(
                NxInventoryPublishLine.builder().sku("SKU-1").externalLocationId("111").outcome(NxInventoryPublishLine.PUBLISHED).publishedQty(30).build(),
                NxInventoryPublishLine.builder().sku("SKU-1").externalLocationId("222").outcome(NxInventoryPublishLine.PUBLISHED).publishedQty(50).build()));
        shopifyHolds("{\"inventory_levels\":[{\"inventory_item_id\":100,\"location_id\":111,\"available\":12},{\"inventory_item_id\":100,\"location_id\":222,\"available\":50}]}");

        service.publish(storeId, false, "alice");

        List<NxInventoryPublishLine> saved = savedLines();
        assertTrue(line(saved, "111").getDrift());
        assertEquals(NxInventoryPublishLine.PUBLISHED, line(saved, "111").getOutcome());
        verify(client).setInventoryLevel(any(), any(), argThat(m -> Long.valueOf(111).equals(m.get("location_id")) && Integer.valueOf(30).equals(m.get("available"))));
    }

    @Test
    void oneFailingWrite_doesNotStopTheOthers_andTheRunIsPartial() throws Exception {
        shopifyHolds("{\"inventory_levels\":[{\"inventory_item_id\":100,\"location_id\":111,\"available\":5},{\"inventory_item_id\":100,\"location_id\":222,\"available\":5}]}");
        when(client.setInventoryLevel(any(), any(), argThat(m -> m != null && Long.valueOf(111).equals(m.get("location_id")))))
                .thenThrow(new BadRequestException("Shopify API error: 422"));

        NxInventoryPublishRun run = service.publish(storeId, false, "alice");

        assertEquals(NxInventoryPublishRun.PARTIAL, run.getStatus());
        assertEquals(1, run.getItemsPublished());
        assertEquals(1, run.getItemsFailed());
        assertTrue(line(savedLines(), "111").getError().contains("422"));
    }

    @Test
    void aDeactivatedOrMissingShopifyLocation_isSkippedWithAReason() throws Exception {
        when(mappings.findByIntegrationStoreId(storeId)).thenReturn(List.of(map(storeA, "333"), map(storeB, "999")));
        shopifyHolds("{\"inventory_levels\":[]}");

        NxInventoryPublishRun run = service.publish(storeId, false, "alice");

        verify(client, never()).setInventoryLevel(any(), any(), any());
        List<NxInventoryPublishLine> saved = savedLines();
        assertTrue(line(saved, "333").getError().contains("deactivated"));
        assertTrue(line(saved, "999").getError().contains("no longer exists"));
        assertEquals(2, run.getItemsSkipped());
    }

    @Test
    void aSkuWithoutAShopifyVariant_isSkipped() throws Exception {
        when(products.findByTenantId(tenant)).thenReturn(List.of(product("SKU-1", 10L), product("SKU-2", 77L)));
        when(inventory.findByTenantId(tenant)).thenReturn(List.of(stock(storeA, "SKU-1", 30), stock(storeA, "SKU-2", 9)));
        shopifyHolds("{\"inventory_levels\":[]}");

        service.publish(storeId, true, "alice");

        List<NxInventoryPublishLine> skipped = savedLines().stream().filter(l -> l.getSku().equals("SKU-2")).toList();
        assertFalse(skipped.isEmpty());
        assertTrue(skipped.stream().allMatch(l -> NxInventoryPublishLine.SKIPPED.equals(l.getOutcome())));
    }

    @Test
    void theCatalogueIsReadAcrossEveryPage() throws Exception {
        when(client.getProductsPage(any(), any(), argThat(p -> p != null && !p.containsKey("page_info")))).thenReturn(new ShopifyClient.ProductPage(
                json("{\"products\":[{\"id\":1,\"variants\":[{\"id\":10,\"inventory_item_id\":100}]}]}"), "NEXT"));
        when(client.getProductsPage(any(), any(), argThat(p -> p != null && "NEXT".equals(p.get("page_info"))))).thenReturn(new ShopifyClient.ProductPage(
                json("{\"products\":[{\"id\":2,\"variants\":[{\"id\":20,\"inventory_item_id\":200}]}]}"), null));
        when(products.findByTenantId(tenant)).thenReturn(List.of(product("SKU-1", 10L), product("SKU-2", 20L)));
        when(inventory.findByTenantId(tenant)).thenReturn(List.of(stock(storeA, "SKU-1", 30), stock(storeA, "SKU-2", 9)));
        shopifyHolds("{\"inventory_levels\":[]}");

        service.publish(storeId, true, "alice");

        List<NxInventoryPublishLine> saved = savedLines();
        assertNotNull(saved.stream().filter(l -> l.getSku().equals("SKU-2") && l.getInventoryItemId() != null).findFirst().orElse(null),
                "a variant on page two is found, not treated as missing");
    }

    @Test
    void aSecondConcurrentRun_isRefusedByTheDatabaseGuard() {
        doThrow(new DataIntegrityViolationException("uq_publish_runs_one_running")).when(runs).saveAndFlush(any());

        BadRequestException e = assertThrows(BadRequestException.class, () -> service.publish(storeId, false, "alice"));

        assertTrue(e.getMessage().contains("already running"));
        verifyNoInteractions(client);
    }

    @Test
    void aShopifyOutage_failsTheRunCleanly_andRecordsWhy() {
        when(client.getLocations(any(), any())).thenThrow(new BadRequestException("Shopify API error: 503"));

        NxInventoryPublishRun run = service.publish(storeId, false, "alice");

        assertEquals(NxInventoryPublishRun.FAILED, run.getStatus());
        assertTrue(run.getMessage().contains("503"));
        verify(client, never()).setInventoryLevel(any(), any(), any());
    }

    @Test
    void aScheduledPassThatChangedNothing_leavesNoHistoryRow() throws Exception {
        shopifyHolds("{\"inventory_levels\":[{\"inventory_item_id\":100,\"location_id\":111,\"available\":30},{\"inventory_item_id\":100,\"location_id\":222,\"available\":50}]}");

        service.publish(storeId, false, ShopifyInventorySyncService.SCHEDULER);

        verify(runs).delete(any(NxInventoryPublishRun.class));
        verify(lines, never()).saveAll(any());
    }

    @Test
    void theLegacyEntryPointSummarisesTheRun() throws Exception {
        shopifyHolds("{\"inventory_levels\":[{\"inventory_item_id\":100,\"location_id\":111,\"available\":5}]}");

        var result = service.pushInventory(storeId);

        assertEquals(2, result.getItemsSucceeded());
        assertEquals(0, result.getItemsFailed());
    }
}
