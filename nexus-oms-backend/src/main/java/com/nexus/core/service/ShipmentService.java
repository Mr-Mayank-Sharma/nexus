package com.nexus.core.service;

import com.nexus.core.model.inventory.InventoryItem;
import com.nexus.core.model.order.OrderItem;
import com.nexus.core.model.order.OrderItemShipGroup;
import com.nexus.core.model.shipment.ItemIssuance;
import com.nexus.core.model.shipment.OrderShipment;
import com.nexus.core.model.shipment.Shipment;
import com.nexus.core.model.shipment.ShipmentItem;
import com.nexus.core.model.shipment.ShipmentPackage;
import com.nexus.core.model.shipment.ShipmentPackageContent;
import com.nexus.core.model.shipment.ShipmentPackageRouteSeg;
import com.nexus.core.model.shipment.ShipmentRouteSegment;
import com.nexus.core.model.shipment.ShipmentStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Outgoing shipments in the OFBiz way: a shipment for an order ship group, its items (linked to order items through
 * {@code order_shipment}), boxes with their contents, a route segment with the carrier and tracking, and an
 * {@code item_issuance} for the stock each order item took when it shipped. Shipping consumes the item's holds,
 * lowers on hand, sets the tracking on the ship group and completes items (and the order) that are fully shipped.
 * {@link #confirm} takes the WMS shipment confirmation (doc 27, section 3.4) in one call.
 */
@Service("coreShipmentService")
@Transactional
public class ShipmentService {

    public static final String FLOW = StatusService.DEFAULT_FLOW;

    private final EntityManager em;
    private final SequenceService sequences;
    private final StatusService statuses;
    private final OrderService orders;
    private final InventoryService inventory;

    public ShipmentService(EntityManager em, SequenceService sequences, StatusService statuses, OrderService orders, InventoryService inventory) {
        this.em = em;
        this.sequences = sequences;
        this.statuses = statuses;
        this.orders = orders;
        this.inventory = inventory;
    }

    /**
     * A shipment for a ship group from its facility, for the given quantity per order item ({@code null} for everything
     * still open and not yet in a shipment).
     */
    public String createSalesShipment(String orderId, String shipGroupSeqId, Map<String, BigDecimal> quantities, String userId) {
        OrderItemShipGroup g = orders.shipGroup(orderId, shipGroupSeqId);
        if (g.getFacilityId() == null) throw new IllegalStateException("Ship group " + shipGroupSeqId + " of order " + orderId + " has no facility yet; route it first");
        Map<String, BigDecimal> wanted = new LinkedHashMap<>();
        for (OrderItem i : orders.items(orderId)) {
            if (!shipGroupSeqId.equals(i.getShipGroupSeqId())) continue;
            BigDecimal left = OrderService.openQuantity(i).subtract(inShipments(orderId, i.getOrderItemSeqId()));
            BigDecimal q = quantities == null ? left : quantities.getOrDefault(i.getOrderItemSeqId(), BigDecimal.ZERO);
            if (q.signum() == 0) continue;
            if (!"ITEM_APPROVED".equals(i.getStatusId())) throw new IllegalStateException("Item " + i.getOrderItemSeqId() + " is " + i.getStatusId() + "; only approved items ship");
            if (q.signum() < 0 || q.compareTo(left) > 0) {
                throw new IllegalArgumentException("Item " + i.getOrderItemSeqId() + " has " + left.stripTrailingZeros().toPlainString() + " left to ship");
            }
            wanted.put(i.getOrderItemSeqId(), q);
        }
        if (quantities != null) {
            for (String seq : quantities.keySet()) {
                if (!wanted.containsKey(seq) && quantities.get(seq).signum() != 0) throw new IllegalArgumentException("Ship group " + shipGroupSeqId + " has no item " + seq);
            }
        }
        if (wanted.isEmpty()) throw new IllegalStateException("Nothing left to ship in ship group " + shipGroupSeqId + " of order " + orderId);

        Shipment s = new Shipment();
        s.setShipmentId(sequences.nextId("Shipment"));
        s.setShipmentTypeId("SALES_SHIPMENT");
        s.setStatusId(statuses.initialStatus(FLOW, "SHIPMENT_STATUS"));
        s.setPrimaryOrderId(orderId);
        s.setPrimaryShipGroupSeqId(shipGroupSeqId);
        s.setOriginFacilityId(g.getFacilityId());
        s.setDestinationContactMechId(g.getContactMechId());
        s.setPartyIdTo(orders.partyInRole(orderId, "SHIP_TO_CUSTOMER").orElse(null));
        s.setEstimatedArrivalDate(g.getEstimatedDeliveryDate());
        s.setCreatedDate(CoreClock.now());
        s.setCreatedByUserLogin(userId);
        em.persist(s);
        status(s, s.getStatusId(), userId);
        int n = 1;
        for (Map.Entry<String, BigDecimal> e : wanted.entrySet()) {
            OrderItem i = orders.orderItem(orderId, e.getKey());
            ShipmentItem si = new ShipmentItem();
            si.setShipmentId(s.getShipmentId());
            si.setShipmentItemSeqId(SequenceService.seqId(n++));
            si.setProductId(i.getProductId());
            si.setQuantity(e.getValue());
            em.persist(si);
            OrderShipment os = new OrderShipment();
            os.setOrderId(orderId);
            os.setOrderItemSeqId(e.getKey());
            os.setShipGroupSeqId(shipGroupSeqId);
            os.setShipmentId(s.getShipmentId());
            os.setShipmentItemSeqId(si.getShipmentItemSeqId());
            os.setQuantity(e.getValue());
            em.persist(os);
        }
        return s.getShipmentId();
    }

    /** A box with its contents: quantity per shipment item. Returns the package sequence id. */
    public String addPackage(String shipmentId, String boxTypeId, BigDecimal weight, String weightUomId, Map<String, BigDecimal> contents) {
        Shipment s = shipment(shipmentId, true);
        if (!List.of("SHIPMENT_INPUT", "SHIPMENT_PICKED", "SHIPMENT_SCHEDULED").contains(s.getStatusId())) {
            throw new IllegalStateException("Shipment " + shipmentId + " is " + s.getStatusId() + "; boxes are added before packing");
        }
        long count = em.createQuery("SELECT COUNT(p) FROM ShipmentPackage p WHERE p.shipmentId = :s", Long.class).setParameter("s", shipmentId).getSingleResult();
        ShipmentPackage p = new ShipmentPackage();
        p.setShipmentId(shipmentId);
        p.setShipmentPackageSeqId(SequenceService.seqId((int) count + 1));
        p.setShipmentBoxTypeId(boxTypeId);
        p.setWeight(weight);
        p.setWeightUomId(weight == null ? null : (weightUomId == null ? "WT_kg" : weightUomId));
        p.setDateCreated(CoreClock.now());
        em.persist(p);
        for (Map.Entry<String, BigDecimal> c : contents.entrySet()) {
            ShipmentItem si = em.find(ShipmentItem.class, new ShipmentItem.Key(shipmentId, c.getKey()));
            if (si == null) throw new IllegalArgumentException("Shipment " + shipmentId + " has no item " + c.getKey());
            BigDecimal packed = packedQuantity(shipmentId, c.getKey());
            if (c.getValue().signum() <= 0 || packed.add(c.getValue()).compareTo(si.getQuantity()) > 0) {
                throw new IllegalArgumentException("Shipment item " + c.getKey() + " has " + si.getQuantity().subtract(packed).stripTrailingZeros().toPlainString() + " left to pack");
            }
            ShipmentPackageContent pc = new ShipmentPackageContent();
            pc.setShipmentId(shipmentId);
            pc.setShipmentPackageSeqId(p.getShipmentPackageSeqId());
            pc.setShipmentItemSeqId(c.getKey());
            pc.setQuantity(c.getValue());
            em.persist(pc);
        }
        return p.getShipmentPackageSeqId();
    }

    /** Packs the shipment; with no boxes yet, everything goes in one. */
    public void pack(String shipmentId, String userId) {
        Shipment s = shipment(shipmentId, true);
        statuses.requireAllowed(FLOW, s.getStatusId(), "SHIPMENT_PACKED");
        List<ShipmentItem> items = shipmentItems(shipmentId);
        Map<String, BigDecimal> unpacked = new LinkedHashMap<>();
        for (ShipmentItem si : items) {
            BigDecimal left = si.getQuantity().subtract(packedQuantity(shipmentId, si.getShipmentItemSeqId()));
            if (left.signum() > 0) unpacked.put(si.getShipmentItemSeqId(), left);
        }
        boolean anyBox = em.createQuery("SELECT COUNT(p) FROM ShipmentPackage p WHERE p.shipmentId = :s", Long.class).setParameter("s", shipmentId).getSingleResult() > 0;
        if (!unpacked.isEmpty()) {
            if (anyBox) throw new IllegalStateException("Shipment " + shipmentId + " has items not in a box: " + unpacked.keySet());
            addPackage(shipmentId, "YOURPACKNG", null, null, unpacked);
        }
        s.setStatusId("SHIPMENT_PACKED");
        status(s, "SHIPMENT_PACKED", userId);
    }

    /**
     * Ships it with a carrier and service; {@code trackingByPackage} gives each box its tracking code. Each order item
     * takes its stock from its holds at the shipping facility, then from available stock there; on hand goes down,
     * holds are consumed, and fully shipped items and orders are completed.
     */
    public void ship(String shipmentId, String carrierPartyId, String shipmentMethodTypeId, Map<String, String> trackingByPackage,
                     LocalDateTime shippedAt, String userId) {
        Shipment s = shipment(shipmentId, true);
        if ("SHIPMENT_INPUT".equals(s.getStatusId()) || "SHIPMENT_PICKED".equals(s.getStatusId()) || "SHIPMENT_SCHEDULED".equals(s.getStatusId())) pack(shipmentId, userId);
        statuses.requireAllowed(FLOW, s.getStatusId(), "SHIPMENT_SHIPPED");
        LocalDateTime when = shippedAt == null ? CoreClock.now() : shippedAt;

        // a label made before shipping already created the segment and the boxes' tracking; keep them
        ShipmentRouteSegment seg = em.find(ShipmentRouteSegment.class, new ShipmentRouteSegment.Key(shipmentId, SequenceService.seqId(1)));
        boolean labelled = seg != null;
        if (!labelled) {
            seg = new ShipmentRouteSegment();
            seg.setShipmentId(shipmentId);
            seg.setShipmentRouteSegmentId(SequenceService.seqId(1));
        }
        if (carrierPartyId != null) seg.setCarrierPartyId(carrierPartyId);
        if (shipmentMethodTypeId != null) seg.setShipmentMethodTypeId(shipmentMethodTypeId);
        seg.setOriginFacilityId(s.getOriginFacilityId());
        seg.setActualStartDate(when);
        String firstTracking = seg.getTrackingIdNumber();
        List<ShipmentPackage> boxes = em.createQuery("SELECT p FROM ShipmentPackage p WHERE p.shipmentId = :s ORDER BY p.shipmentPackageSeqId", ShipmentPackage.class)
                .setParameter("s", shipmentId).getResultList();
        for (ShipmentPackage p : boxes) {
            String code = trackingByPackage == null ? null : trackingByPackage.get(p.getShipmentPackageSeqId());
            ShipmentPackageRouteSeg pr = em.find(ShipmentPackageRouteSeg.class, new ShipmentPackageRouteSeg.Key(shipmentId, p.getShipmentPackageSeqId(), seg.getShipmentRouteSegmentId()));
            if (pr == null) {
                pr = new ShipmentPackageRouteSeg();
                pr.setShipmentId(shipmentId);
                pr.setShipmentPackageSeqId(p.getShipmentPackageSeqId());
                pr.setShipmentRouteSegmentId(seg.getShipmentRouteSegmentId());
                em.persist(pr);
            }
            if (code != null) pr.setTrackingCode(code);
            if (firstTracking == null) firstTracking = pr.getTrackingCode();
        }
        seg.setTrackingIdNumber(firstTracking);
        if (!labelled) em.persist(seg);

        for (OrderShipment os : orderShipments(shipmentId)) issue(s, os, when, userId);
        s.setStatusId("SHIPMENT_SHIPPED");
        status(s, "SHIPMENT_SHIPPED", userId);
        if (firstTracking != null) orders.setTracking(s.getPrimaryOrderId(), s.getPrimaryShipGroupSeqId(), firstTracking);
        for (OrderShipment os : orderShipments(shipmentId)) {
            orders.shipped(os.getOrderId(), os.getOrderItemSeqId(), issuedQuantity(os.getOrderId(), os.getOrderItemSeqId()), userId);
        }
    }

    public record PackageLabel(String shipmentPackageSeqId, String trackingCode, String labelUrl) {
    }

    /**
     * Records a carrier label made for a packed shipment (by the gateway): the carrier and service, each box's tracking
     * code and label link. Shipping later keeps them. Packs the shipment first if it is not packed yet.
     */
    public void recordLabels(String shipmentId, String carrierPartyId, String shipmentMethodTypeId, List<PackageLabel> labels, java.math.BigDecimal cost,
                             String currencyUomId, String userId) {
        Shipment s = shipment(shipmentId, true);
        if (!"SHIPMENT_PACKED".equals(s.getStatusId())) pack(shipmentId, userId);
        ShipmentRouteSegment seg = em.find(ShipmentRouteSegment.class, new ShipmentRouteSegment.Key(shipmentId, SequenceService.seqId(1)));
        if (seg == null) {
            seg = new ShipmentRouteSegment();
            seg.setShipmentId(shipmentId);
            seg.setShipmentRouteSegmentId(SequenceService.seqId(1));
            seg.setOriginFacilityId(s.getOriginFacilityId());
            em.persist(seg);
        }
        seg.setCarrierPartyId(carrierPartyId);
        seg.setShipmentMethodTypeId(shipmentMethodTypeId);
        seg.setActualCost(cost);
        seg.setCurrencyUomId(cost == null ? null : currencyUomId);
        for (PackageLabel l : labels) {
            ShipmentPackageRouteSeg pr = em.find(ShipmentPackageRouteSeg.class, new ShipmentPackageRouteSeg.Key(shipmentId, l.shipmentPackageSeqId(), seg.getShipmentRouteSegmentId()));
            if (pr == null) {
                pr = new ShipmentPackageRouteSeg();
                pr.setShipmentId(shipmentId);
                pr.setShipmentPackageSeqId(l.shipmentPackageSeqId());
                pr.setShipmentRouteSegmentId(seg.getShipmentRouteSegmentId());
                em.persist(pr);
            }
            pr.setTrackingCode(l.trackingCode());
            pr.setLabelUrl(l.labelUrl());
        }
        if (!labels.isEmpty()) seg.setTrackingIdNumber(labels.get(0).trackingCode());
    }

    /** Boxes of a shipment, in order, with their weight. */
    @Transactional(readOnly = true)
    public List<ShipmentPackage> packages(String shipmentId) {
        return em.createQuery("SELECT p FROM ShipmentPackage p WHERE p.shipmentId = :s ORDER BY p.shipmentPackageSeqId", ShipmentPackage.class)
                .setParameter("s", shipmentId).getResultList();
    }

    @Transactional(readOnly = true)
    public Shipment get(String shipmentId) {
        return shipment(shipmentId, false);
    }

    public void deliver(String shipmentId, LocalDateTime at, String userId) {
        Shipment s = shipment(shipmentId, true);
        statuses.requireAllowed(FLOW, s.getStatusId(), "SHIPMENT_DELIVERED");
        s.setStatusId("SHIPMENT_DELIVERED");
        status(s, "SHIPMENT_DELIVERED", userId);
        em.createQuery("SELECT r FROM ShipmentRouteSegment r WHERE r.shipmentId = :s", ShipmentRouteSegment.class).setParameter("s", shipmentId)
                .getResultList().forEach(r -> r.setActualArrivalDate(at == null ? CoreClock.now() : at));
    }

    /** Cancels a shipment not yet shipped; its items can go in another shipment. Holds stay with the order. */
    public void cancel(String shipmentId, String userId) {
        Shipment s = shipment(shipmentId, true);
        statuses.requireAllowed(FLOW, s.getStatusId(), "SHIPMENT_CANCELLED");
        s.setStatusId("SHIPMENT_CANCELLED");
        status(s, "SHIPMENT_CANCELLED", userId);
    }

    // ── the WMS shipment confirmation (doc 27, 3.4) ──

    public record ConfirmedPackage(String trackingCode, BigDecimal weight, String weightUomId, String boxTypeId, Map<String, BigDecimal> contents) {
    }

    public record Confirmation(String externalShipmentId, String orderId, String shipGroupSeqId, LocalDateTime shippedAt, String carrierPartyId,
                               String shipmentMethodTypeId, List<ConfirmedPackage> packages) {
    }

    /**
     * Records a shipment another system made (a WMS, a carrier integration): creates it for what the boxes hold,
     * packs and ships it. Sending the same confirmation again changes nothing and returns the same shipment.
     */
    public String confirm(Confirmation c, String userId) {
        if (c.externalShipmentId() != null) {
            Optional<String> existing = em.createQuery("SELECT s.shipmentId FROM Shipment s WHERE s.externalId = :e AND s.primaryOrderId = :o", String.class)
                    .setParameter("e", c.externalShipmentId()).setParameter("o", c.orderId()).getResultList().stream().findFirst();
            if (existing.isPresent()) return existing.get();
        }
        if (c.packages() == null || c.packages().isEmpty()) throw new IllegalArgumentException("A shipment confirmation needs at least one package");
        Map<String, BigDecimal> perItem = new LinkedHashMap<>();
        c.packages().forEach(p -> p.contents().forEach((seq, q) -> perItem.merge(seq, q, BigDecimal::add)));
        String shipmentId = createSalesShipment(c.orderId(), c.shipGroupSeqId(), perItem, userId);
        shipment(shipmentId, false).setExternalId(c.externalShipmentId());
        Map<String, String> orderToShipmentItem = new LinkedHashMap<>();
        orderShipments(shipmentId).forEach(os -> orderToShipmentItem.put(os.getOrderItemSeqId(), os.getShipmentItemSeqId()));
        Map<String, String> tracking = new LinkedHashMap<>();
        for (ConfirmedPackage p : c.packages()) {
            Map<String, BigDecimal> contents = new LinkedHashMap<>();
            p.contents().forEach((seq, q) -> contents.put(orderToShipmentItem.get(seq), q));
            String pkg = addPackage(shipmentId, p.boxTypeId(), p.weight(), p.weightUomId(), contents);
            tracking.put(pkg, p.trackingCode());
        }
        ship(shipmentId, c.carrierPartyId(), c.shipmentMethodTypeId(), tracking, c.shippedAt(), userId);
        return shipmentId;
    }

    // ── reads ──

    /** Shipped quantity of an order item, over all its shipments. */
    @Transactional(readOnly = true)
    public BigDecimal issuedQuantity(String orderId, String orderItemSeqId) {
        BigDecimal v = em.createQuery("SELECT SUM(i.quantity - COALESCE(i.cancelQuantity, 0)) FROM ItemIssuance i WHERE i.orderId = :o AND i.orderItemSeqId = :s", BigDecimal.class)
                .setParameter("o", orderId).setParameter("s", orderItemSeqId).getSingleResult();
        return v == null ? BigDecimal.ZERO : v;
    }

    @Transactional(readOnly = true)
    public String status(String shipmentId) {
        return shipment(shipmentId, false).getStatusId();
    }

    // ── internals ──

    private void issue(Shipment s, OrderShipment os, LocalDateTime when, String userId) {
        OrderItem item = orders.orderItem(os.getOrderId(), os.getOrderItemSeqId());
        BigDecimal left = os.getQuantity();
        InventoryService.Cause cause = new InventoryService.Cause(os.getOrderId(), os.getOrderItemSeqId(), s.getShipmentId(), null, userId, null);
        for (OrderService.Held h : orders.held(os.getOrderId(), os.getOrderItemSeqId())) {
            if (left.signum() <= 0) break;
            InventoryItem inv = em.find(InventoryItem.class, h.inventoryItemId());
            if (!inv.getFacilityId().equals(s.getOriginFacilityId())) continue;
            BigDecimal take = h.quantity().min(left);
            orders.consumeReservation(os.getOrderId(), os.getOrderItemSeqId(), h.inventoryItemId(), take);
            inventory.ship(inv.getProductId(), inv.getFacilityId(), take, cause);
            issuance(os, h.inventoryItemId(), take, when, userId);
            left = left.subtract(take);
        }
        if (left.signum() > 0) {
            if (item.getProductId() == null) {
                issuance(os, null, left, when, userId);   // a line without a product (gift wrap, a copied line): nothing to take from stock
                return;
            }
            // not held: take it from what is available at the facility now
            inventory.reserve(item.getProductId(), s.getOriginFacilityId(), left, cause);
            inventory.ship(item.getProductId(), s.getOriginFacilityId(), left, cause);
            issuance(os, inventory.bulkItem(item.getProductId(), s.getOriginFacilityId()).getInventoryItemId(), left, when, userId);
        }
    }

    private void issuance(OrderShipment os, String inventoryItemId, BigDecimal quantity, LocalDateTime when, String userId) {
        ItemIssuance ii = new ItemIssuance();
        ii.setItemIssuanceId(sequences.nextId("ItemIssuance"));
        ii.setOrderId(os.getOrderId());
        ii.setOrderItemSeqId(os.getOrderItemSeqId());
        ii.setShipGroupSeqId(os.getShipGroupSeqId());
        ii.setInventoryItemId(inventoryItemId);
        ii.setShipmentId(os.getShipmentId());
        ii.setShipmentItemSeqId(os.getShipmentItemSeqId());
        ii.setIssuedDateTime(when);
        ii.setIssuedByUserLogin(userId);
        ii.setQuantity(quantity);
        ii.setCancelQuantity(BigDecimal.ZERO);
        em.persist(ii);
    }

    /** Quantity of an order item in shipments that are not cancelled. */
    private BigDecimal inShipments(String orderId, String orderItemSeqId) {
        BigDecimal v = em.createQuery("SELECT SUM(os.quantity) FROM OrderShipment os, Shipment s WHERE s.shipmentId = os.shipmentId "
                        + "AND os.orderId = :o AND os.orderItemSeqId = :i AND s.statusId <> 'SHIPMENT_CANCELLED'", BigDecimal.class)
                .setParameter("o", orderId).setParameter("i", orderItemSeqId).getSingleResult();
        return v == null ? BigDecimal.ZERO : v;
    }

    private BigDecimal packedQuantity(String shipmentId, String shipmentItemSeqId) {
        BigDecimal v = em.createQuery("SELECT SUM(c.quantity) FROM ShipmentPackageContent c WHERE c.shipmentId = :s AND c.shipmentItemSeqId = :i", BigDecimal.class)
                .setParameter("s", shipmentId).setParameter("i", shipmentItemSeqId).getSingleResult();
        return v == null ? BigDecimal.ZERO : v;
    }

    private List<ShipmentItem> shipmentItems(String shipmentId) {
        return em.createQuery("SELECT i FROM ShipmentItem i WHERE i.shipmentId = :s ORDER BY i.shipmentItemSeqId", ShipmentItem.class)
                .setParameter("s", shipmentId).getResultList();
    }

    private List<OrderShipment> orderShipments(String shipmentId) {
        return em.createQuery("SELECT o FROM OrderShipment o WHERE o.shipmentId = :s ORDER BY o.shipmentItemSeqId", OrderShipment.class)
                .setParameter("s", shipmentId).getResultList();
    }

    private void status(Shipment s, String statusId, String userId) {
        ShipmentStatus st = new ShipmentStatus();
        st.setShipmentId(s.getShipmentId());
        st.setStatusId(statusId);
        st.setStatusDate(CoreClock.now());
        st.setChangeByUserLoginId(userId);
        em.persist(st);
    }

    private Shipment shipment(String shipmentId, boolean lock) {
        Shipment s = lock ? em.find(Shipment.class, shipmentId, LockModeType.PESSIMISTIC_WRITE) : em.find(Shipment.class, shipmentId);
        if (s == null) throw new IllegalArgumentException("Unknown shipment " + shipmentId);
        return s;
    }
}
