package com.nexus.oms.entitlement;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sellable product modules. OMS and WMS are editions in their own right; PLATFORM (sites,
 * inventory, products, customers, users, settings, integrations, analytics) comes with every
 * edition; THREE_PL, EDI and AI are add-ons.
 */
public enum ProductModule {
    PLATFORM, OMS, WMS, THREE_PL, EDI, AI;

    public static final Set<ProductModule> ADD_ONS = EnumSet.of(THREE_PL, EDI, AI);

    /**
     * API path prefix (after /api/v1) to module. The longest matching prefix wins, so
     * "/twin/orders" is OMS while other "/twin" paths fall through. Unlisted paths are PLATFORM:
     * a new endpoint is never accidentally locked behind a plan.
     */
    static final Map<String, ProductModule> API_PREFIXES = Map.ofEntries(
            // OMS
            Map.entry("/orders", OMS), Map.entry("/returns", OMS), Map.entry("/returns-finance", OMS),
            Map.entry("/pickup", OMS), Map.entry("/order-routing", OMS), Map.entry("/routing-rules", OMS),
            Map.entry("/brokering", OMS), Map.entry("/parked-orders", OMS), Map.entry("/approvals", OMS),
            Map.entry("/rejections", OMS), Map.entry("/promotions", OMS), Map.entry("/endless-aisle", OMS),
            Map.entry("/atp", OMS), Map.entry("/pos", OMS), Map.entry("/fulfillment-limits", OMS), Map.entry("/twin/orders", OMS),
            Map.entry("/reconciliation", OMS), Map.entry("/email-parser", OMS),
            // WMS
            Map.entry("/picking", WMS), Map.entry("/pickers", WMS), Map.entry("/rf", WMS), Map.entry("/rfid", WMS),
            Map.entry("/packing", WMS), Map.entry("/shipping", WMS), Map.entry("/labels", WMS),
            Map.entry("/manifests", WMS), Map.entry("/yards", WMS), Map.entry("/trailers", WMS),
            Map.entry("/waves", WMS), Map.entry("/inventory-receipts", WMS), Map.entry("/cycle-counts", WMS),
            Map.entry("/slotting", WMS), Map.entry("/labor", WMS), Map.entry("/automation", WMS),
            Map.entry("/task-queues", WMS), Map.entry("/carriers", WMS), Map.entry("/carrier-label-config", WMS),
            Map.entry("/rate-shopping", WMS), Map.entry("/freight", WMS), Map.entry("/asn", WMS),
            Map.entry("/twin/warehouses", WMS),
            // Add-ons
            Map.entry("/billing", THREE_PL), Map.entry("/client-portal", THREE_PL), Map.entry("/rate-cards", THREE_PL),
            Map.entry("/edi", EDI),
            Map.entry("/ai", AI), Map.entry("/api/ai", AI), Map.entry("/mcp", AI));

    private static final List<Map.Entry<String, ProductModule>> BY_LENGTH = API_PREFIXES.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()))
            .toList();

    public static ProductModule forApiPath(String path) {
        for (Map.Entry<String, ProductModule> e : BY_LENGTH) {
            String p = e.getKey();
            if (path.equals(p) || path.startsWith(p + "/")) return e.getValue();
        }
        return PLATFORM;
    }
}
