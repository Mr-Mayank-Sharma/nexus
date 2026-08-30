package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.dto.McpAgentUsage;
import com.nexus.oms.dto.McpToolRequest;
import com.nexus.oms.dto.McpToolResult;
import com.nexus.oms.entity.NxMcpAgentBudget;
import com.nexus.oms.entity.NxMcpTool;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.McpToolExecutor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/mcp")
public class McpController {

    private final McpToolExecutor mcpToolExecutor;

    public McpController(McpToolExecutor mcpToolExecutor) {
        this.mcpToolExecutor = mcpToolExecutor;
    }

    @PostMapping("/execute")
    public ResponseEntity<ApiResponse<McpToolResult>> execute(@RequestBody McpToolRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                mcpToolExecutor.execute(request.getToolName(), request.getArgs(), request.getAgentName())));
    }

    @GetMapping("/tools")
    public ResponseEntity<ApiResponse<List<NxMcpTool>>> getTools() {
        return ResponseEntity.ok(ApiResponse.success(
                mcpToolExecutor.getTools(TenantContext.getCurrentTenantId())));
    }

    @PostMapping("/tools")
    public ResponseEntity<ApiResponse<NxMcpTool>> registerTool(@RequestBody NxMcpTool tool) {
        return ResponseEntity.ok(ApiResponse.success(
                mcpToolExecutor.registerTool(tool),
                "Tool registered"));
    }

    @GetMapping("/agents/budgets")
    public ResponseEntity<ApiResponse<List<NxMcpAgentBudget>>> getBudgets() {
        return ResponseEntity.ok(ApiResponse.success(
                mcpToolExecutor.getBudgets(TenantContext.getCurrentTenantId())));
    }

    @PostMapping("/agents/budgets")
    public ResponseEntity<ApiResponse<NxMcpAgentBudget>> upsertBudget(@RequestBody NxMcpAgentBudget budget) {
        return ResponseEntity.ok(ApiResponse.success(
                mcpToolExecutor.upsertBudget(budget),
                "Budget saved"));
    }

    @GetMapping("/agents/{name}/usage")
    public ResponseEntity<ApiResponse<McpAgentUsage>> getAgentUsage(@PathVariable String name) {
        return ResponseEntity.ok(ApiResponse.success(
                mcpToolExecutor.getAgentUsage(TenantContext.getCurrentTenantId(), name)));
    }
}
