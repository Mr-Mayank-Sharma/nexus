package com.nexus.core.service;

import com.nexus.core.CoreStatus;
import com.nexus.core.model.order.OrderAdjustment;
import com.nexus.core.model.order.OrderAttribute;
import com.nexus.core.model.order.OrderContactMech;
import com.nexus.core.model.order.OrderHeader;
import com.nexus.core.model.order.OrderIdentification;
import com.nexus.core.model.order.OrderItem;
import com.nexus.core.model.order.OrderItemAttribute;
import com.nexus.core.model.order.OrderItemShipGroup;
import com.nexus.core.model.order.OrderItemShipGroupAssoc;
import com.nexus.core.model.order.OrderPaymentPreference;
import com.nexus.core.model.order.OrderRole;
import com.nexus.core.model.order.OrderStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Orders in the OFBiz way.
 * <p>
 * An order is a header (who, which store, which channel, its status and total) with:
 * <ul>
 *   <li>roles: the customer who placed it, is billed and receives it;</li>
 *   <li>contacts: billing address, order email, and each ship group's address;</li>
 *   <li>ship groups: the items that ship together, from which facility, to which address, how;</li>
 *   <li>items: one product line each, with its own status, quantity and cancelled quantity;</li>
 *   <li>adjustments: shipping, tax, discounts, on the order or on one item;</li>
 *   <li>payment preferences: how it is paid, each with its own status;</li>
 *   <li>outside ids (Shopify order id, PO number) and free attributes.</li>
 * </ul>
 * Every status change of the order, an item or a payment is an {@code order_status} row with when, who and why.
 * Stock held for items is the job of {@link OrderReservationService}.
 */
@Service("coreOrderService")
@Transactional
public class OrderService {

    /** The status flow every order, item and payment change is checked against. */
    public static final String FLOW = StatusService.DEFAULT_FLOW;

    static final String DEFAULT_ORDER_TYPE = "SALES_ORDER";
    static final String DEFAULT_SHIPMENT_METHOD = "STANDARD";
    static final String PRODUCT_ITEM_TYPE = "PRODUCT_ORDER_ITEM";
    static final String SALES_CHANNEL_ENUM_TYPE = "ORDER_SALES_CHANNEL";
    static final List<String> CUSTOMER_ROLES = List.of("PLACING_CUSTOMER", "BILL_TO_CUSTOMER", "SHIP_TO_CUSTOMER");

    private final EntityManager entityManager;
    private final SequenceService sequenceService;
    private final StatusService statusService;
    private final EnumerationService enumerationService;
    private final OrderReservationService reservationService;

    public OrderService(EntityManager entityManager, SequenceService sequenceService, StatusService statusService,
                        EnumerationService enumerationService, OrderReservationService reservationService) {
        this.entityManager = entityManager;
        this.sequenceService = sequenceService;
        this.statusService = statusService;
        this.enumerationService = enumerationService;
        this.reservationService = reservationService;
    }

    // ── what a new order is made of ──

    /** A ship group of a new order. Only the method is needed; the rest may be null. */
    public record NewShipGroup(String shipmentMethodTypeId, String facilityId, String shipToContactMechId, String phoneContactMechId,
                               String carrierPartyId, Boolean maySplit, Boolean isGift, String giftMessage, String shippingInstructions,
                               LocalDateTime estimatedDeliveryDate, String trackingNumber) {

        /** A standard-delivery group to an address, from wherever routing decides. */
        public static NewShipGroup standard(String shipToContactMechId) {
            return new NewShipGroup(DEFAULT_SHIPMENT_METHOD, null, shipToContactMechId, null, null, true, false, null, null, null, null);
        }
    }

    /** An item of a new order; {@code shipGroup} is the index into the order's ship groups (0 for the first). */
    public record NewItem(String productId, String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal unitListPrice,
                          String externalId, int shipGroup) {
    }

    /** An adjustment on the whole order ({@code item} null) or on one item ({@code item} is its index). */
    public record NewAdjustment(String orderAdjustmentTypeId, BigDecimal amount, Integer item, String description) {
    }

    /** A payment of a new order; a null status starts it in the payment flow's first status. */
    public record NewPayment(String paymentMethodTypeId, BigDecimal maxAmount, String statusId, String manualRefNum) {
    }

    /**
     * An order to create. A null {@code statusId} starts it in the flow's first status (created); a copy from
     * another system gives the status it had there, recorded with {@code statusReason}.
     */
    public record NewOrder(String orderTypeId, String productStoreId, String salesChannelEnumId, String currencyUomId,
                           LocalDateTime orderDate, String externalId, String orderName, String customerPartyId,
                           String billToContactMechId, String emailContactMechId, List<NewShipGroup> shipGroups,
                           List<NewItem> items, List<NewAdjustment> adjustments, List<NewPayment> payments,
                           Map<String, String> identifications, Map<String, String> attributes, String statusId,
                           String statusReason, String userId) {
    }

    // ── creating ──

    /**
     * Creates an order with all its parts and returns its id. The grand total is worked out from the input
     * (items at their price plus adjustments), not by querying what was just written: copy jobs create orders with
     * COMMIT flush mode, and a query would not see the new rows yet.
     */
    public String create(NewOrder newOrder) {
        validate(newOrder);
        LocalDateTime now = CoreClock.now();

        // 1. The header, in the status asked for or the flow's first.
        String orderStatusId = newOrder.statusId() == null
                ? statusService.initialStatus(FLOW, CoreStatus.Type.ORDER)
                : statusService.status(newOrder.statusId()).getStatusId();
        OrderHeader header = newHeader(newOrder, orderStatusId, now);
        entityManager.persist(header);
        String orderId = header.getOrderId();
        writeHistory(orderId, null, null, orderStatusId, newOrder.statusReason(), newOrder.userId());

        // 2. Who the order is for, and the contacts that are not per ship group.
        if (newOrder.customerPartyId() != null) {
            for (String roleTypeId : CUSTOMER_ROLES) {
                addRoleIfMissing(orderId, newOrder.customerPartyId(), roleTypeId);
            }
        }
        addContactIfMissing(orderId, "BILLING_LOCATION", newOrder.billToContactMechId());
        addContactIfMissing(orderId, "ORDER_EMAIL", newOrder.emailContactMechId());

        // 3. Ship groups: 00001, 00002, ... in the order given.
        List<NewShipGroup> shipGroups = newOrder.shipGroups() == null || newOrder.shipGroups().isEmpty()
                ? List.of(NewShipGroup.standard(null))
                : newOrder.shipGroups();
        List<String> shipGroupSeqIds = new ArrayList<>();
        for (int index = 0; index < shipGroups.size(); index++) {
            OrderItemShipGroup shipGroup = newShipGroup(orderId, index, shipGroups.get(index));
            entityManager.persist(shipGroup);
            shipGroupSeqIds.add(shipGroup.getShipGroupSeqId());
            addContactIfMissing(orderId, "SHIPPING_LOCATION", shipGroup.getContactMechId());
        }

        // 4. Items, each in its ship group and in the item status matching the order's status.
        String itemStatusId = itemStatusMatching(orderStatusId);
        boolean itemsCount = !CoreStatus.Item.GONE.contains(itemStatusId);
        BigDecimal itemsSubtotal = BigDecimal.ZERO;
        List<String> itemSeqIds = new ArrayList<>();
        for (int index = 0; index < newOrder.items().size(); index++) {
            NewItem newItem = newOrder.items().get(index);
            OrderItem orderItem = newItem(orderId, index, newItem, shipGroupSeqIds.get(newItem.shipGroup()), itemStatusId);
            entityManager.persist(orderItem);
            entityManager.persist(newShipGroupAssoc(orderItem));
            itemSeqIds.add(orderItem.getOrderItemSeqId());
            if (itemsCount) {
                itemsSubtotal = itemsSubtotal.add(newItem.quantity().multiply(orderItem.getUnitPrice()));
            }
            writeHistory(orderId, orderItem.getOrderItemSeqId(), null, itemStatusId, newOrder.statusReason(), newOrder.userId());
        }

        // 5. Adjustments; one on an item counts only while its item counts.
        BigDecimal adjustmentsTotal = BigDecimal.ZERO;
        for (NewAdjustment adjustment : newOrder.adjustments() == null ? List.<NewAdjustment>of() : newOrder.adjustments()) {
            if (adjustment.amount() == null || adjustment.amount().signum() == 0) {
                continue;
            }
            String itemSeqId = adjustment.item() == null ? null : itemSeqIds.get(adjustment.item());
            addAdjustment(orderId, itemSeqId, adjustment.orderAdjustmentTypeId(), adjustment.amount(), adjustment.description(), newOrder.userId());
            if (itemsCount || adjustment.item() == null) {
                adjustmentsTotal = adjustmentsTotal.add(adjustment.amount());
            }
        }

        // 6. Payments, each with its own status history.
        for (NewPayment payment : newOrder.payments() == null ? List.<NewPayment>of() : newOrder.payments()) {
            OrderPaymentPreference paymentPreference = newPaymentPreference(orderId, payment, newOrder.userId(), now);
            entityManager.persist(paymentPreference);
            writeHistory(orderId, null, paymentPreference.getOrderPaymentPreferenceId(), paymentPreference.getStatusId(),
                    newOrder.statusReason(), newOrder.userId());
        }

        // 7. Outside ids and attributes.
        if (newOrder.identifications() != null) {
            newOrder.identifications().forEach((typeId, value) -> setIdentification(orderId, typeId, value));
        }
        if (newOrder.attributes() != null) {
            newOrder.attributes().forEach((name, value) -> setAttribute(orderId, name, value));
        }

        header.setRemainingSubTotal(itemsSubtotal);
        header.setGrandTotal(itemsSubtotal.add(adjustmentsTotal));
        return orderId;
    }

    // ── status ──

    /**
     * Moves the order to a status its flow allows. The items follow: approving approves new items; cancelling cancels
     * open items and gives their stock back; completing completes approved items. The total is worked out again.
     */
    public void changeStatus(String orderId, String toStatusId, String reason, String userId) {
        OrderHeader header = loadHeaderForUpdate(orderId);
        statusService.requireAllowed(FLOW, header.getStatusId(), toStatusId);
        header.setStatusId(toStatusId);
        writeHistory(orderId, null, null, toStatusId, reason, userId);

        for (OrderItem orderItem : items(orderId)) {
            String itemTarget = itemStatusFollowing(toStatusId, orderItem.getStatusId());
            if (itemTarget == null) {
                continue;   // this item stays as it is
            }
            if (CoreStatus.Item.CANCELLED.equals(itemTarget)) {
                reservationService.releaseAll(orderId, orderItem.getOrderItemSeqId(), userId);
                orderItem.setCancelQuantity(orderItem.getQuantity());
            }
            changeItemStatus(orderItem, itemTarget, reason, userId);
        }
        recalculateTotals(header);
    }

    /**
     * Cancels part or all of an item. What it holds shrinks to what is still open; a fully cancelled item is
     * cancelled, and when no item is left open the order is cancelled too.
     */
    public void cancelItem(String orderId, String orderItemSeqId, BigDecimal quantity, String reason, String userId) {
        OrderHeader header = loadHeaderForUpdate(orderId);
        OrderItem orderItem = loadItem(orderId, orderItemSeqId);
        if (!CoreStatus.Item.OPEN.contains(orderItem.getStatusId())) {
            throw new IllegalStateException("Item " + orderItemSeqId + " is " + orderItem.getStatusId() + " and can't be cancelled");
        }
        BigDecimal openBefore = openQuantity(orderItem);
        BigDecimal quantityToCancel = quantity == null ? openBefore : quantity;
        if (quantityToCancel.signum() <= 0 || quantityToCancel.compareTo(openBefore) > 0) {
            throw new IllegalArgumentException("Cancel between 1 and " + openBefore.stripTrailingZeros().toPlainString() + " of item " + orderItemSeqId);
        }

        // Record the cancelled quantity on the item and on its ship group link.
        orderItem.setCancelQuantity(orderItem.getCancelQuantity().add(quantityToCancel));
        OrderItemShipGroupAssoc shipGroupAssoc = entityManager.find(OrderItemShipGroupAssoc.class,
                new OrderItemShipGroupAssoc.Key(orderId, orderItemSeqId, orderItem.getShipGroupSeqId()));
        if (shipGroupAssoc != null) {
            BigDecimal alreadyCancelled = shipGroupAssoc.getCancelQuantity() == null ? BigDecimal.ZERO : shipGroupAssoc.getCancelQuantity();
            shipGroupAssoc.setCancelQuantity(alreadyCancelled.add(quantityToCancel));
        }

        // Give back whatever is now held beyond what is still open.
        BigDecimal held = reservationService.reservedQuantity(orderId, orderItemSeqId);
        BigDecimal openAfter = openQuantity(orderItem);
        if (held.compareTo(openAfter) > 0) {
            reservationService.release(orderId, orderItemSeqId, held.subtract(openAfter), userId);
        }

        if (openAfter.signum() == 0) {
            changeItemStatus(orderItem, CoreStatus.Item.CANCELLED, reason, userId);
            boolean anyItemOpen = items(orderId).stream().anyMatch(item -> CoreStatus.Item.OPEN.contains(item.getStatusId()));
            if (!anyItemOpen && statusService.isAllowed(FLOW, header.getStatusId(), CoreStatus.Order.CANCELLED)) {
                header.setStatusId(CoreStatus.Order.CANCELLED);
                writeHistory(orderId, null, null, CoreStatus.Order.CANCELLED, "Every item cancelled", userId);
            }
        }
        recalculateTotals(header);
    }

    /**
     * Sets a status another system of record already decided (the mirror of the earlier model), without checking the
     * flow: that system allowed the change. Open items follow as they would on a copy (never back to created);
     * cancelled or rejected items give their stock back. The change is written to the history with the reason.
     */
    public void mirrorStatus(String orderId, String statusId, String reason) {
        OrderHeader header = loadHeaderForUpdate(orderId);
        String targetStatusId = statusService.status(statusId).getStatusId();
        if (targetStatusId.equals(header.getStatusId())) {
            return;
        }
        header.setStatusId(targetStatusId);
        writeHistory(orderId, null, null, targetStatusId, reason, null);

        String itemTarget = itemStatusMatching(targetStatusId);
        for (OrderItem orderItem : items(orderId)) {
            boolean itemIsOpen = CoreStatus.Item.OPEN.contains(orderItem.getStatusId());
            if (!itemIsOpen || itemTarget.equals(orderItem.getStatusId()) || CoreStatus.Item.CREATED.equals(itemTarget)) {
                continue;
            }
            if (CoreStatus.Item.GONE.contains(itemTarget)) {
                reservationService.releaseAll(orderId, orderItem.getOrderItemSeqId(), null);
                orderItem.setCancelQuantity(orderItem.getQuantity());
            }
            orderItem.setStatusId(itemTarget);
            writeHistory(orderId, orderItem.getOrderItemSeqId(), null, itemTarget, reason, null);
        }
        recalculateTotals(header);
    }

    /**
     * After a shipment: an item whose shipped quantity covers what is still open is completed, and an order with no
     * open item left is completed.
     */
    public void markShipped(String orderId, String orderItemSeqId, BigDecimal totalShipped, String userId) {
        OrderHeader header = loadHeaderForUpdate(orderId);
        OrderItem orderItem = loadItem(orderId, orderItemSeqId);
        boolean fullyShipped = totalShipped.compareTo(openQuantity(orderItem)) >= 0;
        if (CoreStatus.Item.APPROVED.equals(orderItem.getStatusId()) && fullyShipped) {
            changeItemStatus(orderItem, CoreStatus.Item.COMPLETED, "Shipped", userId);
        }
        boolean anyItemOpen = items(orderId).stream().anyMatch(item -> CoreStatus.Item.OPEN.contains(item.getStatusId()));
        if (!anyItemOpen && statusService.isAllowed(FLOW, header.getStatusId(), CoreStatus.Order.COMPLETED)) {
            header.setStatusId(CoreStatus.Order.COMPLETED);
            writeHistory(orderId, null, null, CoreStatus.Order.COMPLETED, "Every item shipped or cancelled", userId);
        }
    }

    /** Moves a payment to a status its flow allows (authorized, settled, refunded...). */
    public void setPaymentStatus(String orderPaymentPreferenceId, String toStatusId, String reason, String userId) {
        OrderPaymentPreference payment = entityManager.find(OrderPaymentPreference.class, orderPaymentPreferenceId, LockModeType.PESSIMISTIC_WRITE);
        if (payment == null) {
            throw new IllegalArgumentException("Unknown payment " + orderPaymentPreferenceId);
        }
        statusService.requireAllowed(FLOW, payment.getStatusId(), toStatusId);
        payment.setStatusId(toStatusId);
        writeHistory(payment.getOrderId(), null, orderPaymentPreferenceId, toStatusId, reason, userId);
    }

    // ── ship groups ──

    @Transactional(readOnly = true)
    public OrderItemShipGroup shipGroup(String orderId, String shipGroupSeqId) {
        OrderItemShipGroup shipGroup = entityManager.find(OrderItemShipGroup.class, new OrderItemShipGroup.Key(orderId, shipGroupSeqId));
        if (shipGroup == null) {
            throw new IllegalArgumentException("Order " + orderId + " has no ship group " + shipGroupSeqId);
        }
        return shipGroup;
    }

    /** Sets where a ship group ships from; null puts it back in the routing queue. */
    public void setShipGroupFacility(String orderId, String shipGroupSeqId, String facilityId) {
        shipGroup(orderId, shipGroupSeqId).setFacilityId(facilityId);
    }

    /** Sets the tracking number of a ship group (the first group when none is named). */
    public void setTracking(String orderId, String shipGroupSeqId, String trackingNumber) {
        String groupSeqId = shipGroupSeqId == null ? SequenceService.seqId(1) : shipGroupSeqId;
        shipGroup(orderId, groupSeqId).setTrackingNumber(trackingNumber);
    }

    /** Changes where a ship group goes. The order's shipping contact follows; an address no group uses any more is dropped. */
    public void setShipTo(String orderId, String shipGroupSeqId, String contactMechId) {
        OrderItemShipGroup shipGroup = shipGroup(orderId, shipGroupSeqId);
        String previousContactMechId = shipGroup.getContactMechId();
        shipGroup.setContactMechId(contactMechId);

        if (previousContactMechId != null) {
            long groupsStillUsingIt = entityManager.createQuery(
                            "SELECT COUNT(g) FROM OrderItemShipGroup g WHERE g.orderId = :orderId AND g.contactMechId = :contactMechId", Long.class)
                    .setParameter("orderId", orderId)
                    .setParameter("contactMechId", previousContactMechId)
                    .getSingleResult();
            if (groupsStillUsingIt == 0) {
                OrderContactMech oldContact = entityManager.find(OrderContactMech.class,
                        new OrderContactMech.Key(orderId, "SHIPPING_LOCATION", previousContactMechId));
                if (oldContact != null) {
                    entityManager.remove(oldContact);
                }
            }
        }
        addContactIfMissing(orderId, "SHIPPING_LOCATION", contactMechId);
    }

    /**
     * Moves items to a new ship group of the same order (same address, method and gift details) that ships from the
     * facility given, and returns the new group's id. The items must not hold stock: release it first.
     */
    public String splitShipGroup(String orderId, String fromShipGroupSeqId, List<String> orderItemSeqIds, String facilityId) {
        OrderItemShipGroup sourceGroup = shipGroup(orderId, fromShipGroupSeqId);
        long existingGroups = entityManager.createQuery("SELECT COUNT(g) FROM OrderItemShipGroup g WHERE g.orderId = :orderId", Long.class)
                .setParameter("orderId", orderId)
                .getSingleResult();

        OrderItemShipGroup newGroup = copyOfShipGroup(sourceGroup, SequenceService.seqId((int) existingGroups + 1), facilityId);
        entityManager.persist(newGroup);

        for (String orderItemSeqId : orderItemSeqIds) {
            OrderItem orderItem = loadItem(orderId, orderItemSeqId);
            if (!fromShipGroupSeqId.equals(orderItem.getShipGroupSeqId())) {
                throw new IllegalArgumentException("Item " + orderItemSeqId + " is not in ship group " + fromShipGroupSeqId);
            }
            if (reservationService.reservedQuantity(orderId, orderItemSeqId).signum() > 0) {
                throw new IllegalStateException("Item " + orderItemSeqId + " holds stock; release it before moving it");
            }
            moveItemToShipGroup(orderItem, fromShipGroupSeqId, newGroup.getShipGroupSeqId());
        }
        return newGroup.getShipGroupSeqId();
    }

    // ── reading ──

    /** The order header; an unknown id is an error. */
    @Transactional(readOnly = true)
    public OrderHeader header(String orderId) {
        return loadHeader(orderId);
    }

    // ── items ──

    /** What is still open on an item: ordered less cancelled. */
    public static BigDecimal openQuantity(OrderItem orderItem) {
        BigDecimal cancelled = orderItem.getCancelQuantity() == null ? BigDecimal.ZERO : orderItem.getCancelQuantity();
        return orderItem.getQuantity().subtract(cancelled);
    }

    /** The order's items in sequence order. */
    @Transactional(readOnly = true)
    public List<OrderItem> items(String orderId) {
        return entityManager.createQuery("SELECT i FROM OrderItem i WHERE i.orderId = :orderId ORDER BY i.orderItemSeqId", OrderItem.class)
                .setParameter("orderId", orderId)
                .getResultList();
    }

    @Transactional(readOnly = true)
    public OrderItem orderItem(String orderId, String orderItemSeqId) {
        return loadItem(orderId, orderItemSeqId);
    }

    @Transactional(readOnly = true)
    public String itemAttribute(String orderId, String orderItemSeqId, String name) {
        OrderItemAttribute attribute = entityManager.find(OrderItemAttribute.class, new OrderItemAttribute.Key(orderId, orderItemSeqId, name));
        return attribute == null ? null : attribute.getAttrValue();
    }

    public void setItemAttribute(String orderId, String orderItemSeqId, String name, String value) {
        OrderItemAttribute attribute = entityManager.find(OrderItemAttribute.class, new OrderItemAttribute.Key(orderId, orderItemSeqId, name));
        if (attribute == null) {
            attribute = new OrderItemAttribute();
            attribute.setOrderId(orderId);
            attribute.setOrderItemSeqId(orderItemSeqId);
            attribute.setAttrName(name);
            entityManager.persist(attribute);
        }
        attribute.setAttrValue(value);
    }

    // ── parties, outside ids, attributes, adjustments ──

    /** The party in a role on the order (the ship-to customer, say), if any. */
    @Transactional(readOnly = true)
    public Optional<String> partyInRole(String orderId, String roleTypeId) {
        return entityManager.createQuery(
                        "SELECT r.partyId FROM OrderRole r WHERE r.orderId = :orderId AND r.roleTypeId = :roleTypeId ORDER BY r.partyId", String.class)
                .setParameter("orderId", orderId)
                .setParameter("roleTypeId", roleTypeId)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst();
    }

    /**
     * Sets the current outside id of a kind (Shopify order id, PO number). An earlier different value is ended, not
     * deleted, so the history stays. A blank value does nothing; the same value again does nothing.
     */
    public void setIdentification(String orderId, String typeId, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String trimmedValue = value.trim();
        List<OrderIdentification> currentIds = entityManager.createQuery(
                        "SELECT i FROM OrderIdentification i WHERE i.orderId = :orderId AND i.orderIdentificationTypeId = :typeId "
                                + "AND i.thruDate IS NULL", OrderIdentification.class)
                .setParameter("orderId", orderId)
                .setParameter("typeId", typeId)
                .getResultList();
        if (currentIds.stream().anyMatch(existing -> existing.getIdValue().equals(trimmedValue))) {
            return;
        }
        LocalDateTime now = CoreClock.now();
        currentIds.forEach(existing -> existing.setThruDate(now));

        OrderIdentification identification = new OrderIdentification();
        identification.setOrderIdentificationTypeId(typeId);
        identification.setOrderId(orderId);
        identification.setFromDate(now);
        identification.setIdValue(trimmedValue);
        entityManager.persist(identification);
    }

    /** The order whose current id of this kind is the value: find an order by its Shopify id or PO number. */
    @Transactional(readOnly = true)
    public Optional<String> findByIdentification(String typeId, String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return entityManager.createQuery(
                        "SELECT i.orderId FROM OrderIdentification i WHERE i.orderIdentificationTypeId = :typeId AND i.idValue = :value "
                                + "AND i.thruDate IS NULL ORDER BY i.fromDate DESC", String.class)
                .setParameter("typeId", typeId)
                .setParameter("value", value.trim())
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst();
    }

    /** Sets a named value on the order; null removes it. */
    public void setAttribute(String orderId, String name, String value) {
        OrderAttribute attribute = entityManager.find(OrderAttribute.class, new OrderAttribute.Key(orderId, name));
        if (value == null) {
            if (attribute != null) {
                entityManager.remove(attribute);
            }
            return;
        }
        if (attribute == null) {
            attribute = new OrderAttribute();
            attribute.setOrderId(orderId);
            attribute.setAttrName(name);
            entityManager.persist(attribute);
        }
        attribute.setAttrValue(value);
    }

    /** Adds an amount to the order ({@code orderItemSeqId} null) or to one item, and returns its id. */
    public String addAdjustment(String orderId, String orderItemSeqId, String typeId, BigDecimal amount, String description, String userId) {
        OrderAdjustment adjustment = new OrderAdjustment();
        adjustment.setOrderAdjustmentId(sequenceService.nextId("OrderAdjustment"));
        adjustment.setOrderAdjustmentTypeId(typeId);
        adjustment.setOrderId(orderId);
        adjustment.setOrderItemSeqId(orderItemSeqId);
        adjustment.setAmount(amount);
        adjustment.setDescription(description);
        adjustment.setCreatedDate(CoreClock.now());
        adjustment.setCreatedByUserLogin(userId);
        entityManager.persist(adjustment);
        return adjustment.getOrderAdjustmentId();
    }

    // ── totals ──

    /** Works the order's totals out again and returns the grand total. */
    public BigDecimal recalculate(String orderId) {
        return recalculateTotals(loadHeader(orderId));
    }

    /**
     * Remaining subtotal = what is still open on each item that still counts, at its price.
     * Grand total = remaining subtotal + order adjustments + adjustments on items that still count.
     * Cancelled quantities drop out, as OFBiz keeps the total current.
     */
    private BigDecimal recalculateTotals(OrderHeader header) {
        List<OrderItem> orderItems = items(header.getOrderId());
        BigDecimal remainingSubtotal = BigDecimal.ZERO;
        Set<String> countingItemSeqIds = new HashSet<>();
        for (OrderItem orderItem : orderItems) {
            if (CoreStatus.Item.GONE.contains(orderItem.getStatusId())) {
                continue;
            }
            BigDecimal unitPrice = orderItem.getUnitPrice() == null ? BigDecimal.ZERO : orderItem.getUnitPrice();
            remainingSubtotal = remainingSubtotal.add(openQuantity(orderItem).multiply(unitPrice));
            countingItemSeqIds.add(orderItem.getOrderItemSeqId());
        }

        BigDecimal adjustmentsTotal = BigDecimal.ZERO;
        List<OrderAdjustment> adjustments = entityManager.createQuery("SELECT a FROM OrderAdjustment a WHERE a.orderId = :orderId", OrderAdjustment.class)
                .setParameter("orderId", header.getOrderId())
                .getResultList();
        for (OrderAdjustment adjustment : adjustments) {
            boolean onTheOrder = adjustment.getOrderItemSeqId() == null;
            if (onTheOrder || countingItemSeqIds.contains(adjustment.getOrderItemSeqId())) {
                adjustmentsTotal = adjustmentsTotal.add(adjustment.getAmount());
            }
        }

        header.setRemainingSubTotal(remainingSubtotal);
        header.setGrandTotal(remainingSubtotal.add(adjustmentsTotal));
        return header.getGrandTotal();
    }

    // ── building the rows of a new order ──

    private void validate(NewOrder newOrder) {
        if (newOrder.items() == null || newOrder.items().isEmpty()) {
            throw new IllegalArgumentException("An order needs at least one item");
        }
        if (newOrder.salesChannelEnumId() != null) {
            enumerationService.require(SALES_CHANNEL_ENUM_TYPE, newOrder.salesChannelEnumId());
        }
        int shipGroupCount = newOrder.shipGroups() == null || newOrder.shipGroups().isEmpty() ? 1 : newOrder.shipGroups().size();
        for (int index = 0; index < newOrder.items().size(); index++) {
            NewItem newItem = newOrder.items().get(index);
            int itemNumber = index + 1;
            if (newItem.quantity() == null || newItem.quantity().signum() <= 0) {
                throw new IllegalArgumentException("Item " + itemNumber + " needs a quantity above zero");
            }
            if (newItem.shipGroup() < 0 || newItem.shipGroup() >= shipGroupCount) {
                throw new IllegalArgumentException("Item " + itemNumber + " names ship group " + newItem.shipGroup() + ", which the order doesn't have");
            }
        }
        for (NewAdjustment adjustment : newOrder.adjustments() == null ? List.<NewAdjustment>of() : newOrder.adjustments()) {
            if (adjustment.item() != null && (adjustment.item() < 0 || adjustment.item() >= newOrder.items().size())) {
                throw new IllegalArgumentException("An adjustment names item " + adjustment.item() + ", which the order doesn't have");
            }
        }
    }

    private OrderHeader newHeader(NewOrder newOrder, String statusId, LocalDateTime now) {
        OrderHeader header = new OrderHeader();
        header.setOrderId(sequenceService.nextId("OrderHeader"));
        header.setOrderTypeId(newOrder.orderTypeId() == null ? DEFAULT_ORDER_TYPE : newOrder.orderTypeId());
        header.setOrderName(newOrder.orderName());
        header.setExternalId(newOrder.externalId());
        header.setSalesChannelEnumId(newOrder.salesChannelEnumId());
        header.setOrderDate(newOrder.orderDate() == null ? now : newOrder.orderDate());
        header.setEntryDate(now);
        header.setCurrencyUomId(newOrder.currencyUomId());
        header.setProductStoreId(newOrder.productStoreId());
        header.setCreatedBy(newOrder.userId());
        header.setStatusId(statusId);
        return header;
    }

    private OrderItemShipGroup newShipGroup(String orderId, int index, NewShipGroup input) {
        OrderItemShipGroup shipGroup = new OrderItemShipGroup();
        shipGroup.setOrderId(orderId);
        shipGroup.setShipGroupSeqId(SequenceService.seqId(index + 1));
        shipGroup.setShipmentMethodTypeId(input.shipmentMethodTypeId() == null ? DEFAULT_SHIPMENT_METHOD : input.shipmentMethodTypeId());
        shipGroup.setFacilityId(input.facilityId());
        shipGroup.setContactMechId(input.shipToContactMechId());
        shipGroup.setTelecomContactMechId(input.phoneContactMechId());
        shipGroup.setCarrierPartyId(input.carrierPartyId());
        shipGroup.setMaySplit(input.maySplit() == null || input.maySplit());
        shipGroup.setIsGift(Boolean.TRUE.equals(input.isGift()));
        shipGroup.setGiftMessage(input.giftMessage());
        shipGroup.setShippingInstructions(input.shippingInstructions());
        shipGroup.setEstimatedDeliveryDate(input.estimatedDeliveryDate());
        shipGroup.setTrackingNumber(input.trackingNumber());
        return shipGroup;
    }

    private OrderItem newItem(String orderId, int index, NewItem input, String shipGroupSeqId, String statusId) {
        OrderItem orderItem = new OrderItem();
        orderItem.setOrderId(orderId);
        orderItem.setOrderItemSeqId(SequenceService.seqId(index + 1));
        orderItem.setOrderItemTypeId(PRODUCT_ITEM_TYPE);
        orderItem.setProductId(input.productId());
        orderItem.setItemDescription(input.description());
        orderItem.setQuantity(input.quantity());
        orderItem.setCancelQuantity(BigDecimal.ZERO);
        orderItem.setUnitPrice(input.unitPrice() == null ? BigDecimal.ZERO : input.unitPrice());
        orderItem.setUnitListPrice(input.unitListPrice());
        orderItem.setExternalId(input.externalId());
        orderItem.setShipGroupSeqId(shipGroupSeqId);
        orderItem.setStatusId(statusId);
        orderItem.setIsPromo(false);
        orderItem.setIsModifiedPrice(false);
        return orderItem;
    }

    /** The link saying how much of the item goes in its ship group (all of it, at creation). */
    private static OrderItemShipGroupAssoc newShipGroupAssoc(OrderItem orderItem) {
        OrderItemShipGroupAssoc assoc = new OrderItemShipGroupAssoc();
        assoc.setOrderId(orderItem.getOrderId());
        assoc.setOrderItemSeqId(orderItem.getOrderItemSeqId());
        assoc.setShipGroupSeqId(orderItem.getShipGroupSeqId());
        assoc.setQuantity(orderItem.getQuantity());
        assoc.setCancelQuantity(BigDecimal.ZERO);
        return assoc;
    }

    private OrderPaymentPreference newPaymentPreference(String orderId, NewPayment input, String userId, LocalDateTime now) {
        OrderPaymentPreference payment = new OrderPaymentPreference();
        payment.setOrderPaymentPreferenceId(sequenceService.nextId("OrderPaymentPreference"));
        payment.setOrderId(orderId);
        payment.setPaymentMethodTypeId(input.paymentMethodTypeId());
        payment.setMaxAmount(input.maxAmount());
        payment.setManualRefNum(input.manualRefNum());
        payment.setStatusId(input.statusId() == null
                ? statusService.initialStatus(FLOW, CoreStatus.Type.PAYMENT)
                : statusService.status(input.statusId()).getStatusId());
        payment.setCreatedDate(now);
        payment.setCreatedByUserLogin(userId);
        return payment;
    }

    private static OrderItemShipGroup copyOfShipGroup(OrderItemShipGroup source, String newSeqId, String facilityId) {
        OrderItemShipGroup copy = new OrderItemShipGroup();
        copy.setOrderId(source.getOrderId());
        copy.setShipGroupSeqId(newSeqId);
        copy.setShipmentMethodTypeId(source.getShipmentMethodTypeId());
        copy.setCarrierPartyId(source.getCarrierPartyId());
        copy.setFacilityId(facilityId);
        copy.setContactMechId(source.getContactMechId());
        copy.setTelecomContactMechId(source.getTelecomContactMechId());
        copy.setShippingInstructions(source.getShippingInstructions());
        copy.setMaySplit(source.getMaySplit());
        copy.setIsGift(source.getIsGift());
        copy.setGiftMessage(source.getGiftMessage());
        copy.setShipByDate(source.getShipByDate());
        copy.setEstimatedDeliveryDate(source.getEstimatedDeliveryDate());
        return copy;
    }

    /** Re-points an item and its ship group link from one group to another, keeping its quantities. */
    private void moveItemToShipGroup(OrderItem orderItem, String fromShipGroupSeqId, String toShipGroupSeqId) {
        String orderId = orderItem.getOrderId();
        String orderItemSeqId = orderItem.getOrderItemSeqId();
        OrderItemShipGroupAssoc oldLink = entityManager.find(OrderItemShipGroupAssoc.class,
                new OrderItemShipGroupAssoc.Key(orderId, orderItemSeqId, fromShipGroupSeqId));

        OrderItemShipGroupAssoc newLink = new OrderItemShipGroupAssoc();
        newLink.setOrderId(orderId);
        newLink.setOrderItemSeqId(orderItemSeqId);
        newLink.setShipGroupSeqId(toShipGroupSeqId);
        newLink.setQuantity(oldLink == null ? orderItem.getQuantity() : oldLink.getQuantity());
        newLink.setCancelQuantity(oldLink == null ? orderItem.getCancelQuantity() : oldLink.getCancelQuantity());

        if (oldLink != null) {
            entityManager.remove(oldLink);
        }
        entityManager.persist(newLink);
        orderItem.setShipGroupSeqId(toShipGroupSeqId);
    }

    // ── status helpers ──

    private void changeItemStatus(OrderItem orderItem, String toStatusId, String reason, String userId) {
        statusService.requireAllowed(FLOW, orderItem.getStatusId(), toStatusId);
        orderItem.setStatusId(toStatusId);
        writeHistory(orderItem.getOrderId(), orderItem.getOrderItemSeqId(), null, toStatusId, reason, userId);
    }

    /** The item status that goes with an order status, for an order created or copied in that status. */
    private static String itemStatusMatching(String orderStatusId) {
        return switch (orderStatusId) {
            case CoreStatus.Order.APPROVED, CoreStatus.Order.SENT, CoreStatus.Order.HOLD -> CoreStatus.Item.APPROVED;
            case CoreStatus.Order.COMPLETED -> CoreStatus.Item.COMPLETED;
            case CoreStatus.Order.CANCELLED -> CoreStatus.Item.CANCELLED;
            case CoreStatus.Order.REJECTED -> CoreStatus.Item.REJECTED;
            default -> CoreStatus.Item.CREATED;
        };
    }

    /** Where an item goes when its order changes status; null when it stays. */
    private static String itemStatusFollowing(String newOrderStatusId, String itemStatusId) {
        return switch (newOrderStatusId) {
            case CoreStatus.Order.APPROVED -> CoreStatus.Item.CREATED.equals(itemStatusId) ? CoreStatus.Item.APPROVED : null;
            case CoreStatus.Order.CANCELLED -> CoreStatus.Item.OPEN.contains(itemStatusId) ? CoreStatus.Item.CANCELLED : null;
            case CoreStatus.Order.COMPLETED -> CoreStatus.Item.APPROVED.equals(itemStatusId) ? CoreStatus.Item.COMPLETED : null;
            default -> null;
        };
    }

    /** One {@code order_status} row: the order ({@code itemSeqId} and {@code paymentId} null), an item, or a payment. */
    private void writeHistory(String orderId, String orderItemSeqId, String paymentPreferenceId, String statusId, String reason, String userId) {
        OrderStatus history = new OrderStatus();
        history.setOrderStatusId(sequenceService.nextId("OrderStatus"));
        history.setOrderId(orderId);
        history.setOrderItemSeqId(orderItemSeqId);
        history.setOrderPaymentPreferenceId(paymentPreferenceId);
        history.setStatusId(statusId);
        history.setStatusDatetime(CoreClock.now());
        history.setStatusUserLogin(userId);
        history.setChangeReason(reason);
        entityManager.persist(history);
    }

    private void addRoleIfMissing(String orderId, String partyId, String roleTypeId) {
        if (entityManager.find(OrderRole.class, new OrderRole.Key(orderId, partyId, roleTypeId)) != null) {
            return;
        }
        OrderRole role = new OrderRole();
        role.setOrderId(orderId);
        role.setPartyId(partyId);
        role.setRoleTypeId(roleTypeId);
        entityManager.persist(role);
    }

    private void addContactIfMissing(String orderId, String purposeTypeId, String contactMechId) {
        if (contactMechId == null) {
            return;
        }
        if (entityManager.find(OrderContactMech.class, new OrderContactMech.Key(orderId, purposeTypeId, contactMechId)) != null) {
            return;
        }
        OrderContactMech contact = new OrderContactMech();
        contact.setOrderId(orderId);
        contact.setContactMechPurposeTypeId(purposeTypeId);
        contact.setContactMechId(contactMechId);
        entityManager.persist(contact);
    }

    // ── loading ──

    private OrderHeader loadHeader(String orderId) {
        OrderHeader header = entityManager.find(OrderHeader.class, orderId);
        if (header == null) {
            throw new IllegalArgumentException("Unknown order " + orderId);
        }
        return header;
    }

    /** The header, locked until the transaction ends, so two changes to one order run one after the other. */
    private OrderHeader loadHeaderForUpdate(String orderId) {
        OrderHeader header = entityManager.find(OrderHeader.class, orderId, LockModeType.PESSIMISTIC_WRITE);
        if (header == null) {
            throw new IllegalArgumentException("Unknown order " + orderId);
        }
        return header;
    }

    private OrderItem loadItem(String orderId, String orderItemSeqId) {
        OrderItem orderItem = entityManager.find(OrderItem.class, new OrderItem.Key(orderId, orderItemSeqId));
        if (orderItem == null) {
            throw new IllegalArgumentException("Order " + orderId + " has no item " + orderItemSeqId);
        }
        return orderItem;
    }
}
