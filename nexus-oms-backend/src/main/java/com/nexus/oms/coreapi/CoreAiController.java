package com.nexus.oms.coreapi;

import com.nexus.core.ai.AgentRunner;
import com.nexus.core.ai.ExceptionAgent;
import com.nexus.core.ai.OperationsAssistant;
import com.nexus.core.ai.ProposalService;
import com.nexus.core.ai.RoutingDrafter;
import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * AI on the core model (doc 25): the read-only operations assistant, the exception agent, plain-language routing, the
 * approval queue for AI proposals with undo, action policies, and the audit of every run. Admin only ({@code /core/}).
 */
@RestController
@RequestMapping("/core/ai")
@Tag(name = "Core AI", description = "Operations assistant, AI proposals and approvals, policies and audit")
public class CoreAiController {

    static final String NOT_SET_UP = "AI is not set up: ANTHROPIC_API_KEY is missing";
    static final int MAX_RUNS = 500;

    private final OperationsAssistant operationsAssistant;
    private final ProposalService proposalService;
    private final ExceptionAgent exceptionAgent;
    private final RoutingDrafter routingDrafter;
    private final AgentRunner agentRunner;

    public CoreAiController(OperationsAssistant operationsAssistant, ProposalService proposalService, ExceptionAgent exceptionAgent,
                            RoutingDrafter routingDrafter, AgentRunner agentRunner) {
        this.operationsAssistant = operationsAssistant;
        this.proposalService = proposalService;
        this.exceptionAgent = exceptionAgent;
        this.routingDrafter = routingDrafter;
        this.agentRunner = agentRunner;
    }

    public record AgentOutcome(String runId, String summary, boolean grounded, List<String> toolsUsed, List<ProposalService.View> proposals,
                               List<String> draftGroupIds) {
    }

    public record DraftRequest(String productStoreId, String request) {
    }

    public record Question(String question) {
    }

    public record Answer(String runId, String answer, boolean grounded, List<String> toolsUsed, long inputTokens, long outputTokens, long costMicros) {
    }

    public record Policy(String approvalMode, BigDecimal limitAmount, Long dailyLimit) {
    }

    // ── agents ──

    @Operation(summary = "Run the exception agent: it finds stuck orders, explains why and proposes fixes (applied only by policy or approval)")
    @PostMapping("/exceptions")
    public ResponseEntity<ApiResponse<AgentOutcome>> exceptions(@RequestBody(required = false) Map<String, String> body) {
        if (!operationsAssistant.available()) {
            return notSetUp();
        }
        String instruction = body == null ? null : body.get("instruction");
        ExceptionAgent.Outcome outcome = exceptionAgent.run(instruction, currentUser());
        AgentRunner.Result run = outcome.run();
        return ResponseEntity.ok(ApiResponse.success(new AgentOutcome(run.runId(), run.answer(), run.grounded(), toolsUsed(run), outcome.proposals(),
                List.of()), "Done"));
    }

    @Operation(summary = "Describe a routing change in words: the agent drafts a rule set, simulates it against the live one and proposes it")
    @PostMapping("/routing-drafts")
    public ResponseEntity<ApiResponse<AgentOutcome>> draft(@RequestBody DraftRequest draftRequest) {
        if (!operationsAssistant.available()) {
            return notSetUp();
        }
        RoutingDrafter.Outcome outcome = routingDrafter.draft(draftRequest.productStoreId(), draftRequest.request(), currentUser());
        AgentRunner.Result run = outcome.run();
        return ResponseEntity.ok(ApiResponse.success(new AgentOutcome(run.runId(), run.answer(), run.grounded(), toolsUsed(run), outcome.proposals(),
                outcome.draftGroupIds()), "Drafted"));
    }

    @Operation(summary = "Ask the operations assistant (read-only; answers only from live data)")
    @PostMapping("/assistant")
    public ResponseEntity<ApiResponse<Answer>> ask(@RequestBody Question question) {
        if (!operationsAssistant.available()) {
            return notSetUp();
        }
        AgentRunner.Result run = operationsAssistant.ask(question.question(), currentUser());
        Answer answer = new Answer(run.runId(), run.answer(), run.grounded(), toolsUsed(run), run.usage().inputTokens(), run.usage().outputTokens(),
                run.costMicros());
        return ResponseEntity.ok(ApiResponse.success(answer, "Answered"));
    }

    @Operation(summary = "Recent AI runs: question, tools, answer, tokens and cost (masked as sent)")
    @GetMapping("/runs")
    public ResponseEntity<ApiResponse<List<AgentRunner.RunSummary>>> runs(@RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(ApiResponse.success(agentRunner.recentRuns(Math.min(limit, MAX_RUNS)), "Runs"));
    }

    // ── proposals ──

    @Operation(summary = "AI proposals waiting for approval")
    @GetMapping("/proposals")
    public ResponseEntity<ApiResponse<List<ProposalService.View>>> pending() {
        return ResponseEntity.ok(ApiResponse.success(proposalService.pending(), "Waiting for approval"));
    }

    @Operation(summary = "Approve an AI proposal: it is applied through the same checks as a person's action")
    @PostMapping("/proposals/{id}/approve")
    public ResponseEntity<ApiResponse<ProposalService.View>> approve(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(proposalService.approve(id, currentUser()), "Decided"));
    }

    @Operation(summary = "Reject an AI proposal")
    @PostMapping("/proposals/{id}/reject")
    public ResponseEntity<ApiResponse<ProposalService.View>> reject(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        String note = body == null ? null : body.get("note");
        return ResponseEntity.ok(ApiResponse.success(proposalService.reject(id, currentUser(), note), "Rejected"));
    }

    @Operation(summary = "Undo an applied AI proposal")
    @PostMapping("/proposals/{id}/undo")
    public ResponseEntity<ApiResponse<ProposalService.View>> undo(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(proposalService.undo(id, currentUser()), "Undone"));
    }

    // ── policies ──

    @Operation(summary = "Action policies: how each AI action is approved")
    @GetMapping("/policies")
    public ResponseEntity<ApiResponse<List<ProposalService.PolicyView>>> policies() {
        return ResponseEntity.ok(ApiResponse.success(proposalService.policies(), "Policies"));
    }

    @Operation(summary = "Change how an AI action is approved: ASK, AUTO or AUTO_UNDER_LIMIT")
    @PutMapping("/policies/{actionType}")
    public ResponseEntity<ApiResponse<Void>> policy(@PathVariable String actionType, @RequestBody Policy policy) {
        proposalService.setPolicy(actionType, policy.approvalMode(), policy.limitAmount(), policy.dailyLimit());
        return ResponseEntity.ok(ApiResponse.success(null, "Policy changed"));
    }

    // ── helpers ──

    private static <T> ResponseEntity<ApiResponse<T>> notSetUp() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error(NOT_SET_UP));
    }

    private static List<String> toolsUsed(AgentRunner.Result run) {
        return run.toolCalls().stream().map(AgentRunner.ToolCall::name).toList();
    }

    /** The signed-in user, or null outside a request. */
    private static String currentUser() {
        try {
            return TenantContext.getCurrentUsername();
        } catch (RuntimeException noUser) {
            return null;
        }
    }
}
