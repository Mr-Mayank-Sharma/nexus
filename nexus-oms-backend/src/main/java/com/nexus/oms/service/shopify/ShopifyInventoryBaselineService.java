package com.nexus.oms.service.shopify;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.oms.entity.NxChannelLocationMapping;
import com.nexus.oms.entity.NxIntegrationStore;
import com.nexus.oms.entity.NxInventory;
import com.nexus.oms.entity.NxProductMapping;
import com.nexus.oms.repository.InventoryRepository;
import com.nexus.oms.repository.NxChannelLocationMappingRepository;
import com.nexus.oms.repository.NxProductMappingRepository;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.IntegrationStoreService;
import com.nexus.oms.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Brings a merchant's existing Shopify stock into Nexus, so a newly connected store has something to publish
 * and route against. Without it Nexus holds stock for none of the merchant's SKUs, and every publish skips
 * them (correctly, since unknown is not zero).
 *
 * <p>It reads Shopify's physical {@code on_hand} quantity, not {@code available}. "Available" is already net of
 * units committed to unfulfilled orders; Nexus imports those orders and reserves their units itself, so
 * starting from "available" would count the same units as taken twice.
 *
 * <p>Read-only towards Shopify. Dry run by default. Rows Nexus already manages are never overwritten unless the
 * caller asks, and nothing is pushed back out to any channel.
 */
@Service
public class ShopifyInventoryBaselineService {

    private static final Logger log = LoggerFactory.getLogger(ShopifyInventoryBaselineService.class);
    static final int ITEMS_PER_QUERY = 50;
    static final int MAX_LISTED = 200;

    static final String QUERY = """
            query Levels($ids: [ID!]!) {
              nodes(ids: $ids) {
                ... on InventoryItem {
                  id
                  inventoryLevels(first: 50) {
                    nodes {
                      location { id }
                      quantities(names: ["on_hand"]) { name quantity }
                    }
                  }
                }
              }
            }""";

    private final ShopifyClient shopifyClient;
    private final IntegrationStoreService storeService;
    private final ShopifyTokenService tokenService;
    private final ShopifyInventorySyncService inventorySyncService;
    private final NxChannelLocationMappingRepository mappingRepository;
    private final NxProductMappingRepository productMappingRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryService inventoryService;

    public ShopifyInventoryBaselineService(ShopifyClient shopifyClient,
                                           IntegrationStoreService storeService,
                                           ShopifyTokenService tokenService,
                                           ShopifyInventorySyncService inventorySyncService,
                                           NxChannelLocationMappingRepository mappingRepository,
                                           NxProductMappingRepository productMappingRepository,
                                           InventoryRepository inventoryRepository,
                                           InventoryService inventoryService) {
        this.shopifyClient = shopifyClient;
        this.storeService = storeService;
        this.tokenService = tokenService;
        this.inventorySyncService = inventorySyncService;
        this.mappingRepository = mappingRepository;
        this.productMappingRepository = productMappingRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryService = inventoryService;
    }

    public record Difference(String sku, UUID nodeId, int nexusOnHand, int shopifyOnHand) {}

    public record Report(UUID storeId, boolean dryRun, boolean overwrite, String status, String message,
                         int checked, int created, int updated, int unchanged, int differs, int untracked,
                         List<Difference> differences) {}

    /**
     * @param apply     false (the default at the API) only reports what would change
     * @param overwrite when applying, also set rows Nexus already holds to Shopify's number; off by default
     */
    public Report run(UUID storeId, boolean apply, boolean overwrite) {
        NxIntegrationStore store = storeService.getStore(storeId);
        UUID tenantId = store.getTenantId();
        boolean ownsContext = TenantContext.findCurrentTenantId() == null;
        if (ownsContext) TenantContext.setCurrentTenantId(tenantId);
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

        List<NxChannelLocationMapping> mappings = mappingRepository.findByIntegrationStoreId(storeId).stream()
                .filter(m -> Boolean.TRUE.equals(m.getEnabled())).toList();
        if (mappings.isEmpty()) {
            return new Report(storeId, dry, overwrite, "BLOCKED",
                    "No location mapping is set, so there is nothing to import into. Map each Shopify location to the Nexus warehouse or store that holds that stock.",
                    0, 0, 0, 0, 0, 0, List.of());
        }

        String shop = storeService.getSetting(storeId, "shop_domain");
        String token = tokenService.getAccessToken(storeId);

        // sku -> Shopify inventory item
        Map<Long, Long> itemByVariant = inventorySyncService.fetchInventoryItems(shop, token);
        Map<String, Long> itemBySku = new LinkedHashMap<>();
        for (NxProductMapping pm : productMappingRepository.findByTenantId(tenantId)) {
            if (pm.getNexusSku() == null || pm.getBcVariantId() == null) continue;
            Long item = itemByVariant.get(pm.getBcVariantId());
            if (item != null) itemBySku.putIfAbsent(pm.getNexusSku(), item);
        }

        // Shopify location id -> Nexus node
        Map<Long, UUID> nodeByLocation = new HashMap<>();
        for (NxChannelLocationMapping m : mappings) {
            try {
                nodeByLocation.put(Long.parseLong(m.getExternalLocationId()), m.getNodeId());
            } catch (NumberFormatException ignored) {
                log.warn("Skipping a location mapping with a non-numeric Shopify location id: {}", m.getExternalLocationId());
            }
        }

        // item -> location -> on_hand (a missing entry means Shopify does not stock or track it there)
        Map<Long, Map<Long, Integer>> onHand = readOnHand(shop, token, new ArrayList<>(new LinkedHashSet<>(itemBySku.values())));

        int checked = 0, created = 0, updated = 0, unchanged = 0, differs = 0, untracked = 0;
        List<Difference> listed = new ArrayList<>();
        for (Map.Entry<String, Long> entry : itemBySku.entrySet()) {
            String sku = entry.getKey();
            Map<Long, Integer> atLocations = onHand.getOrDefault(entry.getValue(), Map.of());
            for (Map.Entry<Long, UUID> loc : nodeByLocation.entrySet()) {
                Integer shopifyQty = atLocations.get(loc.getKey());
                if (shopifyQty == null) {
                    untracked++;
                    continue;
                }
                checked++;
                UUID nodeId = loc.getValue();
                Optional<NxInventory> existing = inventoryRepository.findByTenantIdAndSkuAndNodeId(tenantId, sku, nodeId);
                if (existing.isEmpty()) {
                    created++;
                    if (apply) {
                        inventoryService.importOpeningBalance(NxInventory.builder()
                                .tenantId(tenantId).sku(sku).nodeId(nodeId)
                                .quantityOnHand(shopifyQty)
                                .quantityAllocated(0).quantityReserved(0).quantityInTransit(0)
                                .quantityOnOrder(0).quantityDamaged(0).safetyStock(0).reorderPoint(0).reorderQty(0)
                                .build(), "Opening balance from Shopify on-hand");
                    }
                    continue;
                }
                int current = existing.get().getQuantityOnHand() == null ? 0 : existing.get().getQuantityOnHand();
                if (current == shopifyQty) {
                    unchanged++;
                    continue;
                }
                differs++;
                if (listed.size() < MAX_LISTED) listed.add(new Difference(sku, nodeId, current, shopifyQty));
                if (apply && overwrite) {
                    inventoryService.adjustInventoryBySku(tenantId, sku, nodeId, shopifyQty - current,
                            "Baseline from Shopify on-hand (overwrite requested)");
                    updated++;
                }
            }
        }

        String message = dry
                ? "Dry run: nothing was written. " + created + " stock row(s) would be created, " + differs
                    + " already differ from Shopify, " + unchanged + " already match, " + untracked + " not tracked at a mapped location."
                : created + " stock row(s) created from Shopify on-hand, " + updated + " overwritten, " + differs
                    + " differ" + (overwrite ? "" : " (left as they are)") + ", " + unchanged + " already matched.";
        return new Report(storeId, dry, overwrite, "COMPLETED", message,
                checked, created, updated, unchanged, differs, untracked, listed);
    }

    /** Reads physical on-hand per inventory item and location from the GraphQL Admin API, in batches. */
    Map<Long, Map<Long, Integer>> readOnHand(String shop, String token, List<Long> itemIds) {
        Map<Long, Map<Long, Integer>> result = new HashMap<>();
        for (int i = 0; i < itemIds.size(); i += ITEMS_PER_QUERY) {
            List<String> gids = itemIds.subList(i, Math.min(itemIds.size(), i + ITEMS_PER_QUERY)).stream()
                    .map(id -> "gid://shopify/InventoryItem/" + id).toList();
            JsonNode data = shopifyClient.graphql(shop, token, QUERY, Map.of("ids", gids));
            JsonNode nodes = data == null ? null : data.get("nodes");
            if (nodes == null || !nodes.isArray()) continue;
            for (JsonNode node : nodes) {
                if (node == null || node.isNull() || !node.hasNonNull("id")) continue;
                long itemId = lastId(node.get("id").asText());
                Map<Long, Integer> byLocation = new HashMap<>();
                for (JsonNode level : node.path("inventoryLevels").path("nodes")) {
                    for (JsonNode q : level.path("quantities")) {
                        if ("on_hand".equals(q.path("name").asText()) && q.hasNonNull("quantity")) {
                            byLocation.put(lastId(level.path("location").path("id").asText()), q.get("quantity").asInt());
                        }
                    }
                }
                result.put(itemId, byLocation);
            }
        }
        return result;
    }

    private static long lastId(String gid) {
        return Long.parseLong(gid.substring(gid.lastIndexOf('/') + 1));
    }
}
