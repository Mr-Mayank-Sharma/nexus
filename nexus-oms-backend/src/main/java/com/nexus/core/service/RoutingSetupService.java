package com.nexus.core.service;

import com.nexus.core.CoreStatus;
import com.nexus.core.model.product.ProductStore;
import com.nexus.core.model.routing.OrderFilterCondition;
import com.nexus.core.model.routing.OrderRouting;
import com.nexus.core.model.routing.OrderRoutingGroup;
import com.nexus.core.model.routing.OrderRoutingRule;
import com.nexus.core.model.routing.OrderRoutingRuleAction;
import com.nexus.core.model.routing.OrderRoutingRuleInvCond;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import com.nexus.core.CoreTransactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Setting up order routing: a routing group per store, its routings (which orders, in what order), and each routing's
 * rules (where to look, sorted how, one location or split, and what to do when nothing fits). Everything starts as a
 * draft; {@link #activate} turns a routing and its rules on. Running them is {@link RoutingService}.
 */
@Service("coreRoutingSetupService")
@CoreTransactional
public class RoutingSetupService {

    public static final String FLOW = StatusService.DEFAULT_FLOW;

    /** Fields a routing may filter its orders on. */
    public static final Set<String> ORDER_FILTERS = Set.of("facilityId", "salesChannelEnumId", "shipmentMethodTypeId", "priority", "orderAgeMinutes");
    /** Fields a routing may sort its orders by. */
    public static final Set<String> ORDER_SORTS = Set.of("orderDate", "priority", "shipByDate");
    /** Fields a rule may filter locations on. */
    public static final Set<String> INVENTORY_FILTERS = Set.of("facilityGroupId", "facilityTypeId", "facilityId", "distance", "ignoreOrderLimit",
            "allowSubstitute");
    /** Fields a rule may sort locations by. */
    public static final Set<String> INVENTORY_SORTS = Set.of("distance", "groupSequence", "salesVelocity", "weeksOfSupply", "brokenSizeRun",
            "capacityLeft", "inventory", "reliability");

    // Condition types and assignments, as stored.
    static final String FILTER = "ENTCT_FILTER";
    static final String SORT = "ENTCT_SORT_BY";
    static final String ONE_LOCATION = "ORA_SINGLE";
    static final String SPLIT_ALLOWED = "ORA_MULTI";

    // What a rule does when it finds no location.
    static final String NEXT_RULE = "ORA_NEXT_RULE";
    static final String MOVE_TO_QUEUE = "ORA_MV_TO_QUEUE";
    static final String AUTO_CANCEL_DAYS = "ORA_AUTO_CANCEL_DAYS";
    static final Set<String> RULE_ACTIONS = Set.of(NEXT_RULE, MOVE_TO_QUEUE, AUTO_CANCEL_DAYS);

    private final EntityManager entityManager;
    private final SequenceService sequenceService;
    private final StatusService statusService;

    public RoutingSetupService(EntityManager entityManager, SequenceService sequenceService, StatusService statusService) {
        this.entityManager = entityManager;
        this.sequenceService = sequenceService;
        this.statusService = statusService;
    }

    /** A filter ({@code sort} false) or sort on a field, with an operator and a value (comma-separated for "in"). */
    public record Cond(boolean sort, String field, String operator, String value) {
        public static Cond filter(String field, String operator, String value) {
            return new Cond(false, field, operator, value);
        }

        public static Cond sortBy(String field, String direction) {
            return new Cond(true, field, direction, null);
        }
    }

    /** A routing group for a store. {@code scheduleMinutes}: how often the job runs it; null for never. */
    public String createGroup(String productStoreId, String name, Long scheduleMinutes) {
        if (entityManager.find(ProductStore.class, productStoreId) == null) {
            throw new IllegalArgumentException("Unknown store " + productStoreId);
        }
        OrderRoutingGroup group = new OrderRoutingGroup();
        group.setRoutingGroupId(sequenceService.nextId("OrderRoutingGroup"));
        group.setProductStoreId(productStoreId);
        group.setGroupName(name);
        group.setScheduleMinutes(scheduleMinutes);
        group.setCreatedDate(CoreClock.now());
        entityManager.persist(group);
        return group.getRoutingGroupId();
    }

    /** A routing in a group: which orders it takes and in what order. Starts as a draft. */
    public String addRouting(String routingGroupId, String name, long sequenceNum, List<Cond> orderConditions) {
        OrderRouting routing = new OrderRouting();
        routing.setOrderRoutingId(sequenceService.nextId("OrderRouting"));
        routing.setRoutingGroupId(routingGroupId);
        routing.setRoutingName(name);
        routing.setSequenceNum(sequenceNum);
        routing.setStatusId(statusService.initialStatus(FLOW, CoreStatus.Type.ROUTING));
        entityManager.persist(routing);

        int conditionNumber = 1;
        for (Cond condition : orderConditions == null ? List.<Cond>of() : orderConditions) {
            requireKnownField(condition, ORDER_FILTERS, ORDER_SORTS);
            OrderFilterCondition row = new OrderFilterCondition();
            row.setOrderRoutingId(routing.getOrderRoutingId());
            row.setConditionSeqId(SequenceService.seqId(conditionNumber));
            row.setConditionTypeEnumId(condition.sort() ? SORT : FILTER);
            row.setFieldName(condition.field());
            row.setOperator(condition.operator());
            row.setFieldValue(condition.value());
            row.setSequenceNum((long) conditionNumber++);
            entityManager.persist(row);
        }
        return routing.getOrderRoutingId();
    }

    /**
     * A rule of a routing: where to look ({@code inventoryConditions}), one location or split, and what to do when
     * nothing fits ({@code actions}: action type to value). Starts as a draft.
     */
    public String addRule(String orderRoutingId, String name, long sequenceNum, boolean split, List<Cond> inventoryConditions,
                          Map<String, String> actions) {
        OrderRoutingRule rule = new OrderRoutingRule();
        rule.setRoutingRuleId(sequenceService.nextId("OrderRoutingRule"));
        rule.setOrderRoutingId(orderRoutingId);
        rule.setRuleName(name);
        rule.setSequenceNum(sequenceNum);
        rule.setAssignmentEnumId(split ? SPLIT_ALLOWED : ONE_LOCATION);
        rule.setStatusId(statusService.initialStatus(FLOW, CoreStatus.Type.ROUTING));
        entityManager.persist(rule);

        int conditionNumber = 1;
        for (Cond condition : inventoryConditions == null ? List.<Cond>of() : inventoryConditions) {
            requireKnownField(condition, INVENTORY_FILTERS, INVENTORY_SORTS);
            OrderRoutingRuleInvCond row = new OrderRoutingRuleInvCond();
            row.setRoutingRuleId(rule.getRoutingRuleId());
            row.setInventoryConditionSeqId(SequenceService.seqId(conditionNumber));
            row.setConditionTypeEnumId(condition.sort() ? SORT : FILTER);
            row.setFieldName(condition.field());
            row.setOperator(condition.operator());
            row.setFieldValue(condition.value());
            row.setSequenceNum((long) conditionNumber++);
            entityManager.persist(row);
        }

        int actionNumber = 1;
        for (Map.Entry<String, String> action : (actions == null ? Map.<String, String>of() : actions).entrySet()) {
            if (!RULE_ACTIONS.contains(action.getKey())) {
                throw new IllegalArgumentException("Unknown rule action " + action.getKey() + "; use ORA_NEXT_RULE, ORA_MV_TO_QUEUE or ORA_AUTO_CANCEL_DAYS");
            }
            OrderRoutingRuleAction row = new OrderRoutingRuleAction();
            row.setRoutingRuleId(rule.getRoutingRuleId());
            row.setActionSeqId(SequenceService.seqId(actionNumber++));
            row.setActionTypeEnumId(action.getKey());
            row.setActionValue(action.getValue());
            entityManager.persist(row);
        }
        return rule.getRoutingRuleId();
    }

    /** Activates a routing and all its draft rules. */
    public void activate(String orderRoutingId) {
        OrderRouting routing = entityManager.find(OrderRouting.class, orderRoutingId);
        if (routing == null) {
            throw new IllegalArgumentException("Unknown routing " + orderRoutingId);
        }
        statusService.requireAllowed(FLOW, routing.getStatusId(), CoreStatus.Routing.ACTIVE);
        routing.setStatusId(CoreStatus.Routing.ACTIVE);
        rules(orderRoutingId, false).stream()
                .filter(rule -> CoreStatus.Routing.DRAFT.equals(rule.getStatusId()))
                .forEach(rule -> rule.setStatusId(CoreStatus.Routing.ACTIVE));
    }

    /** Every how many minutes a group runs when it takes over from a group that had no schedule. */
    static final long DEFAULT_SCHEDULE_MINUTES = 15;

    /**
     * Makes one group the one that runs: it takes over the other group's schedule (15 minutes when there was none or
     * no other group) and the other stops running. Both keep their rules, so calling it the other way round undoes it.
     */
    public void handOverSchedule(String activateGroupId, String pauseGroupId) {
        OrderRoutingGroup groupToActivate = entityManager.find(OrderRoutingGroup.class, activateGroupId);
        if (groupToActivate == null) {
            throw new IllegalArgumentException("Unknown routing group " + activateGroupId);
        }
        long scheduleMinutes = DEFAULT_SCHEDULE_MINUTES;
        if (pauseGroupId != null) {
            OrderRoutingGroup groupToPause = entityManager.find(OrderRoutingGroup.class, pauseGroupId);
            if (groupToPause == null) {
                throw new IllegalArgumentException("Unknown routing group " + pauseGroupId);
            }
            if (groupToPause.getScheduleMinutes() != null) {
                scheduleMinutes = groupToPause.getScheduleMinutes();
            }
            groupToPause.setScheduleMinutes(null);
        }
        groupToActivate.setScheduleMinutes(scheduleMinutes);
    }

    // ── reading what was set up ──

    /** A store's routing groups, oldest first. */
    @CoreTransactional(readOnly = true)
    public List<OrderRoutingGroup> groupsOfStore(String productStoreId) {
        return entityManager.createQuery("SELECT g FROM OrderRoutingGroup g WHERE g.productStoreId = :storeId ORDER BY g.routingGroupId",
                        OrderRoutingGroup.class)
                .setParameter("storeId", productStoreId)
                .getResultList();
    }

    /** The group that runs for the store on a schedule (the first one when several do); null when none does. */
    @CoreTransactional(readOnly = true)
    public String liveGroupId(String productStoreId) {
        return entityManager.createQuery("SELECT g.routingGroupId FROM OrderRoutingGroup g WHERE g.productStoreId = :storeId "
                        + "AND g.scheduleMinutes IS NOT NULL ORDER BY g.sequenceNum, g.routingGroupId", String.class)
                .setParameter("storeId", productStoreId)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst()
                .orElse(null);
    }

    /** Every routing of a group, whatever its status, in sequence. */
    @CoreTransactional(readOnly = true)
    public List<OrderRouting> routingsOfGroup(String routingGroupId) {
        return entityManager.createQuery("SELECT r FROM OrderRouting r WHERE r.routingGroupId = :groupId ORDER BY r.sequenceNum", OrderRouting.class)
                .setParameter("groupId", routingGroupId)
                .getResultList();
    }

    /** The group's active routings, in sequence. */
    @CoreTransactional(readOnly = true)
    public List<OrderRouting> activeRoutings(String routingGroupId) {
        return entityManager.createQuery("SELECT r FROM OrderRouting r WHERE r.routingGroupId = :groupId AND r.statusId = :active "
                        + "ORDER BY r.sequenceNum, r.orderRoutingId", OrderRouting.class)
                .setParameter("groupId", routingGroupId)
                .setParameter("active", CoreStatus.Routing.ACTIVE)
                .getResultList();
    }

    /** A routing's rules in sequence; only the active ones when {@code activeOnly}. */
    @CoreTransactional(readOnly = true)
    public List<OrderRoutingRule> rules(String orderRoutingId, boolean activeOnly) {
        String activeFilter = activeOnly ? " AND r.statusId = '" + CoreStatus.Routing.ACTIVE + "'" : "";
        return entityManager.createQuery("SELECT r FROM OrderRoutingRule r WHERE r.orderRoutingId = :routingId" + activeFilter
                        + " ORDER BY r.sequenceNum, r.routingRuleId", OrderRoutingRule.class)
                .setParameter("routingId", orderRoutingId)
                .getResultList();
    }

    @CoreTransactional(readOnly = true)
    public List<OrderFilterCondition> orderConditions(String orderRoutingId) {
        return entityManager.createQuery("SELECT c FROM OrderFilterCondition c WHERE c.orderRoutingId = :routingId ORDER BY c.sequenceNum",
                        OrderFilterCondition.class)
                .setParameter("routingId", orderRoutingId)
                .getResultList();
    }

    @CoreTransactional(readOnly = true)
    public List<OrderRoutingRuleInvCond> inventoryConditions(String routingRuleId) {
        return entityManager.createQuery("SELECT c FROM OrderRoutingRuleInvCond c WHERE c.routingRuleId = :ruleId ORDER BY c.sequenceNum",
                        OrderRoutingRuleInvCond.class)
                .setParameter("ruleId", routingRuleId)
                .getResultList();
    }

    @CoreTransactional(readOnly = true)
    public List<OrderRoutingRuleAction> actions(String routingRuleId) {
        return entityManager.createQuery("SELECT a FROM OrderRoutingRuleAction a WHERE a.routingRuleId = :ruleId ORDER BY a.actionSeqId",
                        OrderRoutingRuleAction.class)
                .setParameter("ruleId", routingRuleId)
                .getResultList();
    }

    private static void requireKnownField(Cond condition, Set<String> filterFields, Set<String> sortFields) {
        Set<String> allowed = condition.sort() ? sortFields : filterFields;
        if (!allowed.contains(condition.field())) {
            String verb = condition.sort() ? "sort" : "filter";
            throw new IllegalArgumentException("Can't " + verb + " on " + condition.field() + "; use one of " + new TreeSet<>(allowed));
        }
    }
}
