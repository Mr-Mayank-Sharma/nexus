package com.nexus.core.service;

import org.springframework.beans.factory.annotation.Qualifier;
import com.nexus.core.CoreStatus;
import com.nexus.core.CoreTransactional;
import com.nexus.core.model.facility.Facility;
import com.nexus.core.model.order.OrderItem;
import com.nexus.core.model.order.OrderItemShipGroup;
import com.nexus.core.model.product.ProductStore;
import com.nexus.core.model.routing.OrderFacilityChange;
import com.nexus.core.model.routing.OrderRouting;
import com.nexus.core.model.routing.OrderRoutingGroup;
import com.nexus.core.model.routing.OrderRoutingRule;
import com.nexus.core.model.routing.OrderRoutingRuleAction;
import com.nexus.core.model.routing.OrderRoutingRun;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Order routing (brokering), built the HotWax way and written fresh for Nexus.
 * <p>
 * A routing group belongs to a store and runs its active routings in order (set up with {@link RoutingSetupService}).
 * A routing takes the ship groups waiting in its queues ({@link RoutingQueue}). For each ship group its rules are tried
 * in order, and for each rule:
 * <ol>
 *   <li>the rule's view of the locations is built ({@link LocationFinder});</li>
 *   <li>one location that holds every item is preferred (after swapping in substitutes, when the rule allows them);</li>
 *   <li>otherwise a split rule (and a store that allows splits) spreads the items over as few locations as it can
 *       ({@link SplitPlanner});</li>
 *   <li>otherwise the rule's actions run: set items to cancel after some days, park the order in a queue, or try the
 *       next rule.</li>
 * </ol>
 * Every move is an {@code order_facility_change} with the reason in plain words, and every run is an
 * {@code order_routing_run}. A simulation does all of it and rolls back, keeping only the run record.
 * <p>
 * Transactions are explicit ({@link TransactionTemplate}) because a simulation must roll back its own changes and then
 * write its run record in a second transaction.
 */
@Service("coreRoutingService")
public class RoutingService {

    /** In a {@code facilityId} filter: ship groups with no location yet. */
    public static final String QUEUE = RoutingQueue.NO_LOCATION;

    static final String DEFAULT_PARKING = "UNFILLABLE_PARKING";
    static final String MAX_SPLIT_SETTING = "MAX_SPLIT_LOCATIONS";

    // Why an order moved (order_facility_change.change_reason_enum_id).
    static final String REASON_ROUTED = "FCR_ROUTED";
    static final String REASON_SPLIT = "FCR_SPLIT";
    static final String REASON_QUEUED = "FCR_QUEUED";
    static final String REASON_MANUAL = "FCR_MANUAL";
    static final String REASON_BULK = "FCR_BULK";

    // Outcomes of one ship group.
    public static final String ROUTED = "ROUTED";
    public static final String SPLIT = "SPLIT";
    public static final String QUEUED = "QUEUED";
    public static final String UNFILLABLE = "UNFILLABLE";

    private final EntityManager entityManager;
    private final SequenceService sequenceService;
    private final OrderService orderService;
    private final OrderReservationService reservationService;
    private final FacilityService facilityService;
    private final ProductStoreService productStoreService;
    private final RoutingSetupService setupService;
    private final RoutingQueue routingQueue;
    private final LocationFinder locationFinder;
    private final TransactionTemplate transaction;

    public RoutingService(EntityManager entityManager, SequenceService sequenceService, OrderService orderService, OrderReservationService reservationService,
                          FacilityService facilityService, ProductStoreService productStoreService, RoutingSetupService setupService,
                          RoutingQueue routingQueue, LocationFinder locationFinder,
                          @Qualifier(CoreTransactional.TEMPLATE) TransactionTemplate transaction) {
        this.entityManager = entityManager;
        this.sequenceService = sequenceService;
        this.orderService = orderService;
        this.reservationService = reservationService;
        this.facilityService = facilityService;
        this.productStoreService = productStoreService;
        this.setupService = setupService;
        this.routingQueue = routingQueue;
        this.locationFinder = locationFinder;
        this.transaction = transaction;
    }

    // ── running ──

    /** What happened to one ship group: its outcome (ROUTED, SPLIT, QUEUED, UNFILLABLE), where, and why. */
    public record Decision(String orderId, String shipGroupSeqId, List<String> facilityIds, String outcome, String explanation) {
    }

    public record RunResult(String routingRunId, boolean simulation, int considered, int routed, int split, int unfillable,
                            int substituted, List<Decision> decisions) {
    }

    /** Running totals of one run. */
    private static final class RunCounts {
        int considered;
        int routed;
        int split;
        int unfillable;
        int substituted;
    }

    /** Runs a routing group for real: holds are placed, locations set, the run recorded. */
    public RunResult run(String routingGroupId, String userId) {
        return execute(routingGroupId, false, userId);
    }

    /** Runs a routing group on the real orders and stock, reports what it would do, and changes nothing. */
    public RunResult simulate(String routingGroupId, String userId) {
        return execute(routingGroupId, true, userId);
    }

    private RunResult execute(String routingGroupId, boolean simulation, String userId) {
        LocalDateTime startedAt = CoreClock.now();
        RunResult result = transaction.execute(status -> {
            // One run of a group at a time: the lock waits for any other run to finish.
            OrderRoutingGroup group = entityManager.find(OrderRoutingGroup.class, routingGroupId, LockModeType.PESSIMISTIC_WRITE);
            if (group == null) {
                throw new IllegalArgumentException("Unknown routing group " + routingGroupId);
            }
            String runId = null;
            if (!simulation) {
                runId = startRunRecord(routingGroupId, false, startedAt, userId);
                entityManager.flush();   // the moves below refer to the run
            }
            RunResult outcome = routeGroup(group, runId, simulation, userId);
            if (simulation) {
                status.setRollbackOnly();
            } else {
                finishRunRecord(runId, outcome);
            }
            return outcome;
        });
        if (!simulation) {
            return result;
        }
        // The simulation's changes rolled back; keep only the record of what it would have done.
        String simulationRunId = transaction.execute(status -> {
            String runId = startRunRecord(routingGroupId, true, startedAt, userId);
            finishRunRecord(runId, result);
            return runId;
        });
        return new RunResult(simulationRunId, true, result.considered(), result.routed(), result.split(), result.unfillable(), result.substituted(),
                result.decisions());
    }

    private String startRunRecord(String routingGroupId, boolean simulation, LocalDateTime startedAt, String userId) {
        OrderRoutingRun run = new OrderRoutingRun();
        run.setRoutingRunId(sequenceService.nextId("OrderRoutingRun"));
        run.setRoutingGroupId(routingGroupId);
        run.setIsSimulation(simulation);
        run.setStartDate(startedAt);
        run.setCreatedByUserLogin(userId);
        entityManager.persist(run);
        return run.getRoutingRunId();
    }

    private void finishRunRecord(String runId, RunResult result) {
        OrderRoutingRun run = entityManager.find(OrderRoutingRun.class, runId);
        run.setEndDate(CoreClock.now());
        run.setOrdersConsidered((long) result.considered());
        run.setOrdersRouted((long) result.routed());
        run.setOrdersSplit((long) result.split());
        run.setOrdersUnfillable((long) result.unfillable());
        run.setItemsSubstituted((long) result.substituted());
        run.setSummary(result.decisions().stream()
                .map(decision -> decision.orderId() + "/" + decision.shipGroupSeqId() + " " + decision.outcome() + " " + decision.facilityIds()
                        + ": " + decision.explanation())
                .collect(Collectors.joining("\n")));
    }

    /** Runs each active routing of the group over its waiting ship groups. A ship group is handled once per run. */
    private RunResult routeGroup(OrderRoutingGroup group, String runId, boolean simulation, String userId) {
        ProductStore store = entityManager.find(ProductStore.class, group.getProductStoreId());
        boolean storeAllowsSplit = store == null || !Boolean.FALSE.equals(store.getAllowSplit());
        long maxSplitLocations = store == null
                ? Long.MAX_VALUE
                : productStoreService.settingLong(store.getProductStoreId(), MAX_SPLIT_SETTING, Long.MAX_VALUE);

        RunCounts counts = new RunCounts();
        List<Decision> decisions = new ArrayList<>();
        Set<String> handledShipGroups = new HashSet<>();
        for (OrderRouting routing : setupService.activeRoutings(group.getRoutingGroupId())) {
            List<OrderRoutingRule> rules = setupService.rules(routing.getOrderRoutingId(), true);
            List<OrderItemShipGroup> waiting = routingQueue.waitingShipGroups(group.getProductStoreId(), setupService.orderConditions(routing.getOrderRoutingId()));
            for (OrderItemShipGroup shipGroup : waiting) {
                String shipGroupKey = shipGroup.getOrderId() + "|" + shipGroup.getShipGroupSeqId();
                if (!handledShipGroups.add(shipGroupKey)) {
                    continue;   // an earlier routing in this run already handled it
                }
                counts.considered++;
                decisions.add(routeShipGroup(shipGroup, rules, storeAllowsSplit, maxSplitLocations, runId, userId, counts));
            }
        }
        return new RunResult(runId, simulation, counts.considered, counts.routed, counts.split, counts.unfillable, counts.substituted, decisions);
    }

    /** Tries the rules in order for one ship group and returns what happened. */
    private Decision routeShipGroup(OrderItemShipGroup shipGroup, List<OrderRoutingRule> rules, boolean storeAllowsSplit, long maxSplitLocations,
                                    String runId, String userId, RunCounts counts) {
        String orderId = shipGroup.getOrderId();
        String shipGroupSeqId = shipGroup.getShipGroupSeqId();
        String fromFacility = shipGroup.getFacilityId();
        List<String> notes = new ArrayList<>();

        for (OrderRoutingRule rule : rules) {
            Map<OrderItem, BigDecimal> stillToHold = routingQueue.stillToHold(shipGroup);
            if (stillToHold.isEmpty()) {
                break;
            }
            LocationFinder.RuleContext context = locationFinder.contextFor(rule, shipGroup);
            List<CandidateLocation> candidates = locationFinder.candidates(context, stillToHold);
            String ruleLabel = "Rule '" + rule.getRuleName() + "': ";

            // Option 1: one location for everything (after substitutes, when the rule allows them).
            Optional<CandidateLocation> wholeOrderLocation = firstThatFillsAll(candidates, stillToHold);
            if (wholeOrderLocation.isEmpty() && context.allowSubstitute) {
                int swapped = substitute(stillToHold, candidates, context);
                if (swapped > 0) {
                    counts.substituted += swapped;
                    Map<OrderItem, BigDecimal> afterSwap = routingQueue.stillToHold(shipGroup);
                    candidates = locationFinder.candidates(context, afterSwap);
                    wholeOrderLocation = firstThatFillsAll(candidates, afterSwap);
                    notes.add(swapped + " item(s) swapped for a substitute");
                }
            }
            if (wholeOrderLocation.isPresent()) {
                CandidateLocation chosen = wholeOrderLocation.get();
                String why = chosen.explanation(context.sortNames, true);
                holdAt(shipGroup, chosen.facilityId, context.ignoreOrderLimit);
                recordMove(orderId, shipGroupSeqId, fromFacility, chosen.facilityId, REASON_ROUTED, runId, rule.getRoutingRuleId(), ruleLabel + why, userId);
                counts.routed++;
                return new Decision(orderId, shipGroupSeqId, List.of(chosen.facilityId), ROUTED, why + inBrackets(notes));
            }

            // Option 2: split over as few locations as possible.
            boolean maySplit = RoutingSetupService.SPLIT_ALLOWED.equals(rule.getAssignmentEnumId()) && storeAllowsSplit
                    && !Boolean.FALSE.equals(shipGroup.getMaySplit()) && stillToHold.size() > 1;
            if (maySplit) {
                Optional<Decision> splitDecision = trySplit(shipGroup, fromFacility, stillToHold, candidates, maxSplitLocations, context, ruleLabel, runId,
                        userId, notes, counts);
                if (splitDecision.isPresent()) {
                    return splitDecision.get();
                }
            }

            // Option 3: nothing (or not everything) fits: the rule's actions.
            notes.add("rule '" + rule.getRuleName() + "' found no location" + (candidates.isEmpty() ? " with stock" : " holding everything"));
            List<OrderRoutingRuleAction> actions = setupService.actions(rule.getRoutingRuleId());
            Optional<Decision> parked = applyActions(shipGroup, fromFacility, actions, rule, runId, userId, notes, counts);
            if (parked.isPresent()) {
                return parked.get();
            }
            boolean tryNextRule = actions.isEmpty()
                    || actions.stream().anyMatch(action -> RoutingSetupService.NEXT_RULE.equals(action.getActionTypeEnumId()));
            if (!tryNextRule) {
                break;
            }
        }
        counts.unfillable++;
        String explanation = notes.isEmpty() ? "no active rule" : String.join("; ", notes);
        return new Decision(orderId, shipGroupSeqId, List.of(), UNFILLABLE, explanation);
    }

    /**
     * Splits the ship group over the planned locations. When every item is covered, the location with the most items
     * keeps the original ship group and the others get new ones; when only some are covered, those move to new ship
     * groups and the rest stay to be tried by the next rule. Empty when no useful split exists.
     */
    private Optional<Decision> trySplit(OrderItemShipGroup shipGroup, String fromFacility, Map<OrderItem, BigDecimal> stillToHold,
                                        List<CandidateLocation> candidates, long maxSplitLocations, LocationFinder.RuleContext context, String ruleLabel,
                                        String runId, String userId, List<String> notes, RunCounts counts) {
        String orderId = shipGroup.getOrderId();
        String ruleId = context.rule.getRoutingRuleId();
        Map<String, List<OrderItem>> itemsByFacility = SplitPlanner.plan(stillToHold, candidates);
        Set<OrderItem> covered = itemsByFacility.values().stream().flatMap(List::stream).collect(Collectors.toSet());

        if (itemsByFacility.size() > maxSplitLocations) {
            notes.add("a split needs " + itemsByFacility.size() + " locations; the store allows " + maxSplitLocations);
            return Optional.empty();
        }
        boolean worthSplitting = itemsByFacility.size() > 1 || (itemsByFacility.size() == 1 && covered.size() < stillToHold.size());
        if (!worthSplitting) {
            return Optional.empty();
        }

        boolean everythingCovered = covered.size() == stillToHold.size();
        String locationWithMostItems = itemsByFacility.entrySet().stream()
                .max(Comparator.comparingInt(entry -> entry.getValue().size()))
                .map(Map.Entry::getKey)
                .orElseThrow();
        List<String> usedFacilities = new ArrayList<>();
        for (Map.Entry<String, List<OrderItem>> entry : itemsByFacility.entrySet()) {
            String facilityId = entry.getKey();
            if (everythingCovered && facilityId.equals(locationWithMostItems)) {
                continue;   // the original group keeps the location with most items
            }
            List<String> itemSeqIds = entry.getValue().stream().map(OrderItem::getOrderItemSeqId).toList();
            String newShipGroupSeqId = orderService.splitShipGroup(orderId, shipGroup.getShipGroupSeqId(), itemSeqIds, null);
            holdAt(orderService.shipGroup(orderId, newShipGroupSeqId), facilityId, context.ignoreOrderLimit);
            recordMove(orderId, newShipGroupSeqId, null, facilityId, REASON_SPLIT, runId, ruleId,
                    ruleLabel + "split; " + entry.getValue().size() + " item(s) here", userId);
            usedFacilities.add(facilityId);
        }
        counts.split++;

        if (!everythingCovered) {
            notes.add("split " + covered.size() + " of " + stillToHold.size() + " items to " + usedFacilities);
            return Optional.empty();
        }
        holdAt(shipGroup, locationWithMostItems, context.ignoreOrderLimit);
        recordMove(orderId, shipGroup.getShipGroupSeqId(), fromFacility, locationWithMostItems, REASON_ROUTED, runId, ruleId,
                ruleLabel + "split; the location with the most items keeps the original shipment", userId);
        usedFacilities.add(0, locationWithMostItems);
        counts.routed++;
        return Optional.of(new Decision(orderId, shipGroup.getShipGroupSeqId(), usedFacilities, SPLIT,
                "No one location holds every item; split over " + usedFacilities.size() + inBrackets(notes)));
    }

    /**
     * Runs a rule's no-location actions in a fixed order, whatever order they were added in: first cancel dates, then
     * parking. Returns the decision when the ship group was parked.
     */
    private Optional<Decision> applyActions(OrderItemShipGroup shipGroup, String fromFacility, List<OrderRoutingRuleAction> actions,
                                            OrderRoutingRule rule, String runId, String userId, List<String> notes, RunCounts counts) {
        for (OrderRoutingRuleAction action : actions) {
            if (!RoutingSetupService.AUTO_CANCEL_DAYS.equals(action.getActionTypeEnumId())) {
                continue;
            }
            LocalDateTime cancelAt = CoreClock.now().plusDays(Long.parseLong(action.getActionValue().trim()));
            routingQueue.stillToHold(shipGroup).keySet().stream()
                    .filter(orderItem -> orderItem.getAutoCancelDate() == null)
                    .forEach(orderItem -> orderItem.setAutoCancelDate(cancelAt));
            notes.add("items cancel on " + cancelAt.toLocalDate() + " unless routed");
        }

        Optional<OrderRoutingRuleAction> parkAction = actions.stream()
                .filter(action -> RoutingSetupService.MOVE_TO_QUEUE.equals(action.getActionTypeEnumId()))
                .findFirst();
        if (parkAction.isEmpty()) {
            return Optional.empty();
        }
        String parkingValue = parkAction.get().getActionValue();
        String parkingFacility = parkingValue == null || parkingValue.isBlank() ? DEFAULT_PARKING : parkingValue.trim();
        String explanation = String.join("; ", notes);
        orderService.setShipGroupFacility(shipGroup.getOrderId(), shipGroup.getShipGroupSeqId(), parkingFacility);
        recordMove(shipGroup.getOrderId(), shipGroup.getShipGroupSeqId(), fromFacility, parkingFacility, REASON_QUEUED, runId, rule.getRoutingRuleId(),
                explanation, userId);
        counts.unfillable++;
        return Optional.of(new Decision(shipGroup.getOrderId(), shipGroup.getShipGroupSeqId(), List.of(parkingFacility), QUEUED, explanation));
    }

    /**
     * Swaps items no allowed location can fill for a substitute product (PRODUCT_SUBSTITUTE, in sequence) that some
     * location can. The original product is kept as the item attribute {@code substitutedFor}. Returns how many were swapped.
     */
    private int substitute(Map<OrderItem, BigDecimal> stillToHold, List<CandidateLocation> candidates, LocationFinder.RuleContext context) {
        int swapped = 0;
        for (Map.Entry<OrderItem, BigDecimal> entry : stillToHold.entrySet()) {
            OrderItem orderItem = entry.getKey();
            BigDecimal quantity = entry.getValue();
            boolean someoneCanFill = candidates.stream().anyMatch(location -> location.canFill(orderItem, quantity));
            if (someoneCanFill) {
                continue;
            }
            List<String> substitutes = entityManager.createQuery("SELECT a.productIdTo FROM ProductAssoc a WHERE a.productId = :productId "
                            + "AND a.productAssocTypeId = 'PRODUCT_SUBSTITUTE' AND a.thruDate IS NULL ORDER BY a.sequenceNum", String.class)
                    .setParameter("productId", orderItem.getProductId())
                    .getResultList();
            for (String substituteProductId : substitutes) {
                if (locationFinder.sellableAnywhere(context, substituteProductId, quantity)) {
                    orderService.setItemAttribute(orderItem.getOrderId(), orderItem.getOrderItemSeqId(), "substitutedFor", orderItem.getProductId());
                    orderItem.setProductId(substituteProductId);
                    swapped++;
                    break;
                }
            }
        }
        return swapped;
    }

    /**
     * Routing groups with a schedule whose last real run (simulations don't count) is at least that many minutes old,
     * or that never ran.
     */
    public List<String> scheduledGroupsDue(LocalDateTime now) {
        return transaction.execute(status -> entityManager.createQuery("SELECT g.routingGroupId, g.scheduleMinutes, MAX(r.startDate) FROM OrderRoutingGroup g "
                        + "LEFT JOIN OrderRoutingRun r ON r.routingGroupId = g.routingGroupId AND r.isSimulation = false "
                        + "WHERE g.scheduleMinutes IS NOT NULL AND g.scheduleMinutes > 0 GROUP BY g.routingGroupId, g.scheduleMinutes", Object[].class)
                .getResultList()
                .stream()
                .filter(row -> {
                    LocalDateTime lastRun = (LocalDateTime) row[2];
                    long everyMinutes = ((Number) row[1]).longValue();
                    return lastRun == null || !lastRun.plusMinutes(everyMinutes).isAfter(now);
                })
                .map(row -> (String) row[0])
                .toList());
    }

    // ── moving orders by hand or in bulk ──

    /**
     * Moves a ship group to another facility: its holds where it was are released, it is held at the new place, and
     * the move is recorded. A person may move an order to a facility that is full for the day.
     */
    public void reallocate(String orderId, String shipGroupSeqId, String toFacilityId, String reason, String userId) {
        transaction.executeWithoutResult(status -> {
            OrderItemShipGroup shipGroup = orderService.shipGroup(orderId, shipGroupSeqId);
            String fromFacility = releaseHolds(shipGroup);
            for (OrderItem orderItem : orderService.items(orderId)) {
                boolean inThisGroup = shipGroupSeqId.equals(orderItem.getShipGroupSeqId());
                if (inThisGroup && CoreStatus.Item.APPROVED.equals(orderItem.getStatusId()) && orderItem.getProductId() != null) {
                    reservationService.reserve(orderId, orderItem.getOrderItemSeqId(), toFacilityId, null, userId);
                }
            }
            shipGroup.setFacilityId(toFacilityId);
            countOrderAt(toFacilityId);
            recordMove(orderId, shipGroupSeqId, fromFacility, toFacilityId, REASON_MANUAL, null, null, reason, userId);
        });
    }

    /**
     * Takes every open, unshipped ship group off a facility (it closed, is overloaded, ran out): holds released, the
     * facility excluded for those items, the group back in the routing queue for the next run. Returns how many moved.
     */
    public int moveAllOff(String facilityId, String reason, String userId) {
        return transaction.execute(status -> {
            List<OrderItemShipGroup> shipGroups = entityManager.createQuery("SELECT g FROM OrderItemShipGroup g, OrderHeader h WHERE h.orderId = g.orderId "
                            + "AND g.facilityId = :facilityId AND h.statusId IN (:approved, :held)", OrderItemShipGroup.class)
                    .setParameter("facilityId", facilityId)
                    .setParameter("approved", CoreStatus.Order.APPROVED)
                    .setParameter("held", CoreStatus.Order.HOLD)
                    .getResultList();
            int moved = 0;
            for (OrderItemShipGroup shipGroup : shipGroups) {
                if (hasLiveShipment(shipGroup)) {
                    continue;   // already being shipped from there
                }
                releaseHolds(shipGroup);
                orderService.items(shipGroup.getOrderId()).stream()
                        .filter(orderItem -> shipGroup.getShipGroupSeqId().equals(orderItem.getShipGroupSeqId()))
                        .forEach(orderItem -> reservationService.excludeFacility(shipGroup.getOrderId(), orderItem.getOrderItemSeqId(), facilityId));
                shipGroup.setFacilityId(null);
                recordMove(shipGroup.getOrderId(), shipGroup.getShipGroupSeqId(), facilityId, null, REASON_BULK, null, null, reason, userId);
                moved++;
            }
            return moved;
        });
    }

    /** Why each move of an order happened, oldest first. */
    public List<OrderFacilityChange> history(String orderId) {
        return entityManager.createQuery("SELECT c FROM OrderFacilityChange c WHERE c.orderId = :orderId ORDER BY c.changeDatetime", OrderFacilityChange.class)
                .setParameter("orderId", orderId)
                .getResultList();
    }

    // ── internals ──

    private static Optional<CandidateLocation> firstThatFillsAll(List<CandidateLocation> candidates, Map<OrderItem, BigDecimal> stillToHold) {
        return candidates.stream().filter(location -> location.canFillAll(stillToHold)).findFirst();
    }

    /**
     * Holds every item of the group that still needs stock at the facility, sets the group there, and counts the order
     * against the facility's daily limit. A facility that filled up during the run is an error, unless the rule
     * ignores order limits.
     */
    private void holdAt(OrderItemShipGroup shipGroup, String facilityId, boolean ignoreOrderLimit) {
        routingQueue.stillToHold(shipGroup).forEach((orderItem, quantity) ->
                reservationService.reserve(shipGroup.getOrderId(), orderItem.getOrderItemSeqId(), facilityId, quantity, null));
        shipGroup.setFacilityId(facilityId);
        boolean tookOrder = facilityService.takeOrder(facilityId, today());
        if (!tookOrder) {
            if (!ignoreOrderLimit) {
                throw new IllegalStateException("Facility " + facilityId + " filled up during routing");
            }
            facilityService.countOrder(facilityId, today());
        }
    }

    /** Counts an order at a facility even when it is full: a person decided to send it there. */
    private void countOrderAt(String facilityId) {
        if (!facilityService.takeOrder(facilityId, today())) {
            facilityService.countOrder(facilityId, today());
        }
    }

    /** Releases the group's holds and gives back its place in the facility's daily count; returns the old facility. */
    private String releaseHolds(OrderItemShipGroup shipGroup) {
        String fromFacility = shipGroup.getFacilityId();
        for (OrderItem orderItem : orderService.items(shipGroup.getOrderId())) {
            if (shipGroup.getShipGroupSeqId().equals(orderItem.getShipGroupSeqId())) {
                reservationService.releaseAll(shipGroup.getOrderId(), orderItem.getOrderItemSeqId(), null);
            }
        }
        boolean wasAtRealFacility = fromFacility != null && entityManager.find(Facility.class, fromFacility) != null;
        if (wasAtRealFacility) {
            facilityService.releaseOrder(fromFacility, today());
        }
        return fromFacility;
    }

    private boolean hasLiveShipment(OrderItemShipGroup shipGroup) {
        long liveShipments = entityManager.createQuery("SELECT COUNT(s) FROM Shipment s WHERE s.primaryOrderId = :orderId "
                        + "AND s.primaryShipGroupSeqId = :shipGroupSeqId AND s.statusId <> :cancelled", Long.class)
                .setParameter("orderId", shipGroup.getOrderId())
                .setParameter("shipGroupSeqId", shipGroup.getShipGroupSeqId())
                .setParameter("cancelled", CoreStatus.Shipment.CANCELLED)
                .getSingleResult();
        return liveShipments > 0;
    }

    /** Writes one {@code order_facility_change}: the ship group moved from one facility to another, and why. */
    private void recordMove(String orderId, String shipGroupSeqId, String fromFacilityId, String toFacilityId, String reasonEnumId, String runId,
                            String ruleId, String comments, String userId) {
        OrderFacilityChange change = new OrderFacilityChange();
        change.setOrderFacilityChangeId(sequenceService.nextId("OrderFacilityChange"));
        change.setOrderId(orderId);
        change.setShipGroupSeqId(shipGroupSeqId);
        change.setFromFacilityId(fromFacilityId);
        change.setFacilityId(toFacilityId);
        change.setChangeDatetime(CoreClock.now());
        change.setChangeReasonEnumId(reasonEnumId);
        change.setRoutingRunId(runId);
        change.setRoutingRuleId(ruleId);
        change.setComments(comments);
        change.setChangeUserLogin(userId);
        entityManager.persist(change);
    }

    private static String inBrackets(List<String> notes) {
        return notes.isEmpty() ? "" : " (" + String.join("; ", notes) + ")";
    }

    private static LocalDate today() {
        return CoreClock.now().toLocalDate();
    }
}
