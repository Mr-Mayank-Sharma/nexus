package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * T-11: MCP agent query/cost budget.
 *
 * Per-agent query budget (cost/volume) so an AI agent can't run away with
 * expensive queries against the production OMS.
 */
@Entity
@Table(name = "nx_mcp_agent_budgets")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxMcpAgentBudget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "agent_name", nullable = false, length = 128)
    private String agentName;

    @Column(name = "query_budget", nullable = false)
    private Integer queryBudget;

    @Column(name = "cost_budget", nullable = false, precision = 12, scale = 2)
    private BigDecimal costBudget;

    @Column(nullable = false, length = 20)
    private String period;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (queryBudget == null) queryBudget = 100;
        if (costBudget == null) costBudget = new BigDecimal("100.00");
        if (period == null) period = "DAILY";
        if (isActive == null) isActive = true;
    }
}
