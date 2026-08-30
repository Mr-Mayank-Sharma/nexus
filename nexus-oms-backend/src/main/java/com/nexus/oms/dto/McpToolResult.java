package com.nexus.oms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * T-11: Result of executing an MCP tool.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class McpToolResult {

    private boolean success;
    private String toolName;
    private Object data;
    private String error;
    private long latencyMs;
    private boolean budgetExceeded;
}
