package com.nexus.core.ai;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.core.CoreStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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
    static final String DEFAULT_INSTRUCTION = "Find stuck orders and propose fixes.";
    static final int MAX_STEPS = 12;
    static final int MAX_TOKENS = 4096;

    private final OperationsAssistant operationsAssistant;
    private final ProposalService proposalService;
    private final AgentRunner agentRunner;
    private final LlmProvider provider;
    private final String model;

    public ExceptionAgent(OperationsAssistant operationsAssistant, ProposalService proposalService, AgentRunner agentRunner,
                          @Qualifier(LlmProviderSelection.SELECTED) LlmProvider provider, @Value("${nexus.ai.model.exceptions:claude-opus-5-5}") String model) {
        this.operationsAssistant = operationsAssistant;
        this.proposalService = proposalService;
        this.agentRunner = agentRunner;
        this.provider = provider;
        this.model = model;
    }

    public record Outcome(AgentRunner.Result run, List<ProposalService.View> proposals) {
    }

    public Outcome run(String instruction, String userId) {
        return run(instruction, userId, provider);
    }

    /** Runs the agent once. With another provider: a scripted one in tests and evaluation. */
    public Outcome run(String instruction, String userId, LlmProvider withProvider) {
        // Tools run on the agent's threads, so the proposals it makes are kept in a synchronized list.
        List<String> proposalIds = Collections.synchronizedList(new ArrayList<>());

        List<AgentRunner.AgentTool> tools = new ArrayList<>(operationsAssistant.agent().tools());
        tools.add(proposeTool("propose_reallocate", "Propose moving a ship group to another location.", ProposalActions.REALLOCATE,
                ToolSchema.requiredStrings("orderId", "shipGroupSeqId", "facilityId", "reason"), proposalIds));
        tools.add(proposeTool("propose_hold", "Propose putting an order on hold for a person to look at.", ProposalActions.HOLD_ORDER,
                ToolSchema.requiredStrings("orderId", "reason"), proposalIds));
        tools.add(proposeTool("propose_cancel_item", "Propose cancelling what is left of an order item.", ProposalActions.CANCEL_ITEM,
                ToolSchema.requiredStrings("orderId", "orderItemSeqId", "reason"), proposalIds));

        String question = instruction == null || instruction.isBlank() ? DEFAULT_INSTRUCTION : instruction;
        AgentRunner.Agent agent = new AgentRunner.Agent(NAME, model, SYSTEM, tools, MAX_STEPS, MAX_TOKENS);
        AgentRunner.Result result = agentRunner.run(agent, question, userId, withProvider);
        proposalService.link(proposalIds, result.runId());
        return new Outcome(result, proposalIds.stream().map(proposalService::view).toList());
    }

    /** A tool that turns the model's call into a proposal of the action; the "reason" field becomes the proposal's reason. */
    private AgentRunner.AgentTool proposeTool(String name, String description, String actionType, ToolSchema schema, List<String> proposalIds) {
        AgentRunner.Handler handler = (input, masker) -> {
            ObjectNode payload = input.deepCopy();
            String reason = payload.path("reason").asText(null);
            payload.remove("reason");
            BigDecimal orderValue = proposalService.orderValue(payload.path("orderId").asText());
            ProposalService.View proposal = proposalService.propose(null, actionType, payload, reason, orderValue);
            proposalIds.add(proposal.proposalId());
            String note = CoreStatus.Proposal.PENDING.equals(proposal.statusId()) ? "Waiting for a person to approve" : proposal.result();
            return AgentRunner.JSON.createObjectNode()
                    .put("proposalId", proposal.proposalId())
                    .put("status", proposal.statusId())
                    .put("note", note);
        };
        return new AgentRunner.AgentTool(new LlmProvider.Tool(name, description, schema.json()), handler);
    }
}
