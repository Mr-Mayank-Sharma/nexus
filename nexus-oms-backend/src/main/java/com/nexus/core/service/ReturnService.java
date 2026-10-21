package com.nexus.core.service;

import com.nexus.core.model.order.OrderItem;
import com.nexus.core.model.order.OrderPaymentPreference;
import com.nexus.core.model.returns.ReturnAdjustment;
import com.nexus.core.model.returns.ReturnHeader;
import com.nexus.core.model.returns.ReturnItem;
import com.nexus.core.model.returns.ReturnItemResponse;
import com.nexus.core.model.returns.ReturnStatus;
import com.nexus.core.model.shipment.ShipmentReceipt;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Customer returns in the OFBiz way: a return header from the customer to a facility, items tied to the order items
 * they return, receipts when the goods arrive (resellable stock goes back on sale, the rest is held), and a response
 * that records the refund against the order's payment. A return can't take back more than shipped less what other
 * returns already take back.
 */
@Service("coreReturnService")
@Transactional
public class ReturnService {

    public static final String FLOW = StatusService.DEFAULT_FLOW;
    private static final Set<String> CLOSED = Set.of("RETURN_CANCELLED");

    private final EntityManager em;
    private final SequenceService sequences;
    private final StatusService statuses;
    private final OrderService orders;
    private final ShipmentService shipments;
    private final InventoryService inventory;

    public ReturnService(EntityManager em, SequenceService sequences, StatusService statuses, OrderService orders,
                         ShipmentService shipments, InventoryService inventory) {
        this.em = em;
        this.sequences = sequences;
        this.statuses = statuses;
        this.orders = orders;
        this.shipments = shipments;
        this.inventory = inventory;
    }

    public record NewReturnItem(String orderItemSeqId, BigDecimal quantity, String returnReasonId, String returnTypeId) {
    }

    /** A return of shipped items of one order, to come back to a facility. */
    public String create(String orderId, List<NewReturnItem> items, String destinationFacilityId, String externalId, String userId) {
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("A return needs at least one item");
        var order = em.find(com.nexus.core.model.order.OrderHeader.class, orderId);
        if (order == null) throw new IllegalArgumentException("Unknown order " + orderId);
        ReturnHeader h = new ReturnHeader();
        h.setReturnId(sequences.nextId("ReturnHeader"));
        h.setReturnHeaderTypeId("CUSTOMER_RETURN");
        h.setStatusId(statuses.initialStatus(FLOW, "RETURN_STATUS"));
        h.setExternalId(externalId);
        h.setFromPartyId(orders.partyInRole(orderId, "PLACING_CUSTOMER").orElse(null));
        h.setProductStoreId(order.getProductStoreId());
        h.setSalesChannelEnumId(order.getSalesChannelEnumId());
        h.setDestinationFacilityId(destinationFacilityId);
        h.setNeedsInventoryReceive(true);
        h.setCurrencyUomId(order.getCurrencyUomId());
        h.setEntryDate(CoreClock.now());
        h.setCreatedBy(userId);
        em.persist(h);
        history(h.getReturnId(), null, h.getStatusId(), null, userId);
        int n = 1;
        for (NewReturnItem it : items) {
            OrderItem oi = orders.orderItem(orderId, it.orderItemSeqId());
            BigDecimal returnable = shipments.issuedQuantity(orderId, it.orderItemSeqId()).subtract(returning(orderId, it.orderItemSeqId()));
            if (it.quantity() == null || it.quantity().signum() <= 0 || it.quantity().compareTo(returnable) > 0) {
                throw new IllegalArgumentException("Item " + it.orderItemSeqId() + " of order " + orderId + " can return up to "
                        + returnable.stripTrailingZeros().toPlainString() + " (shipped less what other returns take back)");
            }
            ReturnItem ri = new ReturnItem();
            ri.setReturnId(h.getReturnId());
            ri.setReturnItemSeqId(SequenceService.seqId(n++));
            ri.setReturnReasonId(it.returnReasonId() == null ? "RTN_MISC" : it.returnReasonId());
            ri.setReturnTypeId(it.returnTypeId() == null ? "RTN_REFUND" : it.returnTypeId());
            ri.setProductId(oi.getProductId());
            ri.setDescription(oi.getItemDescription());
            ri.setOrderId(orderId);
            ri.setOrderItemSeqId(it.orderItemSeqId());
            ri.setStatusId(h.getStatusId());
            ri.setExpectedItemStatus("INV_RETURNED");
            ri.setReturnQuantity(it.quantity());
            ri.setReceivedQuantity(BigDecimal.ZERO);
            ri.setReturnPrice(oi.getUnitPrice());
            em.persist(ri);
            history(h.getReturnId(), ri.getReturnItemSeqId(), ri.getStatusId(), null, userId);
        }
        return h.getReturnId();
    }

    public void accept(String returnId, String userId) {
        move(returnId, "RETURN_ACCEPTED", null, userId);
    }

    public void cancel(String returnId, String reason, String userId) {
        move(returnId, "RETURN_CANCELLED", reason, userId);
    }

    /** A fee or charge on the return (a restocking fee is negative). */
    public String addAdjustment(String returnId, String typeId, BigDecimal amount, String description) {
        header(returnId);
        ReturnAdjustment a = new ReturnAdjustment();
        a.setReturnAdjustmentId(sequences.nextId("ReturnAdjustment"));
        a.setReturnAdjustmentTypeId(typeId);
        a.setReturnId(returnId);
        a.setAmount(amount);
        a.setDescription(description);
        a.setCreatedDate(CoreClock.now());
        em.persist(a);
        return a.getReturnAdjustmentId();
    }

    /** How a received unit is treated: back on sale, or held (damaged, defective). */
    public enum Grade { RESELLABLE, DAMAGED, DEFECTIVE }

    /**
     * Goods arrived at the return's facility: accepted units go into stock (resellable ones on sale, others on hold),
     * rejected units are only noted. The return is received once every item has arrived in full.
     */
    public void receive(String returnId, String returnItemSeqId, BigDecimal accepted, BigDecimal rejected, String rejectionId, Grade grade, String userId) {
        ReturnHeader h = header(returnId);
        if (!"RETURN_ACCEPTED".equals(h.getStatusId())) throw new IllegalStateException("Return " + returnId + " is " + h.getStatusId() + "; accept it before receiving");
        if (h.getDestinationFacilityId() == null) throw new IllegalStateException("Return " + returnId + " has no facility to receive into");
        ReturnItem ri = item(returnId, returnItemSeqId);
        BigDecimal acc = accepted == null ? BigDecimal.ZERO : accepted;
        BigDecimal rej = rejected == null ? BigDecimal.ZERO : rejected;
        BigDecimal left = ri.getReturnQuantity().subtract(ri.getReceivedQuantity());
        if (acc.signum() < 0 || rej.signum() < 0 || acc.add(rej).signum() == 0 || acc.add(rej).compareTo(left) > 0) {
            throw new IllegalArgumentException("Return item " + returnItemSeqId + " expects up to " + left.stripTrailingZeros().toPlainString() + " more");
        }
        String inventoryItemId = null;
        if (acc.signum() > 0 && ri.getProductId() != null) {
            InventoryService.Cause cause = new InventoryService.Cause(ri.getOrderId(), ri.getOrderItemSeqId(), null, returnId, userId, null);
            if (grade == null || grade == Grade.RESELLABLE) {
                inventoryItemId = inventory.receive(ri.getProductId(), h.getDestinationFacilityId(), acc, null, null, "IID_RETURN", cause);
            } else {
                // in the building but not for sale
                inventory.adjust(ri.getProductId(), h.getDestinationFacilityId(), acc, BigDecimal.ZERO, "IID_HOLD",
                        new InventoryService.Cause(ri.getOrderId(), ri.getOrderItemSeqId(), null, returnId, userId, "Returned " + grade.name().toLowerCase()));
                inventoryItemId = inventory.bulkItem(ri.getProductId(), h.getDestinationFacilityId()).getInventoryItemId();
            }
        }
        ShipmentReceipt r = new ShipmentReceipt();
        r.setReceiptId(sequences.nextId("ShipmentReceipt"));
        r.setInventoryItemId(inventoryItemId);
        r.setProductId(ri.getProductId());
        r.setReturnId(returnId);
        r.setReturnItemSeqId(returnItemSeqId);
        r.setRejectionId(rej.signum() > 0 ? (rejectionId == null ? "SRJ_DAMAGED" : rejectionId) : null);
        r.setDatetimeReceived(CoreClock.now());
        r.setReceivedByUserLogin(userId);
        r.setQuantityAccepted(acc);
        r.setQuantityRejected(rej);
        em.persist(r);
        ri.setReceivedQuantity(ri.getReceivedQuantity().add(acc).add(rej));
        if (ri.getReceivedQuantity().compareTo(ri.getReturnQuantity()) >= 0 && !"RETURN_RECEIVED".equals(ri.getStatusId())) {
            ri.setStatusId("RETURN_RECEIVED");
            history(returnId, returnItemSeqId, "RETURN_RECEIVED", null, userId);
        }
        if (items(returnId).stream().allMatch(i -> "RETURN_RECEIVED".equals(i.getStatusId()))) {
            h.setStatusId("RETURN_RECEIVED");
            history(returnId, null, "RETURN_RECEIVED", null, userId);
        }
    }

    /**
     * What the customer gets back: for refund items the price of what was accepted (or of what is being returned, for
     * refunds before the goods arrive), plus the return's adjustments.
     */
    @Transactional(readOnly = true)
    public BigDecimal refundAmount(String returnId) {
        BigDecimal sum = BigDecimal.ZERO;
        for (ReturnItem ri : items(returnId)) {
            if ("RETURN_CANCELLED".equals(ri.getStatusId())) continue;
            if (!"RTN_REFUND".equals(ri.getReturnTypeId()) && !"RTN_REFUND_IMMEDIATE".equals(ri.getReturnTypeId())) continue;
            BigDecimal q = "RTN_REFUND_IMMEDIATE".equals(ri.getReturnTypeId()) ? ri.getReturnQuantity() : accepted(returnId, ri.getReturnItemSeqId());
            sum = sum.add(q.multiply(ri.getReturnPrice() == null ? BigDecimal.ZERO : ri.getReturnPrice()));
        }
        for (ReturnAdjustment a : em.createQuery("SELECT a FROM ReturnAdjustment a WHERE a.returnId = :r", ReturnAdjustment.class)
                .setParameter("r", returnId).getResultList()) {
            sum = sum.add(a.getAmount());
        }
        return sum.signum() < 0 ? BigDecimal.ZERO : sum;
    }

    /**
     * Completes a received return and records the refund on the order's first payment, with the reference the payment
     * provider gave. A refund of everything the payment took marks the payment refunded. Returns the response id.
     */
    public String complete(String returnId, String referenceNumber, String userId) {
        ReturnHeader h = header(returnId);
        statuses.requireAllowed(FLOW, h.getStatusId(), "RETURN_COMPLETED");
        BigDecimal amount = refundAmount(returnId);
        String orderId = items(returnId).get(0).getOrderId();
        OrderPaymentPreference pay = em.createQuery("SELECT p FROM OrderPaymentPreference p WHERE p.orderId = :o ORDER BY p.orderPaymentPreferenceId",
                OrderPaymentPreference.class).setParameter("o", orderId).setMaxResults(1).getResultList().stream().findFirst().orElse(null);
        ReturnItemResponse r = new ReturnItemResponse();
        r.setReturnItemResponseId(sequences.nextId("ReturnItemResponse"));
        r.setOrderPaymentPreferenceId(pay == null ? null : pay.getOrderPaymentPreferenceId());
        r.setResponseAmount(amount);
        r.setResponseDate(CoreClock.now());
        r.setReferenceNumber(referenceNumber);
        em.persist(r);
        for (ReturnItem ri : items(returnId)) {
            if (CLOSED.contains(ri.getStatusId())) continue;
            ri.setReturnItemResponseId(r.getReturnItemResponseId());
            ri.setStatusId("RETURN_COMPLETED");
            history(returnId, ri.getReturnItemSeqId(), "RETURN_COMPLETED", null, userId);
        }
        h.setStatusId("RETURN_COMPLETED");
        history(returnId, null, "RETURN_COMPLETED", null, userId);
        if (pay != null && pay.getMaxAmount() != null && amount.compareTo(pay.getMaxAmount()) >= 0
                && statuses.isAllowed(FLOW, pay.getStatusId(), "PAYMENT_REFUNDED")) {
            orders.setPaymentStatus(pay.getOrderPaymentPreferenceId(), "PAYMENT_REFUNDED", "Return " + returnId, userId);
        }
        return r.getReturnItemResponseId();
    }

    // ── internals ──

    private void move(String returnId, String to, String reason, String userId) {
        ReturnHeader h = header(returnId);
        statuses.requireAllowed(FLOW, h.getStatusId(), to);
        h.setStatusId(to);
        history(returnId, null, to, reason, userId);
        for (ReturnItem ri : items(returnId)) {
            if (ri.getStatusId().equals(to) || !statuses.isAllowed(FLOW, ri.getStatusId(), to)) continue;
            ri.setStatusId(to);
            history(returnId, ri.getReturnItemSeqId(), to, reason, userId);
        }
    }

    /** Quantity of an order item on returns that are not cancelled. */
    private BigDecimal returning(String orderId, String orderItemSeqId) {
        BigDecimal v = em.createQuery("SELECT SUM(r.returnQuantity) FROM ReturnItem r WHERE r.orderId = :o AND r.orderItemSeqId = :i "
                + "AND r.statusId <> 'RETURN_CANCELLED'", BigDecimal.class).setParameter("o", orderId).setParameter("i", orderItemSeqId).getSingleResult();
        return v == null ? BigDecimal.ZERO : v;
    }

    private BigDecimal accepted(String returnId, String returnItemSeqId) {
        BigDecimal v = em.createQuery("SELECT SUM(r.quantityAccepted) FROM ShipmentReceipt r WHERE r.returnId = :r AND r.returnItemSeqId = :i", BigDecimal.class)
                .setParameter("r", returnId).setParameter("i", returnItemSeqId).getSingleResult();
        return v == null ? BigDecimal.ZERO : v;
    }

    private void history(String returnId, String returnItemSeqId, String statusId, String reason, String userId) {
        ReturnStatus s = new ReturnStatus();
        s.setReturnStatusId(sequences.nextId("ReturnStatus"));
        s.setReturnId(returnId);
        s.setReturnItemSeqId(returnItemSeqId);
        s.setStatusId(statusId);
        s.setStatusDatetime(CoreClock.now());
        s.setChangeByUserLoginId(userId);
        s.setChangeReason(reason);
        em.persist(s);
    }

    @Transactional(readOnly = true)
    public List<ReturnItem> items(String returnId) {
        return em.createQuery("SELECT i FROM ReturnItem i WHERE i.returnId = :r ORDER BY i.returnItemSeqId", ReturnItem.class)
                .setParameter("r", returnId).getResultList();
    }

    private ReturnItem item(String returnId, String returnItemSeqId) {
        ReturnItem i = em.find(ReturnItem.class, new ReturnItem.Key(returnId, returnItemSeqId));
        if (i == null) throw new IllegalArgumentException("Return " + returnId + " has no item " + returnItemSeqId);
        return i;
    }

    private ReturnHeader header(String returnId) {
        ReturnHeader h = em.find(ReturnHeader.class, returnId, LockModeType.PESSIMISTIC_WRITE);
        if (h == null) throw new IllegalArgumentException("Unknown return " + returnId);
        return h;
    }
}
