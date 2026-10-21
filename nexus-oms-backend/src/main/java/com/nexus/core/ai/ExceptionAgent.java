package com.nexus.core.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The exception agent (doc 25, feature 13): finds orders that are stuck, works out why from live data, and proposes the
 * fix. It reads with the operations assistant's tools and can only propose: move a ship group, hold an order, cancel
 * what is left of an item. Each proposal goes through its action's policy (W12): most wait for a person.
 */
@Component("coreExceptionAgent")
public class ExceptionAgent {

    public static final String NAME = "exception_agent";
    static final String SYSTEM = """
            You are the exception agent of Nexus, an order management system. You look for orders that are stuck, find out \
            why from live data, and propose the smallest fix.
            Rules:
            - Use your tools to look; never guess. Check stock (stock_lookup) and the location's record (store_reliability, \
            phantom_stock) before proposing a move.
            - You cannot change anything yourself. To fix something, call a propose_ tool with a short reason a manager will \
            read. A person or the policy decides.
            - Propose a move only to a location that can sell every item; prefer the more reliable one. Propose a hold when \
            something needs a person (an address, a payment). Propose a cancel only when the customer asked or nothing \
            anywhere can fill it.
            - End with a short summary: what you found, what you proposed, and anything you left for a person.""";

    private final OperationsAssistant assistant;
    private final ProposalService proposals;
    private final AgentRunner runner;
    private final LlmProvider provider;
    private final String model;

    public ExceptionAgent(OperationsAssistant assistant, ProposalService proposals, AgentRunner runner,
                          @Qualifier("coreAnthropicProvider") LlmProvider provider, @Value("${nexus.ai.model.exceptions:claude-opus-5-5}") String model) {
        this.assistant = assistant;
        this.proposals = proposals;
        this.runner = runner;
        this.provider = provider;
        this.model = model;
    }

    public record Outcome(AgentRunner.Result run, List<ProposalService.View> proposals) {
    }

    public Outcome run(String instruction, String userId) {
        return run(instruction, userId, provider);
    }

    public Outcome run(String instruction, String userId, LlmProvider with) {
        List<String> made = Collections.synchronizedList(new ArrayList<>());
        List<AgentRunner.AgentTool> tools = new ArrayList<>(assistant.agent().tools());
        tools.add(propose("propose_reallocate", "Propose moving a ship group to another location.", "REALLOCATE",
                schema(new String[][]{{"orderId", "string"}, {"shipGroupSeqId", "string"}, {"facilityId", "string"}, {"reason", "string"}}), made));
        tools.add(propose("propose_hold", "Propose putting an order on hold for a person to look at.", "HOLD_ORDER",
                schema(new String[][]{{"orderId", "string"}, {"reason", "string"}}), made));
        tools.add(propose("propose_cancel_item", "Propose cancelling what is left of an order item.", "CANCEL_ITEM",
                schema(new String[][]{{"orderId", "string"}, {"orderItemSeqId", "string"}, {"reason", "string"}}), made));
        AgentRunner.Result r = runner.run(new AgentRunner.Agent(NAME, model, SYSTEM, tools, 12, 4096),
                instruction == null || instruction.isBlank() ? "Find stuck orders and propose fixes." : instruction, userId, with);
        proposals.link(made, r.runId());
        return new Outcome(r, made.stream().map(proposals::view).toList());
    }

    private AgentRunner.AgentTool propose(String name, String description, String actionType, JsonNode schema, List<String> made) {
        return new AgentRunner.AgentTool(new LlmProvider.Tool(name, description, schema), (in, masker) -> {
            ObjectNode payload = ((ObjectNode) in.deepCopy());
            String reason = payload.path("reason").asText(null);
            payload.remove("reason");
            ProposalService.View v = proposals.propose(null, actionType, payload, reason, proposals.orderValue(payload.path("orderId").asText()));
            made.add(v.proposalId());
            return AgentRunner.JSON.createObjectNode().put("proposalId", v.proposalId()).put("status", v.statusId())
                    .put("note", "AIP_PENDING".equals(v.statusId()) ? "Waiting for a person to approve" : v.result());
        });
    }

    static JsonNode schema(String[][] fields) {
        ObjectNode s = AgentRunner.JSON.createObjectNode().put("type", "object");
        ObjectNode props = s.putObject("properties");
        var req = s.putArray("required");
        for (String[] f : fields) {
            props.putObject(f[0]).put("type", f[1]);
            req.add(f[0]);
        }
        return s;
    }
}
