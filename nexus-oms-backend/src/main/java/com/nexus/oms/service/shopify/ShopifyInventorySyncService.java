package com.nexus.oms.service.shopify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.dto.SyncResult;
import com.nexus.oms.entity.*;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.repository.*;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.IntegrationStoreService;
import com.nexus.oms.service.shopify.InventoryPublishPlanner.LocationQuantity;
import com.nexus.oms.service.shopify.InventoryPublishPlanner.Node;
import com.nexus.oms.service.shopify.InventoryPublishPlanner.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Publishes sellable inventory from Nexus nodes to Shopify locations.
 *
 * <p>What is published is decided by the store's location mappings and
 * {@link InventoryPublishPlanner}; nothing is published for a store without mappings, and nothing
 * is ever summed across nodes and written to every Shopify location. Every run leaves a
 * reconciliation record (a {@code NxInventoryPublishRun} with one line per SKU and location showing
 * the per-node arithmetic), and a dry run does everything except write to Shopify.
 *
 * <p>Nexus is authoritative for the quantity it publishes. A Shopify quantity that differs from
 * what Nexus last set is overwritten and the line is flagged as drift, so edits made in the
 * Shopify admin are visible rather than silently lost or silently trusted.
 */
@Service
public class ShopifyInventorySyncService {

    private static final Logger log = LoggerFactory.getLogger(ShopifyInventorySyncService.class);

    static final String SCHEDULER = "scheduler";
    private static final int LEVEL_BATCH = 50;
    private static final int MAX_PRODUCT_PAGES = 400;
    private static final long ABANDONED_AFTER_MINUTES = 15;
    private static final long RETENTION_DAYS = 60;

    private final ShopifyClient shopifyClient;
    private final IntegrationStoreService storeService;
    private final ShopifyTokenService tokenService;
    private final NxIntegrationSyncConfigRepository syncConfigRepository;
    private final NxSyncLogRepository syncLogRepository;
    private final NxProductMappingRepository productMappingRepository;
    private final InventoryRepository inventoryRepository;
    private final WarehouseRepository warehouseRepository;
    private final NxChannelLocationMappingRepository mappingRepository;
    private final NxInventoryPublishRunRepository runRepository;
    private final NxInventoryPublishLineRepository lineRepository;
    private final TransactionTemplate tx;
    private final ObjectMapper objectMapper;

    public ShopifyInventorySyncService(ShopifyClient shopifyClient,
                                       IntegrationStoreService storeService,
                                       ShopifyTokenService tokenService,
                                       NxIntegrationSyncConfigRepository syncConfigRepository,
                                       NxSyncLogRepository syncLogRepository,
                                       NxProductMappingRepository productMappingRepository,
                                       InventoryRepository inventoryRepository,
                                       WarehouseRepository warehouseRepository,
                                       NxChannelLocationMappingRepository mappingRepository,
                                       NxInventoryPublishRunRepository runRepository,
                                       NxInventoryPublishLineRepository lineRepository,
                                       PlatformTransactionManager transactionManager,
                                       ObjectMapper objectMapper) {
        this.shopifyClient = shopifyClient;
        this.storeService = storeService;
        this.tokenService = tokenService;
        this.syncConfigRepository = syncConfigRepository;
        this.syncLogRepository = syncLogRepository;
        this.productMappingRepository = productMappingRepository;
        this.inventoryRepository = inventoryRepository;
        this.warehouseRepository = warehouseRepository;
        this.mappingRepository = mappingRepository;
        this.runRepository = runRepository;
        this.lineRepository = lineRepository;
        // Each step commits on its own: the RUNNING marker must be visible to other instances
        // before the (slow) Shopify calls start, and results must survive a failure further on.
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.objectMapper = objectMapper;
    }

    /** Scheduler and legacy entry point: a real publish, summarised as a sync result. */
    public SyncResult pushInventory(UUID storeId) {
        NxInventoryPublishRun run = publish(storeId, false, SCHEDULER);
        int processed = run.getItemsPublished() + run.getItemsUnchanged() + run.getItemsFailed() + run.getItemsSkipped();
        return SyncResult.builder()
                .syncType("INVENTORY_PUSH")
                .status(run.getStatus())
                .itemsProcessed(processed)
                .itemsSucceeded(run.getItemsPublished() + run.getItemsUnchanged())
                .itemsFailed(run.getItemsFailed())
                .message(run.getMessage())
                .build();
    }

    /**
     * @param dryRun when true, calculates and records everything but writes nothing to Shopify
     * @return the persisted run; its lines are the reconciliation detail
     */
    public NxInventoryPublishRun publish(UUID storeId, boolean dryRun, String triggeredBy) {
        return publish(storeId, dryRun, triggeredBy, Set.of());
    }

    /**
     * @param onlySkus when not empty, only these SKUs are considered: a canary for the first real write
     */
    public NxInventoryPublishRun publish(UUID storeId, boolean dryRun, String triggeredBy, Set<String> onlySkus) {
        NxIntegrationStore store = storeService.getStore(storeId);
        UUID tenantId = store.getTenantId();
        boolean ownsContext = TenantContext.findCurrentTenantId() == null;
        if (ownsContext) TenantContext.setCurrentTenantId(tenantId);
        try {
            return doPublish(store, dryRun, triggeredBy, onlySkus);
        } finally {
            if (ownsContext) TenantContext.clear();
        }
    }

    private NxInventoryPublishRun doPublish(NxIntegrationStore store, boolean dryRun, String triggeredBy, Set<String> onlySkus) {
        UUID storeId = store.getId();
        UUID tenantId = store.getTenantId();
        housekeeping(storeId);

        List<NxChannelLocationMapping> mappings = mappingRepository.findByIntegrationStoreId(storeId).stream()
                .filter(m -> Boolean.TRUE.equals(m.getEnabled())).toList();
        if (mappings.isEmpty()) {
            return recordBlocked(store, dryRun, triggeredBy,
                    "No inventory location mapping is configured for this store, so nothing was published. "
                            + "Map each Nexus warehouse or store to the Shopify location it should feed.");
        }

        NxInventoryPublishRun run = startRun(store, dryRun, triggeredBy);
        List<NxInventoryPublishLine> lines = new ArrayList<>();
        String fatal = null;
        try {
            execute(store, mappings, run, dryRun, lines, onlySkus);
        } catch (RuntimeException e) {
            log.error("Inventory publish failed for store {}: {}", storeId, e.getMessage(), e);
            fatal = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        }
        return finishRun(store, run, lines, dryRun, triggeredBy, fatal);
    }

    // ---- the work -------------------------------------------------------------------------------------

    private void execute(NxIntegrationStore store, List<NxChannelLocationMapping> mappings, NxInventoryPublishRun run,
                         boolean dryRun, List<NxInventoryPublishLine> lines, Set<String> onlySkus) {
        UUID storeId = store.getId();
        UUID tenantId = store.getTenantId();
        String shop = storeService.getSetting(storeId, "shop_domain");
        String token = tokenService.getAccessToken(storeId);

        Map<String, Boolean> shopifyLocations = fetchLocations(shop, token);   // id -> active
        Map<Long, Long> inventoryItemByVariant = fetchInventoryItems(shop, token);
        Map<UUID, Node> nodes = loadNodes(mappings);
        List<Rule> rules = mappings.stream().map(m -> new Rule(m.getNodeId(), m.getExternalLocationId(),
                Boolean.TRUE.equals(m.getEnabled()), nz(m.getBufferUnits()), m.getContributionCap())).toList();

        Map<String, List<NxInventory>> stockBySku = inventoryRepository.findByTenantId(tenantId).stream()
                .filter(i -> i.getSku() != null)
                .collect(Collectors.groupingBy(NxInventory::getSku));
        Map<String, NxProductMapping> productBySku = new LinkedHashMap<>();
        for (NxProductMapping pm : productMappingRepository.findByTenantId(tenantId)) {
            if (pm.getNexusSku() != null && (onlySkus.isEmpty() || onlySkus.contains(pm.getNexusSku()))) productBySku.putIfAbsent(pm.getNexusSku(), pm);
        }

        Set<String> mappedLocationIds = rules.stream().map(Rule::externalLocationId).collect(Collectors.toCollection(TreeSet::new));
        Map<String, Integer> channelLevels = fetchLevels(shop, token, productBySku.values(), inventoryItemByVariant, mappedLocationIds);
        Map<String, Integer> lastSet = lastPublished(storeId);
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);

        for (NxProductMapping pm : productBySku.values()) {
            String sku = pm.getNexusSku();
            // No stock record means Nexus does not track this SKU: unknown is not zero. Publishing 0 here
            // would empty the merchant's shelf for every product Nexus has simply never counted.
            if (!stockBySku.containsKey(sku)) continue;
            List<LocationQuantity> plan = InventoryPublishPlanner.plan(sku, stockBySku.getOrDefault(sku, List.of()), rules, nodes, today);
            Long itemId = pm.getBcVariantId() != null ? inventoryItemByVariant.get(pm.getBcVariantId()) : null;
            for (LocationQuantity lq : plan) {
                lines.add(publishLine(shop, token, run, sku, itemId, lq, shopifyLocations, channelLevels, lastSet, dryRun));
            }
        }
    }

    private NxInventoryPublishLine publishLine(String shop, String token, NxInventoryPublishRun run, String sku, Long itemId,
                                               LocationQuantity lq, Map<String, Boolean> shopifyLocations,
                                               Map<String, Integer> channelLevels, Map<String, Integer> lastSet, boolean dryRun) {
        NxInventoryPublishLine.NxInventoryPublishLineBuilder line = NxInventoryPublishLine.builder()
                .tenantId(run.getTenantId()).runId(run.getId()).sku(sku).externalLocationId(lq.externalLocationId())
                .inventoryItemId(itemId).calculatedQty(lq.quantity()).calculation(json(lq.contributions()));

        Boolean locationActive = shopifyLocations.get(lq.externalLocationId());
        if (locationActive == null || !locationActive) {
            return line.outcome(NxInventoryPublishLine.SKIPPED)
                    .error("Shopify location " + lq.externalLocationId() + (locationActive == null ? " no longer exists" : " is deactivated"))
                    .build();
        }
        if (itemId == null) {
            return line.outcome(NxInventoryPublishLine.SKIPPED)
                    .error("No Shopify variant is linked to this SKU; run a product sync first").build();
        }

        String key = itemId + "|" + lq.externalLocationId();
        Integer before = channelLevels.get(key);
        Integer previouslySet = lastSet.get(sku + "|" + lq.externalLocationId());
        boolean drift = before != null && previouslySet != null && !before.equals(previouslySet);
        line.channelQtyBefore(before).drift(drift);

        if (before != null && before == lq.quantity()) {
            return line.outcome(NxInventoryPublishLine.UNCHANGED).publishedQty(before).build();
        }
        if (dryRun) {
            return line.outcome(NxInventoryPublishLine.PLANNED).build();
        }
        try {
            if (before == null) {
                // The item is not stocked at this location yet; Shopify refuses a quantity until it is.
                shopifyClient.connectInventoryLevel(shop, token, Map.of(
                        "inventory_item_id", itemId, "location_id", Long.parseLong(lq.externalLocationId())));
            }
            shopifyClient.setInventoryLevel(shop, token, Map.of(
                    "inventory_item_id", itemId, "location_id", Long.parseLong(lq.externalLocationId()), "available", lq.quantity()));
            return line.outcome(NxInventoryPublishLine.PUBLISHED).publishedQty(lq.quantity()).build();
        } catch (RuntimeException e) {
            log.warn("Could not publish sku={} location={}: {}", sku, lq.externalLocationId(), e.getMessage());
            return line.outcome(NxInventoryPublishLine.FAILED).error(truncate(e.getMessage(), 1000)).build();
        }
    }

    // ---- Shopify reads --------------------------------------------------------------------------------

    private Map<String, Boolean> fetchLocations(String shop, String token) {
        Map<String, Boolean> result = new HashMap<>();
        JsonNode body = shopifyClient.getLocations(shop, token);
        JsonNode locations = body != null ? body.get("locations") : null;
        if (locations != null) {
            for (JsonNode loc : locations) {
                result.put(loc.path("id").asText(), loc.path("active").asBoolean(true));
            }
        }
        return result;
    }

    /** Variant id to inventory item id, across every page of products (not just the first 250). */
    private Map<Long, Long> fetchInventoryItems(String shop, String token) {
        Map<Long, Long> result = new HashMap<>();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("limit", "250");
        params.put("fields", "id,variants");
        for (int page = 0; page < MAX_PRODUCT_PAGES; page++) {
            ShopifyClient.ProductPage response = shopifyClient.getProductsPage(shop, token, params);
            JsonNode products = response.body() != null ? response.body().get("products") : null;
            if (products != null) {
                for (JsonNode product : products) {
                    for (JsonNode variant : product.path("variants")) {
                        long inventoryItemId = variant.path("inventory_item_id").asLong(0);
                        if (inventoryItemId > 0) result.put(variant.path("id").asLong(), inventoryItemId);
                    }
                }
            }
            if (response.nextPageInfo() == null) return result;
            params = new LinkedHashMap<>();   // a page_info request may carry only limit and fields
            params.put("limit", "250");
            params.put("fields", "id,variants");
            params.put("page_info", response.nextPageInfo());
        }
        throw new IllegalStateException("Shopify catalogue has more than " + MAX_PRODUCT_PAGES * 250 + " products; refusing to continue");
    }

    /** What Shopify currently holds, keyed "inventoryItemId|locationId". Absent means not stocked there. */
    private Map<String, Integer> fetchLevels(String shop, String token, Collection<NxProductMapping> products,
                                             Map<Long, Long> itemByVariant, Set<String> locationIds) {
        List<Long> itemIds = products.stream().map(NxProductMapping::getBcVariantId).filter(Objects::nonNull)
                .map(itemByVariant::get).filter(Objects::nonNull).distinct().toList();
        Map<String, Integer> levels = new HashMap<>();
        String locations = String.join(",", locationIds);
        for (int i = 0; i < itemIds.size(); i += LEVEL_BATCH) {
            String ids = itemIds.subList(i, Math.min(itemIds.size(), i + LEVEL_BATCH)).stream()
                    .map(String::valueOf).collect(Collectors.joining(","));
            JsonNode body = shopifyClient.getInventoryLevels(shop, token,
                    Map.of("inventory_item_ids", ids, "location_ids", locations, "limit", "250"));
            JsonNode array = body != null ? body.get("inventory_levels") : null;
            if (array == null) continue;
            for (JsonNode level : array) {
                // "available" is null for an item whose quantity Shopify does not track; treat as unknown (0 would lie).
                if (level.hasNonNull("available")) {
                    levels.put(level.path("inventory_item_id").asLong() + "|" + level.path("location_id").asLong(),
                            level.get("available").asInt());
                }
            }
        }
        return levels;
    }

    private Map<UUID, Node> loadNodes(List<NxChannelLocationMapping> mappings) {
        Set<UUID> ids = mappings.stream().map(NxChannelLocationMapping::getNodeId).collect(Collectors.toSet());
        Map<UUID, Node> nodes = new HashMap<>();
        for (Warehouse w : warehouseRepository.findAllById(ids)) {
            boolean active = !Boolean.FALSE.equals(w.getIsActive()) && !"INACTIVE".equalsIgnoreCase(w.getStatus());
            nodes.put(w.getId(), new Node(w.getId(), w.getName(), active));
        }
        return nodes;
    }

    /** "sku|location" to the quantity Nexus most recently set, from the last real run. */
    private Map<String, Integer> lastPublished(UUID storeId) {
        Map<String, Integer> result = new HashMap<>();
        runRepository.findFirstByIntegrationStoreIdAndDryRunFalseAndStatusInOrderByStartedAtDesc(
                        storeId, List.of(NxInventoryPublishRun.COMPLETED, NxInventoryPublishRun.PARTIAL))
                .ifPresent(last -> lineRepository.findByRunIdOrderBySkuAscExternalLocationIdAsc(last.getId(), PageRequest.of(0, 100_000))
                        .stream()
                        .filter(l -> NxInventoryPublishLine.PUBLISHED.equals(l.getOutcome()) || NxInventoryPublishLine.UNCHANGED.equals(l.getOutcome()))
                        .forEach(l -> result.put(l.getSku() + "|" + l.getExternalLocationId(), l.getPublishedQty())));
        return result;
    }

    // ---- run bookkeeping ------------------------------------------------------------------------------

    /** Frees a store whose earlier run died mid-flight, and trims old history. */
    private void housekeeping(UUID storeId) {
        tx.executeWithoutResult(s -> {
            for (NxInventoryPublishRun stale : runRepository.findByIntegrationStoreIdAndStatusAndStartedAtBefore(
                    storeId, NxInventoryPublishRun.RUNNING, LocalDateTime.now().minusMinutes(ABANDONED_AFTER_MINUTES))) {
                stale.setStatus(NxInventoryPublishRun.FAILED);
                stale.setCompletedAt(LocalDateTime.now());
                stale.setMessage("Abandoned: the run did not finish (the server may have restarted)");
                runRepository.save(stale);
            }
            runRepository.deleteByIntegrationStoreIdAndStartedAtBefore(storeId, LocalDateTime.now().minusDays(RETENTION_DAYS));
        });
    }

    private NxInventoryPublishRun startRun(NxIntegrationStore store, boolean dryRun, String triggeredBy) {
        try {
            return tx.execute(s -> runRepository.saveAndFlush(NxInventoryPublishRun.builder()
                    .tenantId(store.getTenantId()).integrationStoreId(store.getId()).status(NxInventoryPublishRun.RUNNING)
                    .dryRun(dryRun).triggeredBy(triggeredBy).startedAt(LocalDateTime.now()).build()));
        } catch (DataIntegrityViolationException e) {
            throw new BadRequestException("An inventory publish is already running for this store; try again when it finishes");
        }
    }

    private NxInventoryPublishRun recordBlocked(NxIntegrationStore store, boolean dryRun, String triggeredBy, String message) {
        return tx.execute(s -> {
            // The scheduler retries every few minutes; keep one blocked record instead of one per attempt.
            Optional<NxInventoryPublishRun> last = runRepository.findFirstByIntegrationStoreIdOrderByStartedAtDesc(store.getId());
            if (last.isPresent() && NxInventoryPublishRun.BLOCKED.equals(last.get().getStatus()) && message.equals(last.get().getMessage())) {
                last.get().setCompletedAt(LocalDateTime.now());
                return runRepository.save(last.get());
            }
            LocalDateTime now = LocalDateTime.now();
            return runRepository.save(NxInventoryPublishRun.builder()
                    .tenantId(store.getTenantId()).integrationStoreId(store.getId()).status(NxInventoryPublishRun.BLOCKED)
                    .dryRun(dryRun).triggeredBy(triggeredBy).startedAt(now).completedAt(now).message(message).build());
        });
    }

    private NxInventoryPublishRun finishRun(NxIntegrationStore store, NxInventoryPublishRun run, List<NxInventoryPublishLine> lines,
                                            boolean dryRun, String triggeredBy, String fatal) {
        Map<String, Long> counts = lines.stream().collect(Collectors.groupingBy(NxInventoryPublishLine::getOutcome, Collectors.counting()));
        int published = counts.getOrDefault(NxInventoryPublishLine.PUBLISHED, 0L).intValue();
        int unchanged = counts.getOrDefault(NxInventoryPublishLine.UNCHANGED, 0L).intValue();
        int failed = counts.getOrDefault(NxInventoryPublishLine.FAILED, 0L).intValue();
        int skipped = counts.getOrDefault(NxInventoryPublishLine.SKIPPED, 0L).intValue();
        int planned = counts.getOrDefault(NxInventoryPublishLine.PLANNED, 0L).intValue();

        String status = fatal != null ? NxInventoryPublishRun.FAILED
                : failed == 0 ? NxInventoryPublishRun.COMPLETED
                : (published + unchanged + planned) > 0 ? NxInventoryPublishRun.PARTIAL : NxInventoryPublishRun.FAILED;
        String message = fatal != null ? fatal
                : (dryRun ? "Dry run: " + planned + " change(s) planned, nothing written to Shopify. " : "")
                + published + " published, " + unchanged + " already correct, " + skipped + " skipped, " + failed + " failed";

        boolean noop = !dryRun && SCHEDULER.equals(triggeredBy) && fatal == null && published == 0 && failed == 0 && skipped == 0;
        NxInventoryPublishRun saved = tx.execute(s -> {
            if (noop) {
                // A scheduled pass that changed nothing is not worth a history row every few minutes.
                runRepository.delete(run);
                run.setStatus(status);
                run.setMessage(message);
                run.setCompletedAt(LocalDateTime.now());
                run.setItemsUnchanged(unchanged);
                return run;
            }
            run.setStatus(status);
            run.setCompletedAt(LocalDateTime.now());
            run.setItemsPlanned(planned);
            run.setItemsPublished(published);
            run.setItemsUnchanged(unchanged);
            run.setItemsFailed(failed);
            run.setItemsSkipped(skipped);
            run.setMessage(truncate(message, 2000));
            // Unchanged lines are only worth storing for a dry run, where the whole table is the point.
            lineRepository.saveAll(lines.stream()
                    .filter(l -> dryRun || !NxInventoryPublishLine.UNCHANGED.equals(l.getOutcome())).toList());
            return runRepository.save(run);
        });

        if (!dryRun) recordSyncLog(store, status, published + unchanged + failed + skipped, published + unchanged, failed, fatal, message);
        return saved;
    }

    private void recordSyncLog(NxIntegrationStore store, String status, int processed, int succeeded, int failed, String fatal, String message) {
        try {
            tx.executeWithoutResult(s -> {
                NxSyncLog syncLog = NxSyncLog.builder()
                        .tenantId(store.getTenantId())
                        .integrationType("SHOPIFY_" + store.getStoreCode())
                        .syncType("INVENTORY_PUSH")
                        .status(NxInventoryPublishRun.FAILED.equals(status) ? "FAILED" : "COMPLETED")
                        .startedAt(LocalDateTime.now()).completedAt(LocalDateTime.now())
                        .itemsProcessed(processed).itemsSucceeded(succeeded).itemsFailed(failed)
                        .errorMessage(fatal).build();
                syncLogRepository.save(syncLog);
                syncConfigRepository.findByStoreIdAndSyncType(store.getId(), "INVENTORY_PUSH").ifPresent(config -> {
                    config.setLastSyncAt(LocalDateTime.now());
                    config.setLastSyncStatus(status);
                    config.setLastSyncMessage(truncate(message, 250));
                    syncConfigRepository.save(config);
                });
            });
        } catch (RuntimeException e) {
            log.warn("Could not record the sync log for store {}: {}", store.getId(), e.getMessage());
        }
    }

    // ---- helpers --------------------------------------------------------------------------------------

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) return null;
        return text.length() > max ? text.substring(0, max - 3) + "..." : text;
    }

    private static int nz(Integer value) {
        return value != null ? value : 0;
    }
}
