package com.nexus.core.service;

import com.nexus.core.CoreStatus;
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
import com.nexus.core.CoreTransactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Outgoing shipments in the OFBiz way.
 * <p>
 * A sales shipment carries some or all of one order ship group from that group's facility. It has:
 * <ul>
 *   <li>shipment items: product and quantity, each linked to its order item through {@code order_shipment};</li>
 *   <li>packages (boxes) with their contents;</li>
 *   <li>a route segment: the carrier, the service and the tracking number, with one tracking code per box;</li>
 *   <li>item issuances: the stock each order item took when it shipped, and from which inventory item.</li>
 * </ul>
 * Shipping consumes the order items' holds, lowers on hand, sets the tracking on the order and completes items (and
 * the order) that are fully shipped. {@link #confirm} records a shipment a WMS made (doc 27, section 3.4) in one call.
 */
@Service("coreShipmentService")
@CoreTransactional
public class ShipmentService {

    public static final String FLOW = StatusService.DEFAULT_FLOW;

    static final String SALES_SHIPMENT_TYPE = "SALES_SHIPMENT";
    static final String OWN_PACKAGING_BOX = "YOURPACKNG";
    static final String DEFAULT_WEIGHT_UNIT = "WT_kg";
    /** Every shipment here has one route segment: one carrier from the shipping facility to the customer. */
    static final String FIRST_SEGMENT = SequenceService.seqId(1);

    private final EntityManager entityManager;
    private final SequenceService sequenceService;
    private final StatusService statusService;
    private final OrderService orderService;
    private final OrderReservationService reservationService;
    private final InventoryService inventoryService;

    public ShipmentService(EntityManager entityManager, SequenceService sequenceService, StatusService statusService, OrderService orderService,
                           OrderReservationService reservationService, InventoryService inventoryService) {
        this.entityManager = entityManager;
        this.sequenceService = sequenceService;
        this.statusService = statusService;
        this.orderService = orderService;
        this.reservationService = reservationService;
        this.inventoryService = inventoryService;
    }

    // ── making a shipment ──

    /**
     * Makes a shipment for a ship group, from the group's facility.
     *
     * @param quantities quantity per order item seq id; null ships everything still open and not yet in a shipment
     * @return the new shipment's id
     */
    public String createSalesShipment(String orderId, String shipGroupSeqId, Map<String, BigDecimal> quantities, String userId) {
        OrderItemShipGroup shipGroup = orderService.shipGroup(orderId, shipGroupSeqId);
        if (shipGroup.getFacilityId() == null) {
            throw new IllegalStateException("Ship group " + shipGroupSeqId + " of order " + orderId + " has no facility yet; route it first");
        }
        Map<String, BigDecimal> quantityByItem = quantitiesToShip(orderId, shipGroupSeqId, quantities);

        Shipment shipment = new Shipment();
        shipment.setShipmentId(sequenceService.nextId("Shipment"));
        shipment.setShipmentTypeId(SALES_SHIPMENT_TYPE);
        shipment.setStatusId(statusService.initialStatus(FLOW, CoreStatus.Type.SHIPMENT));
        shipment.setPrimaryOrderId(orderId);
        shipment.setPrimaryShipGroupSeqId(shipGroupSeqId);
        shipment.setOriginFacilityId(shipGroup.getFacilityId());
        shipment.setDestinationContactMechId(shipGroup.getContactMechId());
        shipment.setPartyIdTo(orderService.partyInRole(orderId, "SHIP_TO_CUSTOMER").orElse(null));
        shipment.setEstimatedArrivalDate(shipGroup.getEstimatedDeliveryDate());
        shipment.setCreatedDate(CoreClock.now());
        shipment.setCreatedByUserLogin(userId);
        entityManager.persist(shipment);
        recordStatus(shipment.getShipmentId(), shipment.getStatusId(), userId);

        // One shipment item per order item, numbered 00001, 00002, ... and linked back to the order item.
        int shipmentItemNumber = 1;
        for (Map.Entry<String, BigDecimal> entry : quantityByItem.entrySet()) {
            String orderItemSeqId = entry.getKey();
            BigDecimal quantity = entry.getValue();
            OrderItem orderItem = orderService.orderItem(orderId, orderItemSeqId);

            ShipmentItem shipmentItem = new ShipmentItem();
            shipmentItem.setShipmentId(shipment.getShipmentId());
            shipmentItem.setShipmentItemSeqId(SequenceService.seqId(shipmentItemNumber++));
            shipmentItem.setProductId(orderItem.getProductId());
            shipmentItem.setQuantity(quantity);
            entityManager.persist(shipmentItem);

            OrderShipment link = new OrderShipment();
            link.setOrderId(orderId);
            link.setOrderItemSeqId(orderItemSeqId);
            link.setShipGroupSeqId(shipGroupSeqId);
            link.setShipmentId(shipment.getShipmentId());
            link.setShipmentItemSeqId(shipmentItem.getShipmentItemSeqId());
            link.setQuantity(quantity);
            entityManager.persist(link);
        }
        return shipment.getShipmentId();
    }

    /**
     * What each item of the ship group will ship. Each must be approved and may not ship more than is still open and
     * not already in another (not cancelled) shipment. Asking for an item outside the group is an error.
     */
    private Map<String, BigDecimal> quantitiesToShip(String orderId, String shipGroupSeqId, Map<String, BigDecimal> requested) {
        Map<String, BigDecimal> quantityByItem = new LinkedHashMap<>();
        for (OrderItem orderItem : orderService.items(orderId)) {
            if (!shipGroupSeqId.equals(orderItem.getShipGroupSeqId())) {
                continue;
            }
            String itemSeqId = orderItem.getOrderItemSeqId();
            BigDecimal leftToShip = OrderService.openQuantity(orderItem).subtract(quantityInShipments(orderId, itemSeqId));
            BigDecimal quantity = requested == null ? leftToShip : requested.getOrDefault(itemSeqId, BigDecimal.ZERO);
            if (quantity.signum() == 0) {
                continue;
            }
            if (!CoreStatus.Item.APPROVED.equals(orderItem.getStatusId())) {
                throw new IllegalStateException("Item " + itemSeqId + " is " + orderItem.getStatusId() + "; only approved items ship");
            }
            if (quantity.signum() < 0 || quantity.compareTo(leftToShip) > 0) {
                throw new IllegalArgumentException("Item " + itemSeqId + " has " + plain(leftToShip) + " left to ship");
            }
            quantityByItem.put(itemSeqId, quantity);
        }
        if (requested != null) {
            for (Map.Entry<String, BigDecimal> entry : requested.entrySet()) {
                boolean outsideGroup = !quantityByItem.containsKey(entry.getKey()) && entry.getValue().signum() != 0;
                if (outsideGroup) {
                    throw new IllegalArgumentException("Ship group " + shipGroupSeqId + " has no item " + entry.getKey());
                }
            }
        }
        if (quantityByItem.isEmpty()) {
            throw new IllegalStateException("Nothing left to ship in ship group " + shipGroupSeqId + " of order " + orderId);
        }
        return quantityByItem;
    }

    // ── boxes and packing ──

    /**
     * Adds a box with its contents (quantity per shipment item seq id) and returns its package seq id. Only before the
     * shipment is packed, and never more of an item than the shipment carries.
     */
    public String addPackage(String shipmentId, String boxTypeId, BigDecimal weight, String weightUomId, Map<String, BigDecimal> contents) {
        Shipment shipment = loadShipmentForUpdate(shipmentId);
        if (!CoreStatus.Shipment.BEFORE_PACKING.contains(shipment.getStatusId())) {
            throw new IllegalStateException("Shipment " + shipmentId + " is " + shipment.getStatusId() + "; boxes are added before packing");
        }

        ShipmentPackage box = new ShipmentPackage();
        box.setShipmentId(shipmentId);
        box.setShipmentPackageSeqId(SequenceService.seqId(packages(shipmentId).size() + 1));
        box.setShipmentBoxTypeId(boxTypeId);
        box.setWeight(weight);
        box.setWeightUomId(weight == null ? null : weightUomId == null ? DEFAULT_WEIGHT_UNIT : weightUomId);
        box.setDateCreated(CoreClock.now());
        entityManager.persist(box);

        for (Map.Entry<String, BigDecimal> entry : contents.entrySet()) {
            String shipmentItemSeqId = entry.getKey();
            BigDecimal quantity = entry.getValue();
            ShipmentItem shipmentItem = entityManager.find(ShipmentItem.class, new ShipmentItem.Key(shipmentId, shipmentItemSeqId));
            if (shipmentItem == null) {
                throw new IllegalArgumentException("Shipment " + shipmentId + " has no item " + shipmentItemSeqId);
            }
            BigDecimal alreadyPacked = packedQuantity(shipmentId, shipmentItemSeqId);
            if (quantity.signum() <= 0 || alreadyPacked.add(quantity).compareTo(shipmentItem.getQuantity()) > 0) {
                throw new IllegalArgumentException("Shipment item " + shipmentItemSeqId + " has " + plain(shipmentItem.getQuantity().subtract(alreadyPacked))
                        + " left to pack");
            }
            ShipmentPackageContent content = new ShipmentPackageContent();
            content.setShipmentId(shipmentId);
            content.setShipmentPackageSeqId(box.getShipmentPackageSeqId());
            content.setShipmentItemSeqId(shipmentItemSeqId);
            content.setQuantity(quantity);
            entityManager.persist(content);
        }
        return box.getShipmentPackageSeqId();
    }

    /**
     * Packs the shipment. With no boxes yet, everything goes in one box of the shipper's own packaging; with boxes,
     * every unit must already be in one.
     */
    public void pack(String shipmentId, String userId) {
        Shipment shipment = loadShipmentForUpdate(shipmentId);
        statusService.requireAllowed(FLOW, shipment.getStatusId(), CoreStatus.Shipment.PACKED);

        Map<String, BigDecimal> unpacked = new LinkedHashMap<>();
        for (ShipmentItem shipmentItem : shipmentItems(shipmentId)) {
            BigDecimal notInABox = shipmentItem.getQuantity().subtract(packedQuantity(shipmentId, shipmentItem.getShipmentItemSeqId()));
            if (notInABox.signum() > 0) {
                unpacked.put(shipmentItem.getShipmentItemSeqId(), notInABox);
            }
        }
        if (!unpacked.isEmpty()) {
            boolean hasBoxes = !packages(shipmentId).isEmpty();
            if (hasBoxes) {
                throw new IllegalStateException("Shipment " + shipmentId + " has items not in a box: " + unpacked.keySet());
            }
            addPackage(shipmentId, OWN_PACKAGING_BOX, null, null, unpacked);
        }
        changeStatus(shipment, CoreStatus.Shipment.PACKED, userId);
    }

    // ── labels ──

    /** A carrier label of one box: its tracking code and where to get the label again. */
    public record PackageLabel(String shipmentPackageSeqId, String trackingCode, String labelUrl) {
    }

    /**
     * Records carrier labels made for a shipment (by the gateway): the carrier, the service, the cost, and each box's
     * tracking code and label link. Packs the shipment first if needed. Shipping later keeps all of this.
     */
    public void recordLabels(String shipmentId, String carrierPartyId, String shipmentMethodTypeId, List<PackageLabel> labels, BigDecimal cost,
                             String currencyUomId, String userId) {
        Shipment shipment = loadShipmentForUpdate(shipmentId);
        if (!CoreStatus.Shipment.PACKED.equals(shipment.getStatusId())) {
            pack(shipmentId, userId);
        }
        ShipmentRouteSegment segment = routeSegmentOf(shipment);
        segment.setCarrierPartyId(carrierPartyId);
        segment.setShipmentMethodTypeId(shipmentMethodTypeId);
        segment.setActualCost(cost);
        segment.setCurrencyUomId(cost == null ? null : currencyUomId);
        for (PackageLabel label : labels) {
            ShipmentPackageRouteSeg boxOnSegment = boxOnSegment(shipmentId, label.shipmentPackageSeqId());
            boxOnSegment.setTrackingCode(label.trackingCode());
            boxOnSegment.setLabelUrl(label.labelUrl());
        }
        if (!labels.isEmpty()) {
            segment.setTrackingIdNumber(labels.get(0).trackingCode());
        }
    }

    // ── shipping ──

    /**
     * Ships the shipment with a carrier and service ({@code trackingByPackage}: tracking code per package seq id; may
     * be null when labels were already recorded). Packs it first if needed. Then, for each order item in it:
     * <ol>
     *   <li>take the stock from the item's holds at the shipping facility (holds consumed, on hand down);</li>
     *   <li>whatever was not held is taken from what is available there now;</li>
     *   <li>record an item issuance per inventory item used.</li>
     * </ol>
     * Finally the order gets the tracking number, and fully shipped items and orders are completed.
     */
    public void ship(String shipmentId, String carrierPartyId, String shipmentMethodTypeId, Map<String, String> trackingByPackage,
                     LocalDateTime shippedAt, String userId) {
        Shipment shipment = loadShipmentForUpdate(shipmentId);
        if (CoreStatus.Shipment.BEFORE_PACKING.contains(shipment.getStatusId())) {
            pack(shipmentId, userId);
        }
        statusService.requireAllowed(FLOW, shipment.getStatusId(), CoreStatus.Shipment.SHIPPED);
        LocalDateTime shippedTime = shippedAt == null ? CoreClock.now() : shippedAt;

        // Carrier and tracking; labels recorded earlier are kept unless new codes are given.
        ShipmentRouteSegment segment = routeSegmentOf(shipment);
        if (carrierPartyId != null) {
            segment.setCarrierPartyId(carrierPartyId);
        }
        if (shipmentMethodTypeId != null) {
            segment.setShipmentMethodTypeId(shipmentMethodTypeId);
        }
        segment.setActualStartDate(shippedTime);
        String firstTrackingCode = segment.getTrackingIdNumber();
        for (ShipmentPackage box : packages(shipmentId)) {
            ShipmentPackageRouteSeg boxOnSegment = boxOnSegment(shipmentId, box.getShipmentPackageSeqId());
            String newCode = trackingByPackage == null ? null : trackingByPackage.get(box.getShipmentPackageSeqId());
            if (newCode != null) {
                boxOnSegment.setTrackingCode(newCode);
            }
            if (firstTrackingCode == null) {
                firstTrackingCode = boxOnSegment.getTrackingCode();
            }
        }
        segment.setTrackingIdNumber(firstTrackingCode);

        // Stock leaves the building.
        List<OrderShipment> linesShipped = orderShipmentsOf(shipmentId);
        for (OrderShipment line : linesShipped) {
            issueStock(shipment, line, shippedTime, userId);
        }
        changeStatus(shipment, CoreStatus.Shipment.SHIPPED, userId);

        // The order follows.
        if (firstTrackingCode != null) {
            orderService.setTracking(shipment.getPrimaryOrderId(), shipment.getPrimaryShipGroupSeqId(), firstTrackingCode);
        }
        for (OrderShipment line : linesShipped) {
            BigDecimal shippedSoFar = issuedQuantity(line.getOrderId(), line.getOrderItemSeqId());
            orderService.markShipped(line.getOrderId(), line.getOrderItemSeqId(), shippedSoFar, userId);
        }
    }

    /** The carrier delivered it. */
    public void deliver(String shipmentId, LocalDateTime deliveredAt, String userId) {
        Shipment shipment = loadShipmentForUpdate(shipmentId);
        statusService.requireAllowed(FLOW, shipment.getStatusId(), CoreStatus.Shipment.DELIVERED);
        changeStatus(shipment, CoreStatus.Shipment.DELIVERED, userId);
        LocalDateTime arrival = deliveredAt == null ? CoreClock.now() : deliveredAt;
        entityManager.createQuery("SELECT r FROM ShipmentRouteSegment r WHERE r.shipmentId = :shipmentId", ShipmentRouteSegment.class)
                .setParameter("shipmentId", shipmentId)
                .getResultList()
                .forEach(segment -> segment.setActualArrivalDate(arrival));
    }

    /** Cancels a shipment not yet shipped. Its items can go in another shipment; the order keeps its holds. */
    public void cancel(String shipmentId, String userId) {
        Shipment shipment = loadShipmentForUpdate(shipmentId);
        statusService.requireAllowed(FLOW, shipment.getStatusId(), CoreStatus.Shipment.CANCELLED);
        changeStatus(shipment, CoreStatus.Shipment.CANCELLED, userId);
    }

    // ── a shipment another system made (doc 27, section 3.4) ──

    /** A box in a shipment confirmation: its tracking code and what is in it (quantity per order item seq id). */
    public record ConfirmedPackage(String trackingCode, BigDecimal weight, String weightUomId, String boxTypeId, Map<String, BigDecimal> contents) {
    }

    public record Confirmation(String externalShipmentId, String orderId, String shipGroupSeqId, LocalDateTime shippedAt, String carrierPartyId,
                               String shipmentMethodTypeId, List<ConfirmedPackage> packages) {
    }

    /**
     * Records a shipment a WMS or a carrier integration made: creates it for what the boxes hold, adds the boxes, and
     * ships it. The same confirmation sent again (same external shipment id for the same order) returns the shipment
     * already recorded and changes nothing.
     */
    public String confirm(Confirmation confirmation, String userId) {
        Optional<String> alreadyRecorded = shipmentWithExternalId(confirmation.externalShipmentId(), confirmation.orderId());
        if (alreadyRecorded.isPresent()) {
            return alreadyRecorded.get();
        }
        if (confirmation.packages() == null || confirmation.packages().isEmpty()) {
            throw new IllegalArgumentException("A shipment confirmation needs at least one package");
        }

        // Total per order item over all boxes: that is what the shipment carries.
        Map<String, BigDecimal> quantityByOrderItem = new LinkedHashMap<>();
        for (ConfirmedPackage box : confirmation.packages()) {
            box.contents().forEach((orderItemSeqId, quantity) -> quantityByOrderItem.merge(orderItemSeqId, quantity, BigDecimal::add));
        }
        String shipmentId = createSalesShipment(confirmation.orderId(), confirmation.shipGroupSeqId(), quantityByOrderItem, userId);
        loadShipment(shipmentId).setExternalId(confirmation.externalShipmentId());

        // Boxes are given by order item; the shipment's boxes are by shipment item.
        Map<String, String> shipmentItemByOrderItem = new LinkedHashMap<>();
        for (OrderShipment line : orderShipmentsOf(shipmentId)) {
            shipmentItemByOrderItem.put(line.getOrderItemSeqId(), line.getShipmentItemSeqId());
        }
        Map<String, String> trackingByPackage = new LinkedHashMap<>();
        for (ConfirmedPackage box : confirmation.packages()) {
            Map<String, BigDecimal> contentsByShipmentItem = new LinkedHashMap<>();
            box.contents().forEach((orderItemSeqId, quantity) -> contentsByShipmentItem.put(shipmentItemByOrderItem.get(orderItemSeqId), quantity));
            String packageSeqId = addPackage(shipmentId, box.boxTypeId(), box.weight(), box.weightUomId(), contentsByShipmentItem);
            trackingByPackage.put(packageSeqId, box.trackingCode());
        }
        ship(shipmentId, confirmation.carrierPartyId(), confirmation.shipmentMethodTypeId(), trackingByPackage, confirmation.shippedAt(), userId);
        return shipmentId;
    }

    // ── reading ──

    /** How much of an order item has shipped, over all its shipments. */
    @CoreTransactional(readOnly = true)
    public BigDecimal issuedQuantity(String orderId, String orderItemSeqId) {
        BigDecimal total = entityManager.createQuery(
                        "SELECT SUM(i.quantity - COALESCE(i.cancelQuantity, 0)) FROM ItemIssuance i "
                                + "WHERE i.orderId = :orderId AND i.orderItemSeqId = :itemSeqId", BigDecimal.class)
                .setParameter("orderId", orderId)
                .setParameter("itemSeqId", orderItemSeqId)
                .getSingleResult();
        return total == null ? BigDecimal.ZERO : total;
    }

    @CoreTransactional(readOnly = true)
    public String status(String shipmentId) {
        return loadShipment(shipmentId).getStatusId();
    }

    @CoreTransactional(readOnly = true)
    public Shipment get(String shipmentId) {
        return loadShipment(shipmentId);
    }

    /** A shipment's boxes in order. */
    @CoreTransactional(readOnly = true)
    public List<ShipmentPackage> packages(String shipmentId) {
        return entityManager.createQuery("SELECT p FROM ShipmentPackage p WHERE p.shipmentId = :shipmentId ORDER BY p.shipmentPackageSeqId",
                        ShipmentPackage.class)
                .setParameter("shipmentId", shipmentId)
                .getResultList();
    }

    /** Writes a {@code shipment_status} history row. Shared with transfers, which are shipments too. */
    void recordStatus(String shipmentId, String statusId, String userId) {
        ShipmentStatus history = new ShipmentStatus();
        history.setShipmentId(shipmentId);
        history.setStatusId(statusId);
        history.setStatusDate(CoreClock.now());
        history.setChangeByUserLoginId(userId);
        entityManager.persist(history);
    }

    // ── internals ──

    /**
     * Takes the stock for one shipped line: first from the order item's holds at the shipping facility, then (for what
     * was not held) from what is available there. A line without a product (gift wrap, a copied line) takes no stock
     * but is still recorded as issued.
     */
    private void issueStock(Shipment shipment, OrderShipment line, LocalDateTime shippedTime, String userId) {
        String orderId = line.getOrderId();
        String orderItemSeqId = line.getOrderItemSeqId();
        String facilityId = shipment.getOriginFacilityId();
        InventoryService.Cause cause = new InventoryService.Cause(orderId, orderItemSeqId, shipment.getShipmentId(), null, userId, null);
        BigDecimal stillToIssue = line.getQuantity();

        for (OrderReservationService.Held hold : reservationService.held(orderId, orderItemSeqId)) {
            if (stillToIssue.signum() <= 0) {
                break;
            }
            InventoryItem inventoryItem = entityManager.find(InventoryItem.class, hold.inventoryItemId());
            if (!inventoryItem.getFacilityId().equals(facilityId)) {
                continue;   // a hold somewhere else does not ship from here
            }
            BigDecimal takenHere = hold.quantity().min(stillToIssue);
            reservationService.consume(orderId, orderItemSeqId, hold.inventoryItemId(), takenHere);
            inventoryService.ship(inventoryItem.getProductId(), facilityId, takenHere, cause);
            recordIssuance(line, hold.inventoryItemId(), takenHere, shippedTime, userId);
            stillToIssue = stillToIssue.subtract(takenHere);
        }
        if (stillToIssue.signum() <= 0) {
            return;
        }

        OrderItem orderItem = orderService.orderItem(orderId, orderItemSeqId);
        if (orderItem.getProductId() == null) {
            recordIssuance(line, null, stillToIssue, shippedTime, userId);
            return;
        }
        // Not held: hold it now (this checks it is available) and ship it at once.
        inventoryService.reserve(orderItem.getProductId(), facilityId, stillToIssue, cause);
        inventoryService.ship(orderItem.getProductId(), facilityId, stillToIssue, cause);
        String inventoryItemId = inventoryService.bulkItem(orderItem.getProductId(), facilityId).getInventoryItemId();
        recordIssuance(line, inventoryItemId, stillToIssue, shippedTime, userId);
    }

    private void recordIssuance(OrderShipment line, String inventoryItemId, BigDecimal quantity, LocalDateTime issuedAt, String userId) {
        ItemIssuance issuance = new ItemIssuance();
        issuance.setItemIssuanceId(sequenceService.nextId("ItemIssuance"));
        issuance.setOrderId(line.getOrderId());
        issuance.setOrderItemSeqId(line.getOrderItemSeqId());
        issuance.setShipGroupSeqId(line.getShipGroupSeqId());
        issuance.setInventoryItemId(inventoryItemId);
        issuance.setShipmentId(line.getShipmentId());
        issuance.setShipmentItemSeqId(line.getShipmentItemSeqId());
        issuance.setIssuedDateTime(issuedAt);
        issuance.setIssuedByUserLogin(userId);
        issuance.setQuantity(quantity);
        issuance.setCancelQuantity(BigDecimal.ZERO);
        entityManager.persist(issuance);
    }

    /** The shipment's route segment, created when the shipment has none yet. */
    private ShipmentRouteSegment routeSegmentOf(Shipment shipment) {
        ShipmentRouteSegment segment = entityManager.find(ShipmentRouteSegment.class, new ShipmentRouteSegment.Key(shipment.getShipmentId(), FIRST_SEGMENT));
        if (segment == null) {
            segment = new ShipmentRouteSegment();
            segment.setShipmentId(shipment.getShipmentId());
            segment.setShipmentRouteSegmentId(FIRST_SEGMENT);
            segment.setOriginFacilityId(shipment.getOriginFacilityId());
            entityManager.persist(segment);
        }
        return segment;
    }

    /** The row that carries one box's tracking code on the route segment, created when missing. */
    private ShipmentPackageRouteSeg boxOnSegment(String shipmentId, String shipmentPackageSeqId) {
        ShipmentPackageRouteSeg boxOnSegment = entityManager.find(ShipmentPackageRouteSeg.class,
                new ShipmentPackageRouteSeg.Key(shipmentId, shipmentPackageSeqId, FIRST_SEGMENT));
        if (boxOnSegment == null) {
            boxOnSegment = new ShipmentPackageRouteSeg();
            boxOnSegment.setShipmentId(shipmentId);
            boxOnSegment.setShipmentPackageSeqId(shipmentPackageSeqId);
            boxOnSegment.setShipmentRouteSegmentId(FIRST_SEGMENT);
            entityManager.persist(boxOnSegment);
        }
        return boxOnSegment;
    }

    private Optional<String> shipmentWithExternalId(String externalShipmentId, String orderId) {
        if (externalShipmentId == null) {
            return Optional.empty();
        }
        return entityManager.createQuery("SELECT s.shipmentId FROM Shipment s WHERE s.externalId = :externalId AND s.primaryOrderId = :orderId",
                        String.class)
                .setParameter("externalId", externalShipmentId)
                .setParameter("orderId", orderId)
                .getResultList()
                .stream()
                .findFirst();
    }

    /** How much of an order item is in shipments that are not cancelled. */
    private BigDecimal quantityInShipments(String orderId, String orderItemSeqId) {
        BigDecimal total = entityManager.createQuery(
                        "SELECT SUM(link.quantity) FROM OrderShipment link, Shipment s WHERE s.shipmentId = link.shipmentId "
                                + "AND link.orderId = :orderId AND link.orderItemSeqId = :itemSeqId AND s.statusId <> :cancelled", BigDecimal.class)
                .setParameter("orderId", orderId)
                .setParameter("itemSeqId", orderItemSeqId)
                .setParameter("cancelled", CoreStatus.Shipment.CANCELLED)
                .getSingleResult();
        return total == null ? BigDecimal.ZERO : total;
    }

    private BigDecimal packedQuantity(String shipmentId, String shipmentItemSeqId) {
        BigDecimal total = entityManager.createQuery(
                        "SELECT SUM(c.quantity) FROM ShipmentPackageContent c WHERE c.shipmentId = :shipmentId AND c.shipmentItemSeqId = :itemSeqId",
                        BigDecimal.class)
                .setParameter("shipmentId", shipmentId)
                .setParameter("itemSeqId", shipmentItemSeqId)
                .getSingleResult();
        return total == null ? BigDecimal.ZERO : total;
    }

    private List<ShipmentItem> shipmentItems(String shipmentId) {
        return entityManager.createQuery("SELECT i FROM ShipmentItem i WHERE i.shipmentId = :shipmentId ORDER BY i.shipmentItemSeqId", ShipmentItem.class)
                .setParameter("shipmentId", shipmentId)
                .getResultList();
    }

    private List<OrderShipment> orderShipmentsOf(String shipmentId) {
        return entityManager.createQuery("SELECT o FROM OrderShipment o WHERE o.shipmentId = :shipmentId ORDER BY o.shipmentItemSeqId", OrderShipment.class)
                .setParameter("shipmentId", shipmentId)
                .getResultList();
    }

    private void changeStatus(Shipment shipment, String statusId, String userId) {
        shipment.setStatusId(statusId);
        recordStatus(shipment.getShipmentId(), statusId, userId);
    }

    private Shipment loadShipment(String shipmentId) {
        Shipment shipment = entityManager.find(Shipment.class, shipmentId);
        if (shipment == null) {
            throw new IllegalArgumentException("Unknown shipment " + shipmentId);
        }
        return shipment;
    }

    /** The shipment, locked until the transaction ends, so two changes to it run one after the other. */
    private Shipment loadShipmentForUpdate(String shipmentId) {
        Shipment shipment = entityManager.find(Shipment.class, shipmentId, LockModeType.PESSIMISTIC_WRITE);
        if (shipment == null) {
            throw new IllegalArgumentException("Unknown shipment " + shipmentId);
        }
        return shipment;
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
