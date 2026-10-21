package com.nexus.oms.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.core.service.AgentCommerceService;
import com.nexus.core.service.AgentCommerceService.Address;
import com.nexus.core.service.AgentCommerceService.Buyer;
import com.nexus.core.service.AgentCommerceService.Item;
import com.nexus.core.service.AgentCommerceService.Session;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityManager;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The order API for AI shopping agents, in the shape of the Agentic Commerce Protocol (checkout sessions: create, update,
 * complete, cancel; amounts in minor units; idempotency keys), plus agent-safe changes after purchase. An agent
 * authenticates with the store's agent key ({@code Authorization: Bearer nxa_...}); the key decides the store. Field
 * names follow the published ACP checkout specification; run its conformance checks before going live with an agent
 * platform.
 */
@RestController
@RequestMapping("/agent-commerce")
@Tag(name = "Agent commerce", description = "Checkout and order changes for AI shopping agents (ACP-shaped)")
public class AgentCommerceController {

    static final ObjectMapper JSON = new ObjectMapper();
    private final AgentCommerceService commerce;
    private final EntityManager em;

    public AgentCommerceController(AgentCommerceService commerce, EntityManager em) {
        this.commerce = commerce;
        this.em = em;
    }

    @Operation(summary = "Create a checkout session")
    @PostMapping("/checkout_sessions")
    public ResponseEntity<JsonNode> create(@RequestHeader(value = "Authorization", required = false) String auth,
                                           @RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestBody JsonNode body) {
        return ResponseEntity.status(201).body(acp(commerce.create(store(auth), items(body), buyer(body), address(body), key)));
    }

    @Operation(summary = "Update a checkout session: items, buyer, address or delivery option")
    @PostMapping("/checkout_sessions/{id}")
    public JsonNode update(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable String id, @RequestBody JsonNode body) {
        return acp(commerce.update(store(auth), id, body.has("items") ? items(body) : null, buyer(body), address(body), body.path("fulfillment_option_id").asText(null)));
    }

    @Operation(summary = "A checkout session")
    @GetMapping("/checkout_sessions/{id}")
    public JsonNode get(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable String id) {
        return acp(commerce.get(store(auth), id));
    }

    @Operation(summary = "Complete a checkout session with the agent's payment token: the order is placed")
    @PostMapping("/checkout_sessions/{id}/complete")
    public JsonNode complete(@RequestHeader(value = "Authorization", required = false) String auth, @RequestHeader(value = "Idempotency-Key", required = false) String key,
                             @PathVariable String id, @RequestBody JsonNode body) {
        String storeId = store(auth);
        if (body.has("buyer")) commerce.update(storeId, id, null, buyer(body), null, null);
        return acp(commerce.complete(storeId, id, body.at("/payment_data/token").asText(null), body.at("/payment_data/provider").asText(null), key));
    }

    @Operation(summary = "Cancel a checkout session")
    @PostMapping("/checkout_sessions/{id}/cancel")
    public JsonNode cancel(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable String id) {
        return acp(commerce.cancel(store(auth), id));
    }

    // ── after purchase ──

    @Operation(summary = "Where an order is: status, items, tracking, expected delivery (customer-safe)")
    @GetMapping("/orders/{orderId}")
    @Transactional(readOnly = true)
    public JsonNode order(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable String orderId) {
        String storeId = store(auth);
        var h = em.find(com.nexus.core.model.order.OrderHeader.class, orderId);
        if (h == null || !storeId.equals(h.getProductStoreId())) throw new IllegalArgumentException("Unknown order " + orderId);
        ObjectNode o = JSON.createObjectNode().put("id", orderId).put("status", h.getStatusId().replace("ORDER_", "").toLowerCase());
        ArrayNode groups = o.putArray("shipments");
        em.createQuery("SELECT g FROM OrderItemShipGroup g WHERE g.orderId = :o", com.nexus.core.model.order.OrderItemShipGroup.class).setParameter("o", orderId)
                .getResultList().forEach(g -> groups.addObject().put("tracking_number", g.getTrackingNumber())
                        .put("estimated_delivery", g.getEstimatedDeliveryDate() == null ? null : g.getEstimatedDeliveryDate().toLocalDate().toString()));
        return o;
    }

    @Operation(summary = "Cancel an order for the buyer: done within the store's policy, otherwise a person decides")
    @PostMapping("/orders/{orderId}/cancel")
    public JsonNode cancelOrder(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable String orderId,
                                @RequestBody(required = false) JsonNode body) {
        return change(commerce.cancelOrder(store(auth), orderId, body == null ? null : body.path("reason").asText(null)));
    }

    @Operation(summary = "Change the delivery address before packing starts")
    @PostMapping("/orders/{orderId}/address")
    public JsonNode address(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable String orderId, @RequestBody JsonNode body) {
        Address a = address(body);
        if (a == null) throw new IllegalArgumentException("Send fulfillment_address");
        return change(commerce.changeAddress(store(auth), orderId, a));
    }

    @Operation(summary = "Start a return of shipped items within the store's return window")
    @PostMapping("/orders/{orderId}/returns")
    public JsonNode returns(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable String orderId, @RequestBody JsonNode body) {
        return change(commerce.startReturn(store(auth), orderId, items(body), body.path("reason").asText(null)));
    }

    // ── mapping ──

    private String store(String auth) {
        String key = auth != null && auth.startsWith("Bearer ") ? auth.substring(7).trim() : null;
        return commerce.storeForKey(key).orElseThrow(Unauthorized::new);
    }

    static List<Item> items(JsonNode body) {
        List<Item> out = new ArrayList<>();
        for (JsonNode i : body.path("items")) out.add(new Item(i.path("id").asText(), i.path("quantity").asInt(1)));
        return out;
    }

    static Buyer buyer(JsonNode body) {
        JsonNode b = body.get("buyer");
        return b == null ? null : new Buyer(b.path("first_name").asText(null), b.path("last_name").asText(null), b.path("email").asText(null),
                b.path("phone_number").asText(null));
    }

    static Address address(JsonNode body) {
        JsonNode a = body.get("fulfillment_address");
        return a == null ? null : new Address(a.path("name").asText(null), a.path("line_one").asText(null), a.path("line_two").asText(null),
                a.path("city").asText(null), a.path("state").asText(null), a.path("country").asText(null), a.path("postal_code").asText(null));
    }

    /** Minor units (cents) for two-decimal currencies. */
    static long minor(BigDecimal v) {
        return v == null ? 0 : v.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
    }

    static JsonNode acp(Session s) {
        ObjectNode n = JSON.createObjectNode().put("id", s.id()).put("status", s.status()).put("currency", s.currency() == null ? null : s.currency().toLowerCase());
        if (s.buyer() != null) n.putObject("buyer").put("first_name", s.buyer().firstName()).put("last_name", s.buyer().lastName()).put("email", s.buyer().email())
                .put("phone_number", s.buyer().phone());
        n.putObject("payment_provider").put("provider", "stripe").putArray("supported_payment_methods").add("card");
        ArrayNode lines = n.putArray("line_items");
        s.lines().forEach(l -> {
            ObjectNode li = lines.addObject().put("id", l.id());
            li.putObject("item").put("id", l.id()).put("quantity", l.quantity());
            li.put("base_amount", minor(l.subtotal())).put("discount", 0).put("subtotal", minor(l.subtotal())).put("tax", minor(l.tax())).put("total", minor(l.total()));
        });
        if (s.address() != null) n.putObject("fulfillment_address").put("name", s.address().name()).put("line_one", s.address().lineOne())
                .put("line_two", s.address().lineTwo()).put("city", s.address().city()).put("state", s.address().state()).put("country", s.address().country())
                .put("postal_code", s.address().postalCode());
        ArrayNode opts = n.putArray("fulfillment_options");
        s.options().forEach(o -> opts.addObject().put("type", "shipping").put("id", o.id()).put("title", o.title()).put("subtitle", o.subtitle())
                .put("earliest_delivery_time", o.earliest() + "Z").put("latest_delivery_time", o.latest() + "Z")
                .put("subtotal", minor(o.subtotal())).put("tax", minor(o.tax())).put("total", minor(o.total())));
        if (s.optionId() != null) n.put("fulfillment_option_id", s.optionId());
        ArrayNode totals = n.putArray("totals");
        s.totals().forEach((k, v) -> totals.addObject().put("type", k).put("display_text", k.replace('_', ' ')).put("amount", minor(v)));
        ArrayNode messages = n.putArray("messages");
        s.messages().forEach(m -> messages.addObject().put("type", m.type()).put("code", m.code()).put("content_type", "plain").put("content", m.content()));
        n.putArray("links");
        if (s.orderId() != null) n.putObject("order").put("id", s.orderId()).put("checkout_session_id", s.id());
        return n;
    }

    static JsonNode change(AgentCommerceService.Change c) {
        ObjectNode n = JSON.createObjectNode().put("status", c.status()).put("detail", c.detail());
        ArrayNode ids = n.putArray("review_ids");
        c.proposalIds().forEach(ids::add);
        return n;
    }

    static class Unauthorized extends RuntimeException {
    }

    @ExceptionHandler(Unauthorized.class)
    public ResponseEntity<JsonNode> unauthorized() {
        return ResponseEntity.status(401).body(error("invalid_request", "unauthorized", "A valid store agent key is required"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<JsonNode> bad(IllegalArgumentException e) {
        return ResponseEntity.status(400).body(error("invalid_request", "invalid", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<JsonNode> conflict(IllegalStateException e) {
        return ResponseEntity.status(409).body(error("invalid_request", "conflict", e.getMessage()));
    }

    static JsonNode error(String type, String code, String message) {
        return JSON.createObjectNode().put("type", type).put("code", code).put("message", message);
    }
}
