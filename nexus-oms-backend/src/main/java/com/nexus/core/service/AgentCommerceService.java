package com.nexus.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.core.ai.ProposalService;
import com.nexus.core.model.agent.AgentCheckoutSession;
import com.nexus.core.model.order.OrderHeader;
import com.nexus.core.model.order.OrderItem;
import com.nexus.core.model.product.Product;
import com.nexus.core.model.product.ProductStore;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Orders from AI shopping agents (doc 25, features 1 and 3). A checkout session holds what the agent is buying, where
 * it goes and how fast; its delivery options and dates come from the promise service, its prices from the product
 * model, so an agent never promises what the store can't do. Completing it makes an order through the same order
 * service as the web store. After purchase, the buyer's agent may cancel, change the address or start a return inside
 * the store's policy; anything outside it becomes a proposal for a person, never a refusal without a reason.
 * <p>
 * Payment: the agent's delegated payment token is recorded by reference (a hash) on an authorized payment; charging it
 * is the payment provider's integration, which is not part of this service.
 */
@Service("coreAgentCommerceService")
@Transactional
public class AgentCommerceService {

    static final ObjectMapper JSON = new ObjectMapper();
    public static final List<String> OPTIONS = List.of("STANDARD", "NEXT_DAY");

    private final EntityManager em;
    private final SequenceService sequences;
    private final ProductStoreService stores;
    private final ProductService products;
    private final PartyService parties;
    private final OrderService orders;
    private final ReturnService returns;
    private final PromiseService promise;
    private final ProposalService proposals;

    public AgentCommerceService(EntityManager em, SequenceService sequences, ProductStoreService stores, ProductService products, PartyService parties,
                                OrderService orders, ReturnService returns, PromiseService promise, ProposalService proposals) {
        this.em = em;
        this.sequences = sequences;
        this.stores = stores;
        this.products = products;
        this.parties = parties;
        this.orders = orders;
        this.returns = returns;
        this.promise = promise;
        this.proposals = proposals;
    }

    // ── access ──

    /** A new agent key for the store; only its hash is kept. Shown once. */
    public String issueKey(String productStoreId) {
        byte[] b = new byte[32];
        new java.security.SecureRandom().nextBytes(b);
        String key = "nxa_" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        stores.setSetting(productStoreId, "AGENT_API_KEY_HASH", hash(key));
        return key;
    }

    /** The store an agent key belongs to. */
    @Transactional(readOnly = true)
    public Optional<String> storeForKey(String key) {
        if (key == null || key.isBlank()) return Optional.empty();
        return em.createQuery("SELECT s.productStoreId FROM ProductStoreSetting s WHERE s.settingTypeEnumId = 'AGENT_API_KEY_HASH' AND s.settingValue = :h", String.class)
                .setParameter("h", hash(key.trim())).setMaxResults(1).getResultList().stream().findFirst();
    }

    // ── checkout ──

    public record Item(String id, int quantity) {
    }

    public record Address(String name, String lineOne, String lineTwo, String city, String state, String country, String postalCode) {
    }

    public record Buyer(String firstName, String lastName, String email, String phone) {
    }

    public record Line(String id, String productId, String title, int quantity, BigDecimal unitPrice, BigDecimal subtotal, BigDecimal tax, BigDecimal total) {
    }

    public record Option(String id, String title, String subtitle, LocalDateTime earliest, LocalDateTime latest, BigDecimal subtotal, BigDecimal tax, BigDecimal total) {
    }

    public record Message(String type, String code, String content) {
    }

    public record Session(String id, String status, String currency, Buyer buyer, Address address, List<Line> lines, List<Option> options, String optionId,
                          Map<String, BigDecimal> totals, List<Message> messages, String orderId) {
    }

    public Session create(String storeId, List<Item> items, Buyer buyer, Address address, String idempotencyKey) {
        if (idempotencyKey != null) {
            Optional<AgentCheckoutSession> same = em.createQuery("SELECT s FROM AgentCheckoutSession s WHERE s.productStoreId = :p AND s.createIdempotencyKey = :k",
                    AgentCheckoutSession.class).setParameter("p", storeId).setParameter("k", idempotencyKey).setMaxResults(1).getResultList().stream().findFirst();
            if (same.isPresent()) return view(same.get());
        }
        ProductStore store = store(storeId);
        AgentCheckoutSession s = new AgentCheckoutSession();
        s.setCheckoutSessionId(sequences.nextId("AgentCheckoutSession"));
        s.setProductStoreId(storeId);
        s.setStatusId("not_ready_for_payment");
        s.setCurrencyUomId(store.getDefaultCurrencyUomId());
        s.setItemsJson(write(items == null ? List.of() : items));
        s.setCreateIdempotencyKey(idempotencyKey);
        s.setExpiresAt(CoreClock.now().plusHours(6));
        apply(s, buyer, address, null);
        em.persist(s);
        return view(s);
    }

    public Session update(String storeId, String sessionId, List<Item> items, Buyer buyer, Address address, String optionId) {
        AgentCheckoutSession s = session(storeId, sessionId, true);
        open(s);
        if (items != null) s.setItemsJson(write(items));
        apply(s, buyer, address, optionId);
        return view(s);
    }

    public Session cancel(String storeId, String sessionId) {
        AgentCheckoutSession s = session(storeId, sessionId, true);
        open(s);
        s.setStatusId("canceled");
        return view(s);
    }

    @Transactional(readOnly = true)
    public Session get(String storeId, String sessionId) {
        return view(session(storeId, sessionId, false));
    }

    /**
     * Completes a ready session: the buyer becomes (or is found as) a customer, the address is saved, and an order is
     * made with the chosen delivery, its price and tax, and the agent's payment recorded as authorized.
     */
    public Session complete(String storeId, String sessionId, String paymentToken, String paymentProvider, String idempotencyKey) {
        AgentCheckoutSession s = session(storeId, sessionId, true);
        if ("completed".equals(s.getStatusId())) {
            if (idempotencyKey != null && idempotencyKey.equals(s.getCompleteIdempotencyKey())) return view(s);
            throw new IllegalStateException("This checkout is already completed");
        }
        open(s);
        Session v = view(s);
        if (!"ready_for_payment".equals(v.status())) throw new IllegalStateException("The checkout is not ready: " + v.messages().stream().map(Message::content).toList());
        if (paymentToken == null || paymentToken.isBlank()) throw new IllegalArgumentException("A payment token is required");
        Buyer b = read(s, "buyer", Buyer.class);
        String party = parties.findByEmail(b.email()).stream().findFirst().orElseGet(() -> {
            String id = parties.createPerson(PartyService.PersonName.parse(((b.firstName() == null ? "" : b.firstName()) + " " + (b.lastName() == null ? "" : b.lastName())).trim()), null);
            parties.addRole(id, "CUSTOMER");
            parties.addEmail(id, b.email(), "PRIMARY_EMAIL");
            return id;
        });
        Address a = read(s, "address", Address.class);
        String shipTo = parties.addPostalAddress(party, new PartyService.AddressInput(a.name(), a.lineOne(), a.lineTwo(), a.city(), a.state(), a.postalCode(),
                a.country(), null, null, null), "SHIPPING_LOCATION");
        List<OrderService.NewItem> items = new ArrayList<>();
        for (Line l : v.lines()) items.add(new OrderService.NewItem(l.productId(), l.title(), BigDecimal.valueOf(l.quantity()), l.unitPrice(), null, null, 0));
        Option chosen = v.options().stream().filter(o -> o.id().equals(v.optionId())).findFirst().orElseThrow();
        List<OrderService.NewAdjustment> adj = new ArrayList<>();
        adj.add(new OrderService.NewAdjustment("SHIPPING_CHARGES", chosen.subtotal(), null, chosen.title()));
        adj.add(new OrderService.NewAdjustment("SALES_TAX", v.totals().get("tax"), null, "Tax"));
        String orderId = orders.create(new OrderService.NewOrder("SALES_ORDER", storeId, "AGENT_SALES_CHANNEL", s.getCurrencyUomId(), null, s.getCheckoutSessionId(),
                "Agent order", party, null, null, List.of(new OrderService.NewShipGroup(chosen.id(), null, shipTo, null, null, true, false, null, null, chosen.latest(), null)),
                items, adj, List.of(new OrderService.NewPayment("EXT_AGENT_DELEGATED", v.totals().get("total"), "PAYMENT_AUTHORIZED",
                        (paymentProvider == null ? "agent" : paymentProvider) + ":" + hash(paymentToken).substring(0, 16))),
                Map.of("AGENT_CHECKOUT_ID", s.getCheckoutSessionId()), Map.of("agentCheckout", s.getCheckoutSessionId()), null, "Placed by an AI shopping agent", "agent"));
        s.setOrderId(orderId);
        s.setStatusId("completed");
        s.setCompleteIdempotencyKey(idempotencyKey);
        return view(s);
    }

    // ── after purchase ──

    public record Change(String status, String detail, List<String> proposalIds) {
    }

    /** Cancels within the store's window and before anything ships; otherwise a person decides. */
    public Change cancelOrder(String storeId, String orderId, String reason) {
        OrderHeader h = order(storeId, orderId);
        long minutes = stores.settingLong(storeId, "AGENT_CANCEL_MINUTES", 60);
        boolean shipped = em.createQuery("SELECT COUNT(s) FROM Shipment s WHERE s.primaryOrderId = :o AND s.statusId NOT IN ('SHIPMENT_CANCELLED', 'SHIPMENT_INPUT')",
                Long.class).setParameter("o", orderId).getSingleResult() > 0;
        boolean inWindow = !h.getOrderDate().plusMinutes(minutes).isBefore(CoreClock.now());
        if (!shipped && inWindow && List.of("ORDER_CREATED", "ORDER_APPROVED", "ORDER_HOLD").contains(h.getStatusId())) {
            orders.changeStatus(orderId, "ORDER_CANCELLED", "Buyer's agent: " + (reason == null ? "cancelled" : reason), "agent");
            return new Change("done", "Cancelled", List.of());
        }
        List<String> ids = new ArrayList<>();
        for (OrderItem i : orders.items(orderId)) {
            if (!List.of("ITEM_CREATED", "ITEM_APPROVED").contains(i.getStatusId())) continue;
            ids.add(proposals.propose(null, "CANCEL_ITEM", JSON.createObjectNode().put("orderId", orderId).put("orderItemSeqId", i.getOrderItemSeqId()),
                    "Buyer's agent asked to cancel" + (reason == null ? "" : ": " + reason), h.getGrandTotal()).proposalId());
        }
        return new Change("needs_review", shipped ? "Part of the order has shipped; a person will look at it" : "Past the " + minutes + "-minute window; a person will look at it", ids);
    }

    /** Changes the delivery address while nothing has been packed. */
    public Change changeAddress(String storeId, String orderId, Address a) {
        order(storeId, orderId);
        boolean started = em.createQuery("SELECT COUNT(s) FROM Shipment s WHERE s.primaryOrderId = :o AND s.statusId <> 'SHIPMENT_CANCELLED'", Long.class)
                .setParameter("o", orderId).getSingleResult() > 0;
        if (started) return new Change("needs_review", "The order is already being packed; a person must change the address", List.of());
        String party = orders.partyInRole(orderId, "SHIP_TO_CUSTOMER").orElseThrow(() -> new IllegalStateException("The order has no customer"));
        String mech = parties.addPostalAddress(party, new PartyService.AddressInput(a.name(), a.lineOne(), a.lineTwo(), a.city(), a.state(), a.postalCode(),
                a.country(), null, null, null), "SHIPPING_LOCATION");
        em.createQuery("SELECT g.shipGroupSeqId FROM OrderItemShipGroup g WHERE g.orderId = :o", String.class).setParameter("o", orderId).getResultList()
                .forEach(g -> orders.setShipTo(orderId, g, mech));
        return new Change("done", "Address changed", List.of());
    }

    /** Starts a return of shipped items within the store's return days, to the location that shipped them. */
    public Change startReturn(String storeId, String orderId, List<Item> items, String reasonId) {
        order(storeId, orderId);
        ProductStore store = store(storeId);
        long days = store.getDaysToReturn() == null ? 30 : store.getDaysToReturn();
        LocalDateTime lastShipped = em.createQuery("SELECT MAX(i.issuedDateTime) FROM ItemIssuance i WHERE i.orderId = :o", LocalDateTime.class)
                .setParameter("o", orderId).getSingleResult();
        if (lastShipped == null) return new Change("needs_review", "Nothing has shipped yet; cancel instead", List.of());
        if (lastShipped.plusDays(days).isBefore(CoreClock.now())) return new Change("needs_review", "Past the " + days + "-day return window; a person will decide", List.of());
        String facility = em.createQuery("SELECT s.originFacilityId FROM Shipment s WHERE s.primaryOrderId = :o AND s.statusId IN ('SHIPMENT_SHIPPED', 'SHIPMENT_DELIVERED')",
                String.class).setParameter("o", orderId).setMaxResults(1).getResultList().stream().findFirst().orElse(null);
        List<ReturnService.NewReturnItem> lines = new ArrayList<>();
        for (Item i : items) {
            String productId = resolve(i.id()).orElseThrow(() -> new IllegalArgumentException("Unknown item " + i.id()));
            OrderItem oi = orders.items(orderId).stream().filter(x -> productId.equals(x.getProductId())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("The order has no " + i.id()));
            lines.add(new ReturnService.NewReturnItem(oi.getOrderItemSeqId(), BigDecimal.valueOf(i.quantity()), reasonId, "RTN_REFUND"));
        }
        String returnId = returns.create(orderId, lines, facility, null, "agent");
        returns.accept(returnId, "agent");
        return new Change("done", "Return " + returnId + " started; send the items back", List.of());
    }

    // ── internals ──

    private void apply(AgentCheckoutSession s, Buyer buyer, Address address, String optionId) {
        if (buyer != null) {
            s.setBuyerEmail(buyer.email());
            s.setBuyerName(((buyer.firstName() == null ? "" : buyer.firstName()) + " " + (buyer.lastName() == null ? "" : buyer.lastName())).trim());
            s.setBuyerPhone(buyer.phone());
            putJson(s, "buyer", buyer);
        }
        if (address != null) putJson(s, "address", address);
        if (optionId != null) {
            if (!OPTIONS.contains(optionId)) throw new IllegalArgumentException("Unknown delivery option " + optionId + "; use one of " + OPTIONS);
            s.setFulfillmentOptionId(optionId);
        }
    }

    private Session view(AgentCheckoutSession s) {
        List<Message> messages = new ArrayList<>();
        List<Line> lines = new ArrayList<>();
        BigDecimal tax = BigDecimal.valueOf(stores.settingLong(s.getProductStoreId(), "AGENT_TAX_PCT", 0));
        Map<String, BigDecimal> want = new LinkedHashMap<>();
        for (JsonNode i : readTree(s.getItemsJson())) {
            String id = i.path("id").asText();
            int q = i.path("quantity").asInt(0);
            Optional<String> productId = resolve(id);
            if (productId.isEmpty() || q <= 0) {
                messages.add(new Message("error", "invalid", productId.isEmpty() ? "Unknown item " + id : "Quantity of " + id + " must be above zero"));
                continue;
            }
            Optional<BigDecimal> price = products.price(productId.get(), "DEFAULT_PRICE", s.getCurrencyUomId(), null);
            if (price.isEmpty()) {
                messages.add(new Message("error", "invalid", id + " has no price in " + s.getCurrencyUomId()));
                continue;
            }
            Product p = em.find(Product.class, productId.get());
            BigDecimal sub = price.get().multiply(BigDecimal.valueOf(q));
            BigDecimal t = sub.multiply(tax).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            lines.add(new Line(id, productId.get(), p.getProductName(), q, price.get(), sub, t, sub.add(t)));
            want.merge(productId.get(), BigDecimal.valueOf(q), BigDecimal::add);
        }
        if (lines.isEmpty() && messages.isEmpty()) messages.add(new Message("error", "missing", "Add at least one item"));
        Address a = read(s, "address", Address.class);
        List<Option> options = new ArrayList<>();
        if (a == null) messages.add(new Message("error", "missing", "A delivery address is needed"));
        else if (!want.isEmpty()) {
            for (String method : OPTIONS) {
                PromiseService.Answer ans = promise.promise(want, new PromiseService.Destination(null, null, a.postalCode(), a.country()), method, null);
                if (!ans.deliverable()) continue;
                BigDecimal price = BigDecimal.valueOf(stores.settingLong(s.getProductStoreId(), "STANDARD".equals(method) ? "AGENT_SHIPPING_STANDARD" : "AGENT_SHIPPING_NEXT_DAY",
                        "STANDARD".equals(method) ? 0 : 15));
                BigDecimal t = price.multiply(tax).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                options.add(new Option(method, "STANDARD".equals(method) ? "Standard delivery" : "Next-day delivery",
                        "Arrives " + ans.best().expectedDelivery() + " to " + ans.best().promiseBy(), ans.best().expectedDelivery().atStartOfDay(),
                        ans.best().promiseBy().atTime(20, 0), price, t, price.add(t)));
            }
            if (options.isEmpty()) messages.add(new Message("error", "out_of_stock", "No location can deliver all of it now; remove or reduce an item"));
        }
        if (read(s, "buyer", Buyer.class) == null || s.getBuyerEmail() == null) messages.add(new Message("error", "missing", "The buyer's email is needed"));
        String optionId = s.getFulfillmentOptionId();
        if (optionId != null && options.stream().noneMatch(o -> o.id().equals(optionId))) {
            messages.add(new Message("error", "invalid", "Delivery option " + optionId + " is not available for this order"));
        } else if (optionId == null && !options.isEmpty()) {
            messages.add(new Message("info", "choose", "Choose a delivery option"));
        }
        if (tax.signum() > 0) messages.add(new Message("info", "tax_estimate", "Tax is an estimate of " + tax.toPlainString() + "%"));
        BigDecimal items = lines.stream().map(Line::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal itemTax = lines.stream().map(Line::tax).reduce(BigDecimal.ZERO, BigDecimal::add);
        Option chosen = options.stream().filter(o -> o.id().equals(optionId)).findFirst().orElse(null);
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        totals.put("items_base_amount", items);
        totals.put("subtotal", items);
        totals.put("fulfillment", chosen == null ? BigDecimal.ZERO : chosen.subtotal());
        totals.put("tax", itemTax.add(chosen == null ? BigDecimal.ZERO : chosen.tax()));
        totals.put("total", items.add(totals.get("fulfillment")).add(totals.get("tax")));
        String status = s.getStatusId();
        if (!"completed".equals(status) && !"canceled".equals(status)) {
            status = messages.stream().noneMatch(m -> "error".equals(m.type())) && chosen != null ? "ready_for_payment" : "not_ready_for_payment";
            s.setStatusId(status);
            s.setTotalAmount(totals.get("total"));
        }
        return new Session(s.getCheckoutSessionId(), status, s.getCurrencyUomId(), read(s, "buyer", Buyer.class), a, lines, options, optionId, totals, messages, s.getOrderId());
    }

    /** An item id from an agent is a SKU (the product feed's id) or a product id. */
    private Optional<String> resolve(String id) {
        Optional<String> bySku = products.findByIdentification("SKU", id);
        return bySku.isPresent() ? bySku : Optional.ofNullable(em.find(Product.class, id)).map(Product::getProductId);
    }

    private void open(AgentCheckoutSession s) {
        if ("completed".equals(s.getStatusId()) || "canceled".equals(s.getStatusId())) throw new IllegalStateException("This checkout is " + s.getStatusId());
        if (s.getExpiresAt() != null && s.getExpiresAt().isBefore(CoreClock.now())) {
            s.setStatusId("canceled");
            throw new IllegalStateException("This checkout expired; start a new one");
        }
    }

    private AgentCheckoutSession session(String storeId, String id, boolean lock) {
        AgentCheckoutSession s = lock ? em.find(AgentCheckoutSession.class, id, LockModeType.PESSIMISTIC_WRITE) : em.find(AgentCheckoutSession.class, id);
        if (s == null || !s.getProductStoreId().equals(storeId)) throw new IllegalArgumentException("Unknown checkout " + id);
        return s;
    }

    private OrderHeader order(String storeId, String orderId) {
        OrderHeader h = em.find(OrderHeader.class, orderId);
        if (h == null || !storeId.equals(h.getProductStoreId())) throw new IllegalArgumentException("Unknown order " + orderId);
        return h;
    }

    private ProductStore store(String storeId) {
        ProductStore s = em.find(ProductStore.class, storeId);
        if (s == null) throw new IllegalArgumentException("Unknown store " + storeId);
        return s;
    }

    // The buyer and address are kept together in addressJson as {"buyer": ..., "address": ...}.
    private void putJson(AgentCheckoutSession s, String field, Object value) {
        ObjectNode n = s.getAddressJson() == null ? JSON.createObjectNode() : (ObjectNode) readTree(s.getAddressJson());
        n.set(field, JSON.valueToTree(value));
        s.setAddressJson(n.toString());
    }

    private <T> T read(AgentCheckoutSession s, String field, Class<T> type) {
        if (s.getAddressJson() == null) return null;
        JsonNode n = readTree(s.getAddressJson()).get(field);
        try {
            return n == null || n.isNull() ? null : JSON.treeToValue(n, type);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static JsonNode readTree(String s) {
        try {
            return s == null ? JSON.createArrayNode() : JSON.readTree(s);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String write(Object o) {
        try {
            return JSON.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String hash(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
