package com.nexus.core.service;

import com.nexus.core.CoreStatus;
import com.nexus.core.model.facility.Facility;
import com.nexus.core.model.order.OrderHeader;
import com.nexus.core.model.order.OrderItem;
import com.nexus.core.model.order.OrderItemShipGroup;
import com.nexus.core.model.party.PartyGroup;
import com.nexus.core.model.party.Person;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Service;
import com.nexus.core.CoreTransactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Read-only views of orders, locations and stock problems, as plain records. The operations assistant's tools read
 * through this service, so every query it can run is in one place and none of them can change data.
 */
@Service("coreOperationsReadService")
@CoreTransactional(readOnly = true)
public class OperationsReadService {

    /** The order identification that keeps an order's id from the earlier model (a UUID). */
    static final String LEGACY_ID_TYPE = "NEXUS_LEGACY_ID";
    /** The order screen shows an order without a channel number as "ORD-" and the first 8 characters of that id. */
    static final String SCREEN_ORDER_PREFIX = "ORD-";
    private static final Pattern SHORT_LEGACY_ID = Pattern.compile("[0-9A-F]{8}");


    static final String NOT_ROUTED = "not routed yet";
    static final String PLACING_CUSTOMER_ROLE = "PLACING_CUSTOMER";

    private final EntityManager entityManager;
    private final OrderService orderService;
    private final OrderReservationService reservationService;

    public OperationsReadService(EntityManager entityManager, OrderService orderService, OrderReservationService reservationService) {
        this.entityManager = entityManager;
        this.orderService = orderService;
        this.reservationService = reservationService;
    }

    // ── one order ──

    public record ItemLine(String orderItemSeqId, String description, String productId, BigDecimal quantity, BigDecimal cancelledQuantity,
                           String statusId, BigDecimal heldQuantity, String shipGroupSeqId) {
    }

    /** {@code location} is the facility name, or "not routed yet". */
    public record ShipGroupLine(String shipGroupSeqId, String location, String shipmentMethodTypeId, String trackingNumber) {
    }

    /** A move of the order between locations; {@code from} and {@code to} are facility names. */
    public record LocationChange(LocalDateTime changedAt, String from, String to, String reasonEnumId, String comments) {
    }

    /** {@code customerName} is the real name: the caller masks it before showing it to a model. */
    public record OrderSummary(String orderId, String statusId, String productStoreId, LocalDateTime orderDate, String externalId,
                               BigDecimal grandTotal, String currencyUomId, Optional<String> customerName, List<ItemLine> items,
                               List<ShipGroupLine> shipGroups, List<LocationChange> locationChanges) {
    }

    /**
     * An order found by any id people use for it, tried in this order: the Nexus order id, a channel id recorded as an
     * order identification (Shopify id, marketplace id, PO number), then the order's external id.
     */
    public Optional<OrderSummary> findOrder(String anyId) {
        return resolveOrderId(anyId).map(this::summary);
    }

    /**
     * The order an id names. Tried in turn: the core order id, a channel order id ("#9006" too), the external id, and
     * the order screen's short form "ORD-40B89AC5" (the first 8 characters of the earlier model's id), which counts
     * only when exactly one order starts with it.
     */
    private Optional<String> resolveOrderId(String anyId) {
        String id = anyId == null ? "" : anyId.trim();
        if (id.isEmpty()) {
            return Optional.empty();
        }
        if (entityManager.find(OrderHeader.class, id) != null) {
            return Optional.of(id);
        }
        String withoutHash = id.startsWith("#") ? id.substring(1) : id;
        Optional<String> byChannelId = first(entityManager.createQuery(
                        "SELECT i.orderId FROM OrderIdentification i WHERE i.idValue = :idValue AND i.thruDate IS NULL", String.class)
                .setParameter("idValue", withoutHash));
        if (byChannelId.isPresent()) {
            return byChannelId;
        }
        Optional<String> byExternalId = first(entityManager.createQuery("SELECT h.orderId FROM OrderHeader h WHERE h.externalId = :externalId", String.class)
                .setParameter("externalId", withoutHash));
        if (byExternalId.isPresent()) {
            return byExternalId;
        }
        return byScreenShortId(id);
    }

    /** "ORD-40B89AC5": the order whose earlier-model id starts with 40b89ac5, when exactly one does. */
    private Optional<String> byScreenShortId(String id) {
        String upper = id.toUpperCase(Locale.ROOT);
        String shortId = upper.startsWith(SCREEN_ORDER_PREFIX) ? upper.substring(SCREEN_ORDER_PREFIX.length()) : upper;
        if (!SHORT_LEGACY_ID.matcher(shortId).matches()) {
            return Optional.empty();
        }
        List<String> matches = entityManager.createQuery("SELECT i.orderId FROM OrderIdentification i WHERE i.orderIdentificationTypeId = :legacyType "
                        + "AND i.idValue LIKE :prefix AND i.thruDate IS NULL", String.class)
                .setParameter("legacyType", LEGACY_ID_TYPE)
                .setParameter("prefix", shortId.toLowerCase(Locale.ROOT) + "%")
                .setMaxResults(2)
                .getResultList();
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    private OrderSummary summary(String orderId) {
        OrderHeader header = orderService.header(orderId);
        Optional<String> customerName = orderService.partyInRole(orderId, PLACING_CUSTOMER_ROLE).map(this::partyName);

        List<ItemLine> items = orderService.items(orderId).stream()
                .map(orderItem -> itemLine(orderId, orderItem))
                .toList();

        List<ShipGroupLine> shipGroups = entityManager.createQuery(
                        "SELECT g FROM OrderItemShipGroup g WHERE g.orderId = :orderId ORDER BY g.shipGroupSeqId", OrderItemShipGroup.class)
                .setParameter("orderId", orderId)
                .getResultList()
                .stream()
                .map(group -> new ShipGroupLine(group.getShipGroupSeqId(), locationName(group.getFacilityId()), group.getShipmentMethodTypeId(),
                        group.getTrackingNumber()))
                .toList();

        List<LocationChange> locationChanges = entityManager.createQuery(
                        "SELECT c.changeDatetime, c.fromFacilityId, c.facilityId, c.changeReasonEnumId, c.comments FROM OrderFacilityChange c "
                                + "WHERE c.orderId = :orderId ORDER BY c.changeDatetime", Object[].class)
                .setParameter("orderId", orderId)
                .getResultList()
                .stream()
                .map(row -> new LocationChange((LocalDateTime) row[0], facilityNameOrNull((String) row[1]), facilityNameOrNull((String) row[2]),
                        (String) row[3], (String) row[4]))
                .toList();

        return new OrderSummary(orderId, header.getStatusId(), header.getProductStoreId(), header.getOrderDate(), header.getExternalId(),
                header.getGrandTotal(), header.getCurrencyUomId(), customerName, items, shipGroups, locationChanges);
    }

    private ItemLine itemLine(String orderId, OrderItem orderItem) {
        BigDecimal cancelled = orderItem.getCancelQuantity() == null ? BigDecimal.ZERO : orderItem.getCancelQuantity();
        BigDecimal held = reservationService.reservedQuantity(orderId, orderItem.getOrderItemSeqId());
        return new ItemLine(orderItem.getOrderItemSeqId(), orderItem.getItemDescription(), orderItem.getProductId(), orderItem.getQuantity(),
                cancelled, orderItem.getStatusId(), held, orderItem.getShipGroupSeqId());
    }

    // ── many orders ──

    /** How many orders are in each status, placed since {@code since}, for one store or (null) all. */
    public Map<String, Long> orderCountsByStatus(String productStoreId, LocalDateTime since) {
        String storeFilter = productStoreId == null ? "" : " AND h.productStoreId = :storeId";
        TypedQuery<Object[]> query = entityManager.createQuery(
                        "SELECT h.statusId, COUNT(h) FROM OrderHeader h WHERE h.orderDate >= :since" + storeFilter
                                + " GROUP BY h.statusId ORDER BY h.statusId", Object[].class)
                .setParameter("since", since);
        if (productStoreId != null) {
            query.setParameter("storeId", productStoreId);
        }
        Map<String, Long> countByStatus = new LinkedHashMap<>();
        query.getResultList().forEach(row -> countByStatus.put((String) row[0], (Long) row[1]));
        return countByStatus;
    }

    /** {@code shipsFrom}: the name of each ship group's location, or "not routed yet". */
    public record StuckOrder(String orderId, LocalDateTime orderDate, String productStoreId, List<String> shipsFrom) {
    }

    /** Approved orders with items still to ship, placed before {@code placedBefore}, oldest first. */
    /** How many approved orders placed before then still have items to ship. */
    public long countStuckOrders(LocalDateTime placedBefore) {
        return entityManager.createQuery(
                        "SELECT COUNT(DISTINCT h.orderId) FROM OrderHeader h, OrderItem i WHERE i.orderId = h.orderId "
                                + "AND h.statusId = :approvedOrder AND i.statusId = :approvedItem AND h.orderDate < :placedBefore", Long.class)
                .setParameter("approvedOrder", CoreStatus.Order.APPROVED)
                .setParameter("approvedItem", CoreStatus.Item.APPROVED)
                .setParameter("placedBefore", placedBefore)
                .getSingleResult();
    }

    public List<StuckOrder> stuckOrders(LocalDateTime placedBefore, int limit) {
        List<Object[]> rows = entityManager.createQuery(
                        "SELECT DISTINCT h.orderId, h.orderDate, h.productStoreId FROM OrderHeader h, OrderItem i WHERE i.orderId = h.orderId "
                                + "AND h.statusId = :approvedOrder AND i.statusId = :approvedItem AND h.orderDate < :placedBefore ORDER BY h.orderDate",
                        Object[].class)
                .setParameter("approvedOrder", CoreStatus.Order.APPROVED)
                .setParameter("approvedItem", CoreStatus.Item.APPROVED)
                .setParameter("placedBefore", placedBefore)
                .setMaxResults(limit)
                .getResultList();
        return rows.stream()
                .map(row -> {
                    String orderId = (String) row[0];
                    List<String> shipsFrom = entityManager.createQuery("SELECT g.facilityId FROM OrderItemShipGroup g WHERE g.orderId = :orderId", String.class)
                            .setParameter("orderId", orderId)
                            .getResultList()
                            .stream()
                            .map(this::locationName)
                            .toList();
                    return new StuckOrder(orderId, (LocalDateTime) row[1], (String) row[2], shipsFrom);
                })
                .toList();
    }

    // ── products and locations ──

    /** The product with this SKU (or other good identification); the key itself when nothing matches, as it may be a product id. */
    public String productIdForSku(String skuOrProductId) {
        return first(entityManager.createQuery("SELECT g.productId FROM GoodIdentification g WHERE g.idValue = :idValue AND g.thruDate IS NULL", String.class)
                .setParameter("idValue", skuOrProductId))
                .orElse(skuOrProductId);
    }

    /** Problems at one location; a count is null when there was none of that kind. */
    public record LocationProblems(Long itemsNotFoundOrMovedOff, Long transfersReceivedShortWaiting) {
    }

    /** Per location name, since {@code since}: items it could not find or that were moved off it, and transfers received short. */
    public Map<String, LocationProblems> problemsByLocation(LocalDateTime since) {
        Map<String, Long> notFoundByFacility = new LinkedHashMap<>();
        entityManager.createQuery("SELECT x.facilityId, COUNT(x) FROM ExcludedOrderFacility x WHERE x.fromDate >= :since GROUP BY x.facilityId", Object[].class)
                .setParameter("since", since)
                .getResultList()
                .forEach(row -> notFoundByFacility.put((String) row[0], (Long) row[1]));

        Map<String, Long> receivedShortByFacility = new LinkedHashMap<>();
        entityManager.createQuery("SELECT t.facilityIdTo, COUNT(t) FROM InventoryTransfer t WHERE t.statusId = :receivedShort AND t.receiveDate >= :since "
                        + "GROUP BY t.facilityIdTo", Object[].class)
                .setParameter("receivedShort", CoreStatus.Transfer.RECEIVED_SHORT)
                .setParameter("since", since)
                .getResultList()
                .forEach(row -> receivedShortByFacility.put((String) row[0], (Long) row[1]));

        Map<String, LocationProblems> problemsByName = new LinkedHashMap<>();
        notFoundByFacility.forEach((facilityId, count) ->
                problemsByName.merge(locationName(facilityId), new LocationProblems(count, null), OperationsReadService::combine));
        receivedShortByFacility.forEach((facilityId, count) ->
                problemsByName.merge(locationName(facilityId), new LocationProblems(null, count), OperationsReadService::combine));
        return problemsByName;
    }

    private static LocationProblems combine(LocationProblems first, LocationProblems second) {
        Long notFound = first.itemsNotFoundOrMovedOff() != null ? first.itemsNotFoundOrMovedOff() : second.itemsNotFoundOrMovedOff();
        Long receivedShort = first.transfersReceivedShortWaiting() != null ? first.transfersReceivedShortWaiting() : second.transfersReceivedShortWaiting();
        return new LocationProblems(notFound, receivedShort);
    }

    /** {@code dailyOrderLimit} is null when the location has no limit. */
    public record Location(String facilityId, String facilityName, String facilityTypeId, Long dailyOrderLimit) {
    }

    /** All real locations (not virtual, in-transit or drop-ship), by name. */
    public List<Location> locations() {
        return entityManager.createQuery("SELECT f FROM Facility f WHERE f.facilityTypeId NOT IN :virtualTypes ORDER BY f.facilityName", Facility.class)
                .setParameter("virtualTypes", StoreNetworkService.VIRTUAL_FACILITY_TYPES)
                .getResultList()
                .stream()
                .map(facility -> new Location(facility.getFacilityId(), facility.getFacilityName(), facility.getFacilityTypeId(),
                        facility.getMaximumOrderLimit()))
                .toList();
    }

    // ── names ──

    /** The facility's name, "not routed yet" for no facility, or the id when the facility is unknown. */
    public String locationName(String facilityId) {
        return facilityId == null ? NOT_ROUTED : facilityName(facilityId);
    }

    private String facilityNameOrNull(String facilityId) {
        return facilityId == null ? null : facilityName(facilityId);
    }

    private String facilityName(String facilityId) {
        Facility facility = entityManager.find(Facility.class, facilityId);
        return facility == null ? facilityId : facility.getFacilityName();
    }

    /** A person's first and last name, a group's name, or the party id when neither is known. */
    private String partyName(String partyId) {
        Person person = entityManager.find(Person.class, partyId);
        if (person != null) {
            String firstName = person.getFirstName() == null ? "" : person.getFirstName();
            String lastName = person.getLastName() == null ? "" : person.getLastName();
            return (firstName + " " + lastName).trim();
        }
        PartyGroup group = entityManager.find(PartyGroup.class, partyId);
        return group == null ? partyId : group.getGroupName();
    }

    private static <T> Optional<T> first(TypedQuery<T> query) {
        return query.setMaxResults(1).getResultList().stream().findFirst();
    }
}
