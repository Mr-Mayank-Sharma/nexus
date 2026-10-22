package com.nexus.core.service;

import com.nexus.core.CoreStatus;
import com.nexus.core.model.inventory.InventoryItem;
import com.nexus.core.model.order.OrderItem;
import com.nexus.core.model.shipment.Picklist;
import com.nexus.core.model.shipment.PicklistBin;
import com.nexus.core.model.shipment.PicklistItem;
import com.nexus.core.model.shipment.PicklistRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Picking at a store or a warehouse without a WMS (store fulfilment).
 * <ol>
 *   <li>{@link #create}: a picklist gathers order ship groups held at one facility, one bin per ship group, one line
 *       per hold the items have there;</li>
 *   <li>{@link #assign}: a picker takes it;</li>
 *   <li>{@link #recordPick}: the picker says how many of each item they found;</li>
 *   <li>{@link #complete}: what was not found is short-picked (so routing sends it elsewhere), and every bin with
 *       something in it is shipped.</li>
 * </ol>
 */
@Service("corePicklistService")
@Transactional
public class PicklistService {

    public static final String FLOW = StatusService.DEFAULT_FLOW;

    static final String PICKER_ROLE = "PICKER";

    /** Statuses after which a picklist can no longer change. */
    private static final Set<String> FINISHED = Set.of(CoreStatus.Picklist.COMPLETED, CoreStatus.Picklist.CANCELLED);

    private final EntityManager entityManager;
    private final SequenceService sequenceService;
    private final StatusService statusService;
    private final OrderService orderService;
    private final OrderReservationService reservationService;
    private final ShipmentService shipmentService;

    public PicklistService(EntityManager entityManager, SequenceService sequenceService, StatusService statusService, OrderService orderService,
                           OrderReservationService reservationService, ShipmentService shipmentService) {
        this.entityManager = entityManager;
        this.sequenceService = sequenceService;
        this.statusService = statusService;
        this.orderService = orderService;
        this.reservationService = reservationService;
        this.shipmentService = shipmentService;
    }

    /** One ship group of one order. */
    public record GroupRef(String orderId, String shipGroupSeqId) {
    }

    /**
     * Makes a picklist for these ship groups at the facility. Each group must be routed to the facility, and must hold
     * stock there: the picklist lines are the holds, so the picker is asked for exactly what was reserved.
     *
     * @return the new picklist's id
     */
    public String create(String facilityId, String shipmentMethodTypeId, List<GroupRef> shipGroups, String userId) {
        if (shipGroups == null || shipGroups.isEmpty()) {
            throw new IllegalArgumentException("A picklist needs at least one order");
        }

        Picklist picklist = new Picklist();
        picklist.setPicklistId(sequenceService.nextId("Picklist"));
        picklist.setFacilityId(facilityId);
        picklist.setShipmentMethodTypeId(shipmentMethodTypeId);
        picklist.setStatusId(statusService.initialStatus(FLOW, CoreStatus.Type.PICKLIST));
        picklist.setPicklistDate(CoreClock.now());
        picklist.setCreatedByUserLogin(userId);
        entityManager.persist(picklist);

        long binNumber = 1;
        for (GroupRef shipGroup : shipGroups) {
            String routedFacility = orderService.shipGroup(shipGroup.orderId(), shipGroup.shipGroupSeqId()).getFacilityId();
            if (!facilityId.equals(routedFacility)) {
                throw new IllegalArgumentException("Order " + shipGroup.orderId() + " ship group " + shipGroup.shipGroupSeqId()
                        + " is not routed to " + facilityId);
            }
            PicklistBin bin = new PicklistBin();
            bin.setPicklistBinId(sequenceService.nextId("PicklistBin"));
            bin.setPicklistId(picklist.getPicklistId());
            bin.setBinLocationNumber(binNumber++);
            bin.setPrimaryOrderId(shipGroup.orderId());
            bin.setPrimaryShipGroupSeqId(shipGroup.shipGroupSeqId());
            entityManager.persist(bin);

            int linesAdded = addPickLines(bin, shipGroup, facilityId);
            if (linesAdded == 0) {
                throw new IllegalStateException("Order " + shipGroup.orderId() + " holds nothing at " + facilityId + " to pick");
            }
        }
        return picklist.getPicklistId();
    }

    /** Adds one pending pick line per hold the group's approved items have at the facility. Returns how many. */
    private int addPickLines(PicklistBin bin, GroupRef shipGroup, String facilityId) {
        int linesAdded = 0;
        for (OrderItem orderItem : orderService.items(shipGroup.orderId())) {
            boolean inThisGroup = shipGroup.shipGroupSeqId().equals(orderItem.getShipGroupSeqId());
            if (!inThisGroup || !CoreStatus.Item.APPROVED.equals(orderItem.getStatusId())) {
                continue;
            }
            for (OrderReservationService.Held hold : reservationService.held(shipGroup.orderId(), orderItem.getOrderItemSeqId())) {
                InventoryItem inventoryItem = entityManager.find(InventoryItem.class, hold.inventoryItemId());
                if (!facilityId.equals(inventoryItem.getFacilityId())) {
                    continue;   // held at another facility: not picked here
                }
                PicklistItem line = new PicklistItem();
                line.setPicklistBinId(bin.getPicklistBinId());
                line.setOrderId(shipGroup.orderId());
                line.setOrderItemSeqId(orderItem.getOrderItemSeqId());
                line.setShipGroupSeqId(shipGroup.shipGroupSeqId());
                line.setInventoryItemId(hold.inventoryItemId());
                line.setItemStatusId(CoreStatus.Picklist.ITEM_PENDING);
                line.setQuantity(hold.quantity());
                entityManager.persist(line);
                linesAdded++;
            }
        }
        return linesAdded;
    }

    /** Gives the picklist to a picker. The first assignment moves it from input to assigned. */
    public void assign(String picklistId, String pickerPartyId, String userId) {
        Picklist picklist = loadPicklistForUpdate(picklistId);
        PicklistRole picker = new PicklistRole();
        picker.setPicklistId(picklistId);
        picker.setPartyId(pickerPartyId);
        picker.setRoleTypeId(PICKER_ROLE);
        picker.setFromDate(CoreClock.now());
        entityManager.persist(picker);

        if (CoreStatus.Picklist.INPUT.equals(picklist.getStatusId())) {
            statusService.requireAllowed(FLOW, picklist.getStatusId(), CoreStatus.Picklist.ASSIGNED);
            picklist.setStatusId(CoreStatus.Picklist.ASSIGNED);
        }
    }

    /**
     * What the picker found of one order item: from zero (nothing found) up to what the picklist asked for. Spread over
     * the item's pick lines in order; a line that got nothing is cancelled.
     */
    public void recordPick(String picklistId, String orderId, String orderItemSeqId, BigDecimal foundQuantity) {
        Picklist picklist = loadPicklistForUpdate(picklistId);
        if (FINISHED.contains(picklist.getStatusId())) {
            throw new IllegalStateException("Picklist " + picklistId + " is " + picklist.getStatusId());
        }
        List<PicklistItem> linesOfItem = items(picklistId).stream()
                .filter(line -> line.getOrderId().equals(orderId) && line.getOrderItemSeqId().equals(orderItemSeqId))
                .toList();
        if (linesOfItem.isEmpty()) {
            throw new IllegalArgumentException("Picklist " + picklistId + " has no item " + orderItemSeqId + " of order " + orderId);
        }
        BigDecimal askedQuantity = linesOfItem.stream().map(PicklistItem::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (foundQuantity.signum() < 0 || foundQuantity.compareTo(askedQuantity) > 0) {
            throw new IllegalArgumentException("Found must be between 0 and " + askedQuantity.stripTrailingZeros().toPlainString());
        }

        BigDecimal stillToSpread = foundQuantity;
        for (PicklistItem line : linesOfItem) {
            BigDecimal pickedOnLine = line.getQuantity().min(stillToSpread);
            line.setPickedQuantity(pickedOnLine);
            line.setItemStatusId(pickedOnLine.signum() > 0 ? CoreStatus.Picklist.ITEM_PICKED : CoreStatus.Picklist.ITEM_CANCELLED);
            stillToSpread = stillToSpread.subtract(pickedOnLine);
        }
    }

    /**
     * Completes the picklist. Every line must have a pick recorded. Then:
     * <ol>
     *   <li>what was not picked is short-picked: its hold here is released and this facility is excluded for the item;</li>
     *   <li>each ship group with something picked gets a shipment, shipped with the carrier and service given.</li>
     * </ol>
     *
     * @return the ids of the shipments made
     */
    public List<String> complete(String picklistId, String carrierPartyId, String shipmentMethodTypeId, String userId) {
        Picklist picklist = loadPicklistForUpdate(picklistId);
        statusService.requireAllowed(FLOW, picklist.getStatusId(), CoreStatus.Picklist.PICKED);
        List<PicklistItem> allLines = items(picklistId);
        boolean somethingNotPicked = allLines.stream().anyMatch(line -> CoreStatus.Picklist.ITEM_PENDING.equals(line.getItemStatusId()));
        if (somethingNotPicked) {
            throw new IllegalStateException("Picklist " + picklistId + " still has items to pick");
        }
        picklist.setStatusId(CoreStatus.Picklist.PICKED);

        // Step 1: short picks, and what was picked per ship group (quantity per order item).
        Map<GroupRef, Map<String, BigDecimal>> pickedByShipGroup = new LinkedHashMap<>();
        for (PicklistItem line : allLines) {
            BigDecimal picked = line.getPickedQuantity() == null ? BigDecimal.ZERO : line.getPickedQuantity();
            BigDecimal shortQuantity = line.getQuantity().subtract(picked);
            if (shortQuantity.signum() > 0) {
                reservationService.shortPick(line.getOrderId(), line.getOrderItemSeqId(), picklist.getFacilityId(), shortQuantity,
                        "Not found when picking " + picklistId, userId);
            }
            if (picked.signum() > 0) {
                GroupRef shipGroup = new GroupRef(line.getOrderId(), line.getShipGroupSeqId());
                pickedByShipGroup.computeIfAbsent(shipGroup, key -> new LinkedHashMap<>())
                        .merge(line.getOrderItemSeqId(), picked, BigDecimal::add);
            }
        }

        // Step 2: one shipment per ship group, shipped now.
        List<String> shipmentIds = new ArrayList<>();
        for (Map.Entry<GroupRef, Map<String, BigDecimal>> entry : pickedByShipGroup.entrySet()) {
            GroupRef shipGroup = entry.getKey();
            String shipmentId = shipmentService.createSalesShipment(shipGroup.orderId(), shipGroup.shipGroupSeqId(), entry.getValue(), userId);
            shipmentService.ship(shipmentId, carrierPartyId, shipmentMethodTypeId, null, null, userId);
            shipmentIds.add(shipmentId);
        }

        allLines.stream()
                .filter(line -> CoreStatus.Picklist.ITEM_PICKED.equals(line.getItemStatusId()))
                .forEach(line -> line.setItemStatusId(CoreStatus.Picklist.ITEM_COMPLETED));
        picklist.setStatusId(CoreStatus.Picklist.COMPLETED);
        return shipmentIds;
    }

    /** Cancels the picklist. The orders keep their holds and can go on another picklist. */
    public void cancel(String picklistId) {
        Picklist picklist = loadPicklistForUpdate(picklistId);
        statusService.requireAllowed(FLOW, picklist.getStatusId(), CoreStatus.Picklist.CANCELLED);
        picklist.setStatusId(CoreStatus.Picklist.CANCELLED);
        items(picklistId).forEach(line -> line.setItemStatusId(CoreStatus.Picklist.ITEM_CANCELLED));
    }

    /** The picklist's lines, bin by bin. */
    @Transactional(readOnly = true)
    public List<PicklistItem> items(String picklistId) {
        return entityManager.createQuery(
                        "SELECT i FROM PicklistItem i, PicklistBin b WHERE b.picklistBinId = i.picklistBinId AND b.picklistId = :picklistId "
                                + "ORDER BY b.binLocationNumber, i.orderItemSeqId", PicklistItem.class)
                .setParameter("picklistId", picklistId)
                .getResultList();
    }

    /** The picklist, locked until the transaction ends. */
    private Picklist loadPicklistForUpdate(String picklistId) {
        Picklist picklist = entityManager.find(Picklist.class, picklistId, LockModeType.PESSIMISTIC_WRITE);
        if (picklist == null) {
            throw new IllegalArgumentException("Unknown picklist " + picklistId);
        }
        return picklist;
    }
}
