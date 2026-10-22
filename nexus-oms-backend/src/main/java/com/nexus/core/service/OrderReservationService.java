package com.nexus.core.service;

import com.nexus.core.CoreStatus;
import com.nexus.core.model.inventory.InventoryItem;
import com.nexus.core.model.order.OrderItem;
import com.nexus.core.model.order.OrderItemShipGrpInvRes;
import com.nexus.core.model.routing.ExcludedOrderFacility;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import com.nexus.core.CoreTransactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Stock held for order items (OFBiz {@code order_item_ship_grp_inv_res}).
 * <p>
 * A reservation says "this order item holds N units of this inventory item". Making one lowers the inventory item's
 * available-to-promise through {@link InventoryService}; releasing one gives the units back; shipping consumes it
 * (the shipment service then lowers on hand). When a location cannot find stock it held, the hold there is released
 * and the location is excluded for that item, so routing never sends the item back to it.
 * <p>
 * This service reads order items directly and never calls {@link OrderService}, so the two do not depend on each other
 * in a circle: the order service uses this one, not the other way round.
 */
@Service("coreOrderReservationService")
@CoreTransactional
public class OrderReservationService {

    /** The reservation order OFBiz uses when nothing else is said: first received, first out. */
    static final String DEFAULT_RESERVE_ORDER = "INVRO_FIFO_REC";

    private final EntityManager entityManager;
    private final InventoryService inventoryService;

    public OrderReservationService(EntityManager entityManager, InventoryService inventoryService) {
        this.entityManager = entityManager;
        this.inventoryService = inventoryService;
    }

    /** One hold of an item: which inventory item, and how many units. */
    public record Held(String inventoryItemId, BigDecimal quantity) {
    }

    // ── making and releasing holds ──

    /**
     * Holds stock for an order item at a facility. With a null quantity, holds everything the item still needs
     * (its open quantity less what it already holds).
     *
     * @throws IllegalStateException    when the item is not open or has no product
     * @throws IllegalArgumentException when asked to hold more than the item still needs
     * @throws InventoryService.InsufficientStockException when the facility does not have that many available
     */
    public void reserve(String orderId, String orderItemSeqId, String facilityId, BigDecimal quantity, String userId) {
        OrderItem orderItem = loadItem(orderId, orderItemSeqId);
        if (!CoreStatus.Item.OPEN.contains(orderItem.getStatusId())) {
            throw new IllegalStateException("Item " + orderItemSeqId + " is " + orderItem.getStatusId() + "; only open items take stock");
        }
        if (orderItem.getProductId() == null) {
            throw new IllegalStateException("Item " + orderItemSeqId + " has no product to reserve");
        }

        BigDecimal stillNeeded = OrderService.openQuantity(orderItem).subtract(reservedQuantity(orderId, orderItemSeqId));
        BigDecimal quantityToHold = quantity == null ? stillNeeded : quantity;
        if (quantityToHold.signum() <= 0 || quantityToHold.compareTo(stillNeeded) > 0) {
            throw new IllegalArgumentException("Item " + orderItemSeqId + " has " + stillNeeded.stripTrailingZeros().toPlainString() + " left to reserve");
        }

        // Lower available-to-promise first (this checks there is enough), then record which inventory item holds it.
        InventoryService.Cause cause = InventoryService.Cause.order(orderId, orderItemSeqId).byUser(userId);
        inventoryService.reserve(orderItem.getProductId(), facilityId, quantityToHold, cause);
        String inventoryItemId = inventoryService.bulkItem(orderItem.getProductId(), facilityId).getInventoryItemId();
        recordReservation(orderId, orderItemSeqId, inventoryItemId, quantityToHold);
    }

    /**
     * Records a hold without changing stock. Used when the stock was already taken out of available elsewhere (a copy
     * from another system that kept the hold in its own totals), and by {@link #reserve} after it lowered the stock.
     */
    public void recordReservation(String orderId, String orderItemSeqId, String inventoryItemId, BigDecimal quantity) {
        OrderItem orderItem = loadItem(orderId, orderItemSeqId);
        OrderItemShipGrpInvRes.Key key = new OrderItemShipGrpInvRes.Key(orderId, orderItem.getShipGroupSeqId(), orderItemSeqId, inventoryItemId);
        OrderItemShipGrpInvRes reservation = entityManager.find(OrderItemShipGrpInvRes.class, key);
        LocalDateTime now = CoreClock.now();

        if (reservation == null) {
            reservation = new OrderItemShipGrpInvRes();
            reservation.setOrderId(orderId);
            reservation.setShipGroupSeqId(orderItem.getShipGroupSeqId());
            reservation.setOrderItemSeqId(orderItemSeqId);
            reservation.setInventoryItemId(inventoryItemId);
            reservation.setReserveOrderEnumId(DEFAULT_RESERVE_ORDER);
            reservation.setQuantity(quantity);
            reservation.setQuantityNotAvailable(BigDecimal.ZERO);
            reservation.setCreatedDatetime(now);
            reservation.setReservedDatetime(now);
            entityManager.persist(reservation);
        } else {
            // The item already holds stock at this inventory item: add to the same hold.
            reservation.setQuantity(reservation.getQuantity().add(quantity));
            reservation.setReservedDatetime(now);
        }
    }

    /** Gives back everything an item holds, at every facility. */
    public void releaseAll(String orderId, String orderItemSeqId, String userId) {
        BigDecimal held = reservedQuantity(orderId, orderItemSeqId);
        if (held.signum() > 0) {
            release(orderId, orderItemSeqId, held, userId);
        }
    }

    /**
     * Gives back {@code quantity} of what an item holds, newest hold first. Each released unit goes back to
     * available-to-promise at the facility that held it.
     */
    public void release(String orderId, String orderItemSeqId, BigDecimal quantity, String userId) {
        BigDecimal stillToRelease = quantity;
        for (OrderItemShipGrpInvRes reservation : reservationsOf(orderId, orderItemSeqId)) {
            if (stillToRelease.signum() <= 0) {
                break;
            }
            BigDecimal releasedHere = reservation.getQuantity().min(stillToRelease);
            InventoryItem inventoryItem = entityManager.find(InventoryItem.class, reservation.getInventoryItemId());
            inventoryService.release(inventoryItem.getProductId(), inventoryItem.getFacilityId(), releasedHere,
                    InventoryService.Cause.order(orderId, orderItemSeqId).byUser(userId));
            shrinkOrRemove(reservation, releasedHere);
            stillToRelease = stillToRelease.subtract(releasedHere);
        }
    }

    /**
     * Takes shipped units off one hold. The stock itself (on hand) is lowered by the caller when it issues the units;
     * this only removes the hold, so the units are not released back to available.
     */
    public void consume(String orderId, String orderItemSeqId, String inventoryItemId, BigDecimal quantity) {
        OrderItem orderItem = loadItem(orderId, orderItemSeqId);
        OrderItemShipGrpInvRes reservation = entityManager.find(OrderItemShipGrpInvRes.class,
                new OrderItemShipGrpInvRes.Key(orderId, orderItem.getShipGroupSeqId(), orderItemSeqId, inventoryItemId));
        if (reservation == null || reservation.getQuantity().compareTo(quantity) < 0) {
            throw new IllegalStateException("Item " + orderItemSeqId + " of order " + orderId + " holds less than "
                    + quantity.stripTrailingZeros().toPlainString() + " at inventory item " + inventoryItemId);
        }
        shrinkOrRemove(reservation, quantity);
    }

    // ── reading holds ──

    /** How many units an item holds in total. */
    @CoreTransactional(readOnly = true)
    public BigDecimal reservedQuantity(String orderId, String orderItemSeqId) {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemShipGrpInvRes reservation : reservationsOf(orderId, orderItemSeqId)) {
            total = total.add(reservation.getQuantity());
        }
        return total;
    }

    /** An item's holds, newest first. */
    @CoreTransactional(readOnly = true)
    public List<Held> held(String orderId, String orderItemSeqId) {
        return reservationsOf(orderId, orderItemSeqId).stream()
                .map(reservation -> new Held(reservation.getInventoryItemId(), reservation.getQuantity()))
                .toList();
    }

    // ── short picks and excluded facilities ──

    /**
     * A facility could not find stock it held for an item. Its hold there is released for the short quantity (the
     * stock goes back to available, with "Short pick" as the reason, which the store reliability score counts), and
     * the facility is excluded for the item so routing does not send the item back to it.
     */
    public void shortPick(String orderId, String orderItemSeqId, String facilityId, BigDecimal shortQuantity, String reason, String userId) {
        String note = "Short pick" + (reason == null ? "" : ": " + reason);
        BigDecimal stillShort = shortQuantity;
        for (OrderItemShipGrpInvRes reservation : reservationsOf(orderId, orderItemSeqId)) {
            if (stillShort.signum() <= 0) {
                break;
            }
            InventoryItem inventoryItem = entityManager.find(InventoryItem.class, reservation.getInventoryItemId());
            if (!inventoryItem.getFacilityId().equals(facilityId)) {
                continue;   // only the holds at the facility that could not find it
            }
            BigDecimal releasedHere = reservation.getQuantity().min(stillShort);
            inventoryService.release(inventoryItem.getProductId(), facilityId, releasedHere,
                    new InventoryService.Cause(orderId, orderItemSeqId, null, null, userId, note));
            shrinkOrRemove(reservation, releasedHere);
            stillShort = stillShort.subtract(releasedHere);
        }
        excludeFacility(orderId, orderItemSeqId, facilityId);
    }

    /** Routing must not send this item to the facility again (it rejected or short-picked it). Safe to call twice. */
    public void excludeFacility(String orderId, String orderItemSeqId, String facilityId) {
        if (excludedFacilities(orderId, orderItemSeqId).contains(facilityId)) {
            return;
        }
        ExcludedOrderFacility exclusion = new ExcludedOrderFacility();
        exclusion.setOrderId(orderId);
        exclusion.setOrderItemSeqId(orderItemSeqId);
        exclusion.setFacilityId(facilityId);
        exclusion.setFromDate(CoreClock.now());
        entityManager.persist(exclusion);
    }

    /** Facilities routing must not use for this item. */
    @CoreTransactional(readOnly = true)
    public Set<String> excludedFacilities(String orderId, String orderItemSeqId) {
        List<String> facilityIds = entityManager.createQuery(
                        "SELECT x.facilityId FROM ExcludedOrderFacility x "
                                + "WHERE x.orderId = :orderId AND x.orderItemSeqId = :itemSeqId AND x.thruDate IS NULL", String.class)
                .setParameter("orderId", orderId)
                .setParameter("itemSeqId", orderItemSeqId)
                .getResultList();
        return new HashSet<>(facilityIds);
    }

    // ── internals ──

    /** Lowers a hold by the quantity, removing it when nothing is left. */
    private void shrinkOrRemove(OrderItemShipGrpInvRes reservation, BigDecimal quantity) {
        BigDecimal remaining = reservation.getQuantity().subtract(quantity);
        if (remaining.signum() == 0) {
            entityManager.remove(reservation);
        } else {
            reservation.setQuantity(remaining);
        }
    }

    /** An item's holds, newest first, so releases give back the most recent hold first. */
    private List<OrderItemShipGrpInvRes> reservationsOf(String orderId, String orderItemSeqId) {
        return entityManager.createQuery(
                        "SELECT r FROM OrderItemShipGrpInvRes r WHERE r.orderId = :orderId AND r.orderItemSeqId = :itemSeqId "
                                + "ORDER BY r.reservedDatetime DESC", OrderItemShipGrpInvRes.class)
                .setParameter("orderId", orderId)
                .setParameter("itemSeqId", orderItemSeqId)
                .getResultList();
    }

    private OrderItem loadItem(String orderId, String orderItemSeqId) {
        OrderItem orderItem = entityManager.find(OrderItem.class, new OrderItem.Key(orderId, orderItemSeqId));
        if (orderItem == null) {
            throw new IllegalArgumentException("Order " + orderId + " has no item " + orderItemSeqId);
        }
        return orderItem;
    }
}
