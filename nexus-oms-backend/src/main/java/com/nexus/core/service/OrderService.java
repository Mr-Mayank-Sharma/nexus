package com.nexus.core.service;

import com.nexus.core.model.order.OrderAdjustment;
import com.nexus.core.model.order.OrderAttribute;
import com.nexus.core.model.order.OrderContactMech;
import com.nexus.core.model.order.OrderHeader;
import com.nexus.core.model.order.OrderIdentification;
import com.nexus.core.model.order.OrderItem;
import com.nexus.core.model.order.OrderItemShipGroup;
import com.nexus.core.model.order.OrderItemShipGroupAssoc;
import com.nexus.core.model.order.OrderItemShipGrpInvRes;
import com.nexus.core.model.order.OrderPaymentPreference;
import com.nexus.core.model.order.OrderRole;
import com.nexus.core.model.order.OrderStatus;
import com.nexus.core.model.inventory.InventoryItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Orders in the OFBiz way: a header with roles and contacts, ship groups (what ships together, from where, to where),
 * items with their own status, adjustments (shipping, tax, discounts), payment preferences, outside ids, and an
 * {@code order_status} row for every status change. Items reserve stock through {@link InventoryService}; the
 * reservation is kept per item and inventory item in {@code order_item_ship_grp_inv_res}.
 */
@Service("coreOrderService")
@Transactional
public class OrderService {

    public static final String FLOW = StatusService.DEFAULT_FLOW;
    private static final Set<String> ITEM_OPEN = Set.of("ITEM_CREATED", "ITEM_APPROVED");

    private final EntityManager em;
    private final SequenceService sequences;
    private final StatusService statuses;
    private final InventoryService inventory;
    private final EnumerationService enumerations;

    public OrderService(EntityManager em, SequenceService sequences, StatusService statuses, InventoryService inventory,
                        EnumerationService enumerations) {
        this.em = em;
        this.sequences = sequences;
        this.statuses = statuses;
        this.inventory = inventory;
        this.enumerations = enumerations;
    }

    public record NewShipGroup(String shipmentMethodTypeId, String facilityId, String shipToContactMechId, String phoneContactMechId,
                               String carrierPartyId, Boolean maySplit, Boolean isGift, String giftMessage, String shippingInstructions,
                               LocalDateTime estimatedDeliveryDate, String trackingNumber) {
        public static NewShipGroup standard(String shipToContactMechId) {
            return new NewShipGroup("STANDARD", null, shipToContactMechId, null, null, true, false, null, null, null, null);
        }
    }

    /** An item; {@code shipGroup} is the index into the order's ship groups (0 for the first). */
    public record NewItem(String productId, String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal unitListPrice,
                          String externalId, int shipGroup) {
    }

    /** An adjustment on the whole order ({@code item} null) or on one item (index into the items). */
    public record NewAdjustment(String orderAdjustmentTypeId, BigDecimal amount, Integer item, String description) {
    }

    public record NewPayment(String paymentMethodTypeId, BigDecimal maxAmount, String statusId, String manualRefNum) {
    }

    /**
     * An order to create. A null {@code statusId} starts it in the flow's first status; a copy from another system
     * gives the status it had, recorded with {@code statusReason}.
     */
    public record NewOrder(String orderTypeId, String productStoreId, String salesChannelEnumId, String currencyUomId,
                           LocalDateTime orderDate, String externalId, String orderName, String customerPartyId,
                           String billToContactMechId, String emailContactMechId, List<NewShipGroup> shipGroups,
                           List<NewItem> items, List<NewAdjustment> adjustments, List<NewPayment> payments,
                           Map<String, String> identifications, Map<String, String> attributes, String statusId,
                           String statusReason, String userId) {
    }

    public String create(NewOrder n) {
        if (n.items() == null || n.items().isEmpty()) throw new IllegalArgumentException("An order needs at least one item");
        List<NewShipGroup> groups = n.shipGroups() == null || n.shipGroups().isEmpty() ? List.of(NewShipGroup.standard(null)) : n.shipGroups();
        if (n.salesChannelEnumId() != null) enumerations.require("ORDER_SALES_CHANNEL", n.salesChannelEnumId());
        LocalDateTime now = CoreClock.now();

        OrderHeader h = new OrderHeader();
        h.setOrderId(sequences.nextId("OrderHeader"));
        h.setOrderTypeId(n.orderTypeId() == null ? "SALES_ORDER" : n.orderTypeId());
        h.setOrderName(n.orderName());
        h.setExternalId(n.externalId());
        h.setSalesChannelEnumId(n.salesChannelEnumId());
        h.setOrderDate(n.orderDate() == null ? now : n.orderDate());
        h.setEntryDate(now);
        h.setCurrencyUomId(n.currencyUomId());
        h.setProductStoreId(n.productStoreId());
        h.setCreatedBy(n.userId());
        String headerStatus = n.statusId() == null ? statuses.initialStatus(FLOW, "ORDER_STATUS") : statuses.status(n.statusId()).getStatusId();
        h.setStatusId(headerStatus);
        em.persist(h);
        String orderId = h.getOrderId();
        history(orderId, null, null, headerStatus, n.statusReason(), n.userId());

        if (n.customerPartyId() != null) {
            for (String role : List.of("PLACING_CUSTOMER", "BILL_TO_CUSTOMER", "SHIP_TO_CUSTOMER")) role(orderId, n.customerPartyId(), role);
        }
        contact(orderId, "BILLING_LOCATION", n.billToContactMechId());
        contact(orderId, "ORDER_EMAIL", n.emailContactMechId());

        List<String> groupIds = new ArrayList<>();
        for (int g = 0; g < groups.size(); g++) {
            NewShipGroup s = groups.get(g);
            OrderItemShipGroup sg = new OrderItemShipGroup();
            sg.setOrderId(orderId);
            sg.setShipGroupSeqId(SequenceService.seqId(g + 1));
            sg.setShipmentMethodTypeId(s.shipmentMethodTypeId() == null ? "STANDARD" : s.shipmentMethodTypeId());
            sg.setFacilityId(s.facilityId());
            sg.setContactMechId(s.shipToContactMechId());
            sg.setTelecomContactMechId(s.phoneContactMechId());
            sg.setCarrierPartyId(s.carrierPartyId());
            sg.setMaySplit(s.maySplit() == null || s.maySplit());
            sg.setIsGift(Boolean.TRUE.equals(s.isGift()));
            sg.setGiftMessage(s.giftMessage());
            sg.setShippingInstructions(s.shippingInstructions());
            sg.setEstimatedDeliveryDate(s.estimatedDeliveryDate());
            sg.setTrackingNumber(s.trackingNumber());
            em.persist(sg);
            groupIds.add(sg.getShipGroupSeqId());
            contact(orderId, "SHIPPING_LOCATION", s.shipToContactMechId());
        }

        String itemStatus = itemStatusFor(headerStatus);
        boolean counts = !"ITEM_CANCELLED".equals(itemStatus) && !"ITEM_REJECTED".equals(itemStatus);
        BigDecimal subTotal = BigDecimal.ZERO;
        BigDecimal adjustments = BigDecimal.ZERO;
        List<String> itemIds = new ArrayList<>();
        for (int i = 0; i < n.items().size(); i++) {
            NewItem it = n.items().get(i);
            if (it.quantity() == null || it.quantity().signum() <= 0) throw new IllegalArgumentException("Item " + (i + 1) + " needs a quantity above zero");
            if (it.shipGroup() < 0 || it.shipGroup() >= groupIds.size()) throw new IllegalArgumentException("Item " + (i + 1) + " names ship group " + it.shipGroup() + ", which the order doesn't have");
            OrderItem oi = new OrderItem();
            oi.setOrderId(orderId);
            oi.setOrderItemSeqId(SequenceService.seqId(i + 1));
            oi.setOrderItemTypeId("PRODUCT_ORDER_ITEM");
            oi.setProductId(it.productId());
            oi.setItemDescription(it.description());
            oi.setQuantity(it.quantity());
            oi.setCancelQuantity(BigDecimal.ZERO);
            oi.setUnitPrice(it.unitPrice() == null ? BigDecimal.ZERO : it.unitPrice());
            oi.setUnitListPrice(it.unitListPrice());
            oi.setExternalId(it.externalId());
            oi.setShipGroupSeqId(groupIds.get(it.shipGroup()));
            oi.setStatusId(itemStatus);
            oi.setIsPromo(false);
            oi.setIsModifiedPrice(false);
            em.persist(oi);
            OrderItemShipGroupAssoc a = new OrderItemShipGroupAssoc();
            a.setOrderId(orderId);
            a.setOrderItemSeqId(oi.getOrderItemSeqId());
            a.setShipGroupSeqId(oi.getShipGroupSeqId());
            a.setQuantity(it.quantity());
            a.setCancelQuantity(BigDecimal.ZERO);
            em.persist(a);
            itemIds.add(oi.getOrderItemSeqId());
            if (counts) subTotal = subTotal.add(it.quantity().multiply(oi.getUnitPrice()));
            history(orderId, oi.getOrderItemSeqId(), null, itemStatus, n.statusReason(), n.userId());
        }

        if (n.adjustments() != null) {
            for (NewAdjustment adj : n.adjustments()) {
                if (adj.amount() == null || adj.amount().signum() == 0) continue;
                if (adj.item() != null && (adj.item() < 0 || adj.item() >= itemIds.size())) throw new IllegalArgumentException("An adjustment names item " + adj.item() + ", which the order doesn't have");
                addAdjustment(orderId, adj.item() == null ? null : itemIds.get(adj.item()), adj.orderAdjustmentTypeId(), adj.amount(), adj.description(), n.userId());
                if (counts || adj.item() == null) adjustments = adjustments.add(adj.amount());
            }
        }
        if (n.payments() != null) {
            for (NewPayment p : n.payments()) {
                OrderPaymentPreference pp = new OrderPaymentPreference();
                pp.setOrderPaymentPreferenceId(sequences.nextId("OrderPaymentPreference"));
                pp.setOrderId(orderId);
                pp.setPaymentMethodTypeId(p.paymentMethodTypeId());
                pp.setMaxAmount(p.maxAmount());
                pp.setManualRefNum(p.manualRefNum());
                pp.setStatusId(p.statusId() == null ? statuses.initialStatus(FLOW, "PAYMENT_PREF_STATUS") : statuses.status(p.statusId()).getStatusId());
                pp.setCreatedDate(now);
                pp.setCreatedByUserLogin(n.userId());
                em.persist(pp);
                history(orderId, null, pp.getOrderPaymentPreferenceId(), pp.getStatusId(), n.statusReason(), n.userId());
            }
        }
        if (n.identifications() != null) n.identifications().forEach((type, value) -> setIdentification(orderId, type, value));
        if (n.attributes() != null) n.attributes().forEach((name, value) -> setAttribute(orderId, name, value));
        // Totalled from the input, not by query: copy jobs create orders with COMMIT flush mode.
        h.setRemainingSubTotal(subTotal);
        h.setGrandTotal(subTotal.add(adjustments));
        return orderId;
    }

    // ── status ──

    /**
     * Moves the order along its flow. Approving approves its new items; cancelling cancels its open items and gives
     * their stock back; completing completes its open items.
     */
    public void changeStatus(String orderId, String toStatusId, String reason, String userId) {
        OrderHeader h = header(orderId, true);
        statuses.requireAllowed(FLOW, h.getStatusId(), toStatusId);
        h.setStatusId(toStatusId);
        history(orderId, null, null, toStatusId, reason, userId);
        for (OrderItem i : items(orderId)) {
            String to = switch (toStatusId) {
                case "ORDER_APPROVED" -> "ITEM_CREATED".equals(i.getStatusId()) ? "ITEM_APPROVED" : null;
                case "ORDER_CANCELLED" -> ITEM_OPEN.contains(i.getStatusId()) ? "ITEM_CANCELLED" : null;
                case "ORDER_COMPLETED" -> "ITEM_APPROVED".equals(i.getStatusId()) ? "ITEM_COMPLETED" : null;
                default -> null;
            };
            if (to == null) continue;
            if ("ITEM_CANCELLED".equals(to)) {
                releaseReservations(orderId, i.getOrderItemSeqId(), userId);
                i.setCancelQuantity(i.getQuantity());
            }
            itemStatus(i, to, reason, userId);
        }
        recalculate(h);
    }

    /**
     * Cancels part or all of an item. Its reservation shrinks to what is left; a fully cancelled item is cancelled, and
     * when no item is left open the order is cancelled too.
     */
    public void cancelItem(String orderId, String orderItemSeqId, BigDecimal quantity, String reason, String userId) {
        OrderHeader h = header(orderId, true);
        OrderItem i = item(orderId, orderItemSeqId);
        if (!ITEM_OPEN.contains(i.getStatusId())) throw new IllegalStateException("Item " + orderItemSeqId + " is " + i.getStatusId() + " and can't be cancelled");
        BigDecimal open = openQuantity(i);
        BigDecimal q = quantity == null ? open : quantity;
        if (q.signum() <= 0 || q.compareTo(open) > 0) throw new IllegalArgumentException("Cancel between 1 and " + open.stripTrailingZeros().toPlainString() + " of item " + orderItemSeqId);
        i.setCancelQuantity(i.getCancelQuantity().add(q));
        OrderItemShipGroupAssoc a = em.find(OrderItemShipGroupAssoc.class, new OrderItemShipGroupAssoc.Key(orderId, orderItemSeqId, i.getShipGroupSeqId()));
        if (a != null) a.setCancelQuantity((a.getCancelQuantity() == null ? BigDecimal.ZERO : a.getCancelQuantity()).add(q));
        BigDecimal reserved = reservedQuantity(orderId, orderItemSeqId);
        BigDecimal left = openQuantity(i);
        if (reserved.compareTo(left) > 0) releaseQuantity(orderId, orderItemSeqId, reserved.subtract(left), userId);
        if (left.signum() == 0) {
            itemStatus(i, "ITEM_CANCELLED", reason, userId);
            if (items(orderId).stream().noneMatch(x -> ITEM_OPEN.contains(x.getStatusId()))
                    && statuses.isAllowed(FLOW, h.getStatusId(), "ORDER_CANCELLED")) {
                h.setStatusId("ORDER_CANCELLED");
                history(orderId, null, null, "ORDER_CANCELLED", "Every item cancelled", userId);
            }
        }
        recalculate(h);
    }

    /**
     * Sets the status another system of record already decided (the mirror of the earlier model), without checking
     * the flow: that system allowed the change. Items follow as they would on a copy, cancelled items give their
     * stock back, and the change is written to the history with the reason.
     */
    public void mirrorStatus(String orderId, String statusId, String reason) {
        OrderHeader h = header(orderId, true);
        String to = statuses.status(statusId).getStatusId();
        if (to.equals(h.getStatusId())) return;
        h.setStatusId(to);
        history(orderId, null, null, to, reason, null);
        String itemTo = itemStatusFor(to);
        for (OrderItem i : items(orderId)) {
            if (!ITEM_OPEN.contains(i.getStatusId()) || itemTo.equals(i.getStatusId())) continue;
            if ("ITEM_CREATED".equals(itemTo)) continue;   // never move an item back
            if ("ITEM_CANCELLED".equals(itemTo) || "ITEM_REJECTED".equals(itemTo)) {
                releaseReservations(orderId, i.getOrderItemSeqId(), null);
                i.setCancelQuantity(i.getQuantity());
            }
            i.setStatusId(itemTo);
            history(orderId, i.getOrderItemSeqId(), null, itemTo, reason, null);
        }
        recalculate(h);
    }

    /** Sets the tracking number of a ship group (the first when none is named). */
    public void setTracking(String orderId, String shipGroupSeqId, String trackingNumber) {
        OrderItemShipGroup g = em.find(OrderItemShipGroup.class, new OrderItemShipGroup.Key(orderId, shipGroupSeqId == null ? SequenceService.seqId(1) : shipGroupSeqId));
        if (g == null) throw new IllegalArgumentException("Order " + orderId + " has no ship group " + shipGroupSeqId);
        g.setTrackingNumber(trackingNumber);
    }

    public void setPaymentStatus(String orderPaymentPreferenceId, String toStatusId, String reason, String userId) {
        OrderPaymentPreference p = em.find(OrderPaymentPreference.class, orderPaymentPreferenceId, LockModeType.PESSIMISTIC_WRITE);
        if (p == null) throw new IllegalArgumentException("Unknown payment " + orderPaymentPreferenceId);
        statuses.requireAllowed(FLOW, p.getStatusId(), toStatusId);
        p.setStatusId(toStatusId);
        history(p.getOrderId(), null, orderPaymentPreferenceId, toStatusId, reason, userId);
    }

    // ── stock ──

    /** Holds stock for an item at a facility (all of what is still open when quantity is null). */
    public void reserve(String orderId, String orderItemSeqId, String facilityId, BigDecimal quantity, String userId) {
        OrderItem i = item(orderId, orderItemSeqId);
        if (!ITEM_OPEN.contains(i.getStatusId())) throw new IllegalStateException("Item " + orderItemSeqId + " is " + i.getStatusId() + "; only open items take stock");
        if (i.getProductId() == null) throw new IllegalStateException("Item " + orderItemSeqId + " has no product to reserve");
        BigDecimal unreserved = openQuantity(i).subtract(reservedQuantity(orderId, orderItemSeqId));
        BigDecimal q = quantity == null ? unreserved : quantity;
        if (q.signum() <= 0 || q.compareTo(unreserved) > 0) {
            throw new IllegalArgumentException("Item " + orderItemSeqId + " has " + unreserved.stripTrailingZeros().toPlainString() + " left to reserve");
        }
        inventory.reserve(i.getProductId(), facilityId, q, new InventoryService.Cause(orderId, orderItemSeqId, null, null, userId, null));
        recordReservation(orderId, orderItemSeqId, inventory.bulkItem(i.getProductId(), facilityId).getInventoryItemId(), q);
    }

    /**
     * Records stock already held elsewhere (a copy from a system that kept the hold in its own totals): the
     * reservation row only, with no stock change.
     */
    public void recordReservation(String orderId, String orderItemSeqId, String inventoryItemId, BigDecimal quantity) {
        OrderItem i = item(orderId, orderItemSeqId);
        OrderItemShipGrpInvRes.Key key = new OrderItemShipGrpInvRes.Key(orderId, i.getShipGroupSeqId(), orderItemSeqId, inventoryItemId);
        OrderItemShipGrpInvRes r = em.find(OrderItemShipGrpInvRes.class, key);
        LocalDateTime now = CoreClock.now();
        if (r == null) {
            r = new OrderItemShipGrpInvRes();
            r.setOrderId(orderId);
            r.setShipGroupSeqId(i.getShipGroupSeqId());
            r.setOrderItemSeqId(orderItemSeqId);
            r.setInventoryItemId(inventoryItemId);
            r.setReserveOrderEnumId("INVRO_FIFO_REC");
            r.setQuantity(quantity);
            r.setQuantityNotAvailable(BigDecimal.ZERO);
            r.setCreatedDatetime(now);
            r.setReservedDatetime(now);
            em.persist(r);
        } else {
            r.setQuantity(r.getQuantity().add(quantity));
            r.setReservedDatetime(now);
        }
    }

    /** Gives back everything an item holds. */
    public void releaseReservations(String orderId, String orderItemSeqId, String userId) {
        BigDecimal held = reservedQuantity(orderId, orderItemSeqId);
        if (held.signum() > 0) releaseQuantity(orderId, orderItemSeqId, held, userId);
    }

    @Transactional(readOnly = true)
    public BigDecimal reservedQuantity(String orderId, String orderItemSeqId) {
        return reservations(orderId, orderItemSeqId).stream().map(OrderItemShipGrpInvRes::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ── ids, attributes, adjustments ──

    public void setIdentification(String orderId, String typeId, String value) {
        if (value == null || value.isBlank()) return;
        List<OrderIdentification> current = em.createQuery("SELECT i FROM OrderIdentification i WHERE i.orderId = :o "
                        + "AND i.orderIdentificationTypeId = :t AND i.thruDate IS NULL", OrderIdentification.class)
                .setParameter("o", orderId).setParameter("t", typeId).getResultList();
        if (current.stream().anyMatch(i -> i.getIdValue().equals(value.trim()))) return;
        LocalDateTime now = CoreClock.now();
        current.forEach(i -> i.setThruDate(now));
        OrderIdentification i = new OrderIdentification();
        i.setOrderIdentificationTypeId(typeId);
        i.setOrderId(orderId);
        i.setFromDate(now);
        i.setIdValue(value.trim());
        em.persist(i);
    }

    /** The order whose current id of this kind is the value: find an order by its Shopify id or PO number. */
    @Transactional(readOnly = true)
    public Optional<String> findByIdentification(String typeId, String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        return em.createQuery("SELECT i.orderId FROM OrderIdentification i WHERE i.orderIdentificationTypeId = :t "
                        + "AND i.idValue = :v AND i.thruDate IS NULL ORDER BY i.fromDate DESC", String.class)
                .setParameter("t", typeId).setParameter("v", value.trim()).setMaxResults(1).getResultList().stream().findFirst();
    }

    public void setAttribute(String orderId, String name, String value) {
        OrderAttribute a = em.find(OrderAttribute.class, new OrderAttribute.Key(orderId, name));
        if (value == null) {
            if (a != null) em.remove(a);
            return;
        }
        if (a == null) {
            a = new OrderAttribute();
            a.setOrderId(orderId);
            a.setAttrName(name);
            a.setAttrValue(value);
            em.persist(a);
        } else {
            a.setAttrValue(value);
        }
    }

    public String addAdjustment(String orderId, String orderItemSeqId, String typeId, BigDecimal amount, String description, String userId) {
        OrderAdjustment a = new OrderAdjustment();
        a.setOrderAdjustmentId(sequences.nextId("OrderAdjustment"));
        a.setOrderAdjustmentTypeId(typeId);
        a.setOrderId(orderId);
        a.setOrderItemSeqId(orderItemSeqId);
        a.setAmount(amount);
        a.setDescription(description);
        a.setCreatedDate(CoreClock.now());
        a.setCreatedByUserLogin(userId);
        em.persist(a);
        return a.getOrderAdjustmentId();
    }

    /** What is still open on an item: ordered less cancelled. */
    public static BigDecimal openQuantity(OrderItem i) {
        return i.getQuantity().subtract(i.getCancelQuantity() == null ? BigDecimal.ZERO : i.getCancelQuantity());
    }

    @Transactional(readOnly = true)
    public List<OrderItem> items(String orderId) {
        return em.createQuery("SELECT i FROM OrderItem i WHERE i.orderId = :o ORDER BY i.orderItemSeqId", OrderItem.class)
                .setParameter("o", orderId).getResultList();
    }

    /**
     * Grand total = what is still open on the items at their price, plus order adjustments, plus adjustments on
     * items that are still open. Cancelled quantities drop out; OFBiz keeps the total current the same way.
     */
    public BigDecimal recalculate(String orderId) {
        return recalculate(header(orderId, false));
    }

    // ── internals ──

    private BigDecimal recalculate(OrderHeader h) {
        List<OrderItem> items = items(h.getOrderId());
        BigDecimal sub = BigDecimal.ZERO;
        for (OrderItem i : items) {
            if ("ITEM_CANCELLED".equals(i.getStatusId()) || "ITEM_REJECTED".equals(i.getStatusId())) continue;
            sub = sub.add(openQuantity(i).multiply(i.getUnitPrice() == null ? BigDecimal.ZERO : i.getUnitPrice()));
        }
        Set<String> live = new java.util.HashSet<>();
        items.stream().filter(i -> !"ITEM_CANCELLED".equals(i.getStatusId()) && !"ITEM_REJECTED".equals(i.getStatusId()))
                .forEach(i -> live.add(i.getOrderItemSeqId()));
        BigDecimal adj = BigDecimal.ZERO;
        for (OrderAdjustment a : em.createQuery("SELECT a FROM OrderAdjustment a WHERE a.orderId = :o", OrderAdjustment.class)
                .setParameter("o", h.getOrderId()).getResultList()) {
            if (a.getOrderItemSeqId() == null || live.contains(a.getOrderItemSeqId())) adj = adj.add(a.getAmount());
        }
        h.setRemainingSubTotal(sub);
        h.setGrandTotal(sub.add(adj));
        return h.getGrandTotal();
    }

    private void releaseQuantity(String orderId, String orderItemSeqId, BigDecimal quantity, String userId) {
        BigDecimal left = quantity;
        for (OrderItemShipGrpInvRes r : reservations(orderId, orderItemSeqId)) {
            if (left.signum() <= 0) break;
            BigDecimal take = r.getQuantity().min(left);
            InventoryItem inv = em.find(InventoryItem.class, r.getInventoryItemId());
            inventory.release(inv.getProductId(), inv.getFacilityId(), take, new InventoryService.Cause(orderId, orderItemSeqId, null, null, userId, null));
            BigDecimal rest = r.getQuantity().subtract(take);
            if (rest.signum() == 0) em.remove(r);
            else r.setQuantity(rest);
            left = left.subtract(take);
        }
    }

    private List<OrderItemShipGrpInvRes> reservations(String orderId, String orderItemSeqId) {
        return em.createQuery("SELECT r FROM OrderItemShipGrpInvRes r WHERE r.orderId = :o AND r.orderItemSeqId = :i "
                        + "ORDER BY r.reservedDatetime DESC", OrderItemShipGrpInvRes.class)
                .setParameter("o", orderId).setParameter("i", orderItemSeqId).getResultList();
    }

    private void itemStatus(OrderItem i, String toStatusId, String reason, String userId) {
        statuses.requireAllowed(FLOW, i.getStatusId(), toStatusId);
        i.setStatusId(toStatusId);
        history(i.getOrderId(), i.getOrderItemSeqId(), null, toStatusId, reason, userId);
    }

    private static String itemStatusFor(String headerStatus) {
        return switch (headerStatus) {
            case "ORDER_APPROVED", "ORDER_SENT", "ORDER_HOLD" -> "ITEM_APPROVED";
            case "ORDER_COMPLETED" -> "ITEM_COMPLETED";
            case "ORDER_CANCELLED" -> "ITEM_CANCELLED";
            case "ORDER_REJECTED" -> "ITEM_REJECTED";
            default -> "ITEM_CREATED";
        };
    }

    private void history(String orderId, String orderItemSeqId, String paymentId, String statusId, String reason, String userId) {
        OrderStatus s = new OrderStatus();
        s.setOrderStatusId(sequences.nextId("OrderStatus"));
        s.setOrderId(orderId);
        s.setOrderItemSeqId(orderItemSeqId);
        s.setOrderPaymentPreferenceId(paymentId);
        s.setStatusId(statusId);
        s.setStatusDatetime(CoreClock.now());
        s.setStatusUserLogin(userId);
        s.setChangeReason(reason);
        em.persist(s);
    }

    private void role(String orderId, String partyId, String roleTypeId) {
        if (em.find(OrderRole.class, new OrderRole.Key(orderId, partyId, roleTypeId)) != null) return;
        OrderRole r = new OrderRole();
        r.setOrderId(orderId);
        r.setPartyId(partyId);
        r.setRoleTypeId(roleTypeId);
        em.persist(r);
    }

    private void contact(String orderId, String purposeTypeId, String contactMechId) {
        if (contactMechId == null) return;
        if (em.find(OrderContactMech.class, new OrderContactMech.Key(orderId, purposeTypeId, contactMechId)) != null) return;
        OrderContactMech c = new OrderContactMech();
        c.setOrderId(orderId);
        c.setContactMechPurposeTypeId(purposeTypeId);
        c.setContactMechId(contactMechId);
        em.persist(c);
    }

    private OrderHeader header(String orderId, boolean lock) {
        OrderHeader h = lock ? em.find(OrderHeader.class, orderId, LockModeType.PESSIMISTIC_WRITE) : em.find(OrderHeader.class, orderId);
        if (h == null) throw new IllegalArgumentException("Unknown order " + orderId);
        return h;
    }

    private OrderItem item(String orderId, String orderItemSeqId) {
        OrderItem i = em.find(OrderItem.class, new OrderItem.Key(orderId, orderItemSeqId));
        if (i == null) throw new IllegalArgumentException("Order " + orderId + " has no item " + orderItemSeqId);
        return i;
    }
}
