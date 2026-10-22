package com.nexus.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.core.CoreStatus;
import com.nexus.core.model.agent.AgentCheckoutSession;
import com.nexus.core.model.product.Product;
import com.nexus.core.model.product.ProductStore;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Checkout sessions for AI shopping agents (doc 25, feature 1), in the shape of the Agentic Commerce Protocol.
 * <p>
 * A session holds what the agent is buying, for whom, where it goes and how fast. Every time it is read or changed it
 * is priced again ({@link #evaluate}): prices come from the product model, delivery options and dates from the promise
 * service, so an agent never promises what the store can't do. A session is "ready_for_payment" only when it has no
 * error messages and a delivery option is chosen. {@link #complete} makes the order through the same order service as
 * the web store.
 * <p>
 * Payment: the agent's delegated payment token is recorded by reference (a hash) on an authorized payment; charging it
 * is the payment provider's integration, which is not part of this service.
 */
@Service("coreAgentCheckoutService")
@Transactional
public class AgentCheckoutService {

    static final ObjectMapper JSON = new ObjectMapper();

    /** Delivery options an agent may choose. */
    public static final List<String> OPTIONS = List.of("STANDARD", "NEXT_DAY");

    // Session statuses, as the protocol names them.
    public static final String NOT_READY = "not_ready_for_payment";
    public static final String READY = "ready_for_payment";
    public static final String COMPLETED = "completed";
    public static final String CANCELED = "canceled";

    static final long SESSION_HOURS = 6;
    static final String AGENT_USER = "agent";
    static final String AGENT_SALES_CHANNEL = "AGENT_SALES_CHANNEL";
    /** Latest delivery on the promised day, for the protocol's "latest delivery time". */
    static final LocalTime END_OF_DELIVERY_DAY = LocalTime.of(20, 0);

    private final EntityManager entityManager;
    private final SequenceService sequenceService;
    private final ProductStoreService productStoreService;
    private final ProductService productService;
    private final PartyService partyService;
    private final OrderService orderService;
    private final PromiseService promiseService;

    public AgentCheckoutService(EntityManager entityManager, SequenceService sequenceService, ProductStoreService productStoreService,
                                ProductService productService, PartyService partyService, OrderService orderService, PromiseService promiseService) {
        this.entityManager = entityManager;
        this.sequenceService = sequenceService;
        this.productStoreService = productStoreService;
        this.productService = productService;
        this.partyService = partyService;
        this.orderService = orderService;
        this.promiseService = promiseService;
    }

    /** What the agent asks for: a SKU or product id, and how many. */
    public record Item(String id, int quantity) {
    }

    public record Address(String name, String lineOne, String lineTwo, String city, String state, String country, String postalCode) {

        PartyService.AddressInput toAddressInput() {
            return new PartyService.AddressInput(name, lineOne, lineTwo, city, state, postalCode, country, null, null, null);
        }
    }

    public record Buyer(String firstName, String lastName, String email, String phone) {

        String fullName() {
            String first = firstName == null ? "" : firstName;
            String last = lastName == null ? "" : lastName;
            return (first + " " + last).trim();
        }
    }

    /** A priced line: {@code id} is the agent's id for the item. */
    public record Line(String id, String productId, String title, int quantity, BigDecimal unitPrice, BigDecimal subtotal, BigDecimal tax, BigDecimal total) {
    }

    /** A delivery option with its dates and price. */
    public record Option(String id, String title, String subtitle, LocalDateTime earliest, LocalDateTime latest, BigDecimal subtotal, BigDecimal tax,
                         BigDecimal total) {
    }

    /** {@code type} is "error" (blocks payment) or "info". */
    public record Message(String type, String code, String content) {

        static Message error(String code, String content) {
            return new Message("error", code, content);
        }

        static Message info(String code, String content) {
            return new Message("info", code, content);
        }

        boolean isError() {
            return "error".equals(type);
        }
    }

    public record Session(String id, String status, String currency, Buyer buyer, Address address, List<Line> lines, List<Option> options, String optionId,
                          Map<String, BigDecimal> totals, List<Message> messages, String orderId) {
    }

    // ── the session's life ──

    /**
     * Starts a session. The same idempotency key for the same store returns the session it made first, so an agent
     * retrying after a timeout does not start two.
     */
    public Session create(String productStoreId, List<Item> items, Buyer buyer, Address address, String idempotencyKey) {
        if (idempotencyKey != null) {
            Optional<AgentCheckoutSession> alreadyMade = sessionWithCreateKey(productStoreId, idempotencyKey);
            if (alreadyMade.isPresent()) {
                return evaluate(alreadyMade.get());
            }
        }
        ProductStore store = loadStore(productStoreId);
        AgentCheckoutSession session = new AgentCheckoutSession();
        session.setCheckoutSessionId(sequenceService.nextId("AgentCheckoutSession"));
        session.setProductStoreId(productStoreId);
        session.setStatusId(NOT_READY);
        session.setCurrencyUomId(store.getDefaultCurrencyUomId());
        session.setItemsJson(toJson(items == null ? List.of() : items));
        session.setCreateIdempotencyKey(idempotencyKey);
        session.setExpiresAt(CoreClock.now().plusHours(SESSION_HOURS));
        applyChanges(session, buyer, address, null);
        entityManager.persist(session);
        return evaluate(session);
    }

    /** Changes what was sent (null leaves a part as it is) and prices the session again. */
    public Session update(String productStoreId, String sessionId, List<Item> items, Buyer buyer, Address address, String optionId) {
        AgentCheckoutSession session = loadSession(productStoreId, sessionId, true);
        requireOpen(session);
        if (items != null) {
            session.setItemsJson(toJson(items));
        }
        applyChanges(session, buyer, address, optionId);
        return evaluate(session);
    }

    public Session cancel(String productStoreId, String sessionId) {
        AgentCheckoutSession session = loadSession(productStoreId, sessionId, true);
        requireOpen(session);
        session.setStatusId(CANCELED);
        return evaluate(session);
    }

    @Transactional(readOnly = true)
    public Session get(String productStoreId, String sessionId) {
        return evaluate(loadSession(productStoreId, sessionId, false));
    }

    /**
     * Completes a ready session:
     * <ol>
     *   <li>the buyer is found by email, or becomes a new customer;</li>
     *   <li>the address is saved as their shipping address;</li>
     *   <li>an order is made with the priced lines, the chosen delivery and its price, tax, and the agent's payment
     *       recorded as authorized.</li>
     * </ol>
     * Sending the same completion again (same idempotency key) returns the completed session.
     */
    public Session complete(String productStoreId, String sessionId, String paymentToken, String paymentProvider, String idempotencyKey) {
        AgentCheckoutSession session = loadSession(productStoreId, sessionId, true);
        if (COMPLETED.equals(session.getStatusId())) {
            boolean sameRequestAgain = idempotencyKey != null && idempotencyKey.equals(session.getCompleteIdempotencyKey());
            if (sameRequestAgain) {
                return evaluate(session);
            }
            throw new IllegalStateException("This checkout is already completed");
        }
        requireOpen(session);
        Session priced = evaluate(session);
        if (!READY.equals(priced.status())) {
            throw new IllegalStateException("The checkout is not ready: " + priced.messages().stream().map(Message::content).toList());
        }
        if (paymentToken == null || paymentToken.isBlank()) {
            throw new IllegalArgumentException("A payment token is required");
        }

        // Step 1: the customer.
        Buyer buyer = readStored(session, "buyer", Buyer.class);
        String partyId = partyService.findByEmail(buyer.email()).stream().findFirst()
                .orElseGet(() -> newCustomer(buyer));

        // Step 2: where it goes.
        Address address = readStored(session, "address", Address.class);
        String shipToContactMechId = partyService.addPostalAddress(partyId, address.toAddressInput(), "SHIPPING_LOCATION");

        // Step 3: the order.
        List<OrderService.NewItem> orderItems = new ArrayList<>();
        for (Line line : priced.lines()) {
            orderItems.add(new OrderService.NewItem(line.productId(), line.title(), BigDecimal.valueOf(line.quantity()), line.unitPrice(), null, null, 0));
        }
        Option chosenOption = priced.options().stream()
                .filter(option -> option.id().equals(priced.optionId()))
                .findFirst()
                .orElseThrow();
        List<OrderService.NewAdjustment> adjustments = List.of(
                new OrderService.NewAdjustment("SHIPPING_CHARGES", chosenOption.subtotal(), null, chosenOption.title()),
                new OrderService.NewAdjustment("SALES_TAX", priced.totals().get("tax"), null, "Tax"));
        String paymentReference = (paymentProvider == null ? AGENT_USER : paymentProvider) + ":" + AgentKeyService.sha256(paymentToken).substring(0, 16);
        OrderService.NewPayment payment = new OrderService.NewPayment("EXT_AGENT_DELEGATED", priced.totals().get("total"), CoreStatus.Payment.AUTHORIZED,
                paymentReference);
        OrderService.NewShipGroup shipGroup = new OrderService.NewShipGroup(chosenOption.id(), null, shipToContactMechId, null, null, true, false, null, null,
                chosenOption.latest(), null);
        String checkoutId = session.getCheckoutSessionId();
        OrderService.NewOrder newOrder = new OrderService.NewOrder("SALES_ORDER", productStoreId, AGENT_SALES_CHANNEL, session.getCurrencyUomId(), null,
                checkoutId, "Agent order", partyId, null, null, List.of(shipGroup), orderItems, adjustments, List.of(payment),
                Map.of("AGENT_CHECKOUT_ID", checkoutId), Map.of("agentCheckout", checkoutId), null, "Placed by an AI shopping agent", AGENT_USER);
        String orderId = orderService.create(newOrder);

        session.setOrderId(orderId);
        session.setStatusId(COMPLETED);
        session.setCompleteIdempotencyKey(idempotencyKey);
        return evaluate(session);
    }

    // ── pricing and checking ──

    /**
     * Prices the session and checks it: lines, delivery options, totals and the messages that say what is missing.
     * An open session's status and total are updated to match.
     */
    private Session evaluate(AgentCheckoutSession session) {
        List<Message> messages = new ArrayList<>();
        BigDecimal taxPercent = BigDecimal.valueOf(productStoreService.settingLong(session.getProductStoreId(), "AGENT_TAX_PCT", 0));

        // Lines, and the quantity of each product for the delivery promise.
        Map<String, BigDecimal> quantityByProduct = new LinkedHashMap<>();
        List<Line> lines = priceLines(session, taxPercent, messages, quantityByProduct);
        if (lines.isEmpty() && messages.isEmpty()) {
            messages.add(Message.error("missing", "Add at least one item"));
        }

        // Delivery options: only those every item can make.
        Address address = readStored(session, "address", Address.class);
        List<Option> options = new ArrayList<>();
        if (address == null) {
            messages.add(Message.error("missing", "A delivery address is needed"));
        } else if (!quantityByProduct.isEmpty()) {
            options = deliveryOptions(session, address, quantityByProduct, taxPercent);
            if (options.isEmpty()) {
                messages.add(Message.error("out_of_stock", "No location can deliver all of it now; remove or reduce an item"));
            }
        }

        Buyer buyer = readStored(session, "buyer", Buyer.class);
        if (buyer == null || session.getBuyerEmail() == null) {
            messages.add(Message.error("missing", "The buyer's email is needed"));
        }
        String optionId = session.getFulfillmentOptionId();
        boolean optionAvailable = optionId != null && options.stream().anyMatch(option -> option.id().equals(optionId));
        if (optionId != null && !optionAvailable) {
            messages.add(Message.error("invalid", "Delivery option " + optionId + " is not available for this order"));
        } else if (optionId == null && !options.isEmpty()) {
            messages.add(Message.info("choose", "Choose a delivery option"));
        }
        if (taxPercent.signum() > 0) {
            messages.add(Message.info("tax_estimate", "Tax is an estimate of " + taxPercent.toPlainString() + "%"));
        }

        Option chosenOption = options.stream().filter(option -> option.id().equals(optionId)).findFirst().orElse(null);
        Map<String, BigDecimal> totals = totals(lines, chosenOption);

        String status = session.getStatusId();
        boolean stillOpen = !COMPLETED.equals(status) && !CANCELED.equals(status);
        if (stillOpen) {
            boolean noErrors = messages.stream().noneMatch(Message::isError);
            status = noErrors && chosenOption != null ? READY : NOT_READY;
            session.setStatusId(status);
            session.setTotalAmount(totals.get("total"));
        }
        return new Session(session.getCheckoutSessionId(), status, session.getCurrencyUomId(), buyer, address, lines, options, optionId, totals, messages,
                session.getOrderId());
    }

    /** One priced line per valid item; an unknown item, a bad quantity or a missing price is a message instead. */
    private List<Line> priceLines(AgentCheckoutSession session, BigDecimal taxPercent, List<Message> messages, Map<String, BigDecimal> quantityByProduct) {
        List<Line> lines = new ArrayList<>();
        for (JsonNode item : parse(session.getItemsJson())) {
            String agentItemId = item.path("id").asText();
            int quantity = item.path("quantity").asInt(0);
            Optional<String> productId = resolveProduct(agentItemId);
            if (productId.isEmpty()) {
                messages.add(Message.error("invalid", "Unknown item " + agentItemId));
                continue;
            }
            if (quantity <= 0) {
                messages.add(Message.error("invalid", "Quantity of " + agentItemId + " must be above zero"));
                continue;
            }
            Optional<BigDecimal> unitPrice = productService.price(productId.get(), "DEFAULT_PRICE", session.getCurrencyUomId(), null);
            if (unitPrice.isEmpty()) {
                messages.add(Message.error("invalid", agentItemId + " has no price in " + session.getCurrencyUomId()));
                continue;
            }
            Product product = entityManager.find(Product.class, productId.get());
            BigDecimal subtotal = unitPrice.get().multiply(BigDecimal.valueOf(quantity));
            BigDecimal tax = percentOf(subtotal, taxPercent);
            lines.add(new Line(agentItemId, productId.get(), product.getProductName(), quantity, unitPrice.get(), subtotal, tax, subtotal.add(tax)));
            quantityByProduct.merge(productId.get(), BigDecimal.valueOf(quantity), BigDecimal::add);
        }
        return lines;
    }

    /** Each delivery option the promise service says can deliver everything, with its dates and its price from store settings. */
    private List<Option> deliveryOptions(AgentCheckoutSession session, Address address, Map<String, BigDecimal> quantityByProduct, BigDecimal taxPercent) {
        PromiseService.Destination destination = new PromiseService.Destination(null, null, address.postalCode(), address.country());
        List<Option> options = new ArrayList<>();
        for (String method : OPTIONS) {
            PromiseService.Answer answer = promiseService.promise(quantityByProduct, destination, method, null);
            if (!answer.deliverable()) {
                continue;
            }
            boolean standard = "STANDARD".equals(method);
            String priceSetting = standard ? "AGENT_SHIPPING_STANDARD" : "AGENT_SHIPPING_NEXT_DAY";
            long defaultPrice = standard ? 0 : 15;
            BigDecimal price = BigDecimal.valueOf(productStoreService.settingLong(session.getProductStoreId(), priceSetting, defaultPrice));
            BigDecimal tax = percentOf(price, taxPercent);
            PromiseService.Option best = answer.best();
            String title = standard ? "Standard delivery" : "Next-day delivery";
            String subtitle = "Arrives " + best.expectedDelivery() + " to " + best.promiseBy();
            options.add(new Option(method, title, subtitle, best.expectedDelivery().atStartOfDay(), best.promiseBy().atTime(END_OF_DELIVERY_DAY),
                    price, tax, price.add(tax)));
        }
        return options;
    }

    /** The totals the protocol shows, by its type names. */
    private static Map<String, BigDecimal> totals(List<Line> lines, Option chosenOption) {
        BigDecimal itemsAmount = lines.stream().map(Line::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal itemsTax = lines.stream().map(Line::tax).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal delivery = chosenOption == null ? BigDecimal.ZERO : chosenOption.subtotal();
        BigDecimal deliveryTax = chosenOption == null ? BigDecimal.ZERO : chosenOption.tax();
        BigDecimal tax = itemsTax.add(deliveryTax);

        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        totals.put("items_base_amount", itemsAmount);
        totals.put("subtotal", itemsAmount);
        totals.put("fulfillment", delivery);
        totals.put("tax", tax);
        totals.put("total", itemsAmount.add(delivery).add(tax));
        return totals;
    }

    // ── internals ──

    /** Saves what the agent sent: the buyer (also in plain columns, for searching), the address, the delivery option. */
    private void applyChanges(AgentCheckoutSession session, Buyer buyer, Address address, String optionId) {
        if (buyer != null) {
            session.setBuyerEmail(buyer.email());
            session.setBuyerName(buyer.fullName());
            session.setBuyerPhone(buyer.phone());
            storeJson(session, "buyer", buyer);
        }
        if (address != null) {
            storeJson(session, "address", address);
        }
        if (optionId != null) {
            if (!OPTIONS.contains(optionId)) {
                throw new IllegalArgumentException("Unknown delivery option " + optionId + "; use one of " + OPTIONS);
            }
            session.setFulfillmentOptionId(optionId);
        }
    }

    private String newCustomer(Buyer buyer) {
        String partyId = partyService.createPerson(PartyService.PersonName.parse(buyer.fullName()), null);
        partyService.addRole(partyId, "CUSTOMER");
        partyService.addEmail(partyId, buyer.email(), "PRIMARY_EMAIL");
        return partyId;
    }

    /** An item id from an agent is a SKU (the product feed's id) or a product id. */
    Optional<String> resolveProduct(String agentItemId) {
        Optional<String> bySku = productService.findByIdentification("SKU", agentItemId);
        if (bySku.isPresent()) {
            return bySku;
        }
        return Optional.ofNullable(entityManager.find(Product.class, agentItemId)).map(Product::getProductId);
    }

    /** A completed or cancelled session can't change; an expired one is cancelled now. */
    private static void requireOpen(AgentCheckoutSession session) {
        if (COMPLETED.equals(session.getStatusId()) || CANCELED.equals(session.getStatusId())) {
            throw new IllegalStateException("This checkout is " + session.getStatusId());
        }
        boolean expired = session.getExpiresAt() != null && session.getExpiresAt().isBefore(CoreClock.now());
        if (expired) {
            session.setStatusId(CANCELED);
            throw new IllegalStateException("This checkout expired; start a new one");
        }
    }

    private Optional<AgentCheckoutSession> sessionWithCreateKey(String productStoreId, String idempotencyKey) {
        return entityManager.createQuery("SELECT s FROM AgentCheckoutSession s WHERE s.productStoreId = :storeId AND s.createIdempotencyKey = :key",
                        AgentCheckoutSession.class)
                .setParameter("storeId", productStoreId)
                .setParameter("key", idempotencyKey)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst();
    }

    /** The store's session; another store's session is "unknown", so stores can't see each other's checkouts. */
    private AgentCheckoutSession loadSession(String productStoreId, String sessionId, boolean lockForUpdate) {
        AgentCheckoutSession session = lockForUpdate
                ? entityManager.find(AgentCheckoutSession.class, sessionId, LockModeType.PESSIMISTIC_WRITE)
                : entityManager.find(AgentCheckoutSession.class, sessionId);
        if (session == null || !session.getProductStoreId().equals(productStoreId)) {
            throw new IllegalArgumentException("Unknown checkout " + sessionId);
        }
        return session;
    }

    ProductStore loadStore(String productStoreId) {
        ProductStore store = entityManager.find(ProductStore.class, productStoreId);
        if (store == null) {
            throw new IllegalArgumentException("Unknown store " + productStoreId);
        }
        return store;
    }

    private static BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
        return amount.multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    // The buyer and address are kept together in addressJson as {"buyer": ..., "address": ...}.

    private static void storeJson(AgentCheckoutSession session, String field, Object value) {
        ObjectNode stored = session.getAddressJson() == null ? JSON.createObjectNode() : (ObjectNode) parse(session.getAddressJson());
        stored.set(field, JSON.valueToTree(value));
        session.setAddressJson(stored.toString());
    }

    private static <T> T readStored(AgentCheckoutSession session, String field, Class<T> type) {
        if (session.getAddressJson() == null) {
            return null;
        }
        JsonNode value = parse(session.getAddressJson()).get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return JSON.treeToValue(value, type);
        } catch (JsonProcessingException corrupt) {
            throw new IllegalStateException("Stored " + field + " of checkout " + session.getCheckoutSessionId() + " is not readable", corrupt);
        }
    }

    private static JsonNode parse(String json) {
        try {
            return json == null ? JSON.createArrayNode() : JSON.readTree(json);
        } catch (JsonProcessingException corrupt) {
            throw new IllegalStateException("Stored checkout JSON is not readable", corrupt);
        }
    }

    private static String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException notSerializable) {
            throw new IllegalStateException(notSerializable);
        }
    }
}
