package com.nexus.oms.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.core.service.AgentCheckoutService;
import com.nexus.core.service.AgentCheckoutService.Address;
import com.nexus.core.service.AgentCheckoutService.Buyer;
import com.nexus.core.service.AgentCheckoutService.Item;
import com.nexus.core.service.AgentCheckoutService.Session;
import com.nexus.core.service.AgentKeyService;
import com.nexus.core.service.AgentOrderChangeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The order API for AI shopping agents, in the shape of the Agentic Commerce Protocol (checkout sessions: create, update,
 * complete, cancel; amounts in minor units; idempotency keys), plus agent-safe changes after purchase. An agent
 * authenticates with the store's agent key ({@code Authorization: Bearer nxa_...}); the key decides the store. Field
 * names follow the published ACP checkout specification; run its conformance checks before going live with an agent
 * platform.
 * <p>
 * This class only maps protocol JSON to the services and back: {@link AgentKeyService} (who is calling),
 * {@link AgentCheckoutService} (sessions) and {@link AgentOrderChangeService} (after purchase).
 */
@RestController
@RequestMapping("/agent-commerce")
@Tag(name = "Agent commerce", description = "Checkout and order changes for AI shopping agents (ACP-shaped)")
public class AgentCommerceController {

    static final ObjectMapper JSON = new ObjectMapper();
    private static final String BEARER_PREFIX = "Bearer ";

    private final AgentKeyService agentKeyService;
    private final AgentCheckoutService checkoutService;
    private final AgentOrderChangeService orderChangeService;

    public AgentCommerceController(AgentKeyService agentKeyService, AgentCheckoutService checkoutService, AgentOrderChangeService orderChangeService) {
        this.agentKeyService = agentKeyService;
        this.checkoutService = checkoutService;
        this.orderChangeService = orderChangeService;
    }

    // ── checkout sessions ──

    @Operation(summary = "Create a checkout session")
    @PostMapping("/checkout_sessions")
    public ResponseEntity<JsonNode> create(@RequestHeader(value = "Authorization", required = false) String authorization,
                                           @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey, @RequestBody JsonNode body) {
        Session session = checkoutService.create(storeOf(authorization), items(body), buyer(body), address(body), idempotencyKey);
        return ResponseEntity.status(201).body(toAcp(session));
    }

    @Operation(summary = "Update a checkout session: items, buyer, address or delivery option")
    @PostMapping("/checkout_sessions/{id}")
    public JsonNode update(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String id,
                           @RequestBody JsonNode body) {
        List<Item> items = body.has("items") ? items(body) : null;
        String optionId = body.path("fulfillment_option_id").asText(null);
        return toAcp(checkoutService.update(storeOf(authorization), id, items, buyer(body), address(body), optionId));
    }

    @Operation(summary = "A checkout session")
    @GetMapping("/checkout_sessions/{id}")
    public JsonNode get(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String id) {
        return toAcp(checkoutService.get(storeOf(authorization), id));
    }

    @Operation(summary = "Complete a checkout session with the agent's payment token: the order is placed")
    @PostMapping("/checkout_sessions/{id}/complete")
    public JsonNode complete(@RequestHeader(value = "Authorization", required = false) String authorization,
                             @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey, @PathVariable String id,
                             @RequestBody JsonNode body) {
        String productStoreId = storeOf(authorization);
        if (body.has("buyer")) {
            checkoutService.update(productStoreId, id, null, buyer(body), null, null);
        }
        String paymentToken = body.at("/payment_data/token").asText(null);
        String paymentProvider = body.at("/payment_data/provider").asText(null);
        return toAcp(checkoutService.complete(productStoreId, id, paymentToken, paymentProvider, idempotencyKey));
    }

    @Operation(summary = "Cancel a checkout session")
    @PostMapping("/checkout_sessions/{id}/cancel")
    public JsonNode cancel(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String id) {
        return toAcp(checkoutService.cancel(storeOf(authorization), id));
    }

    // ── after purchase ──

    @Operation(summary = "Where an order is: status, items, tracking, expected delivery (customer-safe)")
    @GetMapping("/orders/{orderId}")
    public JsonNode order(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String orderId) {
        AgentOrderChangeService.OrderView order = orderChangeService.view(storeOf(authorization), orderId);
        ObjectNode response = JSON.createObjectNode().put("id", order.orderId()).put("status", order.status());
        ArrayNode shipments = response.putArray("shipments");
        for (AgentOrderChangeService.ShipmentView shipment : order.shipments()) {
            shipments.addObject()
                    .put("tracking_number", shipment.trackingNumber())
                    .put("estimated_delivery", shipment.estimatedDelivery() == null ? null : shipment.estimatedDelivery().toString());
        }
        return response;
    }

    @Operation(summary = "Cancel an order for the buyer: done within the store's policy, otherwise a person decides")
    @PostMapping("/orders/{orderId}/cancel")
    public JsonNode cancelOrder(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String orderId,
                                @RequestBody(required = false) JsonNode body) {
        String reason = body == null ? null : body.path("reason").asText(null);
        return toAcp(orderChangeService.cancelOrder(storeOf(authorization), orderId, reason));
    }

    @Operation(summary = "Change the delivery address before packing starts")
    @PostMapping("/orders/{orderId}/address")
    public JsonNode address(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String orderId,
                            @RequestBody JsonNode body) {
        Address newAddress = address(body);
        if (newAddress == null) {
            throw new IllegalArgumentException("Send fulfillment_address");
        }
        return toAcp(orderChangeService.changeAddress(storeOf(authorization), orderId, newAddress));
    }

    @Operation(summary = "Start a return of shipped items within the store's return window")
    @PostMapping("/orders/{orderId}/returns")
    public JsonNode returns(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String orderId,
                            @RequestBody JsonNode body) {
        return toAcp(orderChangeService.startReturn(storeOf(authorization), orderId, items(body), body.path("reason").asText(null)));
    }

    // ── reading requests ──

    /** The store whose agent key the request carries; 401 without a valid one. */
    private String storeOf(String authorization) {
        String key = authorization != null && authorization.startsWith(BEARER_PREFIX) ? authorization.substring(BEARER_PREFIX.length()).trim() : null;
        return agentKeyService.storeForKey(key).orElseThrow(Unauthorized::new);
    }

    static List<Item> items(JsonNode body) {
        List<Item> items = new ArrayList<>();
        for (JsonNode item : body.path("items")) {
            items.add(new Item(item.path("id").asText(), item.path("quantity").asInt(1)));
        }
        return items;
    }

    static Buyer buyer(JsonNode body) {
        JsonNode buyer = body.get("buyer");
        if (buyer == null) {
            return null;
        }
        return new Buyer(buyer.path("first_name").asText(null), buyer.path("last_name").asText(null), buyer.path("email").asText(null),
                buyer.path("phone_number").asText(null));
    }

    static Address address(JsonNode body) {
        JsonNode address = body.get("fulfillment_address");
        if (address == null) {
            return null;
        }
        return new Address(address.path("name").asText(null), address.path("line_one").asText(null), address.path("line_two").asText(null),
                address.path("city").asText(null), address.path("state").asText(null), address.path("country").asText(null),
                address.path("postal_code").asText(null));
    }

    // ── writing responses ──

    /** Minor units (cents) for two-decimal currencies. */
    static long minorUnits(BigDecimal amount) {
        return amount == null ? 0 : amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** A session in the protocol's checkout shape. */
    static JsonNode toAcp(Session session) {
        ObjectNode response = JSON.createObjectNode()
                .put("id", session.id())
                .put("status", session.status())
                .put("currency", session.currency() == null ? null : session.currency().toLowerCase(Locale.ROOT));
        if (session.buyer() != null) {
            response.putObject("buyer")
                    .put("first_name", session.buyer().firstName())
                    .put("last_name", session.buyer().lastName())
                    .put("email", session.buyer().email())
                    .put("phone_number", session.buyer().phone());
        }
        response.putObject("payment_provider").put("provider", "stripe").putArray("supported_payment_methods").add("card");

        ArrayNode lineItems = response.putArray("line_items");
        for (AgentCheckoutService.Line line : session.lines()) {
            ObjectNode lineItem = lineItems.addObject().put("id", line.id());
            lineItem.putObject("item").put("id", line.id()).put("quantity", line.quantity());
            lineItem.put("base_amount", minorUnits(line.subtotal()))
                    .put("discount", 0)
                    .put("subtotal", minorUnits(line.subtotal()))
                    .put("tax", minorUnits(line.tax()))
                    .put("total", minorUnits(line.total()));
        }

        if (session.address() != null) {
            Address address = session.address();
            response.putObject("fulfillment_address")
                    .put("name", address.name())
                    .put("line_one", address.lineOne())
                    .put("line_two", address.lineTwo())
                    .put("city", address.city())
                    .put("state", address.state())
                    .put("country", address.country())
                    .put("postal_code", address.postalCode());
        }

        ArrayNode fulfillmentOptions = response.putArray("fulfillment_options");
        for (AgentCheckoutService.Option option : session.options()) {
            fulfillmentOptions.addObject()
                    .put("type", "shipping")
                    .put("id", option.id())
                    .put("title", option.title())
                    .put("subtitle", option.subtitle())
                    .put("earliest_delivery_time", option.earliest() + "Z")
                    .put("latest_delivery_time", option.latest() + "Z")
                    .put("subtotal", minorUnits(option.subtotal()))
                    .put("tax", minorUnits(option.tax()))
                    .put("total", minorUnits(option.total()));
        }
        if (session.optionId() != null) {
            response.put("fulfillment_option_id", session.optionId());
        }

        ArrayNode totals = response.putArray("totals");
        session.totals().forEach((type, amount) -> totals.addObject()
                .put("type", type)
                .put("display_text", type.replace('_', ' '))
                .put("amount", minorUnits(amount)));

        ArrayNode messages = response.putArray("messages");
        for (AgentCheckoutService.Message message : session.messages()) {
            messages.addObject()
                    .put("type", message.type())
                    .put("code", message.code())
                    .put("content_type", "plain")
                    .put("content", message.content());
        }
        response.putArray("links");
        if (session.orderId() != null) {
            response.putObject("order").put("id", session.orderId()).put("checkout_session_id", session.id());
        }
        return response;
    }

    /** A change after purchase: its status, what happened, and the review ids a person will look at. */
    static JsonNode toAcp(AgentOrderChangeService.Change change) {
        ObjectNode response = JSON.createObjectNode().put("status", change.status()).put("detail", change.detail());
        ArrayNode reviewIds = response.putArray("review_ids");
        change.proposalIds().forEach(reviewIds::add);
        return response;
    }

    // ── errors, in the protocol's error shape ──

    static class Unauthorized extends RuntimeException {
    }

    @ExceptionHandler(Unauthorized.class)
    public ResponseEntity<JsonNode> unauthorized() {
        return ResponseEntity.status(401).body(error("invalid_request", "unauthorized", "A valid store agent key is required"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<JsonNode> badRequest(IllegalArgumentException problem) {
        return ResponseEntity.status(400).body(error("invalid_request", "invalid", problem.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<JsonNode> conflict(IllegalStateException problem) {
        return ResponseEntity.status(409).body(error("invalid_request", "conflict", problem.getMessage()));
    }

    static JsonNode error(String type, String code, String message) {
        return JSON.createObjectNode().put("type", type).put("code", code).put("message", message);
    }
}
