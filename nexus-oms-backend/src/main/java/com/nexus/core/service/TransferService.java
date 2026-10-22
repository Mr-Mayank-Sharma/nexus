package com.nexus.core.service;

import com.nexus.core.CoreStatus;
import com.nexus.core.model.inventory.InventoryItem;
import com.nexus.core.model.inventory.InventoryItemVariance;
import com.nexus.core.model.inventory.InventoryTransfer;
import com.nexus.core.model.inventory.PhysicalInventory;
import com.nexus.core.model.shipment.Shipment;
import com.nexus.core.model.shipment.ShipmentItem;
import com.nexus.core.model.shipment.ShipmentReceipt;
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
 * {@code inventory_transfer} per product.
 * <ol>
 *   <li>{@link #request}: moves nothing;</li>
 *   <li>{@link #approve}: holds the stock at the sending location at once, so it can no longer be sold there;</li>
 *   <li>{@link #ship}: takes it off the shelf and puts it in the IN_TRANSIT location, so it always has an owner and
 *       nobody reorders what is on its way;</li>
 *   <li>{@link #receive}: by scan, per product; what arrived goes on sale at the receiving location. A difference
 *       within the tolerance is settled at once as a transit loss; a bigger one waits for {@link #settle}.</li>
 * </ol>
 */
@Service("coreTransferService")
@Transactional
public class TransferService {

    public static final String FLOW = StatusService.DEFAULT_FLOW;
    public static final String IN_TRANSIT = "IN_TRANSIT";
    static final String TRANSFER_SHIPMENT_TYPE = "TRANSFER";
    static final String RESOURCE = "nexus.core";
    static final String TRANSIT_LOSS = "VAR_TRANSIT_LOSS";
    /** Longest text kept in a comments column. */
    private static final int MAX_COMMENT_LENGTH = 4000;

    // Receipt outcomes, per product.
    public static final String RECEIVED = "RECEIVED";
    public static final String SETTLED_WITHIN_TOLERANCE = "SETTLED_WITHIN_TOLERANCE";
    public static final String WAITING_FOR_DECISION = "WAITING_FOR_DECISION";
    public static final String MORE_THAN_SENT = "MORE_THAN_SENT";

    private final EntityManager entityManager;
    private final SequenceService sequenceService;
    private final StatusService statusService;
    private final InventoryService inventoryService;
    private final ShipmentService shipmentService;
    private final SystemPropertyService systemPropertyService;

    public TransferService(EntityManager entityManager, SequenceService sequenceService, StatusService statusService, InventoryService inventoryService,
                           ShipmentService shipmentService, SystemPropertyService systemPropertyService) {
        this.entityManager = entityManager;
        this.sequenceService = sequenceService;
        this.statusService = statusService;
        this.inventoryService = inventoryService;
        this.shipmentService = shipmentService;
        this.systemPropertyService = systemPropertyService;
    }

    public record Line(String productId, BigDecimal quantity) {
    }

    /** What happened to one product when a transfer was received. */
    public record Receipt(String productId, BigDecimal shipped, BigDecimal received, BigDecimal difference, String outcome) {
    }

    // ── the transfer's life ──

    /** A transfer request from one location to another. Returns the transfer's shipment id. */
    public String request(String fromFacilityId, String toFacilityId, List<Line> lines, String comments, String userId) {
        if (fromFacilityId.equals(toFacilityId)) {
            throw new IllegalArgumentException("A transfer goes to a different location");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("A transfer needs at least one product");
        }

        Shipment shipment = new Shipment();
        shipment.setShipmentId(sequenceService.nextId("Shipment"));
        shipment.setShipmentTypeId(TRANSFER_SHIPMENT_TYPE);
        shipment.setStatusId(statusService.initialStatus(FLOW, CoreStatus.Type.SHIPMENT));
        shipment.setOriginFacilityId(fromFacilityId);
        shipment.setDestinationFacilityId(toFacilityId);
        shipment.setHandlingInstructions(comments);
        shipment.setCreatedDate(CoreClock.now());
        shipment.setCreatedByUserLogin(userId);
        entityManager.persist(shipment);
        shipmentService.recordStatus(shipment.getShipmentId(), shipment.getStatusId(), userId);

        // One shipment item and one inventory transfer per product.
        int lineNumber = 1;
        for (Line line : lines) {
            if (line.quantity() == null || line.quantity().signum() <= 0) {
                throw new IllegalArgumentException("A transfer quantity must be more than zero");
            }
            ShipmentItem shipmentItem = new ShipmentItem();
            shipmentItem.setShipmentId(shipment.getShipmentId());
            shipmentItem.setShipmentItemSeqId(SequenceService.seqId(lineNumber++));
            shipmentItem.setProductId(line.productId());
            shipmentItem.setQuantity(line.quantity());
            entityManager.persist(shipmentItem);

            InventoryTransfer transfer = new InventoryTransfer();
            transfer.setInventoryTransferId(sequenceService.nextId("InventoryTransfer"));
            transfer.setStatusId(statusService.initialStatus(FLOW, CoreStatus.Type.TRANSFER));
            transfer.setInventoryItemId(inventoryService.bulkItem(line.productId(), fromFacilityId).getInventoryItemId());
            transfer.setFacilityId(fromFacilityId);
            transfer.setFacilityIdTo(toFacilityId);
            transfer.setQuantity(line.quantity());
            transfer.setComments(comments);
            transfer.setShipmentId(shipment.getShipmentId());
            transfer.setShipmentItemSeqId(shipmentItem.getShipmentItemSeqId());
            entityManager.persist(transfer);
        }
        return shipment.getShipmentId();
    }

    /** Approving holds every line at the sending location, so it can't be sold there while it waits to go. */
    public void approve(String shipmentId, String userId) {
        for (InventoryTransfer transfer : lines(shipmentId)) {
            moveTo(transfer, CoreStatus.Transfer.SCHEDULED);
            inventoryService.reserve(productOf(transfer), transfer.getFacilityId(), transfer.getQuantity(), cause(transfer, userId, "approved"));
        }
    }

    /** Shipping moves the held stock off the sending shelf into the IN_TRANSIT location. */
    public void ship(String shipmentId, String trackingNumber, String userId) {
        Shipment shipment = loadTransferForUpdate(shipmentId);
        for (InventoryTransfer transfer : lines(shipmentId)) {
            moveTo(transfer, CoreStatus.Transfer.EN_ROUTE);
            String productId = productOf(transfer);
            BigDecimal quantity = transfer.getQuantity();
            inventoryService.adjust(productId, transfer.getFacilityId(), quantity.negate(), BigDecimal.ZERO, InventoryService.Reason.TRANSFER_OUT,
                    cause(transfer, userId, "shipped"));
            // In transit: owned and counted, never for sale.
            inventoryService.adjust(productId, IN_TRANSIT, quantity, BigDecimal.ZERO, InventoryService.Reason.TRANSFER_OUT,
                    cause(transfer, userId, "in transit to " + transfer.getFacilityIdTo()));
            transfer.setSendDate(CoreClock.now());
        }
        changeShipmentStatus(shipment, CoreStatus.Shipment.SHIPPED, userId);
        if (trackingNumber != null) {
            shipment.setHandlingInstructions(appendNote(shipment.getHandlingInstructions(), "Tracking " + trackingNumber));
        }
    }

    /**
     * Receives by scan: quantity received per product. For each product:
     * <ul>
     *   <li>what arrived (up to what was sent) leaves IN_TRANSIT and goes on sale at the receiving location;</li>
     *   <li>all of it: the line is complete;</li>
     *   <li>short within the tolerance (system properties {@code transfer.tolerancePct}, 2 when unset, and
     *       {@code transfer.toleranceUnits}): settled at once as a transit loss;</li>
     *   <li>short beyond it, or more than was sent: the line waits in IXF_RECEIVED_SHORT for {@link #settle}. Extra
     *       units go on sale anyway, since they are here.</li>
     * </ul>
     * Products not scanned count as zero received; scanning a product the transfer doesn't carry is an error.
     */
    public List<Receipt> receive(String shipmentId, Map<String, BigDecimal> scannedByProduct, String userId) {
        Shipment shipment = loadTransferForUpdate(shipmentId);
        BigDecimal tolerancePercent = BigDecimal.valueOf(systemPropertyService.getLong(RESOURCE, "transfer.tolerancePct", 2));
        BigDecimal toleranceUnits = BigDecimal.valueOf(systemPropertyService.getLong(RESOURCE, "transfer.toleranceUnits", 0));
        Map<String, BigDecimal> notYetMatched = new LinkedHashMap<>(scannedByProduct == null ? Map.of() : scannedByProduct);
        List<Receipt> receipts = new ArrayList<>();

        for (InventoryTransfer transfer : lines(shipmentId)) {
            if (!CoreStatus.Transfer.EN_ROUTE.equals(transfer.getStatusId())) {
                throw new IllegalStateException("Transfer " + transfer.getInventoryTransferId() + " is " + transfer.getStatusId() + "; ship it first");
            }
            String productId = productOf(transfer);
            BigDecimal sent = transfer.getQuantity();
            BigDecimal received = notYetMatched.getOrDefault(productId, BigDecimal.ZERO);
            notYetMatched.remove(productId);
            if (received.signum() < 0) {
                throw new IllegalArgumentException("A received quantity can't be negative");
            }

            // What arrived, up to what was sent, leaves transit and goes on sale.
            BigDecimal receivedOfSent = received.min(sent);
            inventoryService.adjust(productId, IN_TRANSIT, receivedOfSent.negate(), BigDecimal.ZERO, InventoryService.Reason.TRANSFER_IN,
                    cause(transfer, userId, "arrived"));
            if (receivedOfSent.signum() > 0) {
                putOnSale(shipment, transfer, receivedOfSent, "received", userId);
            }
            transfer.setReceiveDate(CoreClock.now());

            BigDecimal shortBy = sent.subtract(received);   // negative when more arrived than was sent
            boolean allArrived = shortBy.signum() == 0;
            boolean shortWithinTolerance = shortBy.signum() > 0 && shortBy.compareTo(tolerance(sent, tolerancePercent, toleranceUnits)) <= 0;
            if (allArrived) {
                moveTo(transfer, CoreStatus.Transfer.COMPLETE);
                receipts.add(new Receipt(productId, sent, received, shortBy, RECEIVED));
            } else if (shortWithinTolerance) {
                writeOffTransitLoss(transfer, shortBy, "Short " + plain(shortBy) + " within tolerance; settled", userId);
                moveTo(transfer, CoreStatus.Transfer.COMPLETE);
                receipts.add(new Receipt(productId, sent, received, shortBy, SETTLED_WITHIN_TOLERANCE));
            } else {
                moveTo(transfer, CoreStatus.Transfer.RECEIVED_SHORT);
                transfer.setComments(appendNote(transfer.getComments(), "Received " + plain(received) + " of " + plain(sent)));
                boolean moreThanSent = shortBy.signum() < 0;
                receipts.add(new Receipt(productId, sent, received, shortBy, moreThanSent ? MORE_THAN_SENT : WAITING_FOR_DECISION));
                if (moreThanSent) {
                    // It is here, so it goes on sale; the sender's count is now wrong.
                    putOnSale(shipment, transfer, shortBy.negate(), "more than sent", userId);
                }
            }
        }
        if (!notYetMatched.isEmpty()) {
            throw new IllegalArgumentException("Transfer " + shipmentId + " doesn't carry " + notYetMatched.keySet());
        }
        markDeliveredIfComplete(shipment, userId);
        return receipts;
    }

    /**
     * A person decides a difference: {@code found} more units turned up (they go on sale at the receiving location),
     * the rest is written off as lost in transit.
     */
    public void settle(String shipmentId, String productId, BigDecimal found, String note, String userId) {
        Shipment shipment = loadTransferForUpdate(shipmentId);
        InventoryTransfer transfer = lines(shipmentId).stream()
                .filter(line -> productId.equals(productOf(line)) && CoreStatus.Transfer.RECEIVED_SHORT.equals(line.getStatusId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Transfer " + shipmentId + " has no difference to settle for " + productId));

        BigDecimal received = receivedQuantity(shipmentId, transfer.getShipmentItemSeqId());
        BigDecimal missing = transfer.getQuantity().subtract(received).max(BigDecimal.ZERO);
        BigDecimal foundQuantity = found == null ? BigDecimal.ZERO : found;
        if (foundQuantity.signum() < 0 || foundQuantity.compareTo(missing) > 0) {
            throw new IllegalArgumentException("Found must be between 0 and " + plain(missing));
        }

        if (foundQuantity.signum() > 0) {
            inventoryService.adjust(productId, IN_TRANSIT, foundQuantity.negate(), BigDecimal.ZERO, InventoryService.Reason.TRANSFER_IN,
                    cause(transfer, userId, "found"));
            putOnSale(shipment, transfer, foundQuantity, "found", userId);
        }
        BigDecimal lost = missing.subtract(foundQuantity);
        if (lost.signum() > 0) {
            writeOffTransitLoss(transfer, lost, note == null ? "Settled by hand" : note, userId);
        }
        moveTo(transfer, CoreStatus.Transfer.COMPLETE);
        markDeliveredIfComplete(shipment, userId);
    }

    /** Cancels a transfer not yet shipped; approved lines give their held stock back. */
    public void cancel(String shipmentId, String userId) {
        Shipment shipment = loadTransferForUpdate(shipmentId);
        for (InventoryTransfer transfer : lines(shipmentId)) {
            boolean stockWasHeld = CoreStatus.Transfer.SCHEDULED.equals(transfer.getStatusId());
            moveTo(transfer, CoreStatus.Transfer.CANCELLED);
            if (stockWasHeld) {
                inventoryService.release(productOf(transfer), transfer.getFacilityId(), transfer.getQuantity(), cause(transfer, userId, "cancelled"));
            }
        }
        statusService.requireAllowed(FLOW, shipment.getStatusId(), CoreStatus.Shipment.CANCELLED);
        changeShipmentStatus(shipment, CoreStatus.Shipment.CANCELLED, userId);
    }

    /** The transfer's lines, one per product, in order. */
    @Transactional(readOnly = true)
    public List<InventoryTransfer> lines(String shipmentId) {
        return entityManager.createQuery("SELECT t FROM InventoryTransfer t WHERE t.shipmentId = :shipmentId ORDER BY t.shipmentItemSeqId",
                        InventoryTransfer.class)
                .setParameter("shipmentId", shipmentId)
                .getResultList();
    }

    public record TransferLineView(String inventoryTransferId, String productId, String productName, BigDecimal quantity, String statusId,
                                   String comments) {
    }

    public record TransferView(String shipmentId, String statusId, String fromFacilityId, String toFacilityId, String createdDate,
                               List<TransferLineView> lines) {
    }

    /** The latest transfers, newest first, with their lines and product names. */
    @Transactional(readOnly = true)
    public List<TransferView> recentTransfers(int limit) {
        List<Object[]> rows = entityManager.createQuery("SELECT s.shipmentId, s.statusId, s.originFacilityId, s.destinationFacilityId, s.createdDate "
                        + "FROM Shipment s WHERE s.shipmentTypeId = :transferType ORDER BY s.createdDate DESC", Object[].class)
                .setParameter("transferType", TRANSFER_SHIPMENT_TYPE)
                .setMaxResults(limit)
                .getResultList();
        return rows.stream()
                .map(row -> new TransferView((String) row[0], (String) row[1], (String) row[2], (String) row[3], String.valueOf(row[4]),
                        lineViews((String) row[0])))
                .toList();
    }

    private List<TransferLineView> lineViews(String shipmentId) {
        List<TransferLineView> views = new ArrayList<>();
        for (InventoryTransfer transfer : lines(shipmentId)) {
            Object[] product = entityManager.createQuery("SELECT p.productId, p.productName FROM InventoryItem i, Product p "
                            + "WHERE p.productId = i.productId AND i.inventoryItemId = :inventoryItemId", Object[].class)
                    .setParameter("inventoryItemId", transfer.getInventoryItemId())
                    .getSingleResult();
            views.add(new TransferLineView(transfer.getInventoryTransferId(), (String) product[0], (String) product[1], transfer.getQuantity(),
                    transfer.getStatusId(), transfer.getComments()));
        }
        return views;
    }

    // ── internals ──

    /** The largest shortfall settled without a person: the percent of the quantity (rounded down), or the units if more. */
    static BigDecimal tolerance(BigDecimal quantity, BigDecimal tolerancePercent, BigDecimal toleranceUnits) {
        return quantity.multiply(tolerancePercent).divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR).max(toleranceUnits);
    }

    /** Receives units at the receiving location and records the receipt against the transfer's shipment item. */
    private void putOnSale(Shipment shipment, InventoryTransfer transfer, BigDecimal quantity, String what, String userId) {
        String inventoryItemId = inventoryService.receive(productOf(transfer), transfer.getFacilityIdTo(), quantity, null, null,
                InventoryService.Reason.TRANSFER_IN, cause(transfer, userId, what));
        ShipmentReceipt receipt = new ShipmentReceipt();
        receipt.setReceiptId(sequenceService.nextId("ShipmentReceipt"));
        receipt.setInventoryItemId(inventoryItemId);
        receipt.setProductId(productOf(transfer));
        receipt.setShipmentId(shipment.getShipmentId());
        receipt.setShipmentItemSeqId(transfer.getShipmentItemSeqId());
        receipt.setDatetimeReceived(CoreClock.now());
        receipt.setReceivedByUserLogin(userId);
        receipt.setQuantityAccepted(quantity);
        receipt.setQuantityRejected(BigDecimal.ZERO);
        entityManager.persist(receipt);
    }

    /** Takes lost units out of IN_TRANSIT and records them as a count variance with the transit-loss reason. */
    private void writeOffTransitLoss(InventoryTransfer transfer, BigDecimal lost, String note, String userId) {
        String productId = productOf(transfer);
        inventoryService.adjust(productId, IN_TRANSIT, lost.negate(), BigDecimal.ZERO, InventoryService.Reason.COUNT,
                cause(transfer, userId, "lost in transit: " + note));

        PhysicalInventory count = new PhysicalInventory();
        count.setPhysicalInventoryId(sequenceService.nextId("PhysicalInventory"));
        count.setPhysicalInventoryDate(CoreClock.now());
        count.setGeneralComments("Transfer " + transfer.getInventoryTransferId() + ": " + note);
        entityManager.persist(count);

        InventoryItemVariance variance = new InventoryItemVariance();
        variance.setInventoryItemId(inventoryService.bulkItem(productId, IN_TRANSIT).getInventoryItemId());
        variance.setPhysicalInventoryId(count.getPhysicalInventoryId());
        variance.setVarianceReasonId(TRANSIT_LOSS);
        variance.setQuantityOnHandVar(lost.negate());
        variance.setAvailableToPromiseVar(BigDecimal.ZERO);
        variance.setComments(note);
        entityManager.persist(variance);
    }

    private BigDecimal receivedQuantity(String shipmentId, String shipmentItemSeqId) {
        return entityManager.createQuery("SELECT COALESCE(SUM(r.quantityAccepted), 0) FROM ShipmentReceipt r "
                        + "WHERE r.shipmentId = :shipmentId AND r.shipmentItemSeqId = :itemSeqId", BigDecimal.class)
                .setParameter("shipmentId", shipmentId)
                .setParameter("itemSeqId", shipmentItemSeqId)
                .getSingleResult();
    }

    private void markDeliveredIfComplete(Shipment shipment, String userId) {
        boolean everyLineComplete = lines(shipment.getShipmentId()).stream()
                .allMatch(line -> CoreStatus.Transfer.COMPLETE.equals(line.getStatusId()));
        if (everyLineComplete) {
            changeShipmentStatus(shipment, CoreStatus.Shipment.DELIVERED, userId);
        }
    }

    private void moveTo(InventoryTransfer transfer, String toStatusId) {
        statusService.requireAllowed(FLOW, transfer.getStatusId(), toStatusId);
        transfer.setStatusId(toStatusId);
    }

    private void changeShipmentStatus(Shipment shipment, String statusId, String userId) {
        shipment.setStatusId(statusId);
        shipmentService.recordStatus(shipment.getShipmentId(), statusId, userId);
    }

    private String productOf(InventoryTransfer transfer) {
        return entityManager.find(InventoryItem.class, transfer.getInventoryItemId()).getProductId();
    }

    private static InventoryService.Cause cause(InventoryTransfer transfer, String userId, String what) {
        return new InventoryService.Cause(null, null, transfer.getShipmentId(), null, userId, "Transfer " + transfer.getInventoryTransferId() + " " + what);
    }

    /** The transfer's shipment, locked until the transaction ends. */
    private Shipment loadTransferForUpdate(String shipmentId) {
        Shipment shipment = entityManager.find(Shipment.class, shipmentId, LockModeType.PESSIMISTIC_WRITE);
        if (shipment == null || !TRANSFER_SHIPMENT_TYPE.equals(shipment.getShipmentTypeId())) {
            throw new IllegalArgumentException("Unknown transfer " + shipmentId);
        }
        return shipment;
    }

    /** Adds a note to a comment, keeping it within the column's length. */
    private static String appendNote(String existing, String note) {
        String combined = existing == null || existing.isBlank() ? note : existing + "; " + note;
        return combined.length() > MAX_COMMENT_LENGTH ? combined.substring(0, MAX_COMMENT_LENGTH) : combined;
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
