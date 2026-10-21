package com.nexus.oms.coreapi;

import com.nexus.core.ai.AgentRunner;
import com.nexus.core.ai.OperationsAssistant;
import com.nexus.core.ai.ProposalService;
import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
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
 * AI on the core model (doc 25): the read-only operations assistant, the approval queue for AI proposals with undo,
 * action policies, and the audit of every run. Admin only ({@code /core/}).
 */
@RestController
@RequestMapping("/core/ai")
@Tag(name = "Core AI", description = "Operations assistant, AI proposals and approvals, policies and audit")
public class CoreAiController {

    private final OperationsAssistant assistant;
    private final ProposalService proposals;
    private final com.nexus.core.ai.ExceptionAgent exceptions;
    private final com.nexus.core.ai.RoutingDrafter drafter;
    private final JdbcTemplate jdbc;

    public CoreAiController(OperationsAssistant assistant, ProposalService proposals, com.nexus.core.ai.ExceptionAgent exceptions,
                            com.nexus.core.ai.RoutingDrafter drafter, JdbcTemplate jdbc) {
        this.assistant = assistant;
        this.proposals = proposals;
        this.exceptions = exceptions;
        this.drafter = drafter;
        this.jdbc = jdbc;
    }

    public record AgentOutcome(String runId, String summary, boolean grounded, List<String> toolsUsed, List<ProposalService.View> proposals, List<String> draftGroupIds) {
    }

    @Operation(summary = "Run the exception agent: it finds stuck orders, explains why and proposes fixes (applied only by policy or approval)")
    @PostMapping("/exceptions")
    public ResponseEntity<ApiResponse<AgentOutcome>> exceptions(@RequestBody(required = false) Map<String, String> body) {
        if (!assistant.available()) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error("AI is not set up: ANTHROPIC_API_KEY is missing"));
        var o = exceptions.run(body == null ? null : body.get("instruction"), user());
        return ResponseEntity.ok(ApiResponse.success(new AgentOutcome(o.run().runId(), o.run().answer(), o.run().grounded(),
                o.run().toolCalls().stream().map(AgentRunner.ToolCall::name).toList(), o.proposals(), List.of()), "Done"));
    }

    public record DraftRequest(String productStoreId, String request) {
    }

    @Operation(summary = "Describe a routing change in words: the agent drafts a rule set, simulates it against the live one and proposes it")
    @PostMapping("/routing-drafts")
    public ResponseEntity<ApiResponse<AgentOutcome>> draft(@RequestBody DraftRequest d) {
        if (!assistant.available()) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error("AI is not set up: ANTHROPIC_API_KEY is missing"));
        var o = drafter.draft(d.productStoreId(), d.request(), user());
        return ResponseEntity.ok(ApiResponse.success(new AgentOutcome(o.run().runId(), o.run().answer(), o.run().grounded(),
                o.run().toolCalls().stream().map(AgentRunner.ToolCall::name).toList(), o.proposals(), o.draftGroupIds()), "Drafted"));
    }

    public record Question(String question) {
    }

    public record Answer(String runId, String answer, boolean grounded, List<String> toolsUsed, long inputTokens, long outputTokens, long costMicros) {
    }

    @Operation(summary = "Ask the operations assistant (read-only; answers only from live data)")
    @PostMapping("/assistant")
    public ResponseEntity<ApiResponse<Answer>> ask(@RequestBody Question q) {
        if (!assistant.available()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error("AI is not set up: ANTHROPIC_API_KEY is missing"));
        }
        AgentRunner.Result r = assistant.ask(q.question(), user());
        return ResponseEntity.ok(ApiResponse.success(new Answer(r.runId(), r.answer(), r.grounded(),
                r.toolCalls().stream().map(AgentRunner.ToolCall::name).toList(), r.usage().inputTokens(), r.usage().outputTokens(), r.costMicros()), "Answered"));
    }

    @Operation(summary = "Recent AI runs: question, tools, answer, tokens and cost (masked as sent)")
    @GetMapping("/runs")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> runs(@RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(ApiResponse.success(jdbc.queryForList("SELECT ai_run_id, agent_name, user_id, model_id, question, tool_calls, answer, grounded, "
                + "status_id, error_text, input_tokens, output_tokens, cache_read_tokens, cost_micros, started_at, ended_at FROM ai_agent_run "
                + "ORDER BY started_at DESC LIMIT ?", Math.min(limit, 500)), "Runs"));
    }

    @Operation(summary = "AI proposals waiting for approval")
    @GetMapping("/proposals")
    public ResponseEntity<ApiResponse<List<ProposalService.View>>> pending() {
        return ResponseEntity.ok(ApiResponse.success(proposals.pending(), "Waiting for approval"));
    }

    @Operation(summary = "Approve an AI proposal: it is applied through the same checks as a person's action")
    @PostMapping("/proposals/{id}/approve")
    public ResponseEntity<ApiResponse<ProposalService.View>> approve(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(proposals.approve(id, user()), "Decided"));
    }

    @Operation(summary = "Reject an AI proposal")
    @PostMapping("/proposals/{id}/reject")
    public ResponseEntity<ApiResponse<ProposalService.View>> reject(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.success(proposals.reject(id, user(), body == null ? null : body.get("note")), "Rejected"));
    }

    @Operation(summary = "Undo an applied AI proposal")
    @PostMapping("/proposals/{id}/undo")
    public ResponseEntity<ApiResponse<ProposalService.View>> undo(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(proposals.undo(id, user()), "Undone"));
    }

    public record Policy(String approvalMode, BigDecimal limitAmount, Long dailyLimit) {
    }

    @Operation(summary = "Action policies: how each AI action is approved")
    @GetMapping("/policies")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> policies() {
        return ResponseEntity.ok(ApiResponse.success(jdbc.queryForList("SELECT action_type, description, risk_level, approval_mode, limit_amount, daily_limit "
                + "FROM ai_action_policy ORDER BY action_type"), "Policies"));
    }

    @Operation(summary = "Change how an AI action is approved: ASK, AUTO or AUTO_UNDER_LIMIT")
    @PutMapping("/policies/{actionType}")
    public ResponseEntity<ApiResponse<Void>> policy(@PathVariable String actionType, @RequestBody Policy p) {
        proposals.setPolicy(actionType, p.approvalMode(), p.limitAmount(), p.dailyLimit());
        return ResponseEntity.ok(ApiResponse.success(null, "Policy changed"));
    }

    private static String user() {
        try {
            return TenantContext.getCurrentUsername();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
