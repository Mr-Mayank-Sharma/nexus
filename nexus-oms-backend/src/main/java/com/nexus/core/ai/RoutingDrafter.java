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
import com.nexus.core.service.FacilityService;
import com.nexus.core.service.RoutingService;
import com.nexus.core.service.RoutingSetupService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
 * <p>
 * This class defines the agent and its tools. Reading and building routings is {@link RoutingSetupService}; simulating
 * is {@link RoutingService}; proposing is {@link ProposalService}.
 */
@Component("coreRoutingDrafter")
public class RoutingDrafter {

    public static final String NAME = "routing_drafter";

    static final int MAX_STEPS = 12;
    static final int MAX_TOKENS = 4096;
    /** How many decisions of each simulation the model sees. */
    static final int SAMPLE_DECISIONS = 10;

    private final RoutingSetupService setupService;
    private final RoutingService routingService;
    private final FacilityService facilityService;
    private final OperationsAssistant operationsAssistant;
    private final ProposalService proposalService;
    private final AgentRunner agentRunner;
    private final LlmProvider provider;
    private final String model;

    public RoutingDrafter(RoutingSetupService setupService, RoutingService routingService, FacilityService facilityService,
                          OperationsAssistant operationsAssistant, ProposalService proposalService, AgentRunner agentRunner,
                          @Qualifier(LlmProviderSelection.SELECTED) LlmProvider provider, @Value("${nexus.ai.model.routing:claude-opus-5-5}") String model) {
        this.setupService = setupService;
        this.routingService = routingService;
        this.facilityService = facilityService;
        this.operationsAssistant = operationsAssistant;
        this.proposalService = proposalService;
        this.agentRunner = agentRunner;
        this.provider = provider;
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
                new TreeSet<>(RoutingSetupService.ORDER_FILTERS), new TreeSet<>(RoutingSetupService.ORDER_SORTS),
                new TreeSet<>(RoutingSetupService.INVENTORY_FILTERS), new TreeSet<>(RoutingSetupService.INVENTORY_SORTS));
    }

    public record Outcome(AgentRunner.Result run, List<ProposalService.View> proposals, List<String> draftGroupIds) {
    }

    public Outcome draft(String productStoreId, String request, String userId) {
        return draft(productStoreId, request, userId, provider);
    }

    /** Runs the drafting agent for one request. With another provider: a scripted one in tests and evaluation. */
    public Outcome draft(String productStoreId, String request, String userId, LlmProvider withProvider) {
        // Tools run on the agent's threads, so what they record is kept in synchronized lists.
        List<String> proposalIds = Collections.synchronizedList(new ArrayList<>());
        List<String> draftGroupIds = Collections.synchronizedList(new ArrayList<>());

        List<AgentRunner.AgentTool> tools = new ArrayList<>();
        operationsAssistant.agent().tools().stream()
                .filter(tool -> tool.spec().name().equals("list_locations"))
                .forEach(tools::add);
        tools.add(tool("current_routing",
                "The store's routing groups with their routings, conditions, rules and actions; which one runs on a schedule.",
                ToolSchema.requiredStrings("productStoreId").json(),
                (input, masker) -> describeRouting(input.path("productStoreId").asText())));
        tools.add(tool("location_groups",
                "Location groups (brokering groups, regions, clusters) with their members in order.",
                ToolSchema.of().json(),
                (input, masker) -> locationGroups()));
        tools.add(tool("draft_and_simulate",
                "Build a draft routing group (it does not run) from routings with conditions, rules and actions; "
                        + "simulate it and the live group on real orders and stock; return both results.",
                draftSchema(),
                (input, masker) -> draftAndSimulate(productStoreId, input, userId, draftGroupIds)));
        tools.add(tool("propose_activation",
                "Propose making a draft group the one that runs for the store. A person approves it.",
                ToolSchema.requiredStrings("draftGroupId", "reason").json(),
                (input, masker) -> proposeActivation(productStoreId, input, draftGroupIds, proposalIds)));

        AgentRunner.Agent agent = new AgentRunner.Agent(NAME, model, system(), tools, MAX_STEPS, MAX_TOKENS);
        AgentRunner.Result result = agentRunner.run(agent, "Store " + productStoreId + ": " + request, userId, withProvider);
        proposalService.link(proposalIds, result.runId());
        List<ProposalService.View> proposals = proposalIds.stream().map(proposalService::view).toList();
        return new Outcome(result, proposals, List.copyOf(draftGroupIds));
    }

    // ── tools ──

    /** Builds the draft group the model described (active routings, so it can be simulated; no schedule, so it never runs). */
    private JsonNode draftAndSimulate(String productStoreId, JsonNode input, String userId, List<String> draftGroupIds) {
        String draftName = input.path("name").asText("Draft");
        String draftGroupId = setupService.createGroup(productStoreId, "Draft: " + draftName, null);

        int routingSequence = 1;
        for (JsonNode routingInput : input.path("routings")) {
            String routingName = routingInput.path("name").asText("Routing " + routingSequence);
            String routingId = setupService.addRouting(draftGroupId, routingName, routingSequence++, conditions(routingInput.path("orderConditions")));
            int ruleSequence = 1;
            for (JsonNode ruleInput : routingInput.path("rules")) {
                Map<String, String> actions = new LinkedHashMap<>();
                ruleInput.path("actions").forEach(action -> actions.put(action.path("type").asText(), action.path("value").asText("")));
                String ruleName = ruleInput.path("name").asText("Rule " + ruleSequence);
                setupService.addRule(routingId, ruleName, ruleSequence++, ruleInput.path("split").asBoolean(false),
                        conditions(ruleInput.path("locationConditions")), actions);
            }
            setupService.activate(routingId);
        }
        draftGroupIds.add(draftGroupId);

        ObjectNode answer = AgentRunner.JSON.createObjectNode().put("draftGroupId", draftGroupId);
        answer.set("draft", simulationSummary(routingService.simulate(draftGroupId, userId)));
        String liveGroupId = setupService.liveGroupId(productStoreId);
        if (liveGroupId == null) {
            answer.put("live", "no group runs for this store yet");
        } else {
            answer.set("live", simulationSummary(routingService.simulate(liveGroupId, userId)));
        }
        return answer;
    }

    /** Proposes the switch; only a group drafted in this conversation may be proposed. */
    private JsonNode proposeActivation(String productStoreId, JsonNode input, List<String> draftGroupIds, List<String> proposalIds) {
        String draftGroupId = input.path("draftGroupId").asText();
        if (!draftGroupIds.contains(draftGroupId)) {
            throw new IllegalArgumentException("Propose only a group you drafted in this conversation");
        }
        ObjectNode payload = AgentRunner.JSON.createObjectNode().put("activateGroupId", draftGroupId);
        String liveGroupId = setupService.liveGroupId(productStoreId);
        if (liveGroupId != null) {
            payload.put("pauseGroupId", liveGroupId);
        }
        ProposalService.View proposal = proposalService.propose(null, "ACTIVATE_ROUTING", payload, input.path("reason").asText(null), null);
        proposalIds.add(proposal.proposalId());
        return AgentRunner.JSON.createObjectNode().put("proposalId", proposal.proposalId()).put("status", proposal.statusId());
    }

    private static ObjectNode simulationSummary(RoutingService.RunResult run) {
        ObjectNode summary = AgentRunner.JSON.createObjectNode()
                .put("ordersConsidered", run.considered())
                .put("routed", run.routed())
                .put("split", run.split())
                .put("unfillable", run.unfillable())
                .put("substituted", run.substituted());
        ArrayNode sample = summary.putArray("sample");
        run.decisions().stream().limit(SAMPLE_DECISIONS).forEach(decision -> sample.addObject()
                .put("order", decision.orderId())
                .put("outcome", decision.outcome())
                .put("locations", String.join(",", decision.facilityIds()))
                .put("why", decision.explanation()));
        return summary;
    }

    /** The store's routing groups, routings, conditions, rules and actions, as the model reads them. */
    private JsonNode describeRouting(String productStoreId) {
        ArrayNode groups = AgentRunner.JSON.createArrayNode();
        for (OrderRoutingGroup group : setupService.groupsOfStore(productStoreId)) {
            String runs = group.getScheduleMinutes() == null ? "no (draft or paused)" : "every " + group.getScheduleMinutes() + " minutes";
            ObjectNode groupNode = groups.addObject()
                    .put("groupId", group.getRoutingGroupId())
                    .put("name", group.getGroupName())
                    .put("runs", runs);
            ArrayNode routings = groupNode.putArray("routings");
            for (OrderRouting routing : setupService.routingsOfGroup(group.getRoutingGroupId())) {
                ObjectNode routingNode = routings.addObject().put("name", routing.getRoutingName()).put("status", routing.getStatusId());
                ArrayNode orderConditions = routingNode.putArray("orderConditions");
                for (OrderFilterCondition condition : setupService.orderConditions(routing.getOrderRoutingId())) {
                    orderConditions.add(conditionNode(condition.getConditionTypeEnumId(), condition.getFieldName(), condition.getOperator(),
                            condition.getFieldValue()));
                }
                ArrayNode rules = routingNode.putArray("rules");
                for (OrderRoutingRule rule : setupService.rules(routing.getOrderRoutingId(), false)) {
                    ObjectNode ruleNode = rules.addObject().put("name", rule.getRuleName()).put("split", "ORA_MULTI".equals(rule.getAssignmentEnumId()));
                    ArrayNode locationConditions = ruleNode.putArray("locationConditions");
                    for (OrderRoutingRuleInvCond condition : setupService.inventoryConditions(rule.getRoutingRuleId())) {
                        locationConditions.add(conditionNode(condition.getConditionTypeEnumId(), condition.getFieldName(), condition.getOperator(),
                                condition.getFieldValue()));
                    }
                    ArrayNode actions = ruleNode.putArray("actions");
                    for (OrderRoutingRuleAction action : setupService.actions(rule.getRoutingRuleId())) {
                        actions.addObject().put("type", action.getActionTypeEnumId()).put("value", action.getActionValue());
                    }
                }
            }
        }
        return groups;
    }

    private JsonNode locationGroups() {
        ArrayNode groups = AgentRunner.JSON.createArrayNode();
        for (FacilityService.GroupSummary group : facilityService.groupSummaries()) {
            ObjectNode groupNode = groups.addObject()
                    .put("groupId", group.facilityGroupId())
                    .put("name", group.name())
                    .put("type", group.facilityGroupTypeId());
            ArrayNode members = groupNode.putArray("members");
            group.memberNames().forEach(members::add);
        }
        return groups;
    }

    // ── helpers ──

    private static ObjectNode conditionNode(String conditionType, String field, String operator, String value) {
        return AgentRunner.JSON.createObjectNode()
                .put("sort", "ENTCT_SORT_BY".equals(conditionType))
                .put("field", field)
                .put("operator", operator)
                .put("value", value);
    }

    /** The model's conditions as routing conditions. */
    private static List<RoutingSetupService.Cond> conditions(JsonNode conditionsInput) {
        List<RoutingSetupService.Cond> conditions = new ArrayList<>();
        for (JsonNode condition : conditionsInput) {
            conditions.add(new RoutingSetupService.Cond(condition.path("sort").asBoolean(false), condition.path("field").asText(),
                    condition.path("operator").asText(null), condition.path("value").asText(null)));
        }
        return conditions;
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
        } catch (Exception badJson) {
            throw new IllegalStateException(badJson);
        }
    }

    private static AgentRunner.AgentTool tool(String name, String description, JsonNode schema, AgentRunner.Handler handler) {
        return new AgentRunner.AgentTool(new LlmProvider.Tool(name, description, schema), handler);
    }
}
