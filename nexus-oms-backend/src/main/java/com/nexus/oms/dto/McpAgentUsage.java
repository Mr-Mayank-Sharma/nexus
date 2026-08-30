package com.nexus.oms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * T-11: Agent budget usage summary.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class McpAgentUsage {

    private String agentName;
    private int queriesUsed;
    private int queryBudget;
    private BigDecimal costUsed;
    private BigDecimal costBudget;
    private boolean budgetExceeded;
}
