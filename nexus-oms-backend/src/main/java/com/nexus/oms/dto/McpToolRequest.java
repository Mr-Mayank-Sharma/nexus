package com.nexus.oms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * T-11: Request to execute an MCP tool on behalf of an AI agent.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class McpToolRequest {

    private String toolName;
    private Map<String, Object> args;
    private String agentName;   // for budget tracking
}
