package com.nexus.core.service;

import com.nexus.core.model.inventory.InventoryItem;
import com.nexus.core.model.inventory.InventoryItemVariance;
import com.nexus.core.model.inventory.InventoryTransfer;
import com.nexus.core.model.inventory.PhysicalInventory;
import com.nexus.core.model.shipment.Shipment;
import com.nexus.core.model.shipment.ShipmentItem;
import com.nexus.core.model.shipment.ShipmentReceipt;
import com.nexus.core.model.shipment.ShipmentStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stock moving between locations (doc 24, section 4b). A transfer is a shipment of type TRANSFER with one
 * {@code inventory_transfer} per product. Requesting moves nothing; approving holds the stock at the sending
 * location at once (it can no longer be sold there); shipping takes it off the shelf and puts it in the IN_TRANSIT
 * location, so it always has an owner and nobody reorders what is on its way; receiving (by scan, per product) puts
 * what arrived on sale at the receiving location. A difference within the tolerance is settled at once as a transit
 * loss; a bigger one waits for a person to settle it.
 */
@Service("coreTransferService")
@Transactional
public class TransferService {

    public static final String FLOW = StatusService.DEFAULT_FLOW;
    public static final String IN_TRANSIT = "IN_TRANSIT";
    static final String RESOURCE = "nexus.core";

    private final EntityManager em;
    private final SequenceService sequences;
    private final StatusService statuses;
    private final InventoryService inventory;
    private final SystemPropertyService properties;

    public TransferService(EntityManager em, SequenceService sequences, StatusService statuses, InventoryService inventory,
                           SystemPropertyService properties) {
        this.em = em;
        this.sequences = sequences;
        this.statuses = statuses;
        this.inventory = inventory;
        this.properties = properties;
    }

    public record Line(String productId, BigDecimal quantity) {
    }

    /** A transfer request from one location to another. Returns the transfer's shipment id. */
    public String request(String fromFacilityId, String toFacilityId, List<Line> lines, String comments, String userId) {
        if (fromFacilityId.equals(toFacilityId)) throw new IllegalArgumentException("A transfer goes to a different location");
        if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("A transfer needs at least one product");
        Shipment s = new Shipment();
        s.setShipmentId(sequences.nextId("Shipment"));
        s.setShipmentTypeId("TRANSFER");
        s.setStatusId(statuses.initialStatus(FLOW, "SHIPMENT_STATUS"));
        s.setOriginFacilityId(fromFacilityId);
        s.setDestinationFacilityId(toFacilityId);
        s.setHandlingInstructions(comments);
        s.setCreatedDate(CoreClock.now());
        s.setCreatedByUserLogin(userId);
        em.persist(s);
        shipmentStatus(s, s.getStatusId(), userId);
        int n = 1;
        for (Line l : lines) {
            if (l.quantity() == null || l.quantity().signum() <= 0) throw new IllegalArgumentException("A transfer quantity must be more than zero");
            ShipmentItem si = new ShipmentItem();
            si.setShipmentId(s.getShipmentId());
            si.setShipmentItemSeqId(SequenceService.seqId(n++));
            si.setProductId(l.productId());
            si.setQuantity(l.quantity());
            em.persist(si);
            InventoryTransfer t = new InventoryTransfer();
            t.setInventoryTransferId(sequences.nextId("InventoryTransfer"));
            t.setStatusId(statuses.initialStatus(FLOW, "INVENTORY_XFER_STATUS"));
            t.setInventoryItemId(inventory.bulkItem(l.productId(), fromFacilityId).getInventoryItemId());
            t.setFacilityId(fromFacilityId);
            t.setFacilityIdTo(toFacilityId);
            t.setQuantity(l.quantity());
            t.setComments(comments);
            t.setShipmentId(s.getShipmentId());
            t.setShipmentItemSeqId(si.getShipmentItemSeqId());
            em.persist(t);
        }
        return s.getShipmentId();
    }

    /** Approving holds every line at the sending location, so it can't be sold there while it waits to go. */
    public void approve(String shipmentId, String userId) {
        for (InventoryTransfer t : lines(shipmentId)) {
            move(t, "IXF_SCHEDULED");
            inventory.reserve(product(t), t.getFacilityId(), t.getQuantity(), cause(t, userId, "approved"));
        }
    }

    /** Shipping moves the held stock off the sending shelf into the IN_TRANSIT location. */
    public void ship(String shipmentId, String trackingNumber, String userId) {
        Shipment s = shipment(shipmentId);
        for (InventoryTransfer t : lines(shipmentId)) {
            move(t, "IXF_EN_ROUTE");
            String p = product(t);
            inventory.adjust(p, t.getFacilityId(), t.getQuantity().negate(), BigDecimal.ZERO, "IID_TRANSFER_OUT", cause(t, userId, "shipped"));
            // in transit: owned, counted, never for sale
            inventory.adjust(p, IN_TRANSIT, t.getQuantity(), BigDecimal.ZERO, "IID_TRANSFER_OUT", cause(t, userId, "in transit to " + t.getFacilityIdTo()));
            t.setSendDate(CoreClock.now());
        }
        s.setStatusId("SHIPMENT_SHIPPED");
        shipmentStatus(s, "SHIPMENT_SHIPPED", userId);
        if (trackingNumber != null) s.setHandlingInstructions(trimJoin(s.getHandlingInstructions(), "Tracking " + trackingNumber));
    }

    public record Receipt(String productId, BigDecimal shipped, BigDecimal received, BigDecimal difference, String outcome) {
    }

    /**
     * Receives by scan: quantity received per product. What arrived goes on sale at the receiving location. A line
     * received short within the tolerance is settled at once as a transit loss; a bigger difference (or more than was
     * sent) waits in IXF_RECEIVED_SHORT for {@link #settle}. Products not scanned count as zero received.
     */
    public List<Receipt> receive(String shipmentId, Map<String, BigDecimal> scanned, String userId) {
        Shipment s = shipment(shipmentId);
        List<Receipt> out = new ArrayList<>();
        BigDecimal pct = BigDecimal.valueOf(properties.getLong(RESOURCE, "transfer.tolerancePct", 2));
        BigDecimal units = BigDecimal.valueOf(properties.getLong(RESOURCE, "transfer.toleranceUnits", 0));
        Map<String, BigDecimal> left = new LinkedHashMap<>(scanned == null ? Map.of() : scanned);
        for (InventoryTransfer t : lines(shipmentId)) {
            if (!"IXF_EN_ROUTE".equals(t.getStatusId())) throw new IllegalStateException("Transfer " + t.getInventoryTransferId() + " is " + t.getStatusId() + "; ship it first");
            String p = product(t);
            BigDecimal got = left.getOrDefault(p, BigDecimal.ZERO);
            left.remove(p);
            if (got.signum() < 0) throw new IllegalArgumentException("A received quantity can't be negative");
            BigDecimal took = got.min(t.getQuantity());
            inventory.adjust(p, IN_TRANSIT, took.negate(), BigDecimal.ZERO, "IID_TRANSFER_IN", cause(t, userId, "arrived"));
            if (took.signum() > 0) {
                String item = inventory.receive(p, t.getFacilityIdTo(), took, null, null, "IID_TRANSFER_IN", cause(t, userId, "received"));
                receipt(s, t, item, took, BigDecimal.ZERO, userId);
            }
            BigDecimal diff = t.getQuantity().subtract(got);
            t.setReceiveDate(CoreClock.now());
            if (diff.signum() == 0) {
                move(t, "IXF_COMPLETE");
                out.add(new Receipt(p, t.getQuantity(), got, diff, "RECEIVED"));
            } else if (diff.signum() > 0 && diff.compareTo(tolerance(t.getQuantity(), pct, units)) <= 0) {
                writeOff(t, diff, "Short " + diff.stripTrailingZeros().toPlainString() + " within tolerance; settled", userId);
                move(t, "IXF_COMPLETE");
                out.add(new Receipt(p, t.getQuantity(), got, diff, "SETTLED_WITHIN_TOLERANCE"));
            } else {
                move(t, "IXF_RECEIVED_SHORT");
                t.setComments(trimJoin(t.getComments(), "Received " + got.stripTrailingZeros().toPlainString() + " of " + t.getQuantity().stripTrailingZeros().toPlainString()));
                out.add(new Receipt(p, t.getQuantity(), got, diff, diff.signum() > 0 ? "WAITING_FOR_DECISION" : "MORE_THAN_SENT"));
                if (diff.signum() < 0) {
                    // more arrived than was sent: it is here, so it goes on sale; the sender's count is now wrong
                    String item = inventory.receive(p, t.getFacilityIdTo(), diff.negate(), null, null, "IID_TRANSFER_IN", cause(t, userId, "more than sent"));
                    receipt(s, t, item, diff.negate(), BigDecimal.ZERO, userId);
                }
            }
        }
        if (!left.isEmpty()) throw new IllegalArgumentException("Transfer " + shipmentId + " doesn't carry " + left.keySet());
        if (lines(shipmentId).stream().allMatch(t -> "IXF_COMPLETE".equals(t.getStatusId()))) {
            s.setStatusId("SHIPMENT_DELIVERED");
            shipmentStatus(s, "SHIPMENT_DELIVERED", userId);
        }
        return out;
    }

    /**
     * A person decides a difference: {@code found} more units turned up (they go on sale at the receiving location),
     * the rest is written off as lost in transit.
     */
    public void settle(String shipmentId, String productId, BigDecimal found, String note, String userId) {
        InventoryTransfer t = lines(shipmentId).stream().filter(x -> productId.equals(product(x)) && "IXF_RECEIVED_SHORT".equals(x.getStatusId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Transfer " + shipmentId + " has no difference to settle for " + productId));
        BigDecimal received = em.createQuery("SELECT COALESCE(SUM(r.quantityAccepted), 0) FROM ShipmentReceipt r WHERE r.shipmentId = :s AND r.shipmentItemSeqId = :i",
                BigDecimal.class).setParameter("s", shipmentId).setParameter("i", t.getShipmentItemSeqId()).getSingleResult();
        BigDecimal missing = t.getQuantity().subtract(received);
        BigDecimal f = found == null ? BigDecimal.ZERO : found;
        if (f.signum() < 0 || f.compareTo(missing.max(BigDecimal.ZERO)) > 0) {
            throw new IllegalArgumentException("Found must be between 0 and " + missing.max(BigDecimal.ZERO).stripTrailingZeros().toPlainString());
        }
        if (f.signum() > 0) {
            inventory.adjust(productId, IN_TRANSIT, f.negate(), BigDecimal.ZERO, "IID_TRANSFER_IN", cause(t, userId, "found"));
            String item = inventory.receive(productId, t.getFacilityIdTo(), f, null, null, "IID_TRANSFER_IN", cause(t, userId, "found"));
            receipt(shipment(shipmentId), t, item, f, BigDecimal.ZERO, userId);
        }
        BigDecimal lost = missing.subtract(f);
        if (lost.signum() > 0) writeOff(t, lost, note == null ? "Settled by hand" : note, userId);
        move(t, "IXF_COMPLETE");
        if (lines(shipmentId).stream().allMatch(x -> "IXF_COMPLETE".equals(x.getStatusId()))) {
            Shipment s = shipment(shipmentId);
            s.setStatusId("SHIPMENT_DELIVERED");
            shipmentStatus(s, "SHIPMENT_DELIVERED", userId);
        }
    }

    /** Cancels a transfer not yet shipped; approved lines give their held stock back. */
    public void cancel(String shipmentId, String userId) {
        Shipment s = shipment(shipmentId);
        for (InventoryTransfer t : lines(shipmentId)) {
            boolean held = "IXF_SCHEDULED".equals(t.getStatusId());
            move(t, "IXF_CANCELLED");
            if (held) inventory.release(product(t), t.getFacilityId(), t.getQuantity(), cause(t, userId, "cancelled"));
        }
        statuses.requireAllowed(FLOW, s.getStatusId(), "SHIPMENT_CANCELLED");
        s.setStatusId("SHIPMENT_CANCELLED");
        shipmentStatus(s, "SHIPMENT_CANCELLED", userId);
    }

    @Transactional(readOnly = true)
    public List<InventoryTransfer> lines(String shipmentId) {
        return em.createQuery("SELECT t FROM InventoryTransfer t WHERE t.shipmentId = :s ORDER BY t.shipmentItemSeqId", InventoryTransfer.class)
                .setParameter("s", shipmentId).getResultList();
    }

    // ── internals ──

    static BigDecimal tolerance(BigDecimal quantity, BigDecimal pct, BigDecimal units) {
        return quantity.multiply(pct).divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR).max(units);
    }

    private void writeOff(InventoryTransfer t, BigDecimal lost, String note, String userId) {
        String p = product(t);
        inventory.adjust(p, IN_TRANSIT, lost.negate(), BigDecimal.ZERO, "IID_COUNT", cause(t, userId, "lost in transit: " + note));
        PhysicalInventory pi = new PhysicalInventory();
        pi.setPhysicalInventoryId(sequences.nextId("PhysicalInventory"));
        pi.setPhysicalInventoryDate(CoreClock.now());
        pi.setGeneralComments("Transfer " + t.getInventoryTransferId() + ": " + note);
        em.persist(pi);
        InventoryItemVariance v = new InventoryItemVariance();
        v.setInventoryItemId(inventory.bulkItem(p, IN_TRANSIT).getInventoryItemId());
        v.setPhysicalInventoryId(pi.getPhysicalInventoryId());
        v.setVarianceReasonId("VAR_TRANSIT_LOSS");
        v.setQuantityOnHandVar(lost.negate());
        v.setAvailableToPromiseVar(BigDecimal.ZERO);
        v.setComments(note);
        em.persist(v);
    }

    private void receipt(Shipment s, InventoryTransfer t, String inventoryItemId, BigDecimal accepted, BigDecimal rejected, String userId) {
        ShipmentReceipt r = new ShipmentReceipt();
        r.setReceiptId(sequences.nextId("ShipmentReceipt"));
        r.setInventoryItemId(inventoryItemId);
        r.setProductId(product(t));
        r.setShipmentId(s.getShipmentId());
        r.setShipmentItemSeqId(t.getShipmentItemSeqId());
        r.setDatetimeReceived(CoreClock.now());
        r.setReceivedByUserLogin(userId);
        r.setQuantityAccepted(accepted);
        r.setQuantityRejected(rejected);
        em.persist(r);
    }

    private void move(InventoryTransfer t, String to) {
        statuses.requireAllowed(FLOW, t.getStatusId(), to);
        t.setStatusId(to);
    }

    private String product(InventoryTransfer t) {
        return em.find(InventoryItem.class, t.getInventoryItemId()).getProductId();
    }

    private static InventoryService.Cause cause(InventoryTransfer t, String userId, String what) {
        return new InventoryService.Cause(null, null, t.getShipmentId(), null, userId, "Transfer " + t.getInventoryTransferId() + " " + what);
    }

    private void shipmentStatus(Shipment s, String statusId, String userId) {
        ShipmentStatus st = new ShipmentStatus();
        st.setShipmentId(s.getShipmentId());
        st.setStatusId(statusId);
        st.setStatusDate(CoreClock.now());
        st.setChangeByUserLoginId(userId);
        em.persist(st);
    }

    private Shipment shipment(String shipmentId) {
        Shipment s = em.find(Shipment.class, shipmentId, LockModeType.PESSIMISTIC_WRITE);
        if (s == null || !"TRANSFER".equals(s.getShipmentTypeId())) throw new IllegalArgumentException("Unknown transfer " + shipmentId);
        return s;
    }

    private static String trimJoin(String a, String b) {
        String v = a == null || a.isBlank() ? b : a + "; " + b;
        return v.length() > 4000 ? v.substring(0, 4000) : v;
    }
}
