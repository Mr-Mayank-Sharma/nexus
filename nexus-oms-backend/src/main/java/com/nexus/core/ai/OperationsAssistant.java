package com.nexus.core.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.core.service.CoreClock;
import com.nexus.core.service.OperationsReadService;
import com.nexus.core.service.StoreHealthService;
import com.nexus.core.service.StoreNetworkService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The operations assistant (doc 25, feature 15): answers questions about orders, stores and stock from live data,
 * read-only.
 * <p>
 * This class only defines the agent: its instructions, its tools, and how each tool's answer is written as JSON for the
 * model. All reading is done by {@link OperationsReadService} (read-only transactions), {@link StoreNetworkService} and
 * {@link StoreHealthService}. Customer names are masked with {@link Masker} before the model sees them and restored in
 * the answer by {@link AgentRunner}.
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

    static final int MAX_STEPS = 8;
    static final int MAX_TOKENS = 2048;
    /** No tool returns more than this many rows, whatever the model asks for. */
    static final int MAX_ROWS = 100;

    private final OperationsReadService readService;
    private final StoreNetworkService storeNetworkService;
    private final StoreHealthService storeHealthService;
    private final AgentRunner agentRunner;
    private final LlmProvider provider;
    private final String model;

    public OperationsAssistant(OperationsReadService readService, StoreNetworkService storeNetworkService, StoreHealthService storeHealthService,
                               AgentRunner agentRunner, @Qualifier("coreAnthropicProvider") LlmProvider provider,
                               @Value("${nexus.ai.model.operations:claude-sonnet-5-5}") String model) {
        this.readService = readService;
        this.storeNetworkService = storeNetworkService;
        this.storeHealthService = storeHealthService;
        this.agentRunner = agentRunner;
        this.provider = provider;
        this.model = model;
    }

    public AgentRunner.Result ask(String question, String userId) {
        return ask(question, userId, provider);
    }

    /** With another provider: a scripted one in tests and evaluation. */
    public AgentRunner.Result ask(String question, String userId, LlmProvider withProvider) {
        return agentRunner.run(agent(), question, userId, withProvider);
    }

    public boolean available() {
        return provider.available();
    }

    /** The agent: instructions and tools. */
    public AgentRunner.Agent agent() {
        List<AgentRunner.AgentTool> tools = List.of(
                tool("find_order",
                        "An order by its Nexus id or a channel id (Shopify order id, marketplace id, PO number): status, items, where it ships "
                                + "from, tracking, and every location change with its reason.",
                        ToolSchema.of().required("id", "string", "The order id or channel order id"),
                        this::findOrder),
                tool("order_counts",
                        "How many orders are in each status, for one store or all, placed in the last days.",
                        ToolSchema.of()
                                .optional("productStoreId", "string", "A store id, or leave out for all")
                                .optional("sinceDays", "integer", "Placed in the last this many days (default 7)"),
                        this::orderCounts),
                tool("stuck_orders",
                        "Approved orders with items still to ship, placed more than some hours ago, oldest first, with where each is meant to ship from.",
                        ToolSchema.of()
                                .optional("olderThanHours", "integer", "Placed more than this many hours ago (default 24)")
                                .optional("limit", "integer", "At most this many (default 20)"),
                        this::stuckOrders),
                tool("stock_lookup",
                        "Which locations can sell a product now, with a confidence level and its reasons. Give a SKU or a product id.",
                        ToolSchema.of().required("sku", "string", "The SKU or product id"),
                        this::stockLookup),
                tool("location_problems",
                        "Per location over the last days: items it could not find or that were moved off it, and transfers received short.",
                        ToolSchema.of().optional("days", "integer", "Look back this many days (default 30)"),
                        this::locationProblems),
                tool("list_locations",
                        "All locations with their type and daily order limit.",
                        ToolSchema.of(),
                        this::listLocations),
                tool("store_reliability",
                        "How reliably each location picks the orders routed to it, least reliable first, with the reason.",
                        ToolSchema.of().optional("days", "integer", "Look back this many days (default 90)"),
                        this::storeReliability),
                tool("phantom_stock",
                        "Products a location shows in stock that are most likely not there, highest risk first, with the reasons; where a count pays.",
                        ToolSchema.of()
                                .optional("facilityId", "string", "One location id, or leave out for all")
                                .optional("limit", "integer", "At most this many (default 20)"),
                        this::phantomStock));
        return new AgentRunner.Agent(NAME, model, SYSTEM, tools, MAX_STEPS, MAX_TOKENS);
    }

    // ── tools: each reads its input, asks a service, and writes the answer as JSON ──

    private JsonNode findOrder(JsonNode input, Masker masker) {
        String anyId = input.path("id").asText("").trim();
        Optional<OperationsReadService.OrderSummary> found = readService.findOrder(anyId);
        if (found.isEmpty()) {
            return newObject().put("found", false).put("message", "No order with id " + anyId);
        }
        OperationsReadService.OrderSummary order = found.get();

        ObjectNode answer = newObject()
                .put("found", true)
                .put("orderId", order.orderId())
                .put("status", order.statusId())
                .put("store", order.productStoreId())
                .put("orderDate", String.valueOf(order.orderDate()))
                .put("externalId", order.externalId())
                .put("grandTotal", order.grandTotal() == null ? null : order.grandTotal().toPlainString())
                .put("currency", order.currencyUomId());
        order.customerName().ifPresent(name -> answer.put("customer", masker.name(name)));

        ArrayNode items = answer.putArray("items");
        for (OperationsReadService.ItemLine item : order.items()) {
            items.addObject()
                    .put("item", item.orderItemSeqId())
                    .put("description", item.description())
                    .put("productId", item.productId())
                    .put("quantity", plain(item.quantity()))
                    .put("cancelled", plain(item.cancelledQuantity()))
                    .put("status", item.statusId())
                    .put("held", plain(item.heldQuantity()))
                    .put("shipGroup", item.shipGroupSeqId());
        }

        ArrayNode shipGroups = answer.putArray("shipGroups");
        for (OperationsReadService.ShipGroupLine group : order.shipGroups()) {
            shipGroups.addObject()
                    .put("shipGroup", group.shipGroupSeqId())
                    .put("location", group.location())
                    .put("method", group.shipmentMethodTypeId())
                    .put("tracking", group.trackingNumber());
        }

        ArrayNode locationChanges = answer.putArray("locationChanges");
        for (OperationsReadService.LocationChange change : order.locationChanges()) {
            locationChanges.addObject()
                    .put("at", String.valueOf(change.changedAt()))
                    .put("from", change.from())
                    .put("to", change.to())
                    .put("reason", change.reasonEnumId())
                    .put("why", change.comments());
        }
        return answer;
    }

    private JsonNode orderCounts(JsonNode input, Masker masker) {
        String productStoreId = input.path("productStoreId").asText(null);
        int sinceDays = input.path("sinceDays").asInt(7);
        Map<String, Long> countByStatus = readService.orderCountsByStatus(productStoreId, CoreClock.now().minusDays(sinceDays));

        ObjectNode answer = newObject().put("store", productStoreId == null ? "all" : productStoreId).put("sinceDays", sinceDays);
        ObjectNode byStatus = answer.putObject("byStatus");
        countByStatus.forEach(byStatus::put);
        return answer;
    }

    private JsonNode stuckOrders(JsonNode input, Masker masker) {
        int olderThanHours = input.path("olderThanHours").asInt(24);
        int limit = Math.min(input.path("limit").asInt(20), MAX_ROWS);
        List<OperationsReadService.StuckOrder> stuck = readService.stuckOrders(CoreClock.now().minusHours(olderThanHours), limit);

        ArrayNode orders = AgentRunner.JSON.createArrayNode();
        for (OperationsReadService.StuckOrder order : stuck) {
            ObjectNode row = orders.addObject()
                    .put("orderId", order.orderId())
                    .put("orderDate", String.valueOf(order.orderDate()))
                    .put("store", order.productStoreId());
            ArrayNode shipsFrom = row.putArray("shipsFrom");
            order.shipsFrom().forEach(shipsFrom::add);
        }
        return newObject().put("olderThanHours", olderThanHours).set("orders", orders);
    }

    private JsonNode stockLookup(JsonNode input, Masker masker) {
        String skuOrProductId = input.path("sku").asText("").trim();
        String productId = readService.productIdForSku(skuOrProductId);

        ObjectNode answer = newObject().put("productId", productId);
        ArrayNode locations = answer.putArray("locations");
        for (StoreNetworkService.Availability availability : storeNetworkService.lookup(productId)) {
            ObjectNode row = locations.addObject()
                    .put("location", availability.facilityName())
                    .put("canSell", plain(availability.sellable()))
                    .put("confidence", availability.confidence().name());
            ArrayNode because = row.putArray("because");
            availability.reasons().forEach(because::add);
        }
        if (locations.isEmpty()) {
            answer.put("message", "No location can sell it now");
        }
        return answer;
    }

    private JsonNode locationProblems(JsonNode input, Masker masker) {
        int days = input.path("days").asInt(30);
        Map<String, OperationsReadService.LocationProblems> problemsByLocation = readService.problemsByLocation(CoreClock.now().minusDays(days));

        ObjectNode answer = newObject().put("days", days);
        ObjectNode byLocation = answer.putObject("byLocation");
        problemsByLocation.forEach((locationName, problems) -> {
            ObjectNode row = byLocation.putObject(locationName);
            if (problems.itemsNotFoundOrMovedOff() != null) {
                row.put("itemsNotFoundOrMovedOff", problems.itemsNotFoundOrMovedOff());
            }
            if (problems.transfersReceivedShortWaiting() != null) {
                row.put("transfersReceivedShortWaiting", problems.transfersReceivedShortWaiting());
            }
        });
        if (byLocation.isEmpty()) {
            answer.put("message", "No problems recorded in the last " + days + " days");
        }
        return answer;
    }

    private JsonNode listLocations(JsonNode input, Masker masker) {
        ArrayNode locations = AgentRunner.JSON.createArrayNode();
        for (OperationsReadService.Location location : readService.locations()) {
            locations.addObject()
                    .put("id", location.facilityId())
                    .put("name", location.facilityName())
                    .put("type", location.facilityTypeId())
                    .put("dailyOrderLimit", location.dailyOrderLimit() == null ? "none" : String.valueOf(location.dailyOrderLimit()));
        }
        return locations;
    }

    private JsonNode storeReliability(JsonNode input, Masker masker) {
        int days = input.path("days").asInt(90);
        return AgentRunner.JSON.valueToTree(storeHealthService.reliability(days));
    }

    private JsonNode phantomStock(JsonNode input, Masker masker) {
        String facilityId = input.path("facilityId").asText(null);
        int limit = Math.min(input.path("limit").asInt(20), MAX_ROWS);
        return AgentRunner.JSON.valueToTree(storeHealthService.phantomStock(facilityId, 30, limit));
    }

    // ── helpers ──

    private static ObjectNode newObject() {
        return AgentRunner.JSON.createObjectNode();
    }

    private static String plain(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private static AgentRunner.AgentTool tool(String name, String description, ToolSchema schema, AgentRunner.Handler handler) {
        return new AgentRunner.AgentTool(new LlmProvider.Tool(name, description, schema.json()), handler);
    }
}
