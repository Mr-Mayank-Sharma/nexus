package com.nexus.core.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.core.model.routing.OrderFilterCondition;
import com.nexus.core.model.routing.OrderRouting;
import com.nexus.core.model.routing.OrderRoutingGroup;
import com.nexus.core.model.routing.OrderRoutingRule;
import com.nexus.core.model.routing.OrderRoutingRuleAction;
import com.nexus.core.model.routing.OrderRoutingRuleInvCond;
import com.nexus.core.service.RoutingService;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Plain-language routing (doc 25, feature 14): a manager describes a change ("send pickup orders only to stores in the
 * Downtown group, nearest first"); the agent reads the current rules, drafts a rule set as a new routing group that does
 * not run, simulates it next to the live group on real orders and stock, and proposes switching. Nothing goes live
 * until a person approves the ACTIVATE_ROUTING proposal; undo switches back.
 */
@Component("coreRoutingDrafter")
public class RoutingDrafter {

    public static final String NAME = "routing_drafter";

    private final EntityManager em;
    private final RoutingService routing;
    private final OperationsAssistant assistant;
    private final ProposalService proposals;
    private final AgentRunner runner;
    private final LlmProvider provider;
    private final TransactionTemplate readOnly;
    private final String model;

    public RoutingDrafter(EntityManager em, RoutingService routing, OperationsAssistant assistant, ProposalService proposals, AgentRunner runner,
                          @Qualifier("coreAnthropicProvider") LlmProvider provider, PlatformTransactionManager tm,
                          @Value("${nexus.ai.model.routing:claude-opus-5-5}") String model) {
        this.em = em;
        this.routing = routing;
        this.assistant = assistant;
        this.proposals = proposals;
        this.runner = runner;
        this.provider = provider;
        this.readOnly = new TransactionTemplate(tm);
        this.readOnly.setReadOnly(true);
        this.model = model;
    }

    static String system() {
        return """
                You turn a manager's description of how orders should be routed into a Nexus routing rule set, test it, and \
                propose it.
                How Nexus routes: a routing group belongs to a store. Its routings run in order; each takes the orders its \
                order conditions allow, sorted by its order sorts. Each routing's rules run in order: a rule looks only at the \
                locations its inventory conditions allow, sorted by its inventory sorts, prefers one location that holds every \
                item, splits only if the rule says split and the store allows it, and when it finds nothing runs its actions: \
                ORA_NEXT_RULE (try the next rule), ORA_MV_TO_QUEUE with a parking location (UNFILLABLE_PARKING, \
                BACKORDER_PARKING, PRE_ORDER_PARKING, REVIEW_PARKING), ORA_AUTO_CANCEL_DAYS with a number of days.
                Order filters: %s. Order sorts: %s.
                Location filters: %s. Location sorts: %s.
                A filter has an operator (in, not-in, =, <=) and a value (comma-separated for in); a sort has asc or desc. \
                facilityId=_NA_ means orders not routed yet.
                Steps: read the current rules (current_routing) and the locations and groups; draft with draft_and_simulate; \
                read the comparison; adjust and draft again if it does worse than the description intends; then call \
                propose_activation once with a one-line reason. Finish with a short summary of the rule set and the \
                simulation numbers. Use only names and ids the tools gave you.""".formatted(
                new TreeSet<>(RoutingService.ORDER_FILTERS), new TreeSet<>(RoutingService.ORDER_SORTS),
                new TreeSet<>(RoutingService.INVENTORY_FILTERS), new TreeSet<>(RoutingService.INVENTORY_SORTS));
    }

    public record Outcome(AgentRunner.Result run, List<ProposalService.View> proposals, List<String> draftGroupIds) {
    }

    public Outcome draft(String productStoreId, String request, String userId) {
        return draft(productStoreId, request, userId, provider);
    }

    public Outcome draft(String productStoreId, String request, String userId, LlmProvider with) {
        List<String> made = Collections.synchronizedList(new ArrayList<>());
        List<String> drafts = Collections.synchronizedList(new ArrayList<>());
        List<AgentRunner.AgentTool> tools = new ArrayList<>();
        assistant.agent().tools().stream().filter(t -> t.spec().name().equals("list_locations")).forEach(tools::add);
        tools.add(tool("current_routing", "The store's routing groups with their routings, conditions, rules and actions; which one runs on a schedule.",
                ExceptionAgent.schema(new String[][]{{"productStoreId", "string"}}), (in, m) -> readOnly.execute(s -> describe(in.path("productStoreId").asText()))));
        tools.add(tool("location_groups", "Location groups (brokering groups, regions, clusters) with their members in order.",
                ExceptionAgent.schema(new String[][]{}), (in, m) -> readOnly.execute(s -> groups())));
        tools.add(tool("draft_and_simulate", "Build a draft routing group (it does not run) from routings with conditions, rules and actions; "
                        + "simulate it and the live group on real orders and stock; return both results.", draftSchema(),
                (in, m) -> draftAndSimulate(productStoreId, in, userId, drafts)));
        tools.add(tool("propose_activation", "Propose making a draft group the one that runs for the store. A person approves it.",
                ExceptionAgent.schema(new String[][]{{"draftGroupId", "string"}, {"reason", "string"}}), (in, m) -> {
                    String draft = in.path("draftGroupId").asText();
                    if (!drafts.contains(draft)) throw new IllegalArgumentException("Propose only a group you drafted in this conversation");
                    ObjectNode payload = AgentRunner.JSON.createObjectNode().put("activateGroupId", draft);
                    String live = liveGroup(productStoreId);
                    if (live != null) payload.put("pauseGroupId", live);
                    ProposalService.View v = proposals.propose(null, "ACTIVATE_ROUTING", payload, in.path("reason").asText(null), null);
                    made.add(v.proposalId());
                    return AgentRunner.JSON.createObjectNode().put("proposalId", v.proposalId()).put("status", v.statusId());
                }));
        AgentRunner.Result r = runner.run(new AgentRunner.Agent(NAME, model, system(), tools, 12, 4096),
                "Store " + productStoreId + ": " + request, userId, with);
        proposals.link(made, r.runId());
        return new Outcome(r, made.stream().map(proposals::view).toList(), List.copyOf(drafts));
    }

    // ── tools ──

    private JsonNode draftAndSimulate(String productStoreId, JsonNode in, String userId, List<String> drafts) {
        String name = in.path("name").asText("Draft");
        String group = routing.createGroup(productStoreId, "Draft: " + name, null);
        int routingSeq = 1;
        for (JsonNode r : in.path("routings")) {
            String routingId = routing.addRouting(group, r.path("name").asText("Routing " + routingSeq), routingSeq++, conds(r.path("orderConditions")));
            int ruleSeq = 1;
            for (JsonNode rule : r.path("rules")) {
                Map<String, String> actions = new LinkedHashMap<>();
                rule.path("actions").forEach(a -> actions.put(a.path("type").asText(), a.path("value").asText("")));
                routing.addRule(routingId, rule.path("name").asText("Rule " + ruleSeq), ruleSeq++, rule.path("split").asBoolean(false),
                        conds(rule.path("locationConditions")), actions);
            }
            routing.activate(routingId);
        }
        drafts.add(group);
        ObjectNode out = AgentRunner.JSON.createObjectNode().put("draftGroupId", group);
        out.set("draft", summary(routing.simulate(group, userId)));
        String live = liveGroup(productStoreId);
        if (live != null) out.set("live", summary(routing.simulate(live, userId)));
        else out.put("live", "no group runs for this store yet");
        return out;
    }

    private static ObjectNode summary(RoutingService.RunResult r) {
        ObjectNode n = AgentRunner.JSON.createObjectNode().put("ordersConsidered", r.considered()).put("routed", r.routed()).put("split", r.split())
                .put("unfillable", r.unfillable()).put("substituted", r.substituted());
        ArrayNode sample = n.putArray("sample");
        r.decisions().stream().limit(10).forEach(d -> sample.addObject().put("order", d.orderId()).put("outcome", d.outcome())
                .put("locations", String.join(",", d.facilityIds())).put("why", d.explanation()));
        return n;
    }

    private String liveGroup(String productStoreId) {
        return readOnly.execute(s -> em.createQuery("SELECT g.routingGroupId FROM OrderRoutingGroup g WHERE g.productStoreId = :p AND g.scheduleMinutes IS NOT NULL "
                + "ORDER BY g.sequenceNum, g.routingGroupId", String.class).setParameter("p", productStoreId).setMaxResults(1).getResultList().stream().findFirst().orElse(null));
    }

    private JsonNode describe(String productStoreId) {
        ArrayNode out = AgentRunner.JSON.createArrayNode();
        for (OrderRoutingGroup g : em.createQuery("SELECT g FROM OrderRoutingGroup g WHERE g.productStoreId = :p ORDER BY g.routingGroupId", OrderRoutingGroup.class)
                .setParameter("p", productStoreId).getResultList()) {
            ObjectNode gn = out.addObject().put("groupId", g.getRoutingGroupId()).put("name", g.getGroupName())
                    .put("runs", g.getScheduleMinutes() == null ? "no (draft or paused)" : "every " + g.getScheduleMinutes() + " minutes");
            ArrayNode rs = gn.putArray("routings");
            for (OrderRouting r : em.createQuery("SELECT r FROM OrderRouting r WHERE r.routingGroupId = :g ORDER BY r.sequenceNum", OrderRouting.class)
                    .setParameter("g", g.getRoutingGroupId()).getResultList()) {
                ObjectNode rn = rs.addObject().put("name", r.getRoutingName()).put("status", r.getStatusId());
                ArrayNode oc = rn.putArray("orderConditions");
                em.createQuery("SELECT c FROM OrderFilterCondition c WHERE c.orderRoutingId = :r ORDER BY c.sequenceNum", OrderFilterCondition.class)
                        .setParameter("r", r.getOrderRoutingId()).getResultList().forEach(c -> oc.add(cond(c.getConditionTypeEnumId(), c.getFieldName(), c.getOperator(), c.getFieldValue())));
                ArrayNode rules = rn.putArray("rules");
                for (OrderRoutingRule rule : em.createQuery("SELECT r FROM OrderRoutingRule r WHERE r.orderRoutingId = :r ORDER BY r.sequenceNum", OrderRoutingRule.class)
                        .setParameter("r", r.getOrderRoutingId()).getResultList()) {
                    ObjectNode un = rules.addObject().put("name", rule.getRuleName()).put("split", "ORA_MULTI".equals(rule.getAssignmentEnumId()));
                    ArrayNode lc = un.putArray("locationConditions");
                    em.createQuery("SELECT c FROM OrderRoutingRuleInvCond c WHERE c.routingRuleId = :r ORDER BY c.sequenceNum", OrderRoutingRuleInvCond.class)
                            .setParameter("r", rule.getRoutingRuleId()).getResultList().forEach(c -> lc.add(cond(c.getConditionTypeEnumId(), c.getFieldName(), c.getOperator(), c.getFieldValue())));
                    ArrayNode an = un.putArray("actions");
                    em.createQuery("SELECT a FROM OrderRoutingRuleAction a WHERE a.routingRuleId = :r ORDER BY a.actionSeqId", OrderRoutingRuleAction.class)
                            .setParameter("r", rule.getRoutingRuleId()).getResultList().forEach(a -> an.addObject().put("type", a.getActionTypeEnumId()).put("value", a.getActionValue()));
                }
            }
        }
        return out;
    }

    private JsonNode groups() {
        ArrayNode out = AgentRunner.JSON.createArrayNode();
        em.createQuery("SELECT g.facilityGroupId, g.facilityGroupName, g.facilityGroupTypeId FROM FacilityGroup g ORDER BY g.facilityGroupName", Object[].class)
                .getResultList().forEach(g -> {
                    ObjectNode n = out.addObject().put("groupId", (String) g[0]).put("name", (String) g[1]).put("type", (String) g[2]);
                    ArrayNode m = n.putArray("members");
                    em.createQuery("SELECT f.facilityName FROM FacilityGroupMember x, Facility f WHERE f.facilityId = x.facilityId AND x.facilityGroupId = :g "
                            + "AND x.thruDate IS NULL ORDER BY x.sequenceNum", String.class).setParameter("g", (String) g[0]).getResultList().forEach(m::add);
                });
        return out;
    }

    private static ObjectNode cond(String type, String field, String operator, String value) {
        return AgentRunner.JSON.createObjectNode().put("sort", "ENTCT_SORT_BY".equals(type)).put("field", field).put("operator", operator).put("value", value);
    }

    private static List<RoutingService.Cond> conds(JsonNode a) {
        List<RoutingService.Cond> out = new ArrayList<>();
        for (JsonNode c : a) out.add(new RoutingService.Cond(c.path("sort").asBoolean(false), c.path("field").asText(), c.path("operator").asText(null), c.path("value").asText(null)));
        return out;
    }

    private static JsonNode draftSchema() {
        try {
            return AgentRunner.JSON.readTree("""
                    {"type": "object", "required": ["name", "routings"], "properties": {
                      "name": {"type": "string"},
                      "routings": {"type": "array", "items": {"type": "object", "required": ["name", "rules"], "properties": {
                        "name": {"type": "string"},
                        "orderConditions": {"type": "array", "items": {"$ref": "#/$defs/cond"}},
                        "rules": {"type": "array", "items": {"type": "object", "required": ["name"], "properties": {
                          "name": {"type": "string"}, "split": {"type": "boolean"},
                          "locationConditions": {"type": "array", "items": {"$ref": "#/$defs/cond"}},
                          "actions": {"type": "array", "items": {"type": "object", "properties": {"type": {"type": "string"}, "value": {"type": "string"}}}}}}}}}}},
                    "$defs": {"cond": {"type": "object", "required": ["field"], "properties": {
                      "sort": {"type": "boolean"}, "field": {"type": "string"}, "operator": {"type": "string"}, "value": {"type": "string"}}}}}""");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static AgentRunner.AgentTool tool(String name, String description, JsonNode schema, AgentRunner.Handler h) {
        return new AgentRunner.AgentTool(new LlmProvider.Tool(name, description, schema), h);
    }
}
