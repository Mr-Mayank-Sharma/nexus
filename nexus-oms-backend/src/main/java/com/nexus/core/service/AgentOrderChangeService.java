package com.nexus.core.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.core.CoreStatus;
import com.nexus.core.ai.ProposalService;
import com.nexus.core.model.order.OrderHeader;
import com.nexus.core.model.order.OrderItem;
import com.nexus.core.model.order.OrderItemShipGroup;
import com.nexus.core.model.product.ProductStore;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import com.nexus.core.CoreTransactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What a buyer's AI agent may do after purchase (doc 25, feature 3): see where the order is, cancel it, change the
 * address, start a return. Each is done at once when it is inside the store's policy; anything outside it becomes a
 * proposal for a person (status "needs_review", with the reason), never a refusal without a reason.
 */
@Service("coreAgentOrderChangeService")
@CoreTransactional
public class AgentOrderChangeService {

    public static final String DONE = "done";
    public static final String NEEDS_REVIEW = "needs_review";

    static final String AGENT_USER = AgentCheckoutService.AGENT_USER;
    static final String CANCEL_MINUTES_SETTING = "AGENT_CANCEL_MINUTES";
    static final long DEFAULT_CANCEL_MINUTES = 60;
    static final long DEFAULT_RETURN_DAYS = 30;

    private final EntityManager entityManager;
    private final OrderService orderService;
    private final PartyService partyService;
    private final ReturnService returnService;
    private final ProductStoreService productStoreService;
    private final AgentCheckoutService checkoutService;
    private final ProposalService proposalService;

    public AgentOrderChangeService(EntityManager entityManager, OrderService orderService, PartyService partyService, ReturnService returnService,
                                   ProductStoreService productStoreService, AgentCheckoutService checkoutService, ProposalService proposalService) {
        this.entityManager = entityManager;
        this.orderService = orderService;
        this.partyService = partyService;
        this.returnService = returnService;
        this.productStoreService = productStoreService;
        this.checkoutService = checkoutService;
        this.proposalService = proposalService;
    }

    /** What happened: DONE or NEEDS_REVIEW, in words, and the proposals a person will look at. */
    public record Change(String status, String detail, List<String> proposalIds) {

        static Change done(String detail) {
            return new Change(DONE, detail, List.of());
        }

        static Change needsReview(String detail, List<String> proposalIds) {
            return new Change(NEEDS_REVIEW, detail, proposalIds);
        }
    }

    // ── where the order is ──

    public record ShipmentView(String trackingNumber, LocalDate estimatedDelivery) {
    }

    /** {@code status} is the order status without its prefix, in lower case ("approved", "completed"). */
    public record OrderView(String orderId, String status, List<ShipmentView> shipments) {
    }

    /** What the buyer may see of their order: its status, and per shipment the tracking and expected delivery. */
    @CoreTransactional(readOnly = true)
    public OrderView view(String productStoreId, String orderId) {
        OrderHeader header = loadStoreOrder(productStoreId, orderId);
        String status = header.getStatusId().replace("ORDER_", "").toLowerCase(Locale.ROOT);
        List<ShipmentView> shipments = entityManager.createQuery("SELECT g FROM OrderItemShipGroup g WHERE g.orderId = :orderId", OrderItemShipGroup.class)
                .setParameter("orderId", orderId)
                .getResultList()
                .stream()
                .map(group -> new ShipmentView(group.getTrackingNumber(),
                        group.getEstimatedDeliveryDate() == null ? null : group.getEstimatedDeliveryDate().toLocalDate()))
                .toList();
        return new OrderView(orderId, status, shipments);
    }

    // ── changes ──

    /**
     * Cancels the order when nothing has shipped, it is still open, and it is within the store's cancel window
     * (setting AGENT_CANCEL_MINUTES, 60 when unset). Otherwise each open item gets a CANCEL_ITEM proposal.
     */
    public Change cancelOrder(String productStoreId, String orderId, String reason) {
        OrderHeader header = loadStoreOrder(productStoreId, orderId);
        long cancelMinutes = productStoreService.settingLong(productStoreId, CANCEL_MINUTES_SETTING, DEFAULT_CANCEL_MINUTES);
        boolean somethingShipped = countShipments(orderId, List.of(CoreStatus.Shipment.CANCELLED, CoreStatus.Shipment.INPUT)) > 0;
        boolean inWindow = !header.getOrderDate().plusMinutes(cancelMinutes).isBefore(CoreClock.now());
        boolean orderOpen = CoreStatus.Order.OPEN.contains(header.getStatusId());

        if (!somethingShipped && inWindow && orderOpen) {
            orderService.changeStatus(orderId, CoreStatus.Order.CANCELLED, "Buyer's agent: " + (reason == null ? "cancelled" : reason), AGENT_USER);
            return Change.done("Cancelled");
        }

        List<String> proposalIds = new ArrayList<>();
        String proposalReason = "Buyer's agent asked to cancel" + (reason == null ? "" : ": " + reason);
        for (OrderItem orderItem : orderService.items(orderId)) {
            if (!CoreStatus.Item.OPEN.contains(orderItem.getStatusId())) {
                continue;
            }
            ObjectNode payload = AgentCheckoutService.JSON.createObjectNode()
                    .put("orderId", orderId)
                    .put("orderItemSeqId", orderItem.getOrderItemSeqId());
            proposalIds.add(proposalService.propose(null, "CANCEL_ITEM", payload, proposalReason, header.getGrandTotal()).proposalId());
        }
        String why = somethingShipped
                ? "Part of the order has shipped; a person will look at it"
                : "Past the " + cancelMinutes + "-minute window; a person will look at it";
        return Change.needsReview(why, proposalIds);
    }

    /** Changes the delivery address of every ship group, while no shipment has been started. */
    public Change changeAddress(String productStoreId, String orderId, AgentCheckoutService.Address address) {
        loadStoreOrder(productStoreId, orderId);
        boolean packingStarted = countShipments(orderId, List.of(CoreStatus.Shipment.CANCELLED)) > 0;
        if (packingStarted) {
            return Change.needsReview("The order is already being packed; a person must change the address", List.of());
        }
        String partyId = orderService.partyInRole(orderId, "SHIP_TO_CUSTOMER")
                .orElseThrow(() -> new IllegalStateException("The order has no customer"));
        String contactMechId = partyService.addPostalAddress(partyId, address.toAddressInput(), "SHIPPING_LOCATION");
        entityManager.createQuery("SELECT g.shipGroupSeqId FROM OrderItemShipGroup g WHERE g.orderId = :orderId", String.class)
                .setParameter("orderId", orderId)
                .getResultList()
                .forEach(shipGroupSeqId -> orderService.setShipTo(orderId, shipGroupSeqId, contactMechId));
        return Change.done("Address changed");
    }

    /**
     * Starts and accepts a return of shipped items within the store's return days (from the last shipment), to the
     * location that shipped them. Items are given by SKU or product id.
     */
    public Change startReturn(String productStoreId, String orderId, List<AgentCheckoutService.Item> items, String reasonId) {
        loadStoreOrder(productStoreId, orderId);
        ProductStore store = checkoutService.loadStore(productStoreId);
        long returnDays = store.getDaysToReturn() == null ? DEFAULT_RETURN_DAYS : store.getDaysToReturn();

        LocalDateTime lastShipped = entityManager.createQuery("SELECT MAX(i.issuedDateTime) FROM ItemIssuance i WHERE i.orderId = :orderId", LocalDateTime.class)
                .setParameter("orderId", orderId)
                .getSingleResult();
        if (lastShipped == null) {
            return Change.needsReview("Nothing has shipped yet; cancel instead", List.of());
        }
        if (lastShipped.plusDays(returnDays).isBefore(CoreClock.now())) {
            return Change.needsReview("Past the " + returnDays + "-day return window; a person will decide", List.of());
        }

        String shippedFrom = entityManager.createQuery("SELECT s.originFacilityId FROM Shipment s WHERE s.primaryOrderId = :orderId "
                        + "AND s.statusId IN (:shipped, :delivered)", String.class)
                .setParameter("orderId", orderId)
                .setParameter("shipped", CoreStatus.Shipment.SHIPPED)
                .setParameter("delivered", CoreStatus.Shipment.DELIVERED)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst()
                .orElse(null);

        List<OrderItem> orderItems = orderService.items(orderId);
        List<ReturnService.NewReturnItem> returnLines = new ArrayList<>();
        for (AgentCheckoutService.Item item : items) {
            String productId = checkoutService.resolveProduct(item.id())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown item " + item.id()));
            OrderItem orderItem = orderItems.stream()
                    .filter(candidate -> productId.equals(candidate.getProductId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("The order has no " + item.id()));
            returnLines.add(new ReturnService.NewReturnItem(orderItem.getOrderItemSeqId(), BigDecimal.valueOf(item.quantity()), reasonId, "RTN_REFUND"));
        }
        String returnId = returnService.create(orderId, returnLines, shippedFrom, null, AGENT_USER);
        returnService.accept(returnId, AGENT_USER);
        return Change.done("Return " + returnId + " started; send the items back");
    }

    // ── internals ──

    /** The order, if it belongs to the store; another store's order is "unknown". */
    private OrderHeader loadStoreOrder(String productStoreId, String orderId) {
        OrderHeader header = entityManager.find(OrderHeader.class, orderId);
        if (header == null || !productStoreId.equals(header.getProductStoreId())) {
            throw new IllegalArgumentException("Unknown order " + orderId);
        }
        return header;
    }

    /** How many of the order's shipments are in none of the given statuses. */
    private long countShipments(String orderId, List<String> exceptStatuses) {
        return entityManager.createQuery("SELECT COUNT(s) FROM Shipment s WHERE s.primaryOrderId = :orderId AND s.statusId NOT IN :exceptStatuses", Long.class)
                .setParameter("orderId", orderId)
                .setParameter("exceptStatuses", exceptStatuses)
                .getSingleResult();
    }
}
