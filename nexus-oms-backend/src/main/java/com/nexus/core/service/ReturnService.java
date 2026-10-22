package com.nexus.core.service;

import com.nexus.core.CoreStatus;
import com.nexus.core.model.order.OrderHeader;
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
import com.nexus.core.CoreTransactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Customer returns in the OFBiz way:
 * <ol>
 *   <li>{@link #create}: a return header from the customer to a facility, with items tied to the order items they
 *       return. A return can't take back more than shipped less what other returns already take back.</li>
 *   <li>{@link #accept}, then {@link #receive} as the goods arrive: resellable stock goes back on sale, the rest is
 *       held.</li>
 *   <li>{@link #complete}: a response records the refund against the order's payment.</li>
 * </ol>
 */
@Service("coreReturnService")
@CoreTransactional
public class ReturnService {

    public static final String FLOW = StatusService.DEFAULT_FLOW;

    public static final String REFUND = "RTN_REFUND";
    /** Refunded before the goods arrive: the whole return quantity counts. */
    public static final String REFUND_IMMEDIATE = "RTN_REFUND_IMMEDIATE";
    static final Set<String> REFUND_TYPES = Set.of(REFUND, REFUND_IMMEDIATE);
    static final String CUSTOMER_RETURN = "CUSTOMER_RETURN";
    static final String DEFAULT_REASON = "RTN_MISC";
    static final String DEFAULT_REJECTION = "SRJ_DAMAGED";

    private final EntityManager entityManager;
    private final SequenceService sequenceService;
    private final StatusService statusService;
    private final OrderService orderService;
    private final ShipmentService shipmentService;
    private final InventoryService inventoryService;

    public ReturnService(EntityManager entityManager, SequenceService sequenceService, StatusService statusService, OrderService orderService,
                         ShipmentService shipmentService, InventoryService inventoryService) {
        this.entityManager = entityManager;
        this.sequenceService = sequenceService;
        this.statusService = statusService;
        this.orderService = orderService;
        this.shipmentService = shipmentService;
        this.inventoryService = inventoryService;
    }

    public record NewReturnItem(String orderItemSeqId, BigDecimal quantity, String returnReasonId, String returnTypeId) {
    }

    /** How a received unit is treated: back on sale, or held (damaged, defective). */
    public enum Grade { RESELLABLE, DAMAGED, DEFECTIVE }

    // ── the return's life ──

    /** A return of shipped items of one order, to come back to a facility. Returns the return id. */
    public String create(String orderId, List<NewReturnItem> items, String destinationFacilityId, String externalId, String userId) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("A return needs at least one item");
        }
        OrderHeader order = orderService.header(orderId);

        ReturnHeader header = new ReturnHeader();
        header.setReturnId(sequenceService.nextId("ReturnHeader"));
        header.setReturnHeaderTypeId(CUSTOMER_RETURN);
        header.setStatusId(statusService.initialStatus(FLOW, CoreStatus.Type.RETURN));
        header.setExternalId(externalId);
        header.setFromPartyId(orderService.partyInRole(orderId, "PLACING_CUSTOMER").orElse(null));
        header.setProductStoreId(order.getProductStoreId());
        header.setSalesChannelEnumId(order.getSalesChannelEnumId());
        header.setDestinationFacilityId(destinationFacilityId);
        header.setNeedsInventoryReceive(true);
        header.setCurrencyUomId(order.getCurrencyUomId());
        header.setEntryDate(CoreClock.now());
        header.setCreatedBy(userId);
        entityManager.persist(header);
        writeHistory(header.getReturnId(), null, header.getStatusId(), null, userId);

        int itemNumber = 1;
        for (NewReturnItem newItem : items) {
            OrderItem orderItem = orderService.orderItem(orderId, newItem.orderItemSeqId());
            BigDecimal shipped = shipmentService.issuedQuantity(orderId, newItem.orderItemSeqId());
            BigDecimal returnable = shipped.subtract(quantityOnOtherReturns(orderId, newItem.orderItemSeqId()));
            boolean validQuantity = newItem.quantity() != null && newItem.quantity().signum() > 0 && newItem.quantity().compareTo(returnable) <= 0;
            if (!validQuantity) {
                throw new IllegalArgumentException("Item " + newItem.orderItemSeqId() + " of order " + orderId + " can return up to "
                        + plain(returnable) + " (shipped less what other returns take back)");
            }
            ReturnItem returnItem = new ReturnItem();
            returnItem.setReturnId(header.getReturnId());
            returnItem.setReturnItemSeqId(SequenceService.seqId(itemNumber++));
            returnItem.setReturnReasonId(newItem.returnReasonId() == null ? DEFAULT_REASON : newItem.returnReasonId());
            returnItem.setReturnTypeId(newItem.returnTypeId() == null ? REFUND : newItem.returnTypeId());
            returnItem.setProductId(orderItem.getProductId());
            returnItem.setDescription(orderItem.getItemDescription());
            returnItem.setOrderId(orderId);
            returnItem.setOrderItemSeqId(newItem.orderItemSeqId());
            returnItem.setStatusId(header.getStatusId());
            returnItem.setExpectedItemStatus("INV_RETURNED");
            returnItem.setReturnQuantity(newItem.quantity());
            returnItem.setReceivedQuantity(BigDecimal.ZERO);
            returnItem.setReturnPrice(orderItem.getUnitPrice());
            entityManager.persist(returnItem);
            writeHistory(header.getReturnId(), returnItem.getReturnItemSeqId(), returnItem.getStatusId(), null, userId);
        }
        return header.getReturnId();
    }

    public void accept(String returnId, String userId) {
        changeStatus(returnId, CoreStatus.Return.ACCEPTED, null, userId);
    }

    public void cancel(String returnId, String reason, String userId) {
        changeStatus(returnId, CoreStatus.Return.CANCELLED, reason, userId);
    }

    /** A fee or charge on the return (a restocking fee is negative). Returns the adjustment id. */
    public String addAdjustment(String returnId, String typeId, BigDecimal amount, String description) {
        loadHeaderForUpdate(returnId);
        ReturnAdjustment adjustment = new ReturnAdjustment();
        adjustment.setReturnAdjustmentId(sequenceService.nextId("ReturnAdjustment"));
        adjustment.setReturnAdjustmentTypeId(typeId);
        adjustment.setReturnId(returnId);
        adjustment.setAmount(amount);
        adjustment.setDescription(description);
        adjustment.setCreatedDate(CoreClock.now());
        entityManager.persist(adjustment);
        return adjustment.getReturnAdjustmentId();
    }

    /**
     * Goods arrived at the return's facility. Accepted units go into stock: resellable ones on sale, others on hold.
     * Rejected units are only noted. An item is received once it has arrived in full, and the return once every item
     * has.
     */
    public void receive(String returnId, String returnItemSeqId, BigDecimal accepted, BigDecimal rejected, String rejectionId, Grade grade, String userId) {
        ReturnHeader header = loadHeaderForUpdate(returnId);
        if (!CoreStatus.Return.ACCEPTED.equals(header.getStatusId())) {
            throw new IllegalStateException("Return " + returnId + " is " + header.getStatusId() + "; accept it before receiving");
        }
        String facilityId = header.getDestinationFacilityId();
        if (facilityId == null) {
            throw new IllegalStateException("Return " + returnId + " has no facility to receive into");
        }
        ReturnItem returnItem = loadItem(returnId, returnItemSeqId);
        BigDecimal acceptedQuantity = accepted == null ? BigDecimal.ZERO : accepted;
        BigDecimal rejectedQuantity = rejected == null ? BigDecimal.ZERO : rejected;
        BigDecimal arriving = acceptedQuantity.add(rejectedQuantity);
        BigDecimal stillExpected = returnItem.getReturnQuantity().subtract(returnItem.getReceivedQuantity());
        boolean validQuantities = acceptedQuantity.signum() >= 0 && rejectedQuantity.signum() >= 0 && arriving.signum() != 0
                && arriving.compareTo(stillExpected) <= 0;
        if (!validQuantities) {
            throw new IllegalArgumentException("Return item " + returnItemSeqId + " expects up to " + plain(stillExpected) + " more");
        }

        // Step 1: accepted units into stock.
        String inventoryItemId = null;
        if (acceptedQuantity.signum() > 0 && returnItem.getProductId() != null) {
            String productId = returnItem.getProductId();
            boolean resellable = grade == null || grade == Grade.RESELLABLE;
            if (resellable) {
                InventoryService.Cause cause = new InventoryService.Cause(returnItem.getOrderId(), returnItem.getOrderItemSeqId(), null, returnId, userId, null);
                inventoryItemId = inventoryService.receive(productId, facilityId, acceptedQuantity, null, null, InventoryService.Reason.RETURN, cause);
            } else {
                // In the building but not for sale.
                InventoryService.Cause cause = new InventoryService.Cause(returnItem.getOrderId(), returnItem.getOrderItemSeqId(), null, returnId, userId,
                        "Returned " + grade.name().toLowerCase(Locale.ROOT));
                inventoryService.adjust(productId, facilityId, acceptedQuantity, BigDecimal.ZERO, InventoryService.Reason.HOLD, cause);
                inventoryItemId = inventoryService.bulkItem(productId, facilityId).getInventoryItemId();
            }
        }

        // Step 2: the receipt.
        ShipmentReceipt receipt = new ShipmentReceipt();
        receipt.setReceiptId(sequenceService.nextId("ShipmentReceipt"));
        receipt.setInventoryItemId(inventoryItemId);
        receipt.setProductId(returnItem.getProductId());
        receipt.setReturnId(returnId);
        receipt.setReturnItemSeqId(returnItemSeqId);
        receipt.setRejectionId(rejectedQuantity.signum() > 0 ? (rejectionId == null ? DEFAULT_REJECTION : rejectionId) : null);
        receipt.setDatetimeReceived(CoreClock.now());
        receipt.setReceivedByUserLogin(userId);
        receipt.setQuantityAccepted(acceptedQuantity);
        receipt.setQuantityRejected(rejectedQuantity);
        entityManager.persist(receipt);

        // Step 3: statuses.
        returnItem.setReceivedQuantity(returnItem.getReceivedQuantity().add(arriving));
        boolean itemArrivedInFull = returnItem.getReceivedQuantity().compareTo(returnItem.getReturnQuantity()) >= 0;
        if (itemArrivedInFull && !CoreStatus.Return.RECEIVED.equals(returnItem.getStatusId())) {
            returnItem.setStatusId(CoreStatus.Return.RECEIVED);
            writeHistory(returnId, returnItemSeqId, CoreStatus.Return.RECEIVED, null, userId);
        }
        boolean everyItemReceived = items(returnId).stream().allMatch(item -> CoreStatus.Return.RECEIVED.equals(item.getStatusId()));
        if (everyItemReceived) {
            header.setStatusId(CoreStatus.Return.RECEIVED);
            writeHistory(returnId, null, CoreStatus.Return.RECEIVED, null, userId);
        }
    }

    /**
     * What the customer gets back: for refund items the price of what was accepted (or of the whole return quantity,
     * for refunds before the goods arrive), plus the return's adjustments. Never below zero.
     */
    @CoreTransactional(readOnly = true)
    public BigDecimal refundAmount(String returnId) {
        BigDecimal total = BigDecimal.ZERO;
        for (ReturnItem returnItem : items(returnId)) {
            if (CoreStatus.Return.CANCELLED.equals(returnItem.getStatusId()) || !REFUND_TYPES.contains(returnItem.getReturnTypeId())) {
                continue;
            }
            BigDecimal refundedQuantity = REFUND_IMMEDIATE.equals(returnItem.getReturnTypeId())
                    ? returnItem.getReturnQuantity()
                    : acceptedQuantity(returnId, returnItem.getReturnItemSeqId());
            BigDecimal price = returnItem.getReturnPrice() == null ? BigDecimal.ZERO : returnItem.getReturnPrice();
            total = total.add(refundedQuantity.multiply(price));
        }
        List<ReturnAdjustment> adjustments = entityManager.createQuery("SELECT a FROM ReturnAdjustment a WHERE a.returnId = :returnId", ReturnAdjustment.class)
                .setParameter("returnId", returnId)
                .getResultList();
        for (ReturnAdjustment adjustment : adjustments) {
            total = total.add(adjustment.getAmount());
        }
        return total.signum() < 0 ? BigDecimal.ZERO : total;
    }

    /**
     * Completes a received return and records the refund on the order's first payment, with the reference the payment
     * provider gave. A refund of everything the payment took marks the payment refunded. Returns the response id.
     */
    public String complete(String returnId, String referenceNumber, String userId) {
        ReturnHeader header = loadHeaderForUpdate(returnId);
        statusService.requireAllowed(FLOW, header.getStatusId(), CoreStatus.Return.COMPLETED);
        BigDecimal refund = refundAmount(returnId);
        List<ReturnItem> returnItems = items(returnId);
        String orderId = returnItems.get(0).getOrderId();
        OrderPaymentPreference payment = firstPayment(orderId);

        ReturnItemResponse response = new ReturnItemResponse();
        response.setReturnItemResponseId(sequenceService.nextId("ReturnItemResponse"));
        response.setOrderPaymentPreferenceId(payment == null ? null : payment.getOrderPaymentPreferenceId());
        response.setResponseAmount(refund);
        response.setResponseDate(CoreClock.now());
        response.setReferenceNumber(referenceNumber);
        entityManager.persist(response);

        for (ReturnItem returnItem : returnItems) {
            if (CoreStatus.Return.CANCELLED.equals(returnItem.getStatusId())) {
                continue;
            }
            returnItem.setReturnItemResponseId(response.getReturnItemResponseId());
            returnItem.setStatusId(CoreStatus.Return.COMPLETED);
            writeHistory(returnId, returnItem.getReturnItemSeqId(), CoreStatus.Return.COMPLETED, null, userId);
        }
        header.setStatusId(CoreStatus.Return.COMPLETED);
        writeHistory(returnId, null, CoreStatus.Return.COMPLETED, null, userId);

        boolean refundedInFull = payment != null && payment.getMaxAmount() != null && refund.compareTo(payment.getMaxAmount()) >= 0;
        if (refundedInFull && statusService.isAllowed(FLOW, payment.getStatusId(), CoreStatus.Payment.REFUNDED)) {
            orderService.setPaymentStatus(payment.getOrderPaymentPreferenceId(), CoreStatus.Payment.REFUNDED, "Return " + returnId, userId);
        }
        return response.getReturnItemResponseId();
    }

    /** The return's items in order. */
    @CoreTransactional(readOnly = true)
    public List<ReturnItem> items(String returnId) {
        return entityManager.createQuery("SELECT i FROM ReturnItem i WHERE i.returnId = :returnId ORDER BY i.returnItemSeqId", ReturnItem.class)
                .setParameter("returnId", returnId)
                .getResultList();
    }

    // ── internals ──

    /** Moves the return, and every item that may follow, to the status. */
    private void changeStatus(String returnId, String toStatusId, String reason, String userId) {
        ReturnHeader header = loadHeaderForUpdate(returnId);
        statusService.requireAllowed(FLOW, header.getStatusId(), toStatusId);
        header.setStatusId(toStatusId);
        writeHistory(returnId, null, toStatusId, reason, userId);
        for (ReturnItem returnItem : items(returnId)) {
            boolean alreadyThere = returnItem.getStatusId().equals(toStatusId);
            if (alreadyThere || !statusService.isAllowed(FLOW, returnItem.getStatusId(), toStatusId)) {
                continue;
            }
            returnItem.setStatusId(toStatusId);
            writeHistory(returnId, returnItem.getReturnItemSeqId(), toStatusId, reason, userId);
        }
    }

    /** Quantity of an order item on returns that are not cancelled. */
    private BigDecimal quantityOnOtherReturns(String orderId, String orderItemSeqId) {
        BigDecimal total = entityManager.createQuery("SELECT SUM(r.returnQuantity) FROM ReturnItem r WHERE r.orderId = :orderId "
                        + "AND r.orderItemSeqId = :itemSeqId AND r.statusId <> :cancelled", BigDecimal.class)
                .setParameter("orderId", orderId)
                .setParameter("itemSeqId", orderItemSeqId)
                .setParameter("cancelled", CoreStatus.Return.CANCELLED)
                .getSingleResult();
        return total == null ? BigDecimal.ZERO : total;
    }

    private BigDecimal acceptedQuantity(String returnId, String returnItemSeqId) {
        BigDecimal total = entityManager.createQuery("SELECT SUM(r.quantityAccepted) FROM ShipmentReceipt r "
                        + "WHERE r.returnId = :returnId AND r.returnItemSeqId = :itemSeqId", BigDecimal.class)
                .setParameter("returnId", returnId)
                .setParameter("itemSeqId", returnItemSeqId)
                .getSingleResult();
        return total == null ? BigDecimal.ZERO : total;
    }

    private OrderPaymentPreference firstPayment(String orderId) {
        return entityManager.createQuery("SELECT p FROM OrderPaymentPreference p WHERE p.orderId = :orderId ORDER BY p.orderPaymentPreferenceId",
                        OrderPaymentPreference.class)
                .setParameter("orderId", orderId)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst()
                .orElse(null);
    }

    /** Writes a {@code return_status} row, for the return ({@code returnItemSeqId} null) or one item. */
    private void writeHistory(String returnId, String returnItemSeqId, String statusId, String reason, String userId) {
        ReturnStatus history = new ReturnStatus();
        history.setReturnStatusId(sequenceService.nextId("ReturnStatus"));
        history.setReturnId(returnId);
        history.setReturnItemSeqId(returnItemSeqId);
        history.setStatusId(statusId);
        history.setStatusDatetime(CoreClock.now());
        history.setChangeByUserLoginId(userId);
        history.setChangeReason(reason);
        entityManager.persist(history);
    }

    private ReturnItem loadItem(String returnId, String returnItemSeqId) {
        ReturnItem returnItem = entityManager.find(ReturnItem.class, new ReturnItem.Key(returnId, returnItemSeqId));
        if (returnItem == null) {
            throw new IllegalArgumentException("Return " + returnId + " has no item " + returnItemSeqId);
        }
        return returnItem;
    }

    /** The return header, locked until the transaction ends. */
    private ReturnHeader loadHeaderForUpdate(String returnId) {
        ReturnHeader header = entityManager.find(ReturnHeader.class, returnId, LockModeType.PESSIMISTIC_WRITE);
        if (header == null) {
            throw new IllegalArgumentException("Unknown return " + returnId);
        }
        return header;
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
