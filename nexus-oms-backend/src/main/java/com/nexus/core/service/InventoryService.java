package com.nexus.core.service;

import com.nexus.core.model.inventory.InventoryItem;
import com.nexus.core.model.inventory.InventoryItemDetail;
import com.nexus.core.model.inventory.InventoryItemVariance;
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
 * Stock in the OFBiz way.
 * <p>
 * An {@code inventory_item} holds the totals for one product at one facility (one per lot when stock has lots):
 * <ul>
 *   <li>quantity on hand (QOH): what is physically there;</li>
 *   <li>available to promise (ATP): what can still be sold, which is on hand less what orders hold.</li>
 * </ul>
 * Every change is an {@code inventory_item_detail} row saying how QOH and ATP moved, why (a {@link Reason}), and for
 * which order, shipment, return or count. Holding stock for an order lowers ATP only; shipping it then lowers QOH.
 * <p>
 * {@code product_facility} keeps, per product and facility, the last ATP and what routing may promise (ATP less
 * safety stock), so routing reads one row per facility instead of summing items. Transfers between facilities are in
 * {@link TransferService}.
 */
@Service("coreInventoryService")
@Transactional
public class InventoryService {

    /** Bulk stock (not tracked by serial number): one inventory item per product and facility. */
    public static final String BULK = "NON_SERIAL_INV_ITEM";

    /** Why stock changed ({@code INV_DETAIL_REASON} enumerations). */
    public static final class Reason {
        public static final String RECEIPT = "IID_RECEIPT";
        public static final String SHIPMENT = "IID_SHIPMENT";
        public static final String RESERVE = "IID_RESERVE";
        public static final String RELEASE = "IID_RELEASE";
        public static final String RETURN = "IID_RETURN";
        public static final String ADJUST = "IID_ADJUST";
        public static final String COUNT = "IID_COUNT";
        public static final String TRANSFER_OUT = "IID_TRANSFER_OUT";
        public static final String TRANSFER_IN = "IID_TRANSFER_IN";
        public static final String HOLD = "IID_HOLD";
        public static final String MIGRATION = "IID_MIGRATION";

        private Reason() {
        }
    }

    /** The variance reason a count records when the counter gives none. */
    static final String DEFAULT_VARIANCE_REASON = "VAR_COUNT";

    private final EntityManager entityManager;
    private final SequenceService sequenceService;

    public InventoryService(EntityManager entityManager, SequenceService sequenceService) {
        this.entityManager = entityManager;
        this.sequenceService = sequenceService;
    }

    /** What caused a stock change: an order line, a shipment, a return, a person, a note. Any of them may be null. */
    public record Cause(String orderId, String orderItemSeqId, String shipmentId, String returnId, String userId, String description) {
        public static final Cause NONE = new Cause(null, null, null, null, null, null);

        public static Cause order(String orderId, String orderItemSeqId) {
            return new Cause(orderId, orderItemSeqId, null, null, null, null);
        }

        public static Cause note(String description) {
            return new Cause(null, null, null, null, null, description);
        }

        /** The same cause, made by this person. */
        public Cause byUser(String changedByUserId) {
            return new Cause(orderId, orderItemSeqId, shipmentId, returnId, changedByUserId, description);
        }
    }

    /** There is not enough stock for what was asked. */
    public static class InsufficientStockException extends IllegalStateException {
        public InsufficientStockException(String message) {
            super(message);
        }
    }

    // ── the inventory item ──

    /**
     * The bulk inventory item of a product at a facility, locked for change until the transaction ends; created empty
     * when the facility has never had this product. The query flushes first, so an item created earlier in the same
     * transaction is found even when the caller runs with COMMIT flush mode.
     */
    public InventoryItem bulkItem(String productId, String facilityId) {
        List<InventoryItem> found = entityManager.createQuery(
                        "SELECT i FROM InventoryItem i WHERE i.productId = :productId AND i.facilityId = :facilityId "
                                + "AND i.inventoryItemTypeId = :bulkType AND i.lotId IS NULL AND i.serialNumber IS NULL "
                                + "ORDER BY i.inventoryItemId", InventoryItem.class)
                .setParameter("productId", productId)
                .setParameter("facilityId", facilityId)
                .setParameter("bulkType", BULK)
                .setFlushMode(FlushModeType.AUTO)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setMaxResults(1)
                .getResultList();
        return found.isEmpty() ? createBulkItem(productId, facilityId) : found.get(0);
    }

    /** Stock a copy from another system starts from; a lot id makes it a separate item for that lot. */
    public record Opening(String productId, String facilityId, String lotId, LocalDateTime expireDate, BigDecimal onHand,
                          BigDecimal available, BigDecimal unitCost, String currencyUomId, String comments) {
    }

    /**
     * Opening stock for a product at a facility that has none yet: one new item and one detail row with the reason, so
     * the history starts from this balance. The caller makes sure the item does not exist yet.
     */
    public String openingBalance(Opening opening, String reasonEnumId, Cause cause) {
        InventoryItem inventoryItem = createBulkItem(opening.productId(), opening.facilityId());
        if (opening.lotId() != null) {
            createLotIfMissing(opening.lotId(), opening.expireDate());
            inventoryItem.setLotId(opening.lotId());
        }
        inventoryItem.setExpireDate(opening.expireDate());
        inventoryItem.setUnitCost(opening.unitCost());
        inventoryItem.setCurrencyUomId(opening.unitCost() == null ? null : opening.currencyUomId());
        inventoryItem.setDatetimeReceived(CoreClock.now());
        inventoryItem.setComments(opening.comments());

        BigDecimal onHand = opening.onHand() == null ? BigDecimal.ZERO : opening.onHand();
        BigDecimal available = opening.available() == null ? BigDecimal.ZERO : opening.available();
        applyChange(inventoryItem, onHand, available, reasonEnumId, cause);
        return inventoryItem.getInventoryItemId();
    }

    // ── changes ──

    /** Stock arrived (a receipt, a return, a feed): on hand and available both go up. Returns the inventory item id. */
    public String receive(String productId, String facilityId, BigDecimal quantity, BigDecimal unitCost, String currencyUomId,
                          String reasonEnumId, Cause cause) {
        requirePositive(quantity);
        InventoryItem inventoryItem = bulkItem(productId, facilityId);
        if (unitCost != null) {
            inventoryItem.setUnitCost(unitCost);
            inventoryItem.setCurrencyUomId(currencyUomId);
        }
        if (inventoryItem.getDatetimeReceived() == null) {
            inventoryItem.setDatetimeReceived(CoreClock.now());
        }
        applyChange(inventoryItem, quantity, quantity, reasonEnumId == null ? Reason.RECEIPT : reasonEnumId, cause);
        return inventoryItem.getInventoryItemId();
    }

    /**
     * Holds stock for an order: available goes down, on hand stays until it ships.
     *
     * @throws InsufficientStockException when fewer than {@code quantity} are available
     */
    public void reserve(String productId, String facilityId, BigDecimal quantity, Cause cause) {
        requirePositive(quantity);
        InventoryItem inventoryItem = bulkItem(productId, facilityId);
        BigDecimal available = inventoryItem.getAvailableToPromiseTotal();
        if (available.compareTo(quantity) < 0) {
            throw new InsufficientStockException("Only " + plain(available) + " of product " + productId + " available at " + facilityId
                    + ", not " + plain(quantity));
        }
        applyChange(inventoryItem, BigDecimal.ZERO, quantity.negate(), Reason.RESERVE, cause);
    }

    /** Gives held stock back to available. */
    public void release(String productId, String facilityId, BigDecimal quantity, Cause cause) {
        requirePositive(quantity);
        applyChange(bulkItem(productId, facilityId), BigDecimal.ZERO, quantity, Reason.RELEASE, cause);
    }

    /**
     * Held stock left the building: on hand goes down. Available already went down when the stock was held.
     *
     * @throws InsufficientStockException when fewer than {@code quantity} are on hand
     */
    public void ship(String productId, String facilityId, BigDecimal quantity, Cause cause) {
        requirePositive(quantity);
        InventoryItem inventoryItem = bulkItem(productId, facilityId);
        BigDecimal onHand = inventoryItem.getQuantityOnHandTotal();
        if (onHand.compareTo(quantity) < 0) {
            throw new InsufficientStockException("Only " + plain(onHand) + " of product " + productId + " on hand at " + facilityId);
        }
        applyChange(inventoryItem, quantity.negate(), BigDecimal.ZERO, Reason.SHIPMENT, cause);
    }

    /** A change by hand or from a feed, by any amount either way. */
    public void adjust(String productId, String facilityId, BigDecimal onHandChange, BigDecimal availableChange, String reasonEnumId, Cause cause) {
        applyChange(bulkItem(productId, facilityId), onHandChange, availableChange, reasonEnumId == null ? Reason.ADJUST : reasonEnumId, cause);
    }

    /**
     * A count found {@code counted} on hand. The difference moves on hand and available alike and is kept as a
     * variance with its reason. A count that matches the shelf changes no stock but is still recorded, because a
     * recent clean count is evidence the stock is real. Returns the count's id.
     */
    public String recordCount(String productId, String facilityId, BigDecimal counted, String varianceReasonId, String partyId, String comments) {
        if (counted == null || counted.signum() < 0) {
            throw new IllegalArgumentException("A count is zero or more");
        }
        InventoryItem inventoryItem = bulkItem(productId, facilityId);
        BigDecimal difference = counted.subtract(inventoryItem.getQuantityOnHandTotal());

        PhysicalInventory count = new PhysicalInventory();
        count.setPhysicalInventoryId(sequenceService.nextId("PhysicalInventory"));
        count.setPhysicalInventoryDate(CoreClock.now());
        count.setPartyId(partyId);
        count.setGeneralComments(comments);
        entityManager.persist(count);

        InventoryItemVariance variance = new InventoryItemVariance();
        variance.setInventoryItemId(inventoryItem.getInventoryItemId());
        variance.setPhysicalInventoryId(count.getPhysicalInventoryId());
        boolean countMatched = difference.signum() == 0;
        variance.setVarianceReasonId(countMatched ? null : varianceReasonId == null ? DEFAULT_VARIANCE_REASON : varianceReasonId);
        variance.setQuantityOnHandVar(difference);
        variance.setAvailableToPromiseVar(difference);
        variance.setComments(comments);
        entityManager.persist(variance);

        if (!countMatched) {
            InventoryItemDetail detail = applyChange(inventoryItem, difference, difference, Reason.COUNT, Cause.note(comments));
            detail.setPhysicalInventoryId(count.getPhysicalInventoryId());
        }
        return count.getPhysicalInventoryId();
    }

    // ── reading ──

    /** Available to promise of a product at a facility. */
    @Transactional(readOnly = true)
    public BigDecimal atp(String productId, String facilityId) {
        BigDecimal total = entityManager.createQuery(
                        "SELECT SUM(i.availableToPromiseTotal) FROM InventoryItem i WHERE i.productId = :productId AND i.facilityId = :facilityId",
                        BigDecimal.class)
                .setParameter("productId", productId)
                .setParameter("facilityId", facilityId)
                .getSingleResult();
        return total == null ? BigDecimal.ZERO : total;
    }

    /** On hand of a product at a facility. */
    @Transactional(readOnly = true)
    public BigDecimal onHand(String productId, String facilityId) {
        BigDecimal total = entityManager.createQuery(
                        "SELECT SUM(i.quantityOnHandTotal) FROM InventoryItem i WHERE i.productId = :productId AND i.facilityId = :facilityId",
                        BigDecimal.class)
                .setParameter("productId", productId)
                .setParameter("facilityId", facilityId)
                .getSingleResult();
        return total == null ? BigDecimal.ZERO : total;
    }

    /** Available to promise of a product at every facility that has any of it, most first. */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> atpByFacility(String productId) {
        Map<String, BigDecimal> availableByFacility = new LinkedHashMap<>();
        List<Object[]> rows = entityManager.createQuery(
                        "SELECT i.facilityId, SUM(i.availableToPromiseTotal) FROM InventoryItem i WHERE i.productId = :productId "
                                + "GROUP BY i.facilityId ORDER BY SUM(i.availableToPromiseTotal) DESC, i.facilityId", Object[].class)
                .setParameter("productId", productId)
                .getResultList();
        for (Object[] row : rows) {
            String facilityId = (String) row[0];
            BigDecimal available = (BigDecimal) row[1];
            availableByFacility.put(facilityId, available);
        }
        return availableByFacility;
    }

    // ── levels per product and facility ──

    /** Safety stock, reorder levels and the brokering and pickup switches of a product at a facility. */
    public record Levels(BigDecimal minimumStock, BigDecimal reorderPoint, BigDecimal reorderQuantity, BigDecimal maximumStock,
                         Boolean allowBrokering, Boolean allowPickup) {
    }

    /** Sets a product's levels at a facility; null switches leave the current setting. */
    public void setLevels(String productId, String facilityId, Levels levels) {
        ProductFacility productFacility = productFacilityOf(productId, facilityId);
        productFacility.setMinimumStock(levels.minimumStock());
        productFacility.setReorderPoint(levels.reorderPoint());
        productFacility.setReorderQuantity(levels.reorderQuantity());
        productFacility.setMaximumStock(levels.maximumStock());
        if (levels.allowBrokering() != null) {
            productFacility.setAllowBrokering(levels.allowBrokering());
        }
        if (levels.allowPickup() != null) {
            productFacility.setAllowPickup(levels.allowPickup());
        }
        // Safety stock changed, so what routing may promise changes too.
        productFacility.setComputedLastInventoryCount(sellableFor(productFacility));
    }

    // ── internals ──

    /**
     * Applies one change to an inventory item and records it:
     * <ol>
     *   <li>move the item's totals (on hand may never go below zero; available may, when stock is oversold);</li>
     *   <li>write the detail row with the reason and cause;</li>
     *   <li>move product_facility's last ATP by the same amount and work out what routing may promise.</li>
     * </ol>
     * Step 3 adds the difference instead of re-summing the items, so it is correct without a flush (copy jobs run
     * with COMMIT flush mode).
     */
    private InventoryItemDetail applyChange(InventoryItem inventoryItem, BigDecimal onHandChange, BigDecimal availableChange,
                                            String reasonEnumId, Cause cause) {
        BigDecimal newOnHand = inventoryItem.getQuantityOnHandTotal().add(onHandChange);
        if (newOnHand.signum() < 0) {
            throw new InsufficientStockException("On hand of product " + inventoryItem.getProductId() + " at " + inventoryItem.getFacilityId()
                    + " can't go below zero");
        }
        inventoryItem.setQuantityOnHandTotal(newOnHand);
        inventoryItem.setAvailableToPromiseTotal(inventoryItem.getAvailableToPromiseTotal().add(availableChange));
        BigDecimal accountingBefore = inventoryItem.getAccountingQuantityTotal() == null ? BigDecimal.ZERO : inventoryItem.getAccountingQuantityTotal();
        inventoryItem.setAccountingQuantityTotal(accountingBefore.add(onHandChange));

        Cause actualCause = cause == null ? Cause.NONE : cause;
        InventoryItemDetail detail = new InventoryItemDetail();
        detail.setInventoryItemId(inventoryItem.getInventoryItemId());
        // A global sequence rather than a per-item counter: counting existing rows would need a flush.
        detail.setInventoryItemDetailSeqId(sequenceService.nextId("InventoryItemDetail"));
        detail.setEffectiveDate(CoreClock.now());
        detail.setQuantityOnHandDiff(onHandChange);
        detail.setAvailableToPromiseDiff(availableChange);
        detail.setAccountingQuantityDiff(onHandChange);
        detail.setUnitCost(inventoryItem.getUnitCost());
        detail.setReasonEnumId(reasonEnumId);
        detail.setOrderId(actualCause.orderId());
        detail.setOrderItemSeqId(actualCause.orderItemSeqId());
        detail.setShipmentId(actualCause.shipmentId());
        detail.setReturnId(actualCause.returnId());
        detail.setChangeByUserId(actualCause.userId());
        detail.setDescription(actualCause.description());
        entityManager.persist(detail);

        ProductFacility productFacility = productFacilityOf(inventoryItem.getProductId(), inventoryItem.getFacilityId());
        BigDecimal lastAvailable = productFacility.getLastInventoryCount() == null ? BigDecimal.ZERO : productFacility.getLastInventoryCount();
        productFacility.setLastInventoryCount(lastAvailable.add(availableChange));
        productFacility.setComputedLastInventoryCount(sellableFor(productFacility));
        return detail;
    }

    private InventoryItem createBulkItem(String productId, String facilityId) {
        InventoryItem inventoryItem = new InventoryItem();
        inventoryItem.setInventoryItemId(sequenceService.nextId("InventoryItem"));
        inventoryItem.setInventoryItemTypeId(BULK);
        inventoryItem.setProductId(productId);
        inventoryItem.setFacilityId(facilityId);
        inventoryItem.setQuantityOnHandTotal(BigDecimal.ZERO);
        inventoryItem.setAvailableToPromiseTotal(BigDecimal.ZERO);
        inventoryItem.setAccountingQuantityTotal(BigDecimal.ZERO);
        entityManager.persist(inventoryItem);
        return inventoryItem;
    }

    private void createLotIfMissing(String lotId, LocalDateTime expirationDate) {
        if (entityManager.find(Lot.class, lotId) != null) {
            return;
        }
        Lot lot = new Lot();
        lot.setLotId(lotId);
        lot.setCreationDate(CoreClock.now());
        lot.setExpirationDate(expirationDate);
        entityManager.persist(lot);
    }

    /** The product_facility row of a product at a facility, created with both switches on when missing. */
    private ProductFacility productFacilityOf(String productId, String facilityId) {
        ProductFacility productFacility = entityManager.find(ProductFacility.class, new ProductFacility.Key(productId, facilityId));
        if (productFacility != null) {
            return productFacility;
        }
        productFacility = new ProductFacility();
        productFacility.setProductId(productId);
        productFacility.setFacilityId(facilityId);
        productFacility.setLastInventoryCount(BigDecimal.ZERO);
        productFacility.setComputedLastInventoryCount(BigDecimal.ZERO);
        productFacility.setAllowBrokering(true);
        productFacility.setAllowPickup(true);
        entityManager.persist(productFacility);
        return productFacility;
    }

    /** What routing may promise: available less safety stock, never below zero. */
    private static BigDecimal sellableFor(ProductFacility productFacility) {
        BigDecimal available = productFacility.getLastInventoryCount() == null ? BigDecimal.ZERO : productFacility.getLastInventoryCount();
        BigDecimal sellable = productFacility.getMinimumStock() == null ? available : available.subtract(productFacility.getMinimumStock());
        return sellable.signum() < 0 ? BigDecimal.ZERO : sellable;
    }

    private static void requirePositive(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("A quantity must be more than zero");
        }
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
