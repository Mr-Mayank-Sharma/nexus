package com.nexus.core.service;

import com.nexus.core.model.facility.Facility;
import com.nexus.core.model.facility.FacilityType;
import com.nexus.core.model.inventory.ProductFacility;
import com.nexus.core.model.order.OrderItem;
import com.nexus.core.model.order.OrderItemShipGroup;
import com.nexus.core.model.party.PostalAddress;
import com.nexus.core.model.routing.OrderRoutingRule;
import com.nexus.core.model.routing.OrderRoutingRuleInvCond;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One routing rule's view of the locations for one ship group: which locations its inventory conditions allow, what
 * each can promise, and in which order the rule prefers them.
 * <p>
 * A location is left out when it is virtual (or of a virtual type), closed, excluded for an item of the group (it
 * rejected or short-picked it), not in the rule's facility ids / types / groups, further than the rule's distance, or
 * full for the day (unless the rule ignores order limits). Stock is what routing may promise:
 * {@code product_facility.computed_last_inventory_count}.
 */
@Component("coreRoutingLocationFinder")
class LocationFinder {

    static final String PICKUP_METHOD = "STOREPICKUP";
    static final int RELIABILITY_DAYS = 90;
    /** Facility group members are ranked by group (in the rule's order) then by sequence: group index times this. */
    private static final long GROUP_RANK_STEP = 100_000L;
    private static final long NO_SEQUENCE = GROUP_RANK_STEP - 1;

    /** Sorts whose natural order is already "best first" (most supply, most capacity, most stock, most reliable). */
    private static final Set<String> BIGGEST_FIRST_SORTS = Set.of("weeksOfSupply", "capacityLeft", "inventory", "reliability");

    private final EntityManager entityManager;
    private final OrderReservationService reservationService;
    private final OrderService orderService;
    private final FacilityService facilityService;
    private final StoreHealthService storeHealthService;
    private final RoutingSetupService setupService;

    LocationFinder(EntityManager entityManager, OrderReservationService reservationService, OrderService orderService, FacilityService facilityService,
                   StoreHealthService storeHealthService, RoutingSetupService setupService) {
        this.entityManager = entityManager;
        this.reservationService = reservationService;
        this.orderService = orderService;
        this.facilityService = facilityService;
        this.storeHealthService = storeHealthService;
        this.setupService = setupService;
    }

    /** A rule's inventory conditions, read once, plus what the ship group adds (where it goes, what is excluded). */
    static final class RuleContext {
        final OrderRoutingRule rule;
        final List<String> facilityGroupIds;
        final List<String> facilityTypes;
        final List<String> excludedFacilityTypes;
        final List<String> onlyFacilityIds;
        final Double maxDistanceKm;
        final boolean ignoreOrderLimit;
        final boolean allowSubstitute;
        final List<OrderRoutingRuleInvCond> sorts;
        final List<String> sortNames;
        final Optional<GeoDistance.Point> shipTo;
        final Set<String> excludedFacilities;
        final boolean pickup;
        final Map<String, Double> reliabilityByFacility;

        private RuleContext(OrderRoutingRule rule, List<OrderRoutingRuleInvCond> conditions, Optional<GeoDistance.Point> shipTo,
                            Set<String> excludedFacilities, boolean pickup, Map<String, Double> reliabilityByFacility) {
            this.rule = rule;
            Map<String, OrderRoutingRuleInvCond> filterByField = new HashMap<>();
            conditions.stream()
                    .filter(condition -> RoutingSetupService.FILTER.equals(condition.getConditionTypeEnumId()))
                    .forEach(condition -> filterByField.put(condition.getFieldName(), condition));

            this.facilityGroupIds = valuesOf(filterByField.get("facilityGroupId"));
            OrderRoutingRuleInvCond typeFilter = filterByField.get("facilityTypeId");
            boolean typeNegated = typeFilter != null && RoutingQueue.isNegated(typeFilter.getOperator());
            this.facilityTypes = typeNegated ? List.of() : valuesOf(typeFilter);
            this.excludedFacilityTypes = typeNegated ? valuesOf(typeFilter) : List.of();
            this.onlyFacilityIds = valuesOf(filterByField.get("facilityId"));
            this.maxDistanceKm = filterByField.containsKey("distance") ? Double.valueOf(filterByField.get("distance").getFieldValue().trim()) : null;
            this.ignoreOrderLimit = isYes(filterByField.get("ignoreOrderLimit"));
            this.allowSubstitute = isYes(filterByField.get("allowSubstitute"));
            this.sorts = conditions.stream().filter(condition -> RoutingSetupService.SORT.equals(condition.getConditionTypeEnumId())).toList();
            this.sortNames = sorts.stream().map(OrderRoutingRuleInvCond::getFieldName).toList();
            this.shipTo = shipTo;
            this.excludedFacilities = excludedFacilities;
            this.pickup = pickup;
            this.reliabilityByFacility = reliabilityByFacility;
        }

        boolean sortsBy(String field) {
            return sortNames.contains(field);
        }

        private static List<String> valuesOf(OrderRoutingRuleInvCond condition) {
            return condition == null ? List.of() : RoutingQueue.values(condition.getFieldValue());
        }

        private static boolean isYes(OrderRoutingRuleInvCond condition) {
            return condition != null && "Y".equalsIgnoreCase(condition.getFieldValue());
        }
    }

    /** Reads the rule's conditions and what the ship group adds to them. */
    RuleContext contextFor(OrderRoutingRule rule, OrderItemShipGroup shipGroup) {
        List<OrderRoutingRuleInvCond> conditions = setupService.inventoryConditions(rule.getRoutingRuleId());
        boolean sortsByReliability = conditions.stream()
                .anyMatch(condition -> RoutingSetupService.SORT.equals(condition.getConditionTypeEnumId()) && "reliability".equals(condition.getFieldName()));
        Map<String, Double> reliability = sortsByReliability ? storeHealthService.reliabilityScores(RELIABILITY_DAYS) : Map.of();

        Set<String> excluded = new HashSet<>();
        for (OrderItem orderItem : orderService.items(shipGroup.getOrderId())) {
            if (shipGroup.getShipGroupSeqId().equals(orderItem.getShipGroupSeqId())) {
                excluded.addAll(reservationService.excludedFacilities(orderItem.getOrderId(), orderItem.getOrderItemSeqId()));
            }
        }
        Optional<GeoDistance.Point> shipTo = shipGroup.getContactMechId() == null
                ? Optional.empty()
                : GeoDistance.Point.of(entityManager.find(PostalAddress.class, shipGroup.getContactMechId()));
        boolean pickup = PICKUP_METHOD.equals(shipGroup.getShipmentMethodTypeId());
        return new RuleContext(rule, conditions, shipTo, excluded, pickup, reliability);
    }

    /** The locations the rule allows that hold any of the products, best first by the rule's sorts (then by id). */
    List<CandidateLocation> candidates(RuleContext context, Map<OrderItem, BigDecimal> quantityByItem) {
        Set<String> productIds = quantityByItem.keySet().stream().map(OrderItem::getProductId).collect(Collectors.toSet());
        if (productIds.isEmpty()) {
            return List.of();
        }

        // Step 1: every location with stock of any of the products, and its numbers.
        Map<String, CandidateLocation> candidateByFacility = new LinkedHashMap<>();
        List<ProductFacility> stockRows = entityManager.createQuery(
                        "SELECT pf FROM ProductFacility pf WHERE pf.productId IN :productIds AND pf.computedLastInventoryCount > 0", ProductFacility.class)
                .setParameter("productIds", productIds)
                .getResultList();
        for (ProductFacility stock : stockRows) {
            if (Boolean.FALSE.equals(stock.getAllowBrokering())) {
                continue;
            }
            if (context.pickup && Boolean.FALSE.equals(stock.getAllowPickup())) {
                continue;
            }
            CandidateLocation candidate = candidateByFacility.computeIfAbsent(stock.getFacilityId(), CandidateLocation::new);
            candidate.sellableByProduct.put(stock.getProductId(), stock.getComputedLastInventoryCount());
            BigDecimal dailySales = stock.getSalesVelocity();
            if (dailySales != null) {
                candidate.salesVelocity = Math.max(candidate.salesVelocity, dailySales.doubleValue());
                if (dailySales.signum() > 0) {
                    double weeks = stock.getComputedLastInventoryCount().doubleValue() / (dailySales.doubleValue() * 7);
                    candidate.weeksOfSupply = Math.min(candidate.weeksOfSupply, weeks);
                }
            }
        }

        // Step 2: keep the ones the rule allows.
        Map<String, Long> groupRankByFacility = context.facilityGroupIds.isEmpty() ? Map.of() : groupRanks(context.facilityGroupIds);
        LocalDate today = CoreClock.now().toLocalDate();
        List<CandidateLocation> allowed = new ArrayList<>();
        for (CandidateLocation candidate : candidateByFacility.values()) {
            Facility facility = entityManager.find(Facility.class, candidate.facilityId);
            if (!isRealOpenFacility(facility) || context.excludedFacilities.contains(candidate.facilityId)) {
                continue;
            }
            if (!context.onlyFacilityIds.isEmpty() && !context.onlyFacilityIds.contains(candidate.facilityId)) {
                continue;
            }
            if (!context.facilityTypes.isEmpty() && !context.facilityTypes.contains(facility.getFacilityTypeId())) {
                continue;
            }
            if (context.excludedFacilityTypes.contains(facility.getFacilityTypeId())) {
                continue;
            }
            if (!context.facilityGroupIds.isEmpty()) {
                Long rank = groupRankByFacility.get(candidate.facilityId);
                if (rank == null) {
                    continue;   // not in any of the rule's groups
                }
                candidate.groupSequence = rank % GROUP_RANK_STEP;
            }
            if (facility.getMaximumOrderLimit() != null) {
                long ordersLeft = facilityService.ordersLeft(candidate.facilityId, today).orElse(Long.MAX_VALUE);
                if (ordersLeft <= 0 && !context.ignoreOrderLimit) {
                    continue;   // full for today
                }
                candidate.capacityLeft = ordersLeft;
            }
            Optional<GeoDistance.Point> facilityPoint = GeoDistance.Point.of(facility.getLatitude(), facility.getLongitude());
            if (context.shipTo.isPresent() && facilityPoint.isPresent()) {
                candidate.distanceKm = GeoDistance.km(context.shipTo.get(), facilityPoint.get());
            }
            if (context.maxDistanceKm != null && (candidate.distanceKm == null || candidate.distanceKm > context.maxDistanceKm)) {
                continue;
            }
            if (context.sortsBy("brokenSizeRun")) {
                candidate.sizeRunCompleteness = sizeRunCompleteness(quantityByItem.keySet(), candidate.facilityId);
            }
            candidate.reliability = context.reliabilityByFacility.getOrDefault(candidate.facilityId, CandidateLocation.DEFAULT_RELIABILITY);
            allowed.add(candidate);
        }

        // Step 3: best first.
        allowed.sort(preference(context.sorts).thenComparing(candidate -> candidate.facilityId));
        return allowed;
    }

    /** Can a real, not excluded location promise this quantity of the product? Used to choose a substitute. */
    boolean sellableAnywhere(RuleContext context, String productId, BigDecimal quantity) {
        List<String> facilityIds = entityManager.createQuery(
                        "SELECT pf.facilityId FROM ProductFacility pf WHERE pf.productId = :productId AND pf.computedLastInventoryCount >= :quantity",
                        String.class)
                .setParameter("productId", productId)
                .setParameter("quantity", quantity)
                .getResultList();
        return facilityIds.stream()
                .filter(facilityId -> !context.excludedFacilities.contains(facilityId))
                .map(facilityId -> entityManager.find(Facility.class, facilityId))
                .anyMatch(facility -> facility != null && !StoreNetworkService.VIRTUAL_FACILITY_TYPES.contains(facility.getFacilityTypeId()));
    }

    // ── internals ──

    /** Not virtual (by type or by parent type) and not closed. */
    private boolean isRealOpenFacility(Facility facility) {
        if (facility == null) {
            return false;
        }
        String typeId = facility.getFacilityTypeId();
        if (StoreNetworkService.VIRTUAL_FACILITY_TYPES.contains(typeId)) {
            return false;
        }
        FacilityType type = entityManager.find(FacilityType.class, typeId);
        boolean virtualParent = type != null && type.getParentTypeId() != null && StoreNetworkService.VIRTUAL_FACILITY_TYPES.contains(type.getParentTypeId());
        if (virtualParent) {
            return false;
        }
        boolean closed = facility.getClosedDate() != null && !facility.getClosedDate().isAfter(CoreClock.now());
        return !closed;
    }

    /**
     * Each member's rank over the rule's groups: earlier groups first, then the member's sequence in its group. A
     * facility in several groups takes its best rank.
     */
    private Map<String, Long> groupRanks(List<String> facilityGroupIds) {
        LocalDateTime now = CoreClock.now();
        List<Object[]> members = entityManager.createQuery(
                        "SELECT m.facilityId, m.sequenceNum, m.facilityGroupId FROM FacilityGroupMember m WHERE m.facilityGroupId IN :groupIds "
                                + "AND m.fromDate <= :now AND (m.thruDate IS NULL OR m.thruDate > :now)", Object[].class)
                .setParameter("groupIds", facilityGroupIds)
                .setParameter("now", now)
                .getResultList();
        Map<String, Long> rankByFacility = new HashMap<>();
        for (Object[] member : members) {
            String facilityId = (String) member[0];
            long sequence = member[1] == null ? NO_SEQUENCE : (Long) member[1];
            long rank = facilityGroupIds.indexOf((String) member[2]) * GROUP_RANK_STEP + sequence;
            rankByFacility.merge(facilityId, rank, Math::min);
        }
        return rankByFacility;
    }

    /** The rule's sorts as one comparator, best location first. */
    private static Comparator<CandidateLocation> preference(List<OrderRoutingRuleInvCond> sorts) {
        Comparator<CandidateLocation> combined = (first, second) -> 0;
        for (OrderRoutingRuleInvCond sort : sorts) {
            String field = sort.getFieldName();
            Comparator<CandidateLocation> oneSort = switch (field) {
                case "distance" -> Comparator.comparing(candidate -> candidate.distanceKm == null ? Double.MAX_VALUE : candidate.distanceKm);
                case "groupSequence" -> Comparator.comparingLong(candidate -> candidate.groupSequence);
                // protect fast sellers: the slowest-selling location gives first
                case "salesVelocity" -> Comparator.comparingDouble(candidate -> candidate.salesVelocity);
                // most supply left first
                case "weeksOfSupply" -> Comparator.<CandidateLocation>comparingDouble(candidate -> candidate.weeksOfSupply).reversed();
                // a location whose size run is already broken gives first, keeping full runs together
                case "brokenSizeRun" -> Comparator.comparingDouble(candidate -> candidate.sizeRunCompleteness);
                case "capacityLeft" -> Comparator.<CandidateLocation>comparingLong(candidate -> candidate.capacityLeft).reversed();
                case "inventory" -> Comparator.<CandidateLocation>comparingDouble(CandidateLocation::totalSellable).reversed();
                // the location most likely to pick it first
                case "reliability" -> Comparator.<CandidateLocation>comparingDouble(candidate -> candidate.reliability).reversed();
                default -> (first, second) -> 0;
            };
            boolean descending = "desc".equalsIgnoreCase(sort.getOperator());
            if (descending && !BIGGEST_FIRST_SORTS.contains(field)) {
                oneSort = oneSort.reversed();
            }
            combined = combined.thenComparing(oneSort);
        }
        return combined;
    }

    /** Share of the style's sizes (sibling variants) the facility can still sell, worst over the items; lower is more broken. */
    private double sizeRunCompleteness(Set<OrderItem> orderItems, String facilityId) {
        double worst = 1;
        for (OrderItem orderItem : orderItems) {
            List<String> styles = entityManager.createQuery("SELECT a.productId FROM ProductAssoc a WHERE a.productIdTo = :productId "
                            + "AND a.productAssocTypeId = 'PRODUCT_VARIANT' AND a.thruDate IS NULL", String.class)
                    .setParameter("productId", orderItem.getProductId())
                    .getResultList();
            if (styles.isEmpty()) {
                continue;   // not a variant: no size run
            }
            List<String> sizes = entityManager.createQuery("SELECT a.productIdTo FROM ProductAssoc a WHERE a.productId = :styleId "
                            + "AND a.productAssocTypeId = 'PRODUCT_VARIANT' AND a.thruDate IS NULL", String.class)
                    .setParameter("styleId", styles.get(0))
                    .getResultList();
            if (sizes.isEmpty()) {
                continue;
            }
            long sizesInStock = entityManager.createQuery("SELECT COUNT(pf) FROM ProductFacility pf WHERE pf.facilityId = :facilityId "
                            + "AND pf.productId IN :sizes AND pf.computedLastInventoryCount > 0", Long.class)
                    .setParameter("facilityId", facilityId)
                    .setParameter("sizes", sizes)
                    .getSingleResult();
            worst = Math.min(worst, (double) sizesInStock / sizes.size());
        }
        return worst;
    }
}
