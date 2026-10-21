package com.nexus.core.service;

import com.nexus.core.model.facility.Facility;
import com.nexus.core.model.inventory.ProductFacility;
import com.nexus.core.model.order.OrderHeader;
import com.nexus.core.model.order.OrderItem;
import com.nexus.core.model.order.OrderItemShipGroup;
import com.nexus.core.model.party.PostalAddress;
import com.nexus.core.model.product.ProductStore;
import com.nexus.core.model.routing.OrderFacilityChange;
import com.nexus.core.model.routing.OrderFilterCondition;
import com.nexus.core.model.routing.OrderRouting;
import com.nexus.core.model.routing.OrderRoutingGroup;
import com.nexus.core.model.routing.OrderRoutingRule;
import com.nexus.core.model.routing.OrderRoutingRuleAction;
import com.nexus.core.model.routing.OrderRoutingRuleInvCond;
import com.nexus.core.model.routing.OrderRoutingRun;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Order routing (brokering), built the HotWax way and written fresh for Nexus. A routing group belongs to a store and
 * runs its routings in order. A routing takes approved orders whose ship groups wait in a queue (no location yet, or a
 * parking facility), filtered and sorted by its order conditions. For each ship group its rules are tried in order:
 * a rule looks at the locations its inventory conditions allow (group, type, distance, never an excluded location,
 * never a full one), sorted by its sorts (distance, group sequence, sales velocity, weeks of supply, broken size runs,
 * capacity left), and prefers one location that holds every item. A split rule (and a store that allows splits)
 * spreads the items over as few locations as it can. A rule that finds nothing runs its actions: try the next rule,
 * park the order, or set items to cancel after some days. Every move is an {@code order_facility_change} with the
 * reason in plain words, and every run is an {@code order_routing_run}. A simulation does all of it and rolls back.
 * <p>
 * Stock is what routing may promise: {@code product_facility.computed_last_inventory_count} (available less safety
 * stock), which every hold lowers at once, so a run never promises the same unit twice.
 */
@Service("coreRoutingService")
public class RoutingService {

    public static final String FLOW = StatusService.DEFAULT_FLOW;
    public static final String QUEUE = "_NA_";   // in a facilityId filter: ship groups with no location yet
    static final Set<String> VIRTUAL = Set.of("VIRTUAL_FACILITY", "IN_TRANSIT", "DROPSHIP");

    private final EntityManager em;
    private final SequenceService sequences;
    private final StatusService statuses;
    private final OrderService orders;
    private final FacilityService facilities;
    private final ProductStoreService stores;
    private final TransactionTemplate tx;

    public RoutingService(EntityManager em, SequenceService sequences, StatusService statuses, OrderService orders,
                          FacilityService facilities, ProductStoreService stores, TransactionTemplate tx) {
        this.em = em;
        this.sequences = sequences;
        this.statuses = statuses;
        this.orders = orders;
        this.facilities = facilities;
        this.stores = stores;
        this.tx = tx;
    }

    // ── setting up routing ──

    /** A filter ({@code sort} false) or sort on a field, with an operator and a value (comma-separated for "in"). */
    public record Cond(boolean sort, String field, String operator, String value) {
        public static Cond filter(String field, String operator, String value) {
            return new Cond(false, field, operator, value);
        }

        public static Cond sortBy(String field, String direction) {
            return new Cond(true, field, direction, null);
        }
    }

    public String createGroup(String productStoreId, String name, Long scheduleMinutes) {
        return tx.execute(s -> {
            if (em.find(ProductStore.class, productStoreId) == null) throw new IllegalArgumentException("Unknown store " + productStoreId);
            OrderRoutingGroup g = new OrderRoutingGroup();
            g.setRoutingGroupId(sequences.nextId("OrderRoutingGroup"));
            g.setProductStoreId(productStoreId);
            g.setGroupName(name);
            g.setScheduleMinutes(scheduleMinutes);
            g.setCreatedDate(CoreClock.now());
            em.persist(g);
            return g.getRoutingGroupId();
        });
    }

    /** A routing in a group: which orders it takes and in what order. Starts as a draft. */
    public String addRouting(String routingGroupId, String name, long sequenceNum, List<Cond> orderConditions) {
        return tx.execute(s -> {
            OrderRouting r = new OrderRouting();
            r.setOrderRoutingId(sequences.nextId("OrderRouting"));
            r.setRoutingGroupId(routingGroupId);
            r.setRoutingName(name);
            r.setSequenceNum(sequenceNum);
            r.setStatusId(statuses.initialStatus(FLOW, "ROUTING_STATUS"));
            em.persist(r);
            int n = 1;
            for (Cond c : orderConditions == null ? List.<Cond>of() : orderConditions) {
                checkField(c, ORDER_FILTERS, ORDER_SORTS);
                OrderFilterCondition f = new OrderFilterCondition();
                f.setOrderRoutingId(r.getOrderRoutingId());
                f.setConditionSeqId(SequenceService.seqId(n));
                f.setConditionTypeEnumId(c.sort() ? "ENTCT_SORT_BY" : "ENTCT_FILTER");
                f.setFieldName(c.field());
                f.setOperator(c.operator());
                f.setFieldValue(c.value());
                f.setSequenceNum((long) n++);
                em.persist(f);
            }
            return r.getOrderRoutingId();
        });
    }

    /** A rule of a routing: where to look, sorted how, one location or split, and what to do when nothing fits. */
    public String addRule(String orderRoutingId, String name, long sequenceNum, boolean split, List<Cond> inventoryConditions,
                          Map<String, String> actions) {
        return tx.execute(s -> {
            OrderRoutingRule r = new OrderRoutingRule();
            r.setRoutingRuleId(sequences.nextId("OrderRoutingRule"));
            r.setOrderRoutingId(orderRoutingId);
            r.setRuleName(name);
            r.setSequenceNum(sequenceNum);
            r.setAssignmentEnumId(split ? "ORA_MULTI" : "ORA_SINGLE");
            r.setStatusId(statuses.initialStatus(FLOW, "ROUTING_STATUS"));
            em.persist(r);
            int n = 1;
            for (Cond c : inventoryConditions == null ? List.<Cond>of() : inventoryConditions) {
                checkField(c, INVENTORY_FILTERS, INVENTORY_SORTS);
                OrderRoutingRuleInvCond ic = new OrderRoutingRuleInvCond();
                ic.setRoutingRuleId(r.getRoutingRuleId());
                ic.setInventoryConditionSeqId(SequenceService.seqId(n));
                ic.setConditionTypeEnumId(c.sort() ? "ENTCT_SORT_BY" : "ENTCT_FILTER");
                ic.setFieldName(c.field());
                ic.setOperator(c.operator());
                ic.setFieldValue(c.value());
                ic.setSequenceNum((long) n++);
                em.persist(ic);
            }
            int a = 1;
            for (Map.Entry<String, String> e : (actions == null ? Map.<String, String>of() : actions).entrySet()) {
                if (!Set.of("ORA_NEXT_RULE", "ORA_MV_TO_QUEUE", "ORA_AUTO_CANCEL_DAYS").contains(e.getKey())) {
                    throw new IllegalArgumentException("Unknown rule action " + e.getKey() + "; use ORA_NEXT_RULE, ORA_MV_TO_QUEUE or ORA_AUTO_CANCEL_DAYS");
                }
                OrderRoutingRuleAction ra = new OrderRoutingRuleAction();
                ra.setRoutingRuleId(r.getRoutingRuleId());
                ra.setActionSeqId(SequenceService.seqId(a++));
                ra.setActionTypeEnumId(e.getKey());
                ra.setActionValue(e.getValue());
                em.persist(ra);
            }
            return r.getRoutingRuleId();
        });
    }

    /** Activates a routing and all its draft rules. */
    public void activate(String orderRoutingId) {
        tx.executeWithoutResult(s -> {
            OrderRouting r = em.find(OrderRouting.class, orderRoutingId);
            if (r == null) throw new IllegalArgumentException("Unknown routing " + orderRoutingId);
            statuses.requireAllowed(FLOW, r.getStatusId(), "ROUTING_ACTIVE");
            r.setStatusId("ROUTING_ACTIVE");
            rules(orderRoutingId, false).stream().filter(x -> "ROUTING_DRAFT".equals(x.getStatusId())).forEach(x -> x.setStatusId("ROUTING_ACTIVE"));
        });
    }

    static final Set<String> ORDER_FILTERS = Set.of("facilityId", "salesChannelEnumId", "shipmentMethodTypeId", "priority", "orderAgeMinutes");
    static final Set<String> ORDER_SORTS = Set.of("orderDate", "priority", "shipByDate");
    static final Set<String> INVENTORY_FILTERS = Set.of("facilityGroupId", "facilityTypeId", "facilityId", "distance", "ignoreOrderLimit", "allowSubstitute");
    static final Set<String> INVENTORY_SORTS = Set.of("distance", "groupSequence", "salesVelocity", "weeksOfSupply", "brokenSizeRun", "capacityLeft", "inventory");

    private static void checkField(Cond c, Set<String> filters, Set<String> sorts) {
        Set<String> allowed = c.sort() ? sorts : filters;
        if (!allowed.contains(c.field())) {
            throw new IllegalArgumentException("Can't " + (c.sort() ? "sort" : "filter") + " on " + c.field() + "; use one of " + new java.util.TreeSet<>(allowed));
        }
    }

    // ── running ──

    /** What happened to one ship group. */
    public record Decision(String orderId, String shipGroupSeqId, List<String> facilityIds, String outcome, String explanation) {
    }

    public record RunResult(String routingRunId, boolean simulation, int considered, int routed, int split, int unfillable,
                            int substituted, List<Decision> decisions) {
    }

    /** Runs a routing group for real: holds are placed, locations set, the run recorded. */
    public RunResult run(String routingGroupId, String userId) {
        return execute(routingGroupId, false, userId);
    }

    /** Runs a routing group on the real orders and stock, reports what it would do, and changes nothing. */
    public RunResult simulate(String routingGroupId, String userId) {
        return execute(routingGroupId, true, userId);
    }

    private RunResult execute(String routingGroupId, boolean simulate, String userId) {
        LocalDateTime started = CoreClock.now();
        RunResult r = tx.execute(s -> {
            OrderRoutingGroup g = em.find(OrderRoutingGroup.class, routingGroupId, LockModeType.PESSIMISTIC_WRITE);
            if (g == null) throw new IllegalArgumentException("Unknown routing group " + routingGroupId);
            String runId = simulate ? null : sequences.nextId("OrderRoutingRun");
            if (!simulate) {
                OrderRoutingRun run = new OrderRoutingRun();
                run.setRoutingRunId(runId);
                run.setRoutingGroupId(routingGroupId);
                run.setIsSimulation(false);
                run.setStartDate(started);
                run.setCreatedByUserLogin(userId);
                em.persist(run);
                em.flush();
            }
            RunResult result = routeGroup(g, runId, simulate, userId);
            if (simulate) s.setRollbackOnly();
            else finish(runId, result);
            return result;
        });
        if (simulate) {
            // the simulation's changes rolled back; keep only the record of what it would have done
            String runId = tx.execute(s -> {
                OrderRoutingRun run = new OrderRoutingRun();
                run.setRoutingRunId(sequences.nextId("OrderRoutingRun"));
                run.setRoutingGroupId(routingGroupId);
                run.setIsSimulation(true);
                run.setStartDate(started);
                run.setCreatedByUserLogin(userId);
                em.persist(run);
                finish(run.getRoutingRunId(), r);
                return run.getRoutingRunId();
            });
            return new RunResult(runId, true, r.considered(), r.routed(), r.split(), r.unfillable(), r.substituted(), r.decisions());
        }
        return r;
    }

    private void finish(String runId, RunResult r) {
        OrderRoutingRun run = em.find(OrderRoutingRun.class, runId);
        run.setEndDate(CoreClock.now());
        run.setOrdersConsidered((long) r.considered());
        run.setOrdersRouted((long) r.routed());
        run.setOrdersSplit((long) r.split());
        run.setOrdersUnfillable((long) r.unfillable());
        run.setItemsSubstituted((long) r.substituted());
        run.setSummary(r.decisions().stream().map(d -> d.orderId() + "/" + d.shipGroupSeqId() + " " + d.outcome() + " " + d.facilityIds() + ": " + d.explanation())
                .collect(Collectors.joining("\n")));
    }

    private RunResult routeGroup(OrderRoutingGroup g, String runId, boolean simulate, String userId) {
        ProductStore store = em.find(ProductStore.class, g.getProductStoreId());
        boolean storeAllowsSplit = store == null || !Boolean.FALSE.equals(store.getAllowSplit());
        long maxLocations = store == null ? Long.MAX_VALUE : stores.settingLong(store.getProductStoreId(), "MAX_SPLIT_LOCATIONS", Long.MAX_VALUE);
        List<Decision> decisions = new ArrayList<>();
        int[] c = new int[5];   // considered, routed, split, unfillable, substituted
        Set<String> done = new HashSet<>();
        List<OrderRouting> routings = em.createQuery("SELECT r FROM OrderRouting r WHERE r.routingGroupId = :g AND r.statusId = 'ROUTING_ACTIVE' "
                + "ORDER BY r.sequenceNum, r.orderRoutingId", OrderRouting.class).setParameter("g", g.getRoutingGroupId()).getResultList();
        for (OrderRouting routing : routings) {
            List<OrderFilterCondition> conds = em.createQuery("SELECT c FROM OrderFilterCondition c WHERE c.orderRoutingId = :r ORDER BY c.sequenceNum",
                    OrderFilterCondition.class).setParameter("r", routing.getOrderRoutingId()).getResultList();
            List<OrderRoutingRule> rules = rules(routing.getOrderRoutingId(), true);
            for (OrderItemShipGroup sg : candidates(g.getProductStoreId(), conds)) {
                String key = sg.getOrderId() + "|" + sg.getShipGroupSeqId();
                if (!done.add(key)) continue;   // an earlier routing in this run already handled it
                c[0]++;
                Decision d = routeShipGroup(sg, rules, storeAllowsSplit, maxLocations, runId, userId, c);
                decisions.add(d);
            }
        }
        return new RunResult(runId, simulate, c[0], c[1], c[2], c[3], c[4], decisions);
    }

    /** Ship groups in the queue the routing's filters name, of approved orders of the store, sorted. */
    private List<OrderItemShipGroup> candidates(String productStoreId, List<OrderFilterCondition> conds) {
        List<OrderFilterCondition> filters = conds.stream().filter(c -> "ENTCT_FILTER".equals(c.getConditionTypeEnumId())).toList();
        List<String> queues = filters.stream().filter(c -> "facilityId".equals(c.getFieldName())).findFirst()
                .map(c -> values(c.getFieldValue())).orElse(List.of(QUEUE));
        List<Object[]> rows = em.createQuery("SELECT g, h FROM OrderItemShipGroup g, OrderHeader h WHERE h.orderId = g.orderId "
                + "AND h.productStoreId = :s AND h.statusId = 'ORDER_APPROVED'", Object[].class).setParameter("s", productStoreId).getResultList();
        LocalDateTime now = CoreClock.now();
        List<Object[]> kept = new ArrayList<>();
        for (Object[] row : rows) {
            OrderItemShipGroup sg = (OrderItemShipGroup) row[0];
            OrderHeader h = (OrderHeader) row[1];
            String at = sg.getFacilityId() == null ? QUEUE : sg.getFacilityId();
            if (!queues.contains(at)) continue;
            if (open(sg).isEmpty()) continue;
            boolean ok = true;
            for (OrderFilterCondition f : filters) {
                String v = f.getFieldValue();
                boolean not = "not-in".equalsIgnoreCase(f.getOperator()) || "!=".equals(f.getOperator());
                switch (f.getFieldName()) {
                    case "salesChannelEnumId" -> ok &= values(v).contains(h.getSalesChannelEnumId()) != not;
                    case "shipmentMethodTypeId" -> ok &= values(v).contains(sg.getShipmentMethodTypeId()) != not;
                    case "priority" -> ok &= h.getPriority() != null && h.getPriority() >= Long.parseLong(v.trim());
                    case "orderAgeMinutes" -> ok &= !h.getOrderDate().isAfter(now.minusMinutes(Long.parseLong(v.trim())));
                    default -> { }
                }
            }
            if (ok) kept.add(row);
        }
        Comparator<Object[]> order = Comparator.comparing(r -> ((OrderHeader) r[1]).getOrderDate());
        List<OrderFilterCondition> sorts = conds.stream().filter(c -> "ENTCT_SORT_BY".equals(c.getConditionTypeEnumId())).toList();
        if (!sorts.isEmpty()) {
            Comparator<Object[]> cmp = null;
            for (OrderFilterCondition s : sorts) {
                boolean desc = "desc".equalsIgnoreCase(s.getOperator());
                Comparator<Object[]> one = switch (s.getFieldName()) {
                    case "priority" -> Comparator.comparing(r -> ((OrderHeader) r[1]).getPriority() == null ? 0L : ((OrderHeader) r[1]).getPriority());
                    case "shipByDate" -> Comparator.comparing(r -> Optional.ofNullable(((OrderItemShipGroup) r[0]).getShipByDate()).orElse(LocalDateTime.MAX));
                    default -> Comparator.comparing(r -> ((OrderHeader) r[1]).getOrderDate());
                };
                if (desc) one = one.reversed();
                cmp = cmp == null ? one : cmp.thenComparing(one);
            }
            order = cmp.thenComparing(order);
        }
        kept.sort(order.thenComparing(r -> ((OrderHeader) r[1]).getOrderId()));
        return kept.stream().map(r -> (OrderItemShipGroup) r[0]).toList();
    }

    /** The group's items still waiting for stock: approved, with open quantity not yet held. */
    private Map<OrderItem, BigDecimal> open(OrderItemShipGroup sg) {
        Map<OrderItem, BigDecimal> m = new LinkedHashMap<>();
        for (OrderItem i : orders.items(sg.getOrderId())) {
            if (!sg.getShipGroupSeqId().equals(i.getShipGroupSeqId()) || !"ITEM_APPROVED".equals(i.getStatusId()) || i.getProductId() == null) continue;
            BigDecimal need = OrderService.openQuantity(i).subtract(orders.reservedQuantity(i.getOrderId(), i.getOrderItemSeqId()));
            if (need.signum() > 0) m.put(i, need);
        }
        return m;
    }

    private Decision routeShipGroup(OrderItemShipGroup sg, List<OrderRoutingRule> rules, boolean storeAllowsSplit, long maxLocations, String runId,
                                    String userId, int[] c) {
        String orderId = sg.getOrderId();
        String from = sg.getFacilityId();
        List<String> notes = new ArrayList<>();
        for (OrderRoutingRule rule : rules) {
            Map<OrderItem, BigDecimal> need = open(sg);
            if (need.isEmpty()) break;
            List<OrderRoutingRuleInvCond> conds = em.createQuery("SELECT c FROM OrderRoutingRuleInvCond c WHERE c.routingRuleId = :r ORDER BY c.sequenceNum",
                    OrderRoutingRuleInvCond.class).setParameter("r", rule.getRoutingRuleId()).getResultList();
            Rule ctx = new Rule(rule, conds, sg);
            List<Candidate> places = ctx.places(need);

            // 1. one location for everything
            Optional<Candidate> whole = places.stream().filter(p -> p.fillsAll(need)).findFirst();
            if (whole.isEmpty() && ctx.allowSubstitute) {
                int swapped = substitute(sg, need, places, ctx);
                if (swapped > 0) {
                    c[4] += swapped;
                    Map<OrderItem, BigDecimal> need2 = open(sg);
                    places = ctx.places(need2);
                    whole = places.stream().filter(p -> p.fillsAll(need2)).findFirst();
                    notes.add(swapped + " item(s) swapped for a substitute");
                }
            }
            if (whole.isPresent()) {
                Candidate p = whole.get();
                hold(sg, p.facilityId, ctx);
                change(orderId, sg.getShipGroupSeqId(), from, p.facilityId, "FCR_ROUTED", runId, rule.getRoutingRuleId(),
                        "Rule '" + rule.getRuleName() + "': " + p.why(ctx, true), userId);
                c[1]++;
                return new Decision(orderId, sg.getShipGroupSeqId(), List.of(p.facilityId), "ROUTED", p.why(ctx, true) + note(notes));
            }

            // 2. split over as few locations as possible
            if ("ORA_MULTI".equals(rule.getAssignmentEnumId()) && storeAllowsSplit && !Boolean.FALSE.equals(sg.getMaySplit()) && need.size() > 1) {
                Map<String, List<OrderItem>> plan = splitPlan(need, places);
                Set<OrderItem> covered = plan.values().stream().flatMap(List::stream).collect(Collectors.toSet());
                if (plan.size() > maxLocations) {
                    notes.add("a split needs " + plan.size() + " locations; the store allows " + maxLocations);
                } else if (plan.size() > 1 || (plan.size() == 1 && covered.size() < need.size())) {
                    List<String> used = new ArrayList<>();
                    boolean allCovered = covered.size() == need.size();
                    String biggest = plan.entrySet().stream().max(Comparator.comparingInt(e -> e.getValue().size())).map(Map.Entry::getKey).orElseThrow();
                    for (Map.Entry<String, List<OrderItem>> e : plan.entrySet()) {
                        if (allCovered && e.getKey().equals(biggest)) continue;   // the original group keeps the location with most items
                        String newGroup = orders.splitShipGroup(orderId, sg.getShipGroupSeqId(), e.getValue().stream().map(OrderItem::getOrderItemSeqId).toList(), null);
                        OrderItemShipGroup ng = orders.shipGroup(orderId, newGroup);
                        hold(ng, e.getKey(), ctx);
                        change(orderId, newGroup, null, e.getKey(), "FCR_SPLIT", runId, rule.getRoutingRuleId(),
                                "Rule '" + rule.getRuleName() + "': split; " + e.getValue().size() + " item(s) here", userId);
                        used.add(e.getKey());
                    }
                    if (allCovered) {
                        hold(sg, biggest, ctx);
                        change(orderId, sg.getShipGroupSeqId(), from, biggest, "FCR_ROUTED", runId, rule.getRoutingRuleId(),
                                "Rule '" + rule.getRuleName() + "': split; the location with the most items keeps the original shipment", userId);
                        used.add(0, biggest);
                    }
                    c[2]++;
                    if (allCovered) {
                        c[1]++;
                        return new Decision(orderId, sg.getShipGroupSeqId(), used, "SPLIT", "No one location holds every item; split over " + used.size() + note(notes));
                    }
                    notes.add("split " + covered.size() + " of " + need.size() + " items to " + used);
                }
            }

            // 3. nothing (or not everything) fits: the rule's actions
            List<OrderRoutingRuleAction> actions = em.createQuery("SELECT a FROM OrderRoutingRuleAction a WHERE a.routingRuleId = :r ORDER BY a.actionSeqId",
                    OrderRoutingRuleAction.class).setParameter("r", rule.getRoutingRuleId()).getResultList();
            notes.add("rule '" + rule.getRuleName() + "' found no location" + (places.isEmpty() ? " with stock" : " holding everything"));
            // order-independent: set cancel dates first, then park the order or try the next rule
            boolean next = actions.isEmpty() || actions.stream().anyMatch(x -> "ORA_NEXT_RULE".equals(x.getActionTypeEnumId()));
            for (OrderRoutingRuleAction a : actions) {
                if (!"ORA_AUTO_CANCEL_DAYS".equals(a.getActionTypeEnumId())) continue;
                LocalDateTime when = CoreClock.now().plusDays(Long.parseLong(a.getActionValue().trim()));
                open(sg).keySet().forEach(i -> { if (i.getAutoCancelDate() == null) i.setAutoCancelDate(when); });
                notes.add("items cancel on " + when.toLocalDate() + " unless routed");
            }
            Optional<OrderRoutingRuleAction> park = actions.stream().filter(x -> "ORA_MV_TO_QUEUE".equals(x.getActionTypeEnumId())).findFirst();
            if (park.isPresent()) {
                String q = park.get().getActionValue() == null || park.get().getActionValue().isBlank() ? "UNFILLABLE_PARKING" : park.get().getActionValue().trim();
                orders.setShipGroupFacility(orderId, sg.getShipGroupSeqId(), q);
                change(orderId, sg.getShipGroupSeqId(), from, q, "FCR_QUEUED", runId, rule.getRoutingRuleId(), String.join("; ", notes), userId);
                c[3]++;
                return new Decision(orderId, sg.getShipGroupSeqId(), List.of(q), "QUEUED", String.join("; ", notes));
            }
            if (!next) break;
        }
        c[3]++;
        return new Decision(orderId, sg.getShipGroupSeqId(), List.of(), "UNFILLABLE", notes.isEmpty() ? "no active rule" : String.join("; ", notes));
    }

    private static String note(List<String> notes) {
        return notes.isEmpty() ? "" : " (" + String.join("; ", notes) + ")";
    }

    /** Holds every open item of the group at the facility, sets the group there and counts the order there today. */
    private void hold(OrderItemShipGroup sg, String facilityId, Rule ctx) {
        open(sg).forEach((i, q) -> orders.reserve(sg.getOrderId(), i.getOrderItemSeqId(), facilityId, q, null));
        sg.setFacilityId(facilityId);
        if (!facilities.takeOrder(facilityId, today())) {
            if (!ctx.ignoreOrderLimit) throw new IllegalStateException("Facility " + facilityId + " filled up during routing");
            facilities.countOrder(facilityId, today());
        }
    }

    /** Greedy: the location covering the most remaining items first (ties by the rule's sort), until all are covered. */
    private static Map<String, List<OrderItem>> splitPlan(Map<OrderItem, BigDecimal> need, List<Candidate> places) {
        Map<String, List<OrderItem>> plan = new LinkedHashMap<>();
        Set<OrderItem> left = new HashSet<>(need.keySet());
        while (!left.isEmpty()) {
            Candidate best = null;
            List<OrderItem> bestItems = List.of();
            for (Candidate p : places) {
                if (plan.containsKey(p.facilityId)) continue;
                List<OrderItem> fits = left.stream().filter(i -> p.fills(i, need.get(i))).sorted(Comparator.comparing(OrderItem::getOrderItemSeqId)).toList();
                if (fits.size() > bestItems.size()) {
                    best = p;
                    bestItems = fits;
                }
            }
            if (best == null) break;
            plan.put(best.facilityId, bestItems);
            bestItems.forEach(left::remove);
        }
        return plan;
    }

    /** Swaps items no allowed location can fill for a substitute product one can; returns how many were swapped. */
    private int substitute(OrderItemShipGroup sg, Map<OrderItem, BigDecimal> need, List<Candidate> places, Rule ctx) {
        int swapped = 0;
        for (Map.Entry<OrderItem, BigDecimal> e : need.entrySet()) {
            OrderItem i = e.getKey();
            if (places.stream().anyMatch(p -> p.fills(i, e.getValue()))) continue;
            List<String> subs = em.createQuery("SELECT a.productIdTo FROM ProductAssoc a WHERE a.productId = :p AND a.productAssocTypeId = 'PRODUCT_SUBSTITUTE' "
                    + "AND a.thruDate IS NULL ORDER BY a.sequenceNum", String.class).setParameter("p", i.getProductId()).getResultList();
            for (String sub : subs) {
                if (ctx.sellableAnywhere(sub, e.getValue())) {
                    orders.setItemAttribute(i.getOrderId(), i.getOrderItemSeqId(), "substitutedFor", i.getProductId());
                    i.setProductId(sub);
                    swapped++;
                    break;
                }
            }
        }
        return swapped;
    }

    // ── moving orders by hand or in bulk ──

    /**
     * Moves a ship group to another facility: its holds where it was are released, it is held at the new place, and
     * the move is recorded. A person may move an order to a facility that is full for the day.
     */
    public void reallocate(String orderId, String shipGroupSeqId, String toFacilityId, String reason, String userId) {
        tx.executeWithoutResult(s -> {
            OrderItemShipGroup sg = orders.shipGroup(orderId, shipGroupSeqId);
            String from = release(sg);
            for (OrderItem i : orders.items(orderId)) {
                if (!shipGroupSeqId.equals(i.getShipGroupSeqId()) || !"ITEM_APPROVED".equals(i.getStatusId()) || i.getProductId() == null) continue;
                orders.reserve(orderId, i.getOrderItemSeqId(), toFacilityId, null, userId);
            }
            sg.setFacilityId(toFacilityId);
            if (!facilities.takeOrder(toFacilityId, today())) facilities.countOrder(toFacilityId, today());
            change(orderId, shipGroupSeqId, from, toFacilityId, "FCR_MANUAL", null, null, reason, userId);
        });
    }

    /**
     * Takes every open, unshipped ship group off a facility (it closed, is overloaded, ran out): holds released, the
     * facility excluded for those items, the group back in the routing queue for the next run. Returns how many moved.
     */
    public int moveAllOff(String facilityId, String reason, String userId) {
        return tx.execute(s -> {
            List<OrderItemShipGroup> groups = em.createQuery("SELECT g FROM OrderItemShipGroup g, OrderHeader h WHERE h.orderId = g.orderId "
                    + "AND g.facilityId = :f AND h.statusId IN ('ORDER_APPROVED', 'ORDER_HOLD')", OrderItemShipGroup.class).setParameter("f", facilityId).getResultList();
            int moved = 0;
            for (OrderItemShipGroup sg : groups) {
                boolean shipping = em.createQuery("SELECT COUNT(sh) FROM Shipment sh WHERE sh.primaryOrderId = :o AND sh.primaryShipGroupSeqId = :g "
                        + "AND sh.statusId NOT IN ('SHIPMENT_CANCELLED')", Long.class).setParameter("o", sg.getOrderId()).setParameter("g", sg.getShipGroupSeqId())
                        .getSingleResult() > 0;
                if (shipping) continue;
                release(sg);
                orders.items(sg.getOrderId()).stream().filter(i -> sg.getShipGroupSeqId().equals(i.getShipGroupSeqId()))
                        .forEach(i -> orders.exclude(sg.getOrderId(), i.getOrderItemSeqId(), facilityId));
                sg.setFacilityId(null);
                change(sg.getOrderId(), sg.getShipGroupSeqId(), facilityId, null, "FCR_BULK", null, null, reason, userId);
                moved++;
            }
            return moved;
        });
    }

    /** Why each move of an order happened, oldest first. */
    public List<OrderFacilityChange> history(String orderId) {
        return em.createQuery("SELECT c FROM OrderFacilityChange c WHERE c.orderId = :o ORDER BY c.changeDatetime", OrderFacilityChange.class)
                .setParameter("o", orderId).getResultList();
    }

    /** Releases the group's holds and gives back its place in the facility's daily count; returns the old facility. */
    private String release(OrderItemShipGroup sg) {
        String from = sg.getFacilityId();
        for (OrderItem i : orders.items(sg.getOrderId())) {
            if (sg.getShipGroupSeqId().equals(i.getShipGroupSeqId())) orders.releaseReservations(sg.getOrderId(), i.getOrderItemSeqId(), null);
        }
        if (from != null && em.find(Facility.class, from) != null) facilities.releaseOrder(from, today());
        return from;
    }

    private void change(String orderId, String shipGroupSeqId, String from, String to, String reasonEnumId, String runId, String ruleId,
                        String comments, String userId) {
        OrderFacilityChange c = new OrderFacilityChange();
        c.setOrderFacilityChangeId(sequences.nextId("OrderFacilityChange"));
        c.setOrderId(orderId);
        c.setShipGroupSeqId(shipGroupSeqId);
        c.setFromFacilityId(from);
        c.setFacilityId(to);
        c.setChangeDatetime(CoreClock.now());
        c.setChangeReasonEnumId(reasonEnumId);
        c.setRoutingRunId(runId);
        c.setRoutingRuleId(ruleId);
        c.setComments(comments);
        c.setChangeUserLogin(userId);
        em.persist(c);
    }

    private List<OrderRoutingRule> rules(String orderRoutingId, boolean activeOnly) {
        return em.createQuery("SELECT r FROM OrderRoutingRule r WHERE r.orderRoutingId = :r" + (activeOnly ? " AND r.statusId = 'ROUTING_ACTIVE'" : "")
                + " ORDER BY r.sequenceNum, r.routingRuleId", OrderRoutingRule.class).setParameter("r", orderRoutingId).getResultList();
    }

    private static LocalDate today() {
        return CoreClock.now().toLocalDate();
    }

    static List<String> values(String v) {
        return v == null ? List.of() : Arrays.stream(v.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }

    // ── one rule's view of the locations ──

    /** A location a rule may use, with what it can promise per product and the numbers its sorts use. */
    private static final class Candidate {
        final String facilityId;
        final Map<String, BigDecimal> sellable = new HashMap<>();
        Double distanceKm;
        long groupSequence = Long.MAX_VALUE;
        double velocity;
        double weeksOfSupply = Double.MAX_VALUE;
        double sizeRunCompleteness = 1;
        long capacityLeft = Long.MAX_VALUE;

        Candidate(String facilityId) {
            this.facilityId = facilityId;
        }

        boolean fills(OrderItem i, BigDecimal q) {
            return sellable.getOrDefault(i.getProductId(), BigDecimal.ZERO).compareTo(q) >= 0;
        }

        boolean fillsAll(Map<OrderItem, BigDecimal> need) {
            return need.entrySet().stream().allMatch(e -> fills(e.getKey(), e.getValue()));
        }

        String why(Rule ctx, boolean whole) {
            List<String> parts = new ArrayList<>();
            if (whole) parts.add("holds every item");
            if (distanceKm != null) parts.add(String.format(Locale.ROOT, "%.1f km away", distanceKm));
            if (groupSequence != Long.MAX_VALUE) parts.add("place " + groupSequence + " in its group");
            if (capacityLeft != Long.MAX_VALUE) parts.add(capacityLeft + " orders left today");
            if (!ctx.sortNames.isEmpty()) parts.add("sorted by " + String.join(", ", ctx.sortNames));
            return String.join("; ", parts);
        }
    }

    private final class Rule {
        final List<String> groupIds;
        final List<String> types;
        final List<String> notTypes;
        final List<String> onlyFacilities;
        final Double maxKm;
        final boolean ignoreOrderLimit;
        final boolean allowSubstitute;
        final List<OrderRoutingRuleInvCond> sorts;
        final List<String> sortNames;
        final double[] shipTo;
        final Set<String> excluded = new HashSet<>();
        final boolean pickup;

        Rule(OrderRoutingRule rule, List<OrderRoutingRuleInvCond> conds, OrderItemShipGroup sg) {
            Map<String, OrderRoutingRuleInvCond> f = new HashMap<>();
            conds.stream().filter(c -> "ENTCT_FILTER".equals(c.getConditionTypeEnumId())).forEach(c -> f.put(c.getFieldName(), c));
            groupIds = f.containsKey("facilityGroupId") ? values(f.get("facilityGroupId").getFieldValue()) : List.of();
            OrderRoutingRuleInvCond type = f.get("facilityTypeId");
            boolean typeNot = type != null && ("not-in".equalsIgnoreCase(type.getOperator()) || "!=".equals(type.getOperator()));
            types = type != null && !typeNot ? values(type.getFieldValue()) : List.of();
            notTypes = type != null && typeNot ? values(type.getFieldValue()) : List.of();
            onlyFacilities = f.containsKey("facilityId") ? values(f.get("facilityId").getFieldValue()) : List.of();
            maxKm = f.containsKey("distance") ? Double.valueOf(f.get("distance").getFieldValue().trim()) : null;
            ignoreOrderLimit = f.containsKey("ignoreOrderLimit") && "Y".equalsIgnoreCase(f.get("ignoreOrderLimit").getFieldValue());
            allowSubstitute = f.containsKey("allowSubstitute") && "Y".equalsIgnoreCase(f.get("allowSubstitute").getFieldValue());
            sorts = conds.stream().filter(c -> "ENTCT_SORT_BY".equals(c.getConditionTypeEnumId())).toList();
            sortNames = sorts.stream().map(OrderRoutingRuleInvCond::getFieldName).toList();
            shipTo = coordinates(sg.getContactMechId());
            pickup = "STOREPICKUP".equals(sg.getShipmentMethodTypeId());
            for (OrderItem i : orders.items(sg.getOrderId())) {
                if (sg.getShipGroupSeqId().equals(i.getShipGroupSeqId())) excluded.addAll(orders.excludedFacilities(i.getOrderId(), i.getOrderItemSeqId()));
            }
        }

        /** Allowed locations holding any of the products, sorted by the rule. */
        List<Candidate> places(Map<OrderItem, BigDecimal> need) {
            Set<String> productIds = need.keySet().stream().map(OrderItem::getProductId).collect(Collectors.toSet());
            if (productIds.isEmpty()) return List.of();
            List<ProductFacility> stock = em.createQuery("SELECT pf FROM ProductFacility pf WHERE pf.productId IN :p AND pf.computedLastInventoryCount > 0",
                    ProductFacility.class).setParameter("p", productIds).getResultList();
            Map<String, Candidate> byFacility = new LinkedHashMap<>();
            for (ProductFacility pf : stock) {
                if (Boolean.FALSE.equals(pf.getAllowBrokering())) continue;
                if (pickup && Boolean.FALSE.equals(pf.getAllowPickup())) continue;
                Candidate c = byFacility.computeIfAbsent(pf.getFacilityId(), Candidate::new);
                c.sellable.put(pf.getProductId(), pf.getComputedLastInventoryCount());
                if (pf.getSalesVelocity() != null) c.velocity = Math.max(c.velocity, pf.getSalesVelocity().doubleValue());
                if (pf.getSalesVelocity() != null && pf.getSalesVelocity().signum() > 0) {
                    c.weeksOfSupply = Math.min(c.weeksOfSupply, pf.getComputedLastInventoryCount().doubleValue() / (pf.getSalesVelocity().doubleValue() * 7));
                }
            }
            Map<String, Long> groupSeq = new HashMap<>();
            if (!groupIds.isEmpty()) {
                LocalDateTime now = CoreClock.now();
                for (Object[] r : em.createQuery("SELECT m.facilityId, m.sequenceNum, m.facilityGroupId FROM FacilityGroupMember m WHERE m.facilityGroupId IN :g "
                        + "AND m.fromDate <= :now AND (m.thruDate IS NULL OR m.thruDate > :now)", Object[].class)
                        .setParameter("g", groupIds).setParameter("now", now).getResultList()) {
                    long seq = (groupIds.indexOf((String) r[2]) * 100000L) + (r[1] == null ? 99999L : (Long) r[1]);
                    groupSeq.merge((String) r[0], seq, Math::min);
                }
            }
            List<Candidate> out = new ArrayList<>();
            for (Candidate c : byFacility.values()) {
                Facility f = em.find(Facility.class, c.facilityId);
                if (f == null || VIRTUAL.contains(f.getFacilityTypeId()) || isVirtualParent(f.getFacilityTypeId())) continue;
                if (f.getClosedDate() != null && !f.getClosedDate().isAfter(CoreClock.now())) continue;
                if (excluded.contains(c.facilityId)) continue;
                if (!onlyFacilities.isEmpty() && !onlyFacilities.contains(c.facilityId)) continue;
                if (!types.isEmpty() && !types.contains(f.getFacilityTypeId())) continue;
                if (notTypes.contains(f.getFacilityTypeId())) continue;
                if (!groupIds.isEmpty()) {
                    Long seq = groupSeq.get(c.facilityId);
                    if (seq == null) continue;
                    c.groupSequence = seq % 100000L;
                }
                if (f.getMaximumOrderLimit() != null) {
                    long left = facilities.ordersLeft(c.facilityId, today()).orElse(Long.MAX_VALUE);
                    if (left <= 0 && !ignoreOrderLimit) continue;
                    c.capacityLeft = left;
                }
                if (shipTo != null && f.getLatitude() != null && f.getLongitude() != null) {
                    c.distanceKm = km(shipTo[0], shipTo[1], f.getLatitude().doubleValue(), f.getLongitude().doubleValue());
                }
                if (maxKm != null && (c.distanceKm == null || c.distanceKm > maxKm)) continue;
                if (sortNames.contains("brokenSizeRun")) c.sizeRunCompleteness = sizeRun(need.keySet(), c.facilityId);
                out.add(c);
            }
            out.sort(comparator().thenComparing(c -> c.facilityId));
            return out;
        }

        boolean sellableAnywhere(String productId, BigDecimal q) {
            return em.createQuery("SELECT pf.facilityId FROM ProductFacility pf WHERE pf.productId = :p AND pf.computedLastInventoryCount >= :q", String.class)
                    .setParameter("p", productId).setParameter("q", q).getResultList().stream()
                    .anyMatch(fid -> !excluded.contains(fid) && Optional.ofNullable(em.find(Facility.class, fid)).map(f -> !VIRTUAL.contains(f.getFacilityTypeId())).orElse(false));
        }

        private Comparator<Candidate> comparator() {
            Comparator<Candidate> cmp = (a, b) -> 0;
            for (OrderRoutingRuleInvCond s : sorts) {
                boolean desc = "desc".equalsIgnoreCase(s.getOperator());
                Comparator<Candidate> one = switch (s.getFieldName()) {
                    case "distance" -> Comparator.comparing(c -> c.distanceKm == null ? Double.MAX_VALUE : c.distanceKm);
                    case "groupSequence" -> Comparator.comparingLong(c -> c.groupSequence);
                    // protect fast sellers: the slowest-selling location gives first
                    case "salesVelocity" -> Comparator.comparingDouble(c -> c.velocity);
                    // most supply left first
                    case "weeksOfSupply" -> Comparator.<Candidate>comparingDouble(c -> c.weeksOfSupply).reversed();
                    // a location whose size run is already broken gives first, keeping full runs together
                    case "brokenSizeRun" -> Comparator.comparingDouble(c -> c.sizeRunCompleteness);
                    case "capacityLeft" -> Comparator.<Candidate>comparingLong(c -> c.capacityLeft).reversed();
                    case "inventory" -> Comparator.<Candidate>comparingDouble(c -> c.sellable.values().stream().mapToDouble(BigDecimal::doubleValue).sum()).reversed();
                    default -> (a, b) -> 0;
                };
                if (desc && !"weeksOfSupply".equals(s.getFieldName()) && !"capacityLeft".equals(s.getFieldName()) && !"inventory".equals(s.getFieldName())) one = one.reversed();
                cmp = cmp.thenComparing(one);
            }
            return cmp;
        }

        /** Share of the style's sizes (sibling variants) the facility can still sell; lower is more broken. */
        private double sizeRun(Set<OrderItem> items, String facilityId) {
            double worst = 1;
            for (OrderItem i : items) {
                List<String> parents = em.createQuery("SELECT a.productId FROM ProductAssoc a WHERE a.productIdTo = :p AND a.productAssocTypeId = 'PRODUCT_VARIANT' "
                        + "AND a.thruDate IS NULL", String.class).setParameter("p", i.getProductId()).getResultList();
                if (parents.isEmpty()) continue;
                List<String> sizes = em.createQuery("SELECT a.productIdTo FROM ProductAssoc a WHERE a.productId = :p AND a.productAssocTypeId = 'PRODUCT_VARIANT' "
                        + "AND a.thruDate IS NULL", String.class).setParameter("p", parents.get(0)).getResultList();
                if (sizes.isEmpty()) continue;
                long inStock = em.createQuery("SELECT COUNT(pf) FROM ProductFacility pf WHERE pf.facilityId = :f AND pf.productId IN :s "
                        + "AND pf.computedLastInventoryCount > 0", Long.class).setParameter("f", facilityId).setParameter("s", sizes).getSingleResult();
                worst = Math.min(worst, (double) inStock / sizes.size());
            }
            return worst;
        }
    }

    private boolean isVirtualParent(String facilityTypeId) {
        var t = em.find(com.nexus.core.model.facility.FacilityType.class, facilityTypeId);
        return t != null && t.getParentTypeId() != null && VIRTUAL.contains(t.getParentTypeId());
    }

    private double[] coordinates(String contactMechId) {
        if (contactMechId == null) return null;
        PostalAddress a = em.find(PostalAddress.class, contactMechId);
        if (a == null || a.getLatitude() == null || a.getLongitude() == null) return null;
        return new double[]{a.getLatitude().doubleValue(), a.getLongitude().doubleValue()};
    }

    /** Great-circle distance in kilometres. */
    static double km(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }
}
