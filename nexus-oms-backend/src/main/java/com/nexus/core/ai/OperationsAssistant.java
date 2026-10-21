package com.nexus.core.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.core.model.order.OrderHeader;
import com.nexus.core.model.order.OrderItem;
import com.nexus.core.model.order.OrderItemShipGroup;
import com.nexus.core.service.CoreClock;
import com.nexus.core.service.OrderService;
import com.nexus.core.service.StoreNetworkService;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * The operations assistant (doc 25, feature 15): answers questions about orders, stores and stock from live data,
 * read-only. Its tools read the core model in read-only transactions; customer names are masked before the model sees
 * them and restored in the answer.
 */
@Component("coreOperationsAssistant")
public class OperationsAssistant {

    public static final String NAME = "operations_assistant";
    static final String SYSTEM = """
            You are the operations assistant of Nexus, an order management system. You answer questions from store and \
            warehouse managers about orders, locations and stock.
            Rules:
            - Answer only from the results of your tools. If the tools don't show it, say you couldn't find it. Never guess a number.
            - You can only read. If someone asks you to change something, say what they could do and where.
            - Be brief: lead with the answer, then the few facts behind it. Name orders by their id and locations by name.
            - Customer details appear as tokens like [NAME_1]; use them as they are.
            - Times are in UTC.""";

    private final EntityManager em;
    private final OrderService orders;
    private final StoreNetworkService network;
    private final com.nexus.core.service.StoreHealthService health;
    private final AgentRunner runner;
    private final LlmProvider provider;
    private final TransactionTemplate readOnly;
    private final String model;

    public OperationsAssistant(EntityManager em, OrderService orders, StoreNetworkService network, com.nexus.core.service.StoreHealthService health, AgentRunner runner,
                               @org.springframework.beans.factory.annotation.Qualifier("coreAnthropicProvider") LlmProvider provider,
                               PlatformTransactionManager tm, @Value("${nexus.ai.model.operations:claude-sonnet-5-5}") String model) {
        this.em = em;
        this.orders = orders;
        this.network = network;
        this.health = health;
        this.runner = runner;
        this.provider = provider;
        this.readOnly = new TransactionTemplate(tm);
        this.readOnly.setReadOnly(true);
        this.model = model;
    }

    public AgentRunner.Result ask(String question, String userId) {
        return ask(question, userId, provider);
    }

    /** With another provider: a scripted one in tests and evaluation. */
    public AgentRunner.Result ask(String question, String userId, LlmProvider with) {
        return runner.run(agent(), question, userId, with);
    }

    public boolean available() {
        return provider.available();
    }

    public AgentRunner.Agent agent() {
        return new AgentRunner.Agent(NAME, model, SYSTEM, List.of(
                tool("find_order", "An order by its Nexus id or a channel id (Shopify order id, marketplace id, PO number): status, items, where it ships "
                        + "from, tracking, and every location change with its reason.", schema("id", "string", "The order id or channel order id", true), this::findOrder),
                tool("order_counts", "How many orders are in each status, for one store or all, placed in the last days.",
                        schema2("productStoreId", "string", "A store id, or leave out for all", "sinceDays", "integer", "Placed in the last this many days (default 7)"), this::orderCounts),
                tool("stuck_orders", "Approved orders with items still to ship, placed more than some hours ago, oldest first, with where each is meant to ship from.",
                        schema2("olderThanHours", "integer", "Placed more than this many hours ago (default 24)", "limit", "integer", "At most this many (default 20)"), this::stuckOrders),
                tool("stock_lookup", "Which locations can sell a product now, with a confidence level and its reasons. Give a SKU or a product id.",
                        schema("sku", "string", "The SKU or product id", true), this::stockLookup),
                tool("location_problems", "Per location over the last days: items it could not find or that were moved off it, and transfers received short.",
                        schema("days", "integer", "Look back this many days (default 30)", false), this::locationProblems),
                tool("list_locations", "All locations with their type and daily order limit.", JSONObject(), (i, m) -> readOnly.execute(s -> listLocations())),
                tool("store_reliability", "How reliably each location picks the orders routed to it, least reliable first, with the reason.",
                        schema("days", "integer", "Look back this many days (default 90)", false), (i, m) -> AgentRunner.JSON.valueToTree(health.reliability(i.path("days").asInt(90)))),
                tool("phantom_stock", "Products a location shows in stock that are most likely not there, highest risk first, with the reasons; where a count pays.",
                        schema2("facilityId", "string", "One location id, or leave out for all", "limit", "integer", "At most this many (default 20)"),
                        (i, m) -> AgentRunner.JSON.valueToTree(health.phantomStock(i.path("facilityId").asText(null), 30, Math.min(i.path("limit").asInt(20), 100))))),
                8, 2048);
    }

    // ── tools ──

    private JsonNode findOrder(JsonNode in, Masker masker) {
        String id = in.path("id").asText("").trim();
        return readOnly.execute(s -> {
            Optional<String> orderId = Optional.ofNullable(em.find(OrderHeader.class, id)).map(OrderHeader::getOrderId);
            if (orderId.isEmpty()) {
                orderId = em.createQuery("SELECT i.orderId FROM OrderIdentification i WHERE i.idValue = :v AND i.thruDate IS NULL", String.class)
                        .setParameter("v", id).setMaxResults(1).getResultList().stream().findFirst();
            }
            if (orderId.isEmpty()) orderId = em.createQuery("SELECT h.orderId FROM OrderHeader h WHERE h.externalId = :v", String.class)
                    .setParameter("v", id).setMaxResults(1).getResultList().stream().findFirst();
            if (orderId.isEmpty()) return AgentRunner.JSON.createObjectNode().put("found", false).put("message", "No order with id " + id);
            OrderHeader h = em.find(OrderHeader.class, orderId.get());
            ObjectNode o = AgentRunner.JSON.createObjectNode().put("found", true).put("orderId", h.getOrderId()).put("status", h.getStatusId())
                    .put("store", h.getProductStoreId()).put("orderDate", String.valueOf(h.getOrderDate())).put("externalId", h.getExternalId())
                    .put("grandTotal", h.getGrandTotal() == null ? null : h.getGrandTotal().toPlainString()).put("currency", h.getCurrencyUomId());
            orders.partyInRole(h.getOrderId(), "PLACING_CUSTOMER").ifPresent(p -> o.put("customer", masker.name(partyName(p))));
            ArrayNode items = o.putArray("items");
            for (OrderItem i : orders.items(h.getOrderId())) {
                items.addObject().put("item", i.getOrderItemSeqId()).put("description", i.getItemDescription()).put("productId", i.getProductId())
                        .put("quantity", i.getQuantity().stripTrailingZeros().toPlainString())
                        .put("cancelled", i.getCancelQuantity() == null ? "0" : i.getCancelQuantity().stripTrailingZeros().toPlainString())
                        .put("status", i.getStatusId()).put("held", orders.reservedQuantity(h.getOrderId(), i.getOrderItemSeqId()).stripTrailingZeros().toPlainString())
                        .put("shipGroup", i.getShipGroupSeqId());
            }
            ArrayNode groups = o.putArray("shipGroups");
            em.createQuery("SELECT g FROM OrderItemShipGroup g WHERE g.orderId = :o ORDER BY g.shipGroupSeqId", OrderItemShipGroup.class)
                    .setParameter("o", h.getOrderId()).getResultList().forEach(g -> groups.addObject().put("shipGroup", g.getShipGroupSeqId())
                            .put("location", g.getFacilityId() == null ? "not routed yet" : facilityName(g.getFacilityId())).put("method", g.getShipmentMethodTypeId())
                            .put("tracking", g.getTrackingNumber()));
            ArrayNode moves = o.putArray("locationChanges");
            em.createQuery("SELECT c.changeDatetime, c.fromFacilityId, c.facilityId, c.changeReasonEnumId, c.comments FROM OrderFacilityChange c "
                    + "WHERE c.orderId = :o ORDER BY c.changeDatetime", Object[].class).setParameter("o", h.getOrderId()).getResultList()
                    .forEach(c -> moves.addObject().put("at", String.valueOf(c[0])).put("from", c[1] == null ? null : facilityName((String) c[1]))
                            .put("to", c[2] == null ? null : facilityName((String) c[2])).put("reason", (String) c[3]).put("why", (String) c[4]));
            return o;
        });
    }

    private JsonNode orderCounts(JsonNode in, Masker masker) {
        String store = in.path("productStoreId").asText(null);
        int days = in.path("sinceDays").asInt(7);
        return readOnly.execute(s -> {
            ObjectNode o = AgentRunner.JSON.createObjectNode().put("store", store == null ? "all" : store).put("sinceDays", days);
            ObjectNode counts = o.putObject("byStatus");
            var q = em.createQuery("SELECT h.statusId, COUNT(h) FROM OrderHeader h WHERE h.orderDate >= :d" + (store == null ? "" : " AND h.productStoreId = :s")
                    + " GROUP BY h.statusId ORDER BY h.statusId", Object[].class).setParameter("d", CoreClock.now().minusDays(days));
            if (store != null) q.setParameter("s", store);
            q.getResultList().forEach(r -> counts.put((String) r[0], (Long) r[1]));
            return o;
        });
    }

    private JsonNode stuckOrders(JsonNode in, Masker masker) {
        int hours = in.path("olderThanHours").asInt(24);
        int limit = Math.min(in.path("limit").asInt(20), 100);
        return readOnly.execute(s -> {
            LocalDateTime before = CoreClock.now().minusHours(hours);
            ArrayNode out = AgentRunner.JSON.createArrayNode();
            em.createQuery("SELECT DISTINCT h.orderId, h.orderDate, h.productStoreId FROM OrderHeader h, OrderItem i WHERE i.orderId = h.orderId "
                            + "AND h.statusId = 'ORDER_APPROVED' AND i.statusId = 'ITEM_APPROVED' AND h.orderDate < :b ORDER BY h.orderDate", Object[].class)
                    .setParameter("b", before).setMaxResults(limit).getResultList().forEach(r -> {
                        ObjectNode n = out.addObject().put("orderId", (String) r[0]).put("orderDate", String.valueOf(r[1])).put("store", (String) r[2]);
                        ArrayNode where = n.putArray("shipsFrom");
                        em.createQuery("SELECT g.facilityId FROM OrderItemShipGroup g WHERE g.orderId = :o", String.class).setParameter("o", (String) r[0])
                                .getResultList().forEach(f -> where.add(f == null ? "not routed yet" : facilityName(f)));
                    });
            return AgentRunner.JSON.createObjectNode().put("olderThanHours", hours).set("orders", out);
        });
    }

    private JsonNode stockLookup(JsonNode in, Masker masker) {
        String key = in.path("sku").asText("").trim();
        return readOnly.execute(s -> {
            String productId = em.createQuery("SELECT g.productId FROM GoodIdentification g WHERE g.idValue = :v AND g.thruDate IS NULL", String.class)
                    .setParameter("v", key).setMaxResults(1).getResultList().stream().findFirst().orElse(key);
            ObjectNode o = AgentRunner.JSON.createObjectNode().put("productId", productId);
            ArrayNode at = o.putArray("locations");
            network.lookup(productId).forEach(a -> {
                ObjectNode n = at.addObject().put("location", a.facilityName()).put("canSell", a.sellable().stripTrailingZeros().toPlainString())
                        .put("confidence", a.confidence().name());
                ArrayNode r = n.putArray("because");
                a.reasons().forEach(r::add);
            });
            if (at.isEmpty()) o.put("message", "No location can sell it now");
            return o;
        });
    }

    private JsonNode locationProblems(JsonNode in, Masker masker) {
        int days = in.path("days").asInt(30);
        return readOnly.execute(s -> {
            LocalDateTime since = CoreClock.now().minusDays(days);
            ObjectNode o = AgentRunner.JSON.createObjectNode().put("days", days);
            ObjectNode per = o.putObject("byLocation");
            em.createQuery("SELECT x.facilityId, COUNT(x) FROM ExcludedOrderFacility x WHERE x.fromDate >= :d GROUP BY x.facilityId", Object[].class)
                    .setParameter("d", since).getResultList()
                    .forEach(r -> child(per, facilityName((String) r[0])).put("itemsNotFoundOrMovedOff", (Long) r[1]));
            em.createQuery("SELECT t.facilityIdTo, COUNT(t) FROM InventoryTransfer t WHERE t.statusId = 'IXF_RECEIVED_SHORT' AND t.receiveDate >= :d GROUP BY t.facilityIdTo",
                    Object[].class).setParameter("d", since).getResultList()
                    .forEach(r -> child(per, facilityName((String) r[0])).put("transfersReceivedShortWaiting", (Long) r[1]));
            if (per.isEmpty()) o.put("message", "No problems recorded in the last " + days + " days");
            return o;
        });
    }

    private JsonNode listLocations() {
        ArrayNode out = AgentRunner.JSON.createArrayNode();
        em.createQuery("SELECT f.facilityId, f.facilityName, f.facilityTypeId, f.maximumOrderLimit FROM Facility f WHERE f.facilityTypeId NOT IN "
                + "('VIRTUAL_FACILITY', 'IN_TRANSIT', 'DROPSHIP') ORDER BY f.facilityName", Object[].class).getResultList()
                .forEach(r -> out.addObject().put("id", (String) r[0]).put("name", (String) r[1]).put("type", (String) r[2])
                        .put("dailyOrderLimit", r[3] == null ? "none" : String.valueOf(r[3])));
        return out;
    }

    // ── helpers ──

    private static ObjectNode child(ObjectNode parent, String name) {
        return parent.has(name) ? (ObjectNode) parent.get(name) : parent.putObject(name);
    }

    private String facilityName(String facilityId) {
        var f = em.find(com.nexus.core.model.facility.Facility.class, facilityId);
        return f == null ? facilityId : f.getFacilityName();
    }

    private String partyName(String partyId) {
        var p = em.find(com.nexus.core.model.party.Person.class, partyId);
        if (p != null) return ((p.getFirstName() == null ? "" : p.getFirstName()) + " " + (p.getLastName() == null ? "" : p.getLastName())).trim();
        var g = em.find(com.nexus.core.model.party.PartyGroup.class, partyId);
        return g == null ? partyId : g.getGroupName();
    }

    private static AgentRunner.AgentTool tool(String name, String description, JsonNode schema, AgentRunner.Handler h) {
        return new AgentRunner.AgentTool(new LlmProvider.Tool(name, description, schema), h);
    }

    private static ObjectNode JSONObject() {
        ObjectNode s = AgentRunner.JSON.createObjectNode().put("type", "object");
        s.putObject("properties");
        return s;
    }

    private static JsonNode schema(String field, String type, String description, boolean required) {
        ObjectNode s = JSONObject();
        ((ObjectNode) s.get("properties")).putObject(field).put("type", type).put("description", description);
        if (required) s.putArray("required").add(field);
        return s;
    }

    private static JsonNode schema2(String f1, String t1, String d1, String f2, String t2, String d2) {
        ObjectNode s = JSONObject();
        ((ObjectNode) s.get("properties")).putObject(f1).put("type", t1).put("description", d1);
        ((ObjectNode) s.get("properties")).putObject(f2).put("type", t2).put("description", d2);
        return s;
    }
}
