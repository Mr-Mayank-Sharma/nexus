package com.nexus.core.service;

import com.nexus.core.CoreStatus;
import com.nexus.core.model.order.OrderHeader;
import com.nexus.core.model.order.OrderItem;
import com.nexus.core.model.order.OrderItemShipGroup;
import com.nexus.core.model.routing.OrderFilterCondition;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The orders waiting for routing. A ship group waits in a queue when it has no location yet ({@link #NO_LOCATION}) or
 * sits at a parking facility; a routing names the queues it takes (its {@code facilityId} filter) and may filter and
 * sort them further by its order conditions.
 */
@Component("coreRoutingQueue")
class RoutingQueue {

    /** In a {@code facilityId} filter: ship groups with no location yet. */
    static final String NO_LOCATION = "_NA_";

    private final EntityManager entityManager;
    private final OrderService orderService;
    private final OrderReservationService reservationService;

    RoutingQueue(EntityManager entityManager, OrderService orderService, OrderReservationService reservationService) {
        this.entityManager = entityManager;
        this.orderService = orderService;
        this.reservationService = reservationService;
    }

    /** A waiting ship group with its order header, so filters and sorts can read both. */
    private record Waiting(OrderItemShipGroup shipGroup, OrderHeader header) {
    }

    /**
     * Ship groups of the store's approved orders that wait in the routing's queues, still need stock, and pass its
     * filters; sorted by its sorts, then oldest order first, then order id.
     */
    List<OrderItemShipGroup> waitingShipGroups(String productStoreId, List<OrderFilterCondition> orderConditions) {
        List<OrderFilterCondition> filters = orderConditions.stream()
                .filter(condition -> RoutingSetupService.FILTER.equals(condition.getConditionTypeEnumId()))
                .toList();
        List<OrderFilterCondition> sorts = orderConditions.stream()
                .filter(condition -> RoutingSetupService.SORT.equals(condition.getConditionTypeEnumId()))
                .toList();
        List<String> queues = filters.stream()
                .filter(condition -> "facilityId".equals(condition.getFieldName()))
                .findFirst()
                .map(condition -> values(condition.getFieldValue()))
                .orElse(List.of(NO_LOCATION));

        List<Object[]> rows = entityManager.createQuery("SELECT g, h FROM OrderItemShipGroup g, OrderHeader h WHERE h.orderId = g.orderId "
                        + "AND h.productStoreId = :storeId AND h.statusId = :approved", Object[].class)
                .setParameter("storeId", productStoreId)
                .setParameter("approved", CoreStatus.Order.APPROVED)
                .getResultList();

        LocalDateTime now = CoreClock.now();
        List<Waiting> waiting = new ArrayList<>();
        for (Object[] row : rows) {
            Waiting candidate = new Waiting((OrderItemShipGroup) row[0], (OrderHeader) row[1]);
            String queue = candidate.shipGroup().getFacilityId() == null ? NO_LOCATION : candidate.shipGroup().getFacilityId();
            if (!queues.contains(queue)) {
                continue;
            }
            if (stillToHold(candidate.shipGroup()).isEmpty()) {
                continue;   // everything already held: nothing to route
            }
            if (passesFilters(candidate, filters, now)) {
                waiting.add(candidate);
            }
        }

        Comparator<Waiting> oldestFirst = Comparator.comparing(entry -> entry.header().getOrderDate());
        Comparator<Waiting> ordering = sorts.isEmpty() ? oldestFirst : bySorts(sorts).thenComparing(oldestFirst);
        waiting.sort(ordering.thenComparing(entry -> entry.header().getOrderId()));
        return waiting.stream().map(Waiting::shipGroup).toList();
    }

    /** The group's items still waiting for stock: approved, with a product, and open quantity not yet held. */
    Map<OrderItem, BigDecimal> stillToHold(OrderItemShipGroup shipGroup) {
        Map<OrderItem, BigDecimal> quantityByItem = new LinkedHashMap<>();
        for (OrderItem orderItem : orderService.items(shipGroup.getOrderId())) {
            boolean inThisGroup = shipGroup.getShipGroupSeqId().equals(orderItem.getShipGroupSeqId());
            if (!inThisGroup || !CoreStatus.Item.APPROVED.equals(orderItem.getStatusId()) || orderItem.getProductId() == null) {
                continue;
            }
            BigDecimal alreadyHeld = reservationService.reservedQuantity(orderItem.getOrderId(), orderItem.getOrderItemSeqId());
            BigDecimal stillNeeded = OrderService.openQuantity(orderItem).subtract(alreadyHeld);
            if (stillNeeded.signum() > 0) {
                quantityByItem.put(orderItem, stillNeeded);
            }
        }
        return quantityByItem;
    }

    private static boolean passesFilters(Waiting candidate, List<OrderFilterCondition> filters, LocalDateTime now) {
        OrderHeader header = candidate.header();
        OrderItemShipGroup shipGroup = candidate.shipGroup();
        for (OrderFilterCondition filter : filters) {
            String value = filter.getFieldValue();
            boolean negated = isNegated(filter.getOperator());
            boolean passes = switch (filter.getFieldName()) {
                case "salesChannelEnumId" -> values(value).contains(header.getSalesChannelEnumId()) != negated;
                case "shipmentMethodTypeId" -> values(value).contains(shipGroup.getShipmentMethodTypeId()) != negated;
                case "priority" -> header.getPriority() != null && header.getPriority() >= Long.parseLong(value.trim());
                case "orderAgeMinutes" -> !header.getOrderDate().isAfter(now.minusMinutes(Long.parseLong(value.trim())));
                default -> true;   // facilityId picked the queue already
            };
            if (!passes) {
                return false;
            }
        }
        return true;
    }

    private static Comparator<Waiting> bySorts(List<OrderFilterCondition> sorts) {
        Comparator<Waiting> combined = null;
        for (OrderFilterCondition sort : sorts) {
            Comparator<Waiting> oneSort = switch (sort.getFieldName()) {
                case "priority" -> Comparator.comparing(entry -> entry.header().getPriority() == null ? 0L : entry.header().getPriority());
                case "shipByDate" -> Comparator.comparing(entry -> Optional.ofNullable(entry.shipGroup().getShipByDate()).orElse(LocalDateTime.MAX));
                default -> Comparator.comparing(entry -> entry.header().getOrderDate());
            };
            if ("desc".equalsIgnoreCase(sort.getOperator())) {
                oneSort = oneSort.reversed();
            }
            combined = combined == null ? oneSort : combined.thenComparing(oneSort);
        }
        return combined;
    }

    /** "not-in" and "!=" turn a filter round. */
    static boolean isNegated(String operator) {
        return "not-in".equalsIgnoreCase(operator) || "!=".equals(operator);
    }

    /** A comma-separated condition value as a list, blanks dropped. */
    static List<String> values(String commaSeparated) {
        if (commaSeparated == null) {
            return List.of();
        }
        return Arrays.stream(commaSeparated.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }
}
