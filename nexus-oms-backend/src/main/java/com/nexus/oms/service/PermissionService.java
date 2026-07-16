package com.nexus.oms.service;

import com.nexus.oms.entity.RolePermission;
import com.nexus.oms.repository.RolePermissionRepository;
import com.nexus.oms.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);
    private static final long CACHE_TTL_MS = 60_000;

    private final RolePermissionRepository rolePermissionRepository;

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanup = new AtomicLong(System.currentTimeMillis());

    public PermissionService(RolePermissionRepository rolePermissionRepository) {
        this.rolePermissionRepository = rolePermissionRepository;
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "permission-cache-cleaner");
            t.setDaemon(true);
            return t;
        }).scheduleAtFixedRate(this::evictExpired, 30, 30, TimeUnit.SECONDS);
    }

    private static final Map<String, String> PATH_TO_RESOURCE = new LinkedHashMap<>();
    static {
        PATH_TO_RESOURCE.put("/orders/", "orders");
        PATH_TO_RESOURCE.put("/inventory/", "inventory");
        PATH_TO_RESOURCE.put("/inventory-receipts/", "inventory-receipts");
        PATH_TO_RESOURCE.put("/products/", "products");
        PATH_TO_RESOURCE.put("/customers/", "customers");
        PATH_TO_RESOURCE.put("/returns/", "returns");
        PATH_TO_RESOURCE.put("/shipments/", "shipments");
        PATH_TO_RESOURCE.put("/picking/", "picking");
        PATH_TO_RESOURCE.put("/packing/", "packing");
        PATH_TO_RESOURCE.put("/shipping/", "shipping");
        PATH_TO_RESOURCE.put("/warehouse/", "warehouse");
        PATH_TO_RESOURCE.put("/warehouses/", "warehouse");
        PATH_TO_RESOURCE.put("/routing/", "routing");
        PATH_TO_RESOURCE.put("/routing-rules/", "routing-rules");
        PATH_TO_RESOURCE.put("/order-routing/", "routing");
        PATH_TO_RESOURCE.put("/procurement/", "procurement");
        PATH_TO_RESOURCE.put("/invoices/", "invoices");
        PATH_TO_RESOURCE.put("/invoicing/", "invoices");
        PATH_TO_RESOURCE.put("/payments/", "payments");
        PATH_TO_RESOURCE.put("/carriers/", "carriers");
        PATH_TO_RESOURCE.put("/carrier/", "carriers");
        PATH_TO_RESOURCE.put("/rbac/", "rbac");
        PATH_TO_RESOURCE.put("/settings/", "settings");
        PATH_TO_RESOURCE.put("/workflows/", "workflows");
        PATH_TO_RESOURCE.put("/ai/", "ai");
        PATH_TO_RESOURCE.put("/api/ai/", "ai");
        PATH_TO_RESOURCE.put("/analytics/", "analytics");
        PATH_TO_RESOURCE.put("/documents/", "documents");
        PATH_TO_RESOURCE.put("/notifications/", "notifications");
        PATH_TO_RESOURCE.put("/webhooks/", "webhooks");
        PATH_TO_RESOURCE.put("/edi/", "edi");
        PATH_TO_RESOURCE.put("/cycle-counts/", "cycle-counts");
        PATH_TO_RESOURCE.put("/rate-shopping/", "rate-shopping");
        PATH_TO_RESOURCE.put("/import/", "import");
        PATH_TO_RESOURCE.put("/audit/", "audit");
        PATH_TO_RESOURCE.put("/integration/", "integration");
        PATH_TO_RESOURCE.put("/integrations/", "integration");
        PATH_TO_RESOURCE.put("/integration-platform/", "integration");
        PATH_TO_RESOURCE.put("/integration-stores/", "integration");
        PATH_TO_RESOURCE.put("/integration-hub/", "integration");
        PATH_TO_RESOURCE.put("/shopify/", "integration");
        PATH_TO_RESOURCE.put("/email-parser/", "integration");
        PATH_TO_RESOURCE.put("/api/sample-data/", "settings");
        PATH_TO_RESOURCE.put("/fulfillment/", "fulfillment");
        PATH_TO_RESOURCE.put("/receiving/", "warehouse");
        PATH_TO_RESOURCE.put("/bopis/", "inventory");
    }

    private static final Map<String, String> METHOD_TO_ACTION = Map.of(
        "GET", "view",
        "POST", "create",
        "PUT", "edit",
        "PATCH", "edit",
        "DELETE", "delete"
    );

    public String resolveResource(String requestPath) {
        String normalized = requestPath.endsWith("/") ? requestPath : requestPath + "/";
        for (Map.Entry<String, String> entry : PATH_TO_RESOURCE.entrySet()) {
            if (normalized.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    public String resolveAction(String httpMethod) {
        return METHOD_TO_ACTION.getOrDefault(httpMethod.toUpperCase(), null);
    }

    public boolean hasPermission(String role, String resource, String action) {
        if (role == null || resource == null || action == null) return false;
        if (role.equals("ADMIN")) return true;

        UUID tenantId;
        try {
            tenantId = TenantContext.getCurrentTenantId();
        } catch (IllegalStateException e) {
            log.warn("No tenant context available for permission check role={} resource={} action={}", role, resource, action);
            return false;
        }
        String cacheKey = tenantId + ":" + role;

        List<RolePermission> perms = cache.computeIfAbsent(cacheKey, k -> {
            log.debug("Cache miss for role permissions: {}", cacheKey);
            List<RolePermission> loaded = rolePermissionRepository.findByTenantIdAndRole(tenantId, role);
            return new CacheEntry(loaded != null ? loaded : List.of(), System.currentTimeMillis());
        }).permissions;

        for (RolePermission rp : perms) {
            if (rp.getPermissionGroup().equals("*")) return true;
            if (!rp.getPermissionGroup().equals(resource)) continue;
            if (rp.getPermissionName().equals("*")) return true;
            if (rp.getPermissionName().equals(action)) {
                return isActionGranted(rp, action);
            }
        }
        return false;
    }

    public boolean hasPermissionCached(String role, String resource, String action) {
        return hasPermission(role, resource, action);
    }

    private boolean isActionGranted(RolePermission rp, String action) {
        return switch (action) {
            case "view" -> Boolean.TRUE.equals(rp.getCanView());
            case "create" -> Boolean.TRUE.equals(rp.getCanCreate());
            case "edit" -> Boolean.TRUE.equals(rp.getCanEdit());
            case "delete" -> Boolean.TRUE.equals(rp.getCanDelete());
            default -> false;
        };
    }

    public void invalidateCache(UUID tenantId, String role) {
        cache.remove(tenantId + ":" + role);
        log.debug("Permission cache invalidated for tenant={} role={}", tenantId, role);
    }

    public void invalidateAllCache() {
        cache.clear();
        log.debug("Permission cache fully invalidated");
    }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        cache.values().removeIf(entry -> now - entry.createdAt > CACHE_TTL_MS);
    }

    private record CacheEntry(List<RolePermission> permissions, long createdAt) {}
}
