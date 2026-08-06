package com.nexus.oms.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.OffsetDateTime;
import java.util.*;

@Tag(name = "Marketplace Connectors", description = "Mock marketplace connector endpoints served by the Nexus backend")
@RestController
@RequestMapping("/connectors")
public class ConnectorController {

    private static final Set<String> MARKETPLACES = Set.of("amazon", "ebay", "walmart", "bigcommerce");

    private static final Map<String, Object> STATUS = buildStatus();

    private final Map<String, List<Map<String, Object>>> orders = buildOrders();
    private final Map<String, List<Map<String, Object>>> listings = buildListings();
    private final Map<String, Object> inventory = buildInventory();

    @Operation(summary = "Get connector status for all marketplaces")
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(STATUS);
    }

    @Operation(summary = "Configure a connector")
    @PostMapping("/configure")
    public ResponseEntity<Map<String, Object>> configure(@RequestBody Map<String, Object> body) {
        Object marketplace = body.get("marketplace");
        if (!(marketplace instanceof String m) || !MARKETPLACES.contains(m)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "error", "Unknown marketplace: " + marketplace));
        }
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("message", capitalize(m) + " connector configuration saved");
        return ResponseEntity.ok(res);
    }

    @Operation(summary = "Authorize a connector")
    @PostMapping("/{marketplace}/authorize")
    public ResponseEntity<Map<String, Object>> authorize(@PathVariable String marketplace, @RequestBody Map<String, Object> body) {
        if (!MARKETPLACES.contains(marketplace)) {
            return notFound(marketplace);
        }
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("note", capitalize(marketplace) + " connector authorized in sandbox mode");
        return ResponseEntity.ok(res);
    }

    @Operation(summary = "Disconnect a connector")
    @PostMapping("/{marketplace}/disconnect")
    public ResponseEntity<Map<String, Object>> disconnect(@PathVariable String marketplace) {
        if (!MARKETPLACES.contains(marketplace)) {
            return notFound(marketplace);
        }
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("note", capitalize(marketplace) + " connector disconnected");
        return ResponseEntity.ok(res);
    }

    @Operation(summary = "Trigger a sync with a marketplace")
    @PostMapping("/{marketplace}/sync")
    public ResponseEntity<Map<String, Object>> sync(@PathVariable String marketplace) {
        if (!MARKETPLACES.contains(marketplace)) {
            return notFound(marketplace);
        }
        List<Map<String, Object>> orderList = orders.getOrDefault(marketplace, List.of());
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("message", capitalize(marketplace) + " sync completed");
        res.put("orderCount", orderList.size());
        return ResponseEntity.ok(res);
    }

    @Operation(summary = "Fetch orders from a marketplace connector")
    @GetMapping("/{marketplace}/orders")
    public ResponseEntity<Map<String, Object>> getOrders(@PathVariable String marketplace) {
        if (!MARKETPLACES.contains(marketplace)) {
            return notFound(marketplace);
        }
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("orders", orders.getOrDefault(marketplace, List.of()));
        return ResponseEntity.ok(res);
    }

    @Operation(summary = "Fetch a single order from a marketplace connector")
    @GetMapping("/{marketplace}/orders/{orderId}")
    public ResponseEntity<Map<String, Object>> getOrderDetail(@PathVariable String marketplace, @PathVariable String orderId) {
        if (!MARKETPLACES.contains(marketplace)) {
            return notFound(marketplace);
        }
        Optional<Map<String, Object>> match = orders.getOrDefault(marketplace, List.of()).stream()
            .filter(o -> orderId.equals(o.get("orderId")))
            .findFirst();
        if (match.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("success", false, "error", "Order not found: " + orderId));
        }
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("order", match.get());
        return ResponseEntity.ok(res);
    }

    @Operation(summary = "Fetch inventory from a marketplace connector")
    @GetMapping("/{marketplace}/inventory")
    public ResponseEntity<Map<String, Object>> getInventory(@PathVariable String marketplace) {
        if (!MARKETPLACES.contains(marketplace)) {
            return notFound(marketplace);
        }
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.putAll((Map<String, Object>) inventory.get(marketplace));
        return ResponseEntity.ok(res);
    }

    @Operation(summary = "Fetch listings from a marketplace connector")
    @GetMapping("/{marketplace}/listings")
    public ResponseEntity<Map<String, Object>> getListings(@PathVariable String marketplace) {
        if (!MARKETPLACES.contains(marketplace)) {
            return notFound(marketplace);
        }
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("listings", listings.getOrDefault(marketplace, List.of()));
        return ResponseEntity.ok(res);
    }

    private ResponseEntity<Map<String, Object>> notFound(String marketplace) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("success", false, "error", "Unknown marketplace: " + marketplace));
    }

    private static String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private static Map<String, Object> buildStatus() {
        String lastSync = OffsetDateTime.now().minusHours(3).toString();
        Map<String, Object> amazon = connector("Amazon", lastSync, 142);
        Map<String, Object> ebay = connector("eBay", lastSync, 87);
        Map<String, Object> walmart = connector("Walmart", lastSync, 64);
        Map<String, Object> bigcommerce = connector("BigCommerce", lastSync, 31);
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("connectors", Map.of(
            "amazon", amazon,
            "ebay", ebay,
            "walmart", walmart,
            "bigcommerce", bigcommerce
        ));
        return res;
    }

    private static Map<String, Object> connector(String label, String lastSync, int ordersSynced) {
        Map<String, Object> c = new HashMap<>();
        c.put("active", true);
        c.put("sandbox", true);
        c.put("lastSync", lastSync);
        c.put("ordersSynced", ordersSynced);
        c.put("error", null);
        c.put("hasCredentials", true);
        return c;
    }

    private static Map<String, Object> order(String orderId, String status, double total, String currency,
                                             String dateKey, String buyer, List<Map<String, Object>> items) {
        Map<String, Object> o = new HashMap<>();
        o.put("orderId", orderId);
        o.put("status", status);
        o.put("total", total);
        o.put("currency", currency);
        o.put("items", items);
        o.put(dateKey, OffsetDateTime.now().minusDays((long) (orderId.hashCode() % 14) + 1).toString());
        o.put(buyer, buyer);
        return o;
    }

    private static List<Map<String, Object>> items(Map<String, Integer> skuQty) {
        List<Map<String, Object>> list = new ArrayList<>();
        skuQty.forEach((sku, qty) -> list.add(Map.of("sku", sku, "qty", qty)));
        return list;
    }

    private static Map<String, Object> listing(String sku, String title, double price, int qty, String status) {
        Map<String, Object> l = new HashMap<>();
        l.put("sku", sku);
        l.put("title", title);
        l.put("price", price);
        l.put("qty", qty);
        l.put("status", status);
        return l;
    }

    private Map<String, List<Map<String, Object>>> buildOrders() {
        Map<String, List<Map<String, Object>>> m = new HashMap<>();
        m.put("amazon", List.of(
            order("amz-101", "Shipped", 124.99, "USD", "purchaseDate", "buyer@example.com",
                items(Map.of("NX-1001", 1, "NX-1003", 2))),
            order("amz-102", "ACTIVE", 45.50, "USD", "purchaseDate", "priya@example.com",
                items(Map.of("NX-2002", 1))),
            order("amz-103", "Completed", 320.00, "EUR", "purchaseDate", "frank@example.de",
                items(Map.of("NX-3001", 1, "NX-3005", 3)))
        ));
        m.put("ebay", List.of(
            order("ebay-201", "COMPLETED", 89.99, "USD", "creationDate", "techbids201",
                items(Map.of("NX-4001", 1))),
            order("ebay-202", "ACTIVE", 210.00, "GBP", "creationDate", "retrogear",
                items(Map.of("NX-5002", 2))),
            order("ebay-203", "COMPLETED", 17.25, "USD", "creationDate", "sneakerfox",
                items(Map.of("NX-6001", 4)))
        ));
        m.put("walmart", List.of(
            order("wmt-301", "SHIPPED", 58.75, "USD", "orderDate", "Carol Davis",
                items(Map.of("NX-7001", 1, "NX-7002", 1))),
            order("wmt-302", "PROCESSING", 129.40, "USD", "orderDate", "Miguel Santos",
                items(Map.of("NX-8001", 2))),
            order("wmt-303", "DELIVERED", 74.20, "CAD", "orderDate", "Emily Chen",
                items(Map.of("NX-9001", 1)))
        ));
        m.put("bigcommerce", List.of(
            order("bc-401", "awaiting_shipment", 249.99, "USD", "dateCreated", "acme-store",
                items(Map.of("NX-1001", 1, "NX-1002", 1)))
        ));
        return m;
    }

    private Map<String, List<Map<String, Object>>> buildListings() {
        Map<String, List<Map<String, Object>>> m = new HashMap<>();
        m.put("amazon", List.of(
            listing("NX-1001", "Wireless Noise-Cancelling Headphones", 99.99, 250, "ACTIVE"),
            listing("NX-1003", "USB-C Fast Charger 65W", 24.99, 800, "ACTIVE"),
            listing("NX-2002", "Smart Fitness Band", 45.50, 120, "ACTIVE")
        ));
        m.put("ebay", List.of(
            listing("NX-4001", "Vintage Mechanical Keyboard", 89.99, 12, "ACTIVE"),
            listing("NX-5002", "Retro Handheld Console", 105.00, 8, "ACTIVE"),
            listing("NX-6001", "Sports Running Socks (4 Pack)", 17.25, 340, "ACTIVE")
        ));
        m.put("walmart", List.of(
            listing("NX-7001", "Stainless Steel Water Bottle 1L", 29.99, 450, "ACTIVE"),
            listing("NX-7002", "Bamboo Cutting Board Set", 28.75, 95, "ACTIVE"),
            listing("NX-8001", "Ergonomic Office Chair", 64.70, 30, "ACTIVE")
        ));
        m.put("bigcommerce", List.of(
            listing("NX-1002", "Bluetooth Speaker Portable", 149.99, 60, "ACTIVE")
        ));
        return m;
    }

    private Map<String, Object> buildInventory() {
        Map<String, Object> m = new HashMap<>();
        m.put("amazon", Map.of("listings", List.of(
            Map.of("sku", "NX-1001", "fulfillable", 250, "inbound", 100, "reserved", 25),
            Map.of("sku", "NX-1003", "fulfillable", 800, "inbound", 0, "reserved", 40)
        )));
        m.put("ebay", Map.of("inventory", List.of(
            Map.of("sku", "NX-4001", "available", 12, "price", 89.99),
            Map.of("sku", "NX-5002", "available", 8, "price", 105.00)
        )));
        m.put("walmart", Map.of("items", List.of(
            Map.of("sku", "NX-7001", "qty", 450, "fulfillmentType", "WFS"),
            Map.of("sku", "NX-7002", "qty", 95, "fulfillmentType", "SELF")
        )));
        m.put("bigcommerce", Map.of("inventory", List.of(
            Map.of("sku", "NX-1002", "available", 60, "price", 149.99)
        )));
        return m;
    }
}
