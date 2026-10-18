package com.nexus.oms.service.netsuite;

import com.nexus.oms.entity.NxChannelLocationMapping;
import com.nexus.oms.entity.NxIntegrationStore;
import com.nexus.oms.entity.NxInventory;
import com.nexus.oms.repository.InventoryRepository;
import com.nexus.oms.repository.NxChannelLocationMappingRepository;
import com.nexus.oms.repository.ProductRepository;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.IntegrationStoreService;
import com.nexus.oms.service.InventoryService;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Brings stock counts in from NetSuite, the same way the Shopify baseline does and under the same rules:
 * dry run by default, never overwrites stock Nexus already manages unless asked, writes through the ledger,
 * and pushes nothing back out. A SKU NetSuite has but Nexus does not know is reported, not invented.
 *
 * <p>The default query reads NetSuite's inventory balance per item and location. It is a setting
 * ({@code inventory_query}) because account layouts differ; it must return the columns {@code sku},
 * {@code location_id} and {@code on_hand}. <b>Not yet run against a real account.</b>
 */
@Service
public class NetSuiteInventoryBaselineService {

    static final int MAX_LISTED = 200;

    public static final String DEFAULT_QUERY = """
            SELECT item.itemid AS sku, ib.location AS location_id, SUM(ib.quantityonhand) AS on_hand
            FROM inventorybalance ib JOIN item ON item.id = ib.item
            GROUP BY item.itemid, ib.location""";

    private final NetSuiteClient client;
    private final IntegrationStoreService storeService;
    private final NxChannelLocationMappingRepository mappingRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryService inventoryService;

    public NetSuiteInventoryBaselineService(NetSuiteClient client, IntegrationStoreService storeService,
                                            NxChannelLocationMappingRepository mappingRepository,
                                            ProductRepository productRepository, InventoryRepository inventoryRepository,
                                            InventoryService inventoryService) {
        this.client = client;
        this.storeService = storeService;
        this.mappingRepository = mappingRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryService = inventoryService;
    }

    public record Difference(String sku, UUID nodeId, int nexusOnHand, int netsuiteOnHand) {}

    public record Report(UUID storeId, boolean dryRun, boolean overwrite, String status, String message,
                         int rows, int created, int updated, int unchanged, int differs,
                         int unknownSku, int unmappedLocation, List<String> unknownSkus, List<Difference> differences) {}

    public NetSuiteClient.Credentials credentials(UUID storeId) {
        return new NetSuiteClient.Credentials(
                storeService.getSetting(storeId, "account_id"),
                storeService.getSetting(storeId, "consumer_key"),
                storeService.getSetting(storeId, "consumer_secret"),
                storeService.getSetting(storeId, "token_id"),
                storeService.getSetting(storeId, "token_secret"));
    }

    public Report run(UUID storeId, boolean apply, boolean overwrite) {
        NxIntegrationStore store = storeService.getStore(storeId);
        boolean ownsContext = TenantContext.findCurrentTenantId() == null;
        if (ownsContext) TenantContext.setCurrentTenantId(store.getTenantId());
        try {
            return doRun(store, apply, overwrite);
        } finally {
            if (ownsContext) TenantContext.clear();
        }
    }

    private Report doRun(NxIntegrationStore store, boolean apply, boolean overwrite) {
        UUID storeId = store.getId();
        UUID tenantId = store.getTenantId();
        boolean dry = !apply;

        Map<String, UUID> nodeByLocation = new HashMap<>();
        for (NxChannelLocationMapping m : mappingRepository.findByIntegrationStoreId(storeId)) {
            if (Boolean.TRUE.equals(m.getEnabled())) nodeByLocation.put(m.getExternalLocationId(), m.getNodeId());
        }
        if (nodeByLocation.isEmpty()) {
            return new Report(storeId, dry, overwrite, "BLOCKED",
                    "No location mapping is set. Map each NetSuite location to the Nexus warehouse or store that holds that stock.",
                    0, 0, 0, 0, 0, 0, 0, List.of(), List.of());
        }

        String configured = storeService.getSetting(storeId, "inventory_query");
        String suiteQl = configured == null || configured.isBlank() ? DEFAULT_QUERY : configured;
        List<Map<String, Object>> rows = client.query(credentials(storeId), suiteQl);

        // A query that is not grouped can return several rows per SKU and location; they are summed.
        Map<String, Map<String, Integer>> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object sku = row.get("sku"), loc = row.get("location_id"), qty = row.get("on_hand");
            if (sku == null || loc == null || qty == null) {
                throw new com.nexus.oms.exception.BadRequestException(
                        "The inventory query must return the columns sku, location_id and on_hand; got " + row.keySet());
            }
            counts.computeIfAbsent(String.valueOf(sku).trim(), k -> new LinkedHashMap<>())
                    .merge(String.valueOf(loc).replaceAll("\\.0+$", ""), (int) Math.round(Double.parseDouble(String.valueOf(qty))), Integer::sum);
        }

        int created = 0, updated = 0, unchanged = 0, differs = 0, unknownSku = 0, unmapped = 0;
        List<String> unknownSkus = new ArrayList<>();
        List<Difference> listed = new ArrayList<>();
        for (Map.Entry<String, Map<String, Integer>> bySku : counts.entrySet()) {
            String sku = bySku.getKey();
            boolean known = productRepository.findByTenantIdAndSku(tenantId, sku).isPresent();
            for (Map.Entry<String, Integer> atLocation : bySku.getValue().entrySet()) {
                UUID nodeId = nodeByLocation.get(atLocation.getKey());
                if (nodeId == null) {
                    unmapped++;
                    continue;
                }
                if (!known) {
                    unknownSku++;
                    if (unknownSkus.size() < MAX_LISTED && !unknownSkus.contains(sku)) unknownSkus.add(sku);
                    continue;
                }
                int theirs = Math.max(0, atLocation.getValue());
                Optional<NxInventory> existing = inventoryRepository.findByTenantIdAndSkuAndNodeId(tenantId, sku, nodeId);
                if (existing.isEmpty()) {
                    created++;
                    if (apply) {
                        inventoryService.importOpeningBalance(NxInventory.builder()
                                .tenantId(tenantId).sku(sku).nodeId(nodeId).quantityOnHand(theirs)
                                .quantityAllocated(0).quantityReserved(0).quantityInTransit(0)
                                .quantityOnOrder(0).quantityDamaged(0).safetyStock(0).reorderPoint(0).reorderQty(0)
                                .build(), "Opening balance from NetSuite");
                    }
                    continue;
                }
                int ours = existing.get().getQuantityOnHand() == null ? 0 : existing.get().getQuantityOnHand();
                if (ours == theirs) {
                    unchanged++;
                    continue;
                }
                differs++;
                if (listed.size() < MAX_LISTED) listed.add(new Difference(sku, nodeId, ours, theirs));
                if (apply && overwrite) {
                    inventoryService.adjustInventoryBySku(tenantId, sku, nodeId, theirs - ours, "Baseline from NetSuite (overwrite requested)");
                    updated++;
                }
            }
        }
        String message = (dry ? "Dry run: nothing was written. " : "")
                + created + (dry ? " stock row(s) would be created, " : " stock row(s) created, ")
                + differs + " differ" + (apply && !overwrite ? " (left as they are)" : "") + ", " + unchanged + " already match, "
                + unknownSku + " row(s) skipped for SKUs Nexus does not know, " + unmapped + " for locations not mapped.";
        return new Report(storeId, dry, overwrite, "COMPLETED", message, rows.size(), created, updated, unchanged, differs,
                unknownSku, unmapped, unknownSkus, listed);
    }
}
