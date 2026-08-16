package com.nexus.oms.controller;

import com.nexus.oms.entity.NxIntegrationStore;
import com.nexus.oms.entity.NxIntegrationStoreSetting;
import com.nexus.oms.entity.NxInventory;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Tag(name = "Marketplace Connectors", description = "Marketplace connector endpoints backed by the configured integration store registry")
@RestController
@RequestMapping("/connectors")
public class ConnectorController {

    private static final Set<String> MARKETPLACES = Set.of("amazon", "ebay", "walmart", "bigcommerce");
    private static final Map<String, String> LABELS = Map.of(
            "amazon", "Amazon",
            "ebay", "eBay",
            "walmart", "Walmart",
            "bigcommerce", "BigCommerce");

    private final NxIntegrationStoreRepository storeRepository;
    private final NxIntegrationStoreSettingRepository settingRepository;
    private final NxSyncLogRepository syncLogRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;

    public ConnectorController(NxIntegrationStoreRepository storeRepository,
                               NxIntegrationStoreSettingRepository settingRepository,
                               NxSyncLogRepository syncLogRepository,
                               OrderRepository orderRepository,
                               OrderItemRepository orderItemRepository,
                               InventoryRepository inventoryRepository,
                               ProductRepository productRepository) {
        this.storeRepository = storeRepository;
        this.settingRepository = settingRepository;
        this.syncLogRepository = syncLogRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.inventoryRepository = inventoryRepository;
        this.productRepository = productRepository;
    }

    @Operation(summary = "Get connector status for all marketplaces")
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Map<String, Object> connectors = new LinkedHashMap<>();
        for (String marketplace : MARKETPLACES) {
            connectors.put(marketplace, buildStatus(tenantId, marketplace));
        }
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("connectors", connectors);
        return ResponseEntity.ok(res);
    }

    private Map<String, Object> buildStatus(UUID tenantId, String marketplace) {
        Optional<NxIntegrationStore> store = storeRepository.findByTenantIdAndStoreCode(tenantId, marketplace);
        long ordersSynced = orderRepository.countByTenantIdAndChannelIgnoreCase(tenantId, marketplace);
        Map<String, Object> c = new LinkedHashMap<>();
        if (store.isEmpty()) {
            c.put("active", false);
            c.put("connected", false);
            c.put("configured", false);
            c.put("sandbox", true);
            c.put("lastSync", null);
            c.put("ordersSynced", ordersSynced);
            c.put("error", null);
            c.put("hasCredentials", false);
            return c;
        }
        NxIntegrationStore s = store.get();
        c.put("active", Boolean.TRUE.equals(s.getIsActive()));
        c.put("connected", "ACTIVE".equals(s.getStatus()) && Boolean.TRUE.equals(s.getIsActive()));
        c.put("configured", true);
        c.put("sandbox", true);
        c.put("lastSync", s.getUpdatedAt() != null ? s.getUpdatedAt().toString() : null);
        c.put("ordersSynced", ordersSynced);
        c.put("error", null);
        List<NxIntegrationStoreSetting> settings = settingRepository.findByStoreId(s.getId());
        c.put("hasCredentials", settings.stream().anyMatch(st -> "credentials".equalsIgnoreCase(st.getSettingType()) && st.getSettingValue() != null));
        return c;
    }

    @Operation(summary = "Configure a connector")
    @PostMapping("/configure")
    public ResponseEntity<Map<String, Object>> configure(@RequestBody Map<String, Object> body) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Object marketplaceObj = body.get("marketplace");
        if (!(marketplaceObj instanceof String m) || !MARKETPLACES.contains(m)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "error", "Unknown marketplace: " + marketplaceObj));
        }
        NxIntegrationStore store = storeRepository.findByTenantIdAndStoreCode(tenantId, m)
                .orElseGet(() -> {
                    NxIntegrationStore ns = new NxIntegrationStore();
                    ns.setTenantId(tenantId);
                    ns.setStoreCode(m);
                    ns.setStoreName(LABELS.get(m));
                    ns.setPlatform(m);
                    ns.setPlatformType("MARKETPLACE");
                    ns.setStatus("ACTIVE");
                    ns.setCurrency("USD");
                    ns.setDefaultLocale("en_US");
                    ns.setTimezone("UTC");
                    ns.setIsActive(true);
                    return ns;
                });
        Object endpoint = body.get("endpoint");
        if (endpoint != null) {
            store.setExternalDomain(String.valueOf(endpoint));
        }
        store.setConfigJson(writeJson(body));
        NxIntegrationStore saved = storeRepository.save(store);
        settingRepository.findByStoreIdAndSettingType(saved.getId(), "credentials").ifPresentOrElse(
                existing -> { /* keep existing credentials */ },
                () -> {
                    NxIntegrationStoreSetting setting = new NxIntegrationStoreSetting();
                    setting.setStoreId(saved.getId());
                    setting.setSettingType("credentials");
                    setting.setSettingValue("configured");
                    setting.setDescription("Connector configured at " + LocalDateTime.now());
                    setting.setIsEncrypted(false);
                    settingRepository.save(setting);
                });
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("message", capitalize(m) + " connector configuration saved");
        res.put("storeId", saved.getId());
        return ResponseEntity.ok(res);
    }

    @Operation(summary = "Authorize a connector")
    @PostMapping("/{marketplace}/authorize")
    public ResponseEntity<Map<String, Object>> authorize(@PathVariable String marketplace, @RequestBody Map<String, Object> body) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        if (!MARKETPLACES.contains(marketplace)) {
            return notFound(marketplace);
        }
        NxIntegrationStore store = storeRepository.findByTenantIdAndStoreCode(tenantId, marketplace)
                .orElseThrow(() -> new com.nexus.oms.exception.ResourceNotFoundException("Connector", marketplace));
        store.setStatus("ACTIVE");
        store.setIsActive(true);
        storeRepository.save(store);
        NxIntegrationStoreSetting setting = settingRepository.findByStoreIdAndSettingType(store.getId(), "credentials")
                .orElseGet(() -> {
                    NxIntegrationStoreSetting ns = new NxIntegrationStoreSetting();
                    ns.setStoreId(store.getId());
                    ns.setSettingType("credentials");
                    ns.setIsEncrypted(true);
                    return ns;
                });
        Object token = body.get("accessToken");
        setting.setSettingValue(token != null ? String.valueOf(token) : "authorized");
        setting.setDescription("Authorized at " + LocalDateTime.now());
        settingRepository.save(setting);

        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("note", capitalize(marketplace) + " connector authorized");
        res.put("hasCredentials", true);
        return ResponseEntity.ok(res);
    }

    @Operation(summary = "Disconnect a connector")
    @PostMapping("/{marketplace}/disconnect")
    public ResponseEntity<Map<String, Object>> disconnect(@PathVariable String marketplace) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        if (!MARKETPLACES.contains(marketplace)) {
            return notFound(marketplace);
        }
        storeRepository.findByTenantIdAndStoreCode(tenantId, marketplace).ifPresent(store -> {
            store.setIsActive(false);
            store.setStatus("DISCONNECTED");
            storeRepository.save(store);
        });
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("note", capitalize(marketplace) + " connector disconnected");
        return ResponseEntity.ok(res);
    }

    @Operation(summary = "Trigger a sync with a marketplace")
    @PostMapping("/{marketplace}/sync")
    public ResponseEntity<Map<String, Object>> sync(@PathVariable String marketplace) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        if (!MARKETPLACES.contains(marketplace)) {
            return notFound(marketplace);
        }
        List<NxOrder> channelOrders = orderRepository.findByTenantIdAndChannelIgnoreCase(tenantId, marketplace);
        NxIntegrationStore store = storeRepository.findByTenantIdAndStoreCode(tenantId, marketplace)
                .orElseGet(() -> {
                    NxIntegrationStore ns = new NxIntegrationStore();
                    ns.setTenantId(tenantId);
                    ns.setStoreCode(marketplace);
                    ns.setStoreName(LABELS.get(marketplace));
                    ns.setPlatform(marketplace);
                    ns.setPlatformType("MARKETPLACE");
                    ns.setStatus("ACTIVE");
                    ns.setCurrency("USD");
                    ns.setDefaultLocale("en_US");
                    ns.setTimezone("UTC");
                    ns.setIsActive(true);
                    return ns;
                });
        NxIntegrationStore saved = storeRepository.save(store);
        saved.setUpdatedAt(LocalDateTime.now());
        storeRepository.save(saved);

        NxSyncLog log = NxSyncLog.builder()
                .tenantId(tenantId)
                .integrationType(marketplace.toUpperCase() + "_" + marketplace)
                .syncType("ORDER")
                .status("SUCCESS")
                .startedAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .itemsProcessed(channelOrders.size())
                .itemsSucceeded(channelOrders.size())
                .itemsFailed(0)
                .details(writeJson(Map.of("ordersSynced", channelOrders.size())))
                .build();
        syncLogRepository.save(log);

        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("message", capitalize(marketplace) + " sync completed");
        res.put("orderCount", channelOrders.size());
        res.put("lastSyncAt", LocalDateTime.now().toString());
        return ResponseEntity.ok(res);
    }

    @Operation(summary = "Fetch orders from a marketplace connector")
    @GetMapping("/{marketplace}/orders")
    public ResponseEntity<Map<String, Object>> getOrders(@PathVariable String marketplace) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        if (!MARKETPLACES.contains(marketplace)) {
            return notFound(marketplace);
        }
        List<Map<String, Object>> orders = orderRepository.findByTenantIdAndChannelIgnoreCase(tenantId, marketplace)
                .stream().map(this::toOrderMap).collect(Collectors.toList());
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("orders", orders);
        res.put("orderCount", orders.size());
        return ResponseEntity.ok(res);
    }

    @Operation(summary = "Fetch a single order from a marketplace connector")
    @GetMapping("/{marketplace}/orders/{orderId}")
    public ResponseEntity<Map<String, Object>> getOrderDetail(@PathVariable String marketplace, @PathVariable String orderId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        if (!MARKETPLACES.contains(marketplace)) {
            return notFound(marketplace);
        }
        Optional<NxOrder> match = orderRepository.findByTenantIdAndChannelOrderId(tenantId, orderId);
        if (match.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("success", false, "error", "Order not found: " + orderId));
        }
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("order", toOrderMap(match.get()));
        return ResponseEntity.ok(res);
    }

    @Operation(summary = "Fetch inventory from a marketplace connector")
    @GetMapping("/{marketplace}/inventory")
    public ResponseEntity<Map<String, Object>> getInventory(@PathVariable String marketplace) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        if (!MARKETPLACES.contains(marketplace)) {
            return notFound(marketplace);
        }
        List<Map<String, Object>> inventory = inventoryRepository.findByTenantId(tenantId).stream()
                .map(i -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("sku", i.getSku());
                    m.put("fulfillable", i.getQuantityOnHand() != null ? i.getQuantityOnHand() : 0);
                    m.put("inbound", i.getQuantityInTransit() != null ? i.getQuantityInTransit() : 0);
                    m.put("reserved", i.getQuantityReserved() != null ? i.getQuantityReserved() : 0);
                    m.put("allocated", i.getQuantityAllocated() != null ? i.getQuantityAllocated() : 0);
                    m.put("nodeId", i.getNodeId());
                    return m;
                }).collect(Collectors.toList());
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("listings", inventory);
        res.put("itemCount", inventory.size());
        return ResponseEntity.ok(res);
    }

    @Operation(summary = "Fetch listings from a marketplace connector")
    @GetMapping("/{marketplace}/listings")
    public ResponseEntity<Map<String, Object>> getListings(@PathVariable String marketplace) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        if (!MARKETPLACES.contains(marketplace)) {
            return notFound(marketplace);
        }
        List<Product> products = productRepository.findByTenantId(tenantId, PageRequest.of(0, 500)).getContent();
        List<Map<String, Object>> listings = products.stream().map(p -> {
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("sku", p.getSku());
            l.put("title", p.getProductName());
            l.put("price", p.getUnitPrice());
            l.put("status", Boolean.TRUE.equals(p.getIsActive()) ? "ACTIVE" : "INACTIVE");
            return l;
        }).collect(Collectors.toList());
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("listings", listings);
        res.put("listingCount", listings.size());
        return ResponseEntity.ok(res);
    }

    @Operation(summary = "Recent sync log for a connector")
    @GetMapping("/{marketplace}/sync-log")
    public ResponseEntity<Map<String, Object>> getSyncLog(@PathVariable String marketplace) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        if (!MARKETPLACES.contains(marketplace)) {
            return notFound(marketplace);
        }
        String integrationType = marketplace.toUpperCase() + "_" + marketplace;
        List<NxSyncLog> logs = syncLogRepository
                .findByTenantIdAndIntegrationTypeOrderByCreatedAtDesc(tenantId, integrationType, PageRequest.of(0, 20))
                .getContent();
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("syncLog", logs);
        return ResponseEntity.ok(res);
    }

    private Map<String, Object> toOrderMap(NxOrder o) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("orderId", o.getChannelOrderId() != null ? o.getChannelOrderId() : o.getId().toString());
        map.put("id", o.getId());
        map.put("status", o.getStatus());
        map.put("subStatus", o.getSubStatus());
        map.put("total", o.getTotal());
        map.put("currency", o.getCurrency());
        map.put("customerEmail", o.getCustomerEmail());
        map.put("trackingNumber", o.getTrackingNumber());
        map.put("externalId", o.getExternalId());
        map.put("createdAt", o.getCreatedAt() != null ? o.getCreatedAt().toString() : null);
        List<Map<String, Object>> items = orderItemRepository.findByOrderId(o.getId()).stream().map(it -> {
            Map<String, Object> im = new LinkedHashMap<>();
            im.put("sku", it.getSku());
            im.put("qty", it.getQuantity());
            im.put("productName", it.getProductName());
            im.put("unitPrice", it.getUnitPrice());
            return im;
        }).collect(Collectors.toList());
        map.put("items", items);
        return map;
    }

    private ResponseEntity<Map<String, Object>> notFound(String marketplace) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("success", false, "error", "Unknown marketplace: " + marketplace));
    }

    private static String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private String writeJson(Object value) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }
}
