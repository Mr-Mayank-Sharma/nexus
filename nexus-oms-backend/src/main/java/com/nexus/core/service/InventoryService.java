package com.nexus.core.service;

import com.nexus.core.model.inventory.InventoryItem;
import com.nexus.core.model.inventory.InventoryItemDetail;
import com.nexus.core.model.inventory.InventoryItemVariance;
import com.nexus.core.model.inventory.InventoryTransfer;
import com.nexus.core.model.inventory.Lot;
import com.nexus.core.model.inventory.PhysicalInventory;
import com.nexus.core.model.inventory.ProductFacility;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stock in the OFBiz way: an {@code InventoryItem} holds the totals, and every change is an {@code InventoryItemDetail}
 * row saying how on hand (QOH) and available to promise (ATP) moved, why, and for which order, shipment or count.
 * Reserving for an order lowers ATP only; shipping then lowers QOH. Bulk stock is one item per product and facility.
 * {@code ProductFacility.lastInventoryCount} follows ATP so routing can read stock per facility in one row.
 */
@Service("coreInventoryService")
@Transactional
public class InventoryService {

    public static final String BULK = "NON_SERIAL_INV_ITEM";
    public static final String TRANSFER_FLOW = StatusService.DEFAULT_FLOW;

    private final EntityManager em;
    private final SequenceService sequences;
    private final StatusService statuses;

    public InventoryService(EntityManager em, SequenceService sequences, StatusService statuses) {
        this.em = em;
        this.sequences = sequences;
        this.statuses = statuses;
    }

    /** What caused a change: any of these may be null. */
    public record Cause(String orderId, String orderItemSeqId, String shipmentId, String returnId, String userId, String description) {
        public static final Cause NONE = new Cause(null, null, null, null, null, null);

        public static Cause order(String orderId, String orderItemSeqId) {
            return new Cause(orderId, orderItemSeqId, null, null, null, null);
        }

        public static Cause note(String description) {
            return new Cause(null, null, null, null, null, description);
        }
    }

    public static class InsufficientStockException extends IllegalStateException {
        public InsufficientStockException(String message) {
            super(message);
        }
    }

    /** The bulk item of a product at a facility, locked for change; created empty when missing. */
    public InventoryItem bulkItem(String productId, String facilityId) {
        List<InventoryItem> found = em.createQuery("SELECT i FROM InventoryItem i WHERE i.productId = :p AND i.facilityId = :f "
                        + "AND i.inventoryItemTypeId = :t AND i.lotId IS NULL AND i.serialNumber IS NULL ORDER BY i.inventoryItemId", InventoryItem.class)
                .setParameter("p", productId).setParameter("f", facilityId).setParameter("t", BULK)
                .setFlushMode(FlushModeType.AUTO).setLockMode(LockModeType.PESSIMISTIC_WRITE).setMaxResults(1).getResultList();
        if (!found.isEmpty()) return found.get(0);
        return newBulkItem(productId, facilityId);
    }

    /** Stock a copy from another system starts from; a lot id makes it a separate item for that lot. */
    public record Opening(String productId, String facilityId, String lotId, LocalDateTime expireDate, BigDecimal onHand,
                          BigDecimal available, BigDecimal unitCost, String currencyUomId, String comments) {
    }

    /**
     * Opening stock for a product at a facility that has none yet: one new item and one detail row with the reason,
     * so the history starts from this balance. The caller checks the item doesn't exist yet.
     */
    public String openingBalance(Opening o, String reasonEnumId, Cause cause) {
        InventoryItem i = newBulkItem(o.productId(), o.facilityId());
        if (o.lotId() != null) {
            if (em.find(Lot.class, o.lotId()) == null) {
                Lot lot = new Lot();
                lot.setLotId(o.lotId());
                lot.setCreationDate(CoreClock.now());
                lot.setExpirationDate(o.expireDate());
                em.persist(lot);
            }
            i.setLotId(o.lotId());
        }
        i.setExpireDate(o.expireDate());
        i.setUnitCost(o.unitCost());
        i.setCurrencyUomId(o.unitCost() == null ? null : o.currencyUomId());
        i.setDatetimeReceived(CoreClock.now());
        i.setComments(o.comments());
        change(i, o.onHand() == null ? BigDecimal.ZERO : o.onHand(), o.available() == null ? BigDecimal.ZERO : o.available(), reasonEnumId, cause);
        return i.getInventoryItemId();
    }

    private InventoryItem newBulkItem(String productId, String facilityId) {
        InventoryItem i = new InventoryItem();
        i.setInventoryItemId(sequences.nextId("InventoryItem"));
        i.setInventoryItemTypeId(BULK);
        i.setProductId(productId);
        i.setFacilityId(facilityId);
        i.setQuantityOnHandTotal(BigDecimal.ZERO);
        i.setAvailableToPromiseTotal(BigDecimal.ZERO);
        i.setAccountingQuantityTotal(BigDecimal.ZERO);
        em.persist(i);
        return i;
    }

    /** Stock arrived (a receipt, a return, a feed): on hand and available both go up. */
    public String receive(String productId, String facilityId, BigDecimal quantity, BigDecimal unitCost, String currencyUomId,
                          String reasonEnumId, Cause cause) {
        positive(quantity);
        InventoryItem i = bulkItem(productId, facilityId);
        if (unitCost != null) {
            i.setUnitCost(unitCost);
            i.setCurrencyUomId(currencyUomId);
        }
        if (i.getDatetimeReceived() == null) i.setDatetimeReceived(CoreClock.now());
        change(i, quantity, quantity, reasonEnumId == null ? "IID_RECEIPT" : reasonEnumId, cause);
        return i.getInventoryItemId();
    }

    /** Holds stock for an order: available goes down, on hand stays until it ships. */
    public void reserve(String productId, String facilityId, BigDecimal quantity, Cause cause) {
        positive(quantity);
        InventoryItem i = bulkItem(productId, facilityId);
        if (i.getAvailableToPromiseTotal().compareTo(quantity) < 0) {
            throw new InsufficientStockException("Only " + i.getAvailableToPromiseTotal().stripTrailingZeros().toPlainString()
                    + " of product " + productId + " available at " + facilityId + ", not " + quantity.stripTrailingZeros().toPlainString());
        }
        change(i, BigDecimal.ZERO, quantity.negate(), "IID_RESERVE", cause);
    }

    /** Gives a reservation back. */
    public void release(String productId, String facilityId, BigDecimal quantity, Cause cause) {
        positive(quantity);
        change(bulkItem(productId, facilityId), BigDecimal.ZERO, quantity, "IID_RELEASE", cause);
    }

    /** Reserved stock left the building: on hand goes down (available already did when it was reserved). */
    public void ship(String productId, String facilityId, BigDecimal quantity, Cause cause) {
        positive(quantity);
        InventoryItem i = bulkItem(productId, facilityId);
        if (i.getQuantityOnHandTotal().compareTo(quantity) < 0) {
            throw new InsufficientStockException("Only " + i.getQuantityOnHandTotal().stripTrailingZeros().toPlainString()
                    + " of product " + productId + " on hand at " + facilityId);
        }
        change(i, quantity.negate(), BigDecimal.ZERO, "IID_SHIPMENT", cause);
    }

    /** A change by hand or from a feed, by any amount either way. */
    public void adjust(String productId, String facilityId, BigDecimal onHandDiff, BigDecimal availableDiff, String reasonEnumId, Cause cause) {
        change(bulkItem(productId, facilityId), onHandDiff, availableDiff, reasonEnumId == null ? "IID_ADJUST" : reasonEnumId, cause);
    }

    /**
     * A count found {@code counted} on hand. The difference moves on hand and available alike, is kept as a
     * variance with its reason, and the count is returned.
     */
    public String recordCount(String productId, String facilityId, BigDecimal counted, String varianceReasonId, String partyId, String comments) {
        if (counted == null || counted.signum() < 0) throw new IllegalArgumentException("A count is zero or more");
        InventoryItem i = bulkItem(productId, facilityId);
        BigDecimal diff = counted.subtract(i.getQuantityOnHandTotal());
        PhysicalInventory p = new PhysicalInventory();
        p.setPhysicalInventoryId(sequences.nextId("PhysicalInventory"));
        p.setPhysicalInventoryDate(CoreClock.now());
        p.setPartyId(partyId);
        p.setGeneralComments(comments);
        em.persist(p);
        InventoryItemVariance v = new InventoryItemVariance();
        v.setInventoryItemId(i.getInventoryItemId());
        v.setPhysicalInventoryId(p.getPhysicalInventoryId());
        v.setVarianceReasonId(diff.signum() == 0 ? null : varianceReasonId == null ? "VAR_COUNT" : varianceReasonId);
        v.setQuantityOnHandVar(diff);
        v.setAvailableToPromiseVar(diff);
        v.setComments(comments);
        em.persist(v);
        if (diff.signum() != 0) {
            InventoryItemDetail d = change(i, diff, diff, "IID_COUNT", Cause.note(comments));
            d.setPhysicalInventoryId(p.getPhysicalInventoryId());
        }
        return p.getPhysicalInventoryId();
    }

    /** Available to promise of a product at a facility. */
    @Transactional(readOnly = true)
    public BigDecimal atp(String productId, String facilityId) {
        BigDecimal v = em.createQuery("SELECT SUM(i.availableToPromiseTotal) FROM InventoryItem i WHERE i.productId = :p AND i.facilityId = :f", BigDecimal.class)
                .setParameter("p", productId).setParameter("f", facilityId).getSingleResult();
        return v == null ? BigDecimal.ZERO : v;
    }

    /** On hand of a product at a facility. */
    @Transactional(readOnly = true)
    public BigDecimal onHand(String productId, String facilityId) {
        BigDecimal v = em.createQuery("SELECT SUM(i.quantityOnHandTotal) FROM InventoryItem i WHERE i.productId = :p AND i.facilityId = :f", BigDecimal.class)
                .setParameter("p", productId).setParameter("f", facilityId).getSingleResult();
        return v == null ? BigDecimal.ZERO : v;
    }

    /** Available to promise of a product at every facility holding any, most first. */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> atpByFacility(String productId) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        em.createQuery("SELECT i.facilityId, SUM(i.availableToPromiseTotal) FROM InventoryItem i WHERE i.productId = :p "
                        + "GROUP BY i.facilityId ORDER BY SUM(i.availableToPromiseTotal) DESC, i.facilityId", Object[].class)
                .setParameter("p", productId).getResultList()
                .forEach(r -> out.put((String) r[0], (BigDecimal) r[1]));
        return out;
    }

    public record Levels(BigDecimal minimumStock, BigDecimal reorderPoint, BigDecimal reorderQuantity, BigDecimal maximumStock,
                         Boolean allowBrokering, Boolean allowPickup) {
    }

    /** Sets a product's safety stock, reorder levels and brokering switches at a facility. */
    public void setLevels(String productId, String facilityId, Levels l) {
        ProductFacility pf = productFacility(productId, facilityId);
        pf.setMinimumStock(l.minimumStock());
        pf.setReorderPoint(l.reorderPoint());
        pf.setReorderQuantity(l.reorderQuantity());
        pf.setMaximumStock(l.maximumStock());
        if (l.allowBrokering() != null) pf.setAllowBrokering(l.allowBrokering());
        if (l.allowPickup() != null) pf.setAllowPickup(l.allowPickup());
        pf.setComputedLastInventoryCount(sellable(pf));
    }

    // ── transfers between facilities ──

    /** Asks to move stock from one facility to another; nothing moves until it is approved. */
    public String requestTransfer(String productId, String fromFacilityId, String toFacilityId, BigDecimal quantity, String comments) {
        positive(quantity);
        if (fromFacilityId.equals(toFacilityId)) throw new IllegalArgumentException("A transfer goes to a different facility");
        InventoryTransfer t = new InventoryTransfer();
        t.setInventoryTransferId(sequences.nextId("InventoryTransfer"));
        t.setStatusId(statuses.initialStatus(TRANSFER_FLOW, "INVENTORY_XFER_STATUS"));
        t.setInventoryItemId(bulkItem(productId, fromFacilityId).getInventoryItemId());
        t.setFacilityId(fromFacilityId);
        t.setFacilityIdTo(toFacilityId);
        t.setQuantity(quantity);
        t.setComments(comments);
        em.persist(t);
        return t.getInventoryTransferId();
    }

    /** Approving holds the stock at the sending facility. */
    public void approveTransfer(String transferId) {
        InventoryTransfer t = transfer(transferId, "IXF_SCHEDULED");
        reserve(productOf(t), t.getFacilityId(), t.getQuantity(), Cause.note("Transfer " + transferId));
        t.setStatusId("IXF_SCHEDULED");
    }

    /** Shipping takes it off the sending facility's shelf; it is now in transit. */
    public void shipTransfer(String transferId) {
        InventoryTransfer t = transfer(transferId, "IXF_EN_ROUTE");
        InventoryItem i = bulkItem(productOf(t), t.getFacilityId());
        change(i, t.getQuantity().negate(), BigDecimal.ZERO, "IID_TRANSFER_OUT", Cause.note("Transfer " + transferId));
        t.setSendDate(CoreClock.now());
        t.setStatusId("IXF_EN_ROUTE");
    }

    /** Receiving adds what actually arrived at the receiving facility. */
    public void receiveTransfer(String transferId, BigDecimal received) {
        InventoryTransfer t = transfer(transferId, "IXF_COMPLETE");
        BigDecimal q = received == null ? t.getQuantity() : received;
        if (q.signum() > 0) receive(productOf(t), t.getFacilityIdTo(), q, null, null, "IID_TRANSFER_IN", Cause.note("Transfer " + transferId));
        t.setReceiveDate(CoreClock.now());
        t.setStatusId("IXF_COMPLETE");
    }

    /** Cancelling an approved transfer gives the held stock back. */
    public void cancelTransfer(String transferId) {
        InventoryTransfer t = transfer(transferId, "IXF_CANCELLED");
        if ("IXF_SCHEDULED".equals(t.getStatusId())) release(productOf(t), t.getFacilityId(), t.getQuantity(), Cause.note("Transfer " + transferId + " cancelled"));
        t.setStatusId("IXF_CANCELLED");
    }

    // ── internals ──

    private InventoryItemDetail change(InventoryItem i, BigDecimal onHandDiff, BigDecimal availableDiff, String reasonEnumId, Cause cause) {
        BigDecimal qoh = i.getQuantityOnHandTotal().add(onHandDiff);
        if (qoh.signum() < 0) throw new InsufficientStockException("On hand of product " + i.getProductId() + " at " + i.getFacilityId() + " can't go below zero");
        i.setQuantityOnHandTotal(qoh);
        i.setAvailableToPromiseTotal(i.getAvailableToPromiseTotal().add(availableDiff));
        i.setAccountingQuantityTotal((i.getAccountingQuantityTotal() == null ? BigDecimal.ZERO : i.getAccountingQuantityTotal()).add(onHandDiff));
        Cause c = cause == null ? Cause.NONE : cause;
        InventoryItemDetail d = new InventoryItemDetail();
        d.setInventoryItemId(i.getInventoryItemId());
        d.setInventoryItemDetailSeqId(sequences.nextId("InventoryItemDetail"));
        d.setEffectiveDate(CoreClock.now());
        d.setQuantityOnHandDiff(onHandDiff);
        d.setAvailableToPromiseDiff(availableDiff);
        d.setAccountingQuantityDiff(onHandDiff);
        d.setUnitCost(i.getUnitCost());
        d.setReasonEnumId(reasonEnumId);
        d.setOrderId(c.orderId());
        d.setOrderItemSeqId(c.orderItemSeqId());
        d.setShipmentId(c.shipmentId());
        d.setReturnId(c.returnId());
        d.setChangeByUserId(c.userId());
        d.setDescription(c.description());
        em.persist(d);
        // Follow ATP by the same difference, so this works without a flush (copy jobs run with COMMIT flush mode).
        ProductFacility pf = productFacility(i.getProductId(), i.getFacilityId());
        pf.setLastInventoryCount((pf.getLastInventoryCount() == null ? BigDecimal.ZERO : pf.getLastInventoryCount()).add(availableDiff));
        pf.setComputedLastInventoryCount(sellable(pf));
        return d;
    }

    private ProductFacility productFacility(String productId, String facilityId) {
        ProductFacility pf = em.find(ProductFacility.class, new ProductFacility.Key(productId, facilityId));
        if (pf != null) return pf;
        pf = new ProductFacility();
        pf.setProductId(productId);
        pf.setFacilityId(facilityId);
        pf.setLastInventoryCount(BigDecimal.ZERO);
        pf.setComputedLastInventoryCount(BigDecimal.ZERO);
        pf.setAllowBrokering(true);
        pf.setAllowPickup(true);
        em.persist(pf);
        return pf;
    }

    /** What routing may promise: available less safety stock, never below zero. */
    private static BigDecimal sellable(ProductFacility pf) {
        BigDecimal atp = pf.getLastInventoryCount() == null ? BigDecimal.ZERO : pf.getLastInventoryCount();
        BigDecimal v = pf.getMinimumStock() == null ? atp : atp.subtract(pf.getMinimumStock());
        return v.signum() < 0 ? BigDecimal.ZERO : v;
    }

    private InventoryTransfer transfer(String transferId, String toStatusId) {
        InventoryTransfer t = em.find(InventoryTransfer.class, transferId, LockModeType.PESSIMISTIC_WRITE);
        if (t == null) throw new IllegalArgumentException("Unknown transfer " + transferId);
        statuses.requireAllowed(TRANSFER_FLOW, t.getStatusId(), toStatusId);
        return t;
    }

    private String productOf(InventoryTransfer t) {
        return em.find(InventoryItem.class, t.getInventoryItemId()).getProductId();
    }

    private static void positive(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("A quantity must be more than zero");
    }
}
