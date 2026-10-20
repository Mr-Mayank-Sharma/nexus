package com.nexus.oms.generic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.oms.entity.*;
import com.nexus.oms.jobs.JobCsv;
import com.nexus.oms.repository.*;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Sends Nexus data to another system. Each Nexus record is first turned into a plain JSON "view" with stable field
 * names (listed in {@link #VIEW_FIELDS}); the connection's mapping then says, for each field the other system wants,
 * which view field (or literal, or template) goes there: {@code "fields": { "order.id": "orderNumber", "status": "status|lower" }}.
 * Orders and shipments go out incrementally (changed since the last successful export, 2,000 per run, oldest first);
 * inventory goes out as a full snapshot.
 */
@Component
public class GenericExporter {

    static final int PER_RUN = 2000;

    /** The fields a mapping can read, per entity. Also shown in the UI. */
    public static final Map<String, List<String>> VIEW_FIELDS = Map.of(
            "orders", List.of("id", "orderNumber", "externalId", "channel", "status", "subStatus", "fulfillmentType", "orderType",
                    "requestedShipDate", "currency", "subtotal", "shipping", "tax", "total", "email", "carrier", "trackingNumber",
                    "labelUrl", "createdAt", "updatedAt", "shippedAt", "lines[].sku", "lines[].name", "lines[].quantity",
                    "lines[].unitPrice", "lines[].fulfilledQuantity"),
            "shipments", List.of("id", "orderId", "orderNumber", "channel", "carrier", "serviceLevel", "trackingNumber", "labelUrl",
                    "status", "originLocationId", "createdAt", "shippedAt", "estimatedDelivery"),
            "inventory", List.of("sku", "locationId", "locationName", "onHand", "allocated", "reserved", "held", "safetyStock", "sellable"));

    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final ShipmentRepository shipments;
    private final InventoryRepository inventory;
    private final NodeRepository nodes;
    private final GenericHttp http;

    public GenericExporter(OrderRepository orders, OrderItemRepository items, ShipmentRepository shipments,
                           InventoryRepository inventory, NodeRepository nodes, GenericHttp http) {
        this.orders = orders;
        this.items = items;
        this.shipments = shipments;
        this.inventory = inventory;
        this.nodes = nodes;
        this.http = http;
    }

    /** A record ready to send, with the time it last changed (null for snapshots). */
    public record Item(String ref, ObjectNode payload, LocalDateTime changedAt) {}

    public record Result(int sent, int failedBatches, LocalDateTime newWatermark, String csv, List<String> problems) {}

    /** The Nexus records due for export, mapped. {@code limit} caps how many (preview uses a few). */
    public List<Item> collect(UUID tenantId, String entity, GenericConfig c, LocalDateTime since, int limit) {
        LocalDateTime from = since == null ? LocalDateTime.of(2000, 1, 1, 0, 0) : since;
        List<Item> out = new ArrayList<>();
        switch (entity) {
            case "orders" -> {
                List<NxOrder> due = orders.findTop2000ByTenantIdAndUpdatedAtAfterOrderByUpdatedAtAsc(tenantId, from);
                if (due.size() > limit) due = due.subList(0, limit);
                // all lines in one query rather than one per order
                Map<UUID, List<NxOrderItem>> lines = new HashMap<>();
                if (!due.isEmpty()) {
                    for (NxOrderItem i : items.findByOrderIdIn(due.stream().map(NxOrder::getId).toList())) {
                        lines.computeIfAbsent(i.getOrderId(), k -> new ArrayList<>()).add(i);
                    }
                }
                for (NxOrder o : due) {
                    out.add(new Item(o.getChannelOrderId() != null ? o.getChannelOrderId() : o.getId().toString(),
                            map(c, orderView(o, lines.getOrDefault(o.getId(), List.of()))), o.getUpdatedAt()));
                }
            }
            case "shipments" -> {
                for (NxShipment s : shipments.findTop2000ByTenantIdAndCreatedAtAfterOrderByCreatedAtAsc(tenantId, from)) {
                    if (out.size() >= limit) break;
                    out.add(new Item(s.getTrackingNumber() != null ? s.getTrackingNumber() : s.getId().toString(), map(c, shipmentView(s)), s.getCreatedAt()));
                }
            }
            case "inventory" -> {
                Map<UUID, String> names = new HashMap<>();
                nodes.findByTenantId(tenantId).forEach(n -> names.put(n.getId(), n.getName()));
                for (NxInventory i : inventory.findByTenantId(tenantId)) {
                    if (out.size() >= limit) break;
                    out.add(new Item(i.getSku() + "@" + i.getNodeId(), map(c, inventoryView(i, names)), null));
                }
            }
            default -> throw new IllegalArgumentException("Unsupported export entity " + entity);
        }
        return out;
    }

    /** Sends (HTTP) or writes (FILE) the items. HTTP stops at the first failed batch so nothing is skipped silently. */
    public Result deliver(GenericConfig c, String secret, List<Item> items, LocalDateTime previousWatermark) {
        List<String> problems = new ArrayList<>();
        if ("FILE".equals(c.deliveryMode())) {
            LocalDateTime wm = previousWatermark;
            for (Item it : items) if (it.changedAt() != null && (wm == null || it.changedAt().isAfter(wm))) wm = it.changedAt();
            return new Result(items.size(), 0, wm, csv(items), problems);
        }
        int sent = 0;
        LocalDateTime wm = previousWatermark;
        for (int from = 0; from < items.size(); from += c.batchSize()) {
            List<Item> batch = items.subList(from, Math.min(items.size(), from + c.batchSize()));
            try {
                http.send(c, secret, body(c, batch).toString());
            } catch (RuntimeException e) {
                problems.add("Batch starting at " + batch.get(0).ref() + " (" + batch.size() + " records) was not accepted: " + e.getMessage());
                return new Result(sent, 1, wm, null, problems);
            }
            sent += batch.size();
            for (Item it : batch) if (it.changedAt() != null && (wm == null || it.changedAt().isAfter(wm))) wm = it.changedAt();
        }
        return new Result(sent, 0, wm, null, problems);
    }

    /** One object for single-record batches without a wrapper; otherwise an array, wrapped if the config says so. */
    static JsonNode body(GenericConfig c, List<Item> batch) {
        if (batch.size() == 1 && c.batchSize() == 1 && c.wrapper().isBlank()) return batch.get(0).payload();
        ArrayNode arr = GenericConfig.JSON.createArrayNode();
        batch.forEach(i -> arr.add(i.payload()));
        if (c.wrapper().isBlank()) return arr;
        ObjectNode o = GenericConfig.JSON.createObjectNode();
        o.set(c.wrapper(), arr);
        return o;
    }

    static ObjectNode map(GenericConfig c, ObjectNode view) {
        ObjectNode out = GenericConfig.JSON.createObjectNode();
        c.fields().forEach((target, expr) -> {
            String v = ValueExpr.compile(expr).eval(view);
            if (v != null) JsonPaths.put(out, target, v);
        });
        if (!c.linesPath().isBlank() && view.has("lines")) {
            ArrayNode lines = out.putArray(c.linesPath());
            for (JsonNode line : view.get("lines")) {
                ObjectNode l = lines.addObject();
                c.lineFields().forEach((target, expr) -> {
                    String v = ValueExpr.compile(expr).eval(line);
                    if (v != null) JsonPaths.put(l, target, v);
                });
            }
        }
        return out;
    }

    /** Flattened CSV: one row per record, or one per line when lines are mapped. Columns follow the mapping order. */
    static String csv(List<Item> items) {
        LinkedHashSet<String> cols = new LinkedHashSet<>();
        List<Map<String, String>> rows = new ArrayList<>();
        for (Item it : items) {
            Map<String, String> base = new LinkedHashMap<>();
            List<Map<String, String>> lineRows = new ArrayList<>();
            it.payload().fields().forEachRemaining(e -> {
                if (e.getValue().isArray()) {
                    for (JsonNode l : e.getValue()) {
                        Map<String, String> lr = new LinkedHashMap<>();
                        flatten(e.getKey() + ".", l, lr);
                        lineRows.add(lr);
                    }
                } else flatten(e.getKey(), e.getValue(), base);
            });
            if (lineRows.isEmpty()) rows.add(base);
            for (Map<String, String> lr : lineRows) { Map<String, String> r = new LinkedHashMap<>(base); r.putAll(lr); rows.add(r); }
        }
        rows.forEach(r -> cols.addAll(r.keySet()));
        StringBuilder sb = new StringBuilder();
        JobCsv.row(sb, cols.toArray());
        for (Map<String, String> r : rows) JobCsv.row(sb, cols.stream().map(k -> (Object) r.getOrDefault(k, "")).toArray());
        return sb.toString();
    }

    private static void flatten(String prefix, JsonNode n, Map<String, String> out) {
        if (n.isObject()) n.fields().forEachRemaining(e -> flatten(prefix.endsWith(".") ? prefix + e.getKey() : prefix + "." + e.getKey(), e.getValue(), out));
        else out.put(prefix.endsWith(".") ? prefix.substring(0, prefix.length() - 1) : prefix, n.isValueNode() ? n.asText() : n.toString());
    }

    ObjectNode orderView(NxOrder o, List<NxOrderItem> orderLines) {
        ObjectNode v = GenericConfig.JSON.createObjectNode();
        v.put("id", str(o.getId()));
        v.put("orderNumber", o.getChannelOrderId());
        v.put("externalId", o.getExternalId());
        v.put("channel", o.getChannel());
        v.put("status", o.getStatus());
        v.put("subStatus", o.getSubStatus());
        v.put("fulfillmentType", o.getFulfillmentType());
        JsonNode meta = meta(o.getMetadata());
        v.put("orderType", meta.path("orderType").asText("B2C"));
        if (meta.has("requestedShipDate")) v.put("requestedShipDate", meta.get("requestedShipDate").asText());
        v.put("currency", o.getCurrency());
        v.put("subtotal", str(o.getSubtotal()));
        v.put("shipping", str(o.getShippingCost()));
        v.put("tax", str(o.getTaxAmount()));
        v.put("total", str(o.getTotal()));
        v.put("email", o.getCustomerEmail());
        v.put("carrier", o.getCarrierId());
        v.put("trackingNumber", o.getTrackingNumber());
        v.put("labelUrl", o.getLabelUrl());
        v.put("createdAt", str(o.getCreatedAt()));
        v.put("updatedAt", str(o.getUpdatedAt()));
        v.put("shippedAt", str(o.getShippedAt()));
        ArrayNode lines = v.putArray("lines");
        for (NxOrderItem i : orderLines) {
            ObjectNode l = lines.addObject();
            l.put("sku", i.getSku());
            l.put("name", i.getProductName());
            l.put("quantity", str(i.getQuantity()));
            l.put("unitPrice", str(i.getUnitPrice()));
            l.put("fulfilledQuantity", str(i.getFulfilledQty() == null ? 0 : i.getFulfilledQty()));
        }
        return v;
    }

    ObjectNode shipmentView(NxShipment s) {
        ObjectNode v = GenericConfig.JSON.createObjectNode();
        v.put("id", str(s.getId()));
        v.put("orderId", str(s.getOrderId()));
        orders.findById(s.getOrderId()).ifPresent(o -> { v.put("orderNumber", o.getChannelOrderId()); v.put("channel", o.getChannel()); });
        v.put("carrier", s.getCarrierId());
        v.put("serviceLevel", s.getServiceLevel());
        v.put("trackingNumber", s.getTrackingNumber());
        v.put("labelUrl", s.getLabelUrl());
        v.put("status", s.getStatus());
        v.put("originLocationId", str(s.getOriginNodeId()));
        v.put("createdAt", str(s.getCreatedAt()));
        v.put("shippedAt", str(s.getShippedAt()));
        v.put("estimatedDelivery", str(s.getEstimatedDelivery()));
        return v;
    }

    static ObjectNode inventoryView(NxInventory i, Map<UUID, String> names) {
        int onHand = nz(i.getQuantityOnHand()), alloc = nz(i.getQuantityAllocated()), res = nz(i.getQuantityReserved()),
                held = nz(i.getQuantityDamaged()), safety = nz(i.getSafetyStock());
        ObjectNode v = GenericConfig.JSON.createObjectNode();
        v.put("sku", i.getSku());
        v.put("locationId", str(i.getNodeId()));
        v.put("locationName", names.get(i.getNodeId()));
        v.put("onHand", String.valueOf(onHand));
        v.put("allocated", String.valueOf(alloc));
        v.put("reserved", String.valueOf(res));
        v.put("held", String.valueOf(held));
        v.put("safetyStock", String.valueOf(safety));
        v.put("sellable", String.valueOf(Math.max(0, onHand - alloc - res - held - safety)));
        return v;
    }

    private static JsonNode meta(String json) {
        try { return json == null ? GenericConfig.JSON.createObjectNode() : GenericConfig.JSON.readTree(json); }
        catch (Exception e) { return GenericConfig.JSON.createObjectNode(); }
    }

    private static int nz(Integer v) { return v == null ? 0 : v; }

    private static String str(Object o) { return o == null ? null : o.toString(); }
}
