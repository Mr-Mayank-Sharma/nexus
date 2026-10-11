package com.nexus.oms.controller;

import com.nexus.oms.entity.NxIntegrationStore;
import com.nexus.oms.entity.NxIntegrationStoreSetting;
import com.nexus.oms.entity.NxInventory;
import com.nexus.oms.entity.NxOrder;
import com.nexus.oms.entity.NxSyncLog;
import com.nexus.oms.entity.Product;
import com.nexus.oms.service.IntegrationStoreService;
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
/**
 * Marketplace connector endpoints for Amazon, eBay, Walmart and BigCommerce.
 *
 * <p>Nothing here calls a marketplace. No live API client is wired to these endpoints, so they
 * report that plainly instead of simulating a connection: status never says "connected", sync
 * answers 501, and the order, inventory and listing endpoints return Nexus's own records, labelled
 * as such. Credentials are saved sealed and marked unverified. (A Shopify integration with a live
 * client exists separately under /shopify.)
 */
public class ConnectorController {

    static final String SOURCE = "NEXUS_RECORDS";
    static final String NOT_LIVE_NOTE = "These are records already held in Nexus, not data fetched from the marketplace: "
            + "no live marketplace API client is connected in this build.";

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
    private final IntegrationStoreService storeService;

    public ConnectorController(NxIntegrationStoreRepository storeRepository,
                               NxIntegrationStoreSettingRepository settingRepository,
                               NxSyncLogRepository syncLogRepository,
                               OrderRepository orderRepository,
                               OrderItemRepository orderItemRepository,
                               InventoryRepository inventoryRepository,
                               ProductRepository productRepository,
                               IntegrationStoreService storeService) {
        this.storeRepository = storeRepository;
        this.settingRepository = settingRepository;
        this.syncLogRepository = syncLogRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.inventoryRepository = inventoryRepository;
        this.productRepository = productRepository;
        this.storeService = storeService;
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
        long ordersInNexus = orderRepository.countByTenantIdAndChannelIgnoreCase(tenantId, marketplace);
        boolean hasCredentials = store.isPresent() && settingRepository.findByStoreId(store.get().getId()).stream()
                .anyMatch(st -> "credentials".equalsIgnoreCase(st.getSettingType()) && st.getSettingValue() != null && !st.getSettingValue().isBlank());
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("active", store.isPresent() && Boolean.TRUE.equals(store.get().getIsActive()));
        // Never true: nothing here can verify a connection, so none is claimed.
        c.put("connected", false);
        c.put("configured", hasCredentials);
        c.put("state", hasCredentials ? "CREDENTIALS_SAVED_UNVERIFIED" : "NOT_CONFIGURED");
        c.put("liveSync", false);
        c.put("source", SOURCE);
        c.put("sandbox", true);
        c.put("lastSync", null);
        // Kept under the old name for the existing screens; it counts orders already in Nexus.
        c.put("ordersSynced", ordersInNexus);
        c.put("ordersInNexus", ordersInNexus);
        c.put("error", null);
        c.put("hasCredentials", hasCredentials);
        c.put("note", NOT_LIVE_NOTE);
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
        NxIntegrationStore store = storeRepository.findByTenantIdAndStoreCode(tenantId, m).orElseGet(() -> newStore(tenantId, m));
        Object endpoint = body.get("endpoint");
        if (endpoint != null) {
            store.setExternalDomain(String.valueOf(endpoint));
        }
        // Secrets go to sealed settings; the stored config keeps only the non-secret fields.
        Map<String, Object> plain = new LinkedHashMap<>();
        Map<String, String> secrets = new LinkedHashMap<>();
        body.forEach((key, value) -> {
            if (value == null) return;
            if (IntegrationStoreService.isSecretKey(key)) secrets.put(key, String.valueOf(value));
            else plain.put(key, value);
        });
        store.setConfigJson(writeJson(plain));
        NxIntegrationStore saved = storeRepository.save(store);
        secrets.forEach((key, value) -> {
            if (!IntegrationStoreService.MASK.equals(value) && !value.isBlank()) storeService.updateSetting(saved.getId(), key, value);
        });
        if (!secrets.isEmpty()) markCredentialsSaved(saved.getId());

        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("verified", false);
        res.put("message", capitalize(m) + " settings saved. The connection has not been verified: " + NOT_LIVE_NOTE);
        res.put("storeId", saved.getId());
        return ResponseEntity.ok(res);
    }

    @Operation(summary = "Save an access token for a connector (not verified)")
    @PostMapping("/{marketplace}/authorize")
    public ResponseEntity<Map<String, Object>> authorize(@PathVariable String marketplace, @RequestBody Map<String, Object> body) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        if (!MARKETPLACES.contains(marketplace)) {
            return notFound(marketplace);
        }
        Object token = body.get("accessToken");
        if (token == null || String.valueOf(token).isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "error", "An accessToken is required"));
        }
        NxIntegrationStore store = storeRepository.findByTenantIdAndStoreCode(tenantId, marketplace)
                .orElseThrow(() -> new com.nexus.oms.exception.ResourceNotFoundException("Connector", marketplace));
        store.setStatus("PENDING_VERIFICATION");
        store.setIsActive(true);
        storeRepository.save(store);
        storeService.updateSetting(store.getId(), "access_token", String.valueOf(token));   // sealed at rest
        markCredentialsSaved(store.getId());

        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("verified", false);
        res.put("hasCredentials", true);
        res.put("note", "Token saved, but it has not been checked against " + capitalize(marketplace) + ": " + NOT_LIVE_NOTE);
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

    @Operation(summary = "Trigger a sync with a marketplace (not available)")
    @PostMapping("/{marketplace}/sync")
    public ResponseEntity<Map<String, Object>> sync(@PathVariable String marketplace) {
        if (!MARKETPLACES.contains(marketplace)) {
            return notFound(marketplace);
        }
        // Deliberately does nothing else: no sync log, no store record, no "success". The previous
        // implementation counted local orders and reported a completed sync it never performed.
        Map<String, Object> res = new HashMap<>();
        res.put("success", false);
        res.put("implemented", false);
        res.put("error", capitalize(marketplace) + " sync is not available: " + NOT_LIVE_NOTE);
        res.put("message", capitalize(marketplace) + " sync is not available in this build");
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(res);
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
        res.put("source", SOURCE);
        res.put("live", false);
        res.put("note", NOT_LIVE_NOTE);
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
        res.put("source", SOURCE);
        res.put("live", false);
        res.put("note", "This is Nexus's own stock, not inventory reported by the marketplace.");
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
        res.put("source", SOURCE);
        res.put("live", false);
        res.put("note", "These are Nexus's own products, not listings reported by the marketplace.");
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

    private NxIntegrationStore newStore(UUID tenantId, String marketplace) {
        NxIntegrationStore ns = new NxIntegrationStore();
        ns.setTenantId(tenantId);
        ns.setStoreCode(marketplace);
        ns.setStoreName(LABELS.get(marketplace));
        ns.setPlatform(marketplace);
        ns.setPlatformType("MARKETPLACE");
        ns.setStatus("PENDING_VERIFICATION");
        ns.setCurrency("USD");
        ns.setDefaultLocale("en_US");
        ns.setTimezone("UTC");
        ns.setIsActive(true);
        return ns;
    }

    /** Marks that some credentials exist; the value is a flag, never the secret itself. */
    private void markCredentialsSaved(UUID storeId) {
        NxIntegrationStoreSetting marker = settingRepository.findByStoreIdAndSettingType(storeId, "credentials").orElseGet(() -> {
            NxIntegrationStoreSetting ns = new NxIntegrationStoreSetting();
            ns.setStoreId(storeId);
            ns.setSettingType("credentials");
            ns.setIsEncrypted(false);
            return ns;
        });
        marker.setSettingValue("saved");
        marker.setDescription("Credentials saved " + LocalDateTime.now() + " (not verified)");
        settingRepository.save(marker);
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
