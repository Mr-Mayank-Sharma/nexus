package com.nexus.oms.service;

import com.nexus.oms.dto.McpAgentUsage;
import com.nexus.oms.dto.McpToolResult;
import com.nexus.oms.entity.NxMcpAgentBudget;
import com.nexus.oms.entity.NxMcpTool;
import com.nexus.oms.entity.ai.AiCostLog;
import com.nexus.oms.entity.ai.AiInferenceLog;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.McpAgentBudgetRepository;
import com.nexus.oms.repository.McpToolRepository;
import com.nexus.oms.repository.ai.AiCostLogRepository;
import com.nexus.oms.repository.ai.AiInferenceLogRepository;
import com.nexus.oms.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * T-11: MCP server tool executor.
 *
 * Exposes OMS tools to AI agents for production debugging, with query
 * permissions and cost limits. Reuses the existing REST/GraphQL layer as the
 * backend (single source of truth for auth) and the AI platform's cost /
 * inference logs for budget tracking.
 */
@Service
public class McpToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(McpToolExecutor.class);

    private final McpToolRepository toolRepository;
    private final McpAgentBudgetRepository budgetRepository;
    private final AiCostLogRepository costLogRepository;
    private final AiInferenceLogRepository inferenceLogRepository;

    public McpToolExecutor(McpToolRepository toolRepository,
                           McpAgentBudgetRepository budgetRepository,
                           AiCostLogRepository costLogRepository,
                           AiInferenceLogRepository inferenceLogRepository) {
        this.toolRepository = toolRepository;
        this.budgetRepository = budgetRepository;
        this.costLogRepository = costLogRepository;
        this.inferenceLogRepository = inferenceLogRepository;
    }

    /**
     * Execute an MCP tool on behalf of an AI agent.
     *
     * 1. Resolve the tool from nx_mcp_tools
     * 2. Enforce RBAC: the agent's permissions map to the same per-tenant,
     *    per-entity model as the REST/GraphQL layer
     * 3. Enforce budget: check nx_mcp_agent_budgets (query + cost)
     * 4. Execute via the existing REST/GraphQL bridge
     * 5. Return the result to the agent
     */
    @Transactional
    public McpToolResult execute(String toolName, Map<String, Object> args, String agentName) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        long start = System.currentTimeMillis();

        // 1. Resolve the tool.
        NxMcpTool tool = toolRepository.findByTenantIdAndToolName(tenantId, toolName)
                .orElseThrow(() -> new ResourceNotFoundException("MCP tool not found: " + toolName));
        if (!Boolean.TRUE.equals(tool.getIsActive())) {
            throw new BadRequestException("MCP tool is disabled: " + toolName);
        }

        // 2. Enforce RBAC: WRITE tools require a write-capable agent context.
        //    (The calling user's permissions are enforced by the underlying
        //    REST/GraphQL layer — this is a first-line guard.)
        if ("WRITE".equals(tool.getAccess()) && isReadOnlyAgent(agentName)) {
            return McpToolResult.builder()
                    .success(false)
                    .toolName(toolName)
                    .error("Write tool blocked for read-only agent: " + agentName)
                    .latencyMs(System.currentTimeMillis() - start)
                    .build();
        }

        // 3. Enforce budget.
        McpAgentUsage usage = getAgentUsage(tenantId, agentName);
        if (usage.isBudgetExceeded()) {
            return McpToolResult.builder()
                    .success(false)
                    .toolName(toolName)
                    .error("Agent budget exceeded for: " + agentName)
                    .latencyMs(System.currentTimeMillis() - start)
                    .budgetExceeded(true)
                    .build();
        }

        // 4. Execute via the existing REST/GraphQL bridge.
        Object result;
        try {
            result = executeViaBridge(tool, args);
        } catch (Exception e) {
            log.warn("MCP tool {} failed: {}", toolName, e.getMessage());
            return McpToolResult.builder()
                    .success(false)
                    .toolName(toolName)
                    .error(e.getMessage())
                    .latencyMs(System.currentTimeMillis() - start)
                    .build();
        }

        // 5. Log cost + inference for budget tracking.
        recordUsage(tenantId, agentName, toolName, result);

        long latency = System.currentTimeMillis() - start;
        log.info("MCP tool {} executed for agent {} in {}ms", toolName, agentName, latency);

        return McpToolResult.builder()
                .success(true)
                .toolName(toolName)
                .data(result)
                .latencyMs(latency)
                .build();
    }

    // ------------------------------------------------------------------
    // Budget tracking
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public McpAgentUsage getAgentUsage(UUID tenantId, String agentName) {
        NxMcpAgentBudget budget = budgetRepository.findByTenantIdAndAgentName(tenantId, agentName)
                .orElseGet(() -> NxMcpAgentBudget.builder()
                        .tenantId(tenantId)
                        .agentName(agentName)
                        .queryBudget(100)
                        .costBudget(new BigDecimal("100.00"))
                        .period("DAILY")
                        .isActive(true)
                        .build());

        LocalDateTime since = periodStart(budget.getPeriod());
        long queriesUsed = inferenceLogRepository.countByTenantIdAndCreatedAtAfter(tenantId, since);
        BigDecimal costUsed = costLogRepository.sumByTenantAndTypeSince(tenantId, "MCP", since);

        boolean exceeded = queriesUsed >= budget.getQueryBudget()
                || (costUsed != null && costUsed.compareTo(budget.getCostBudget()) >= 0);

        return McpAgentUsage.builder()
                .agentName(agentName)
                .queriesUsed((int) queriesUsed)
                .queryBudget(budget.getQueryBudget())
                .costUsed(costUsed == null ? BigDecimal.ZERO : costUsed)
                .costBudget(budget.getCostBudget())
                .budgetExceeded(exceeded)
                .build();
    }

    private void recordUsage(UUID tenantId, String agentName, String toolName, Object result) {
        // Cost log (MCP type) for budget tracking.
        costLogRepository.save(AiCostLog.builder()
                .tenantId(tenantId)
                .modelId(UUID.nameUUIDFromBytes(("mcp-" + toolName).getBytes()))
                .costType("MCP")
                .amount(new BigDecimal("0.01"))   // placeholder per-query cost
                .currency("USD")
                .description("MCP tool " + toolName + " by agent " + agentName)
                .build());

        // Inference log for query counting.
        inferenceLogRepository.save(AiInferenceLog.builder()
                .tenantId(tenantId)
                .modelId(UUID.nameUUIDFromBytes(("mcp-" + toolName).getBytes()))
                .sourceService("MCP")
                .status("SUCCESS")
                .cost(new BigDecimal("0.01"))
                .build());
    }

    private LocalDateTime periodStart(String period) {
        LocalDateTime now = LocalDateTime.now();
        return switch (period == null ? "DAILY" : period) {
            case "WEEKLY" -> now.minusWeeks(1);
            case "MONTHLY" -> now.minusMonths(1);
            default -> now.minusDays(1);
        };
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private boolean isReadOnlyAgent(String agentName) {
        // Agents named with a "read"/"view" prefix are treated as read-only.
        if (agentName == null) return false;
        String lower = agentName.toLowerCase();
        return lower.startsWith("read") || lower.startsWith("view") || lower.contains("readonly");
    }

    /**
     * Execute the tool via the existing REST/GraphQL bridge.
     *
     * In a full implementation this dispatches to the same service methods the
     * REST controllers call, so auth and tenant isolation are identical. Here
     * we return a structured descriptor of the resolved endpoint.
     */
    private Object executeViaBridge(NxMcpTool tool, Map<String, Object> args) {
        // Placeholder bridge: return the resolved endpoint + args.
        // In production, call the underlying service (e.g., OrderService,
        // InventoryService) with the same tenant context.
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("tool", tool.getToolName());
        response.put("access", tool.getAccess());
        response.put("endpoint", tool.getEndpoint());
        response.put("args", args == null ? Map.of() : args);
        return response;
    }

    // ------------------------------------------------------------------
    // Admin helpers
    // ------------------------------------------------------------------

    @Transactional
    public NxMcpTool registerTool(NxMcpTool tool) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        tool.setTenantId(tenantId);
        return toolRepository.save(tool);
    }

    @Transactional(readOnly = true)
    public List<NxMcpTool> getTools(UUID tenantId) {
        return toolRepository.findByTenantId(tenantId);
    }

    @Transactional
    public NxMcpAgentBudget upsertBudget(NxMcpAgentBudget budget) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        budget.setTenantId(tenantId);
        return budgetRepository.save(budget);
    }

    @Transactional(readOnly = true)
    public List<NxMcpAgentBudget> getBudgets(UUID tenantId) {
        return budgetRepository.findByTenantId(tenantId);
    }
}
