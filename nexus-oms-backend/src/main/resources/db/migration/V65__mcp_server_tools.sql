-- ============================================================
-- T-11: MCP Server Tool Registry + Agent Budgets
-- Defines which OMS operations are exposed as MCP tools, their
-- read/write classification, and per-agent query budgets.
-- ============================================================

-- 1. MCP TOOL REGISTRY.
CREATE TABLE IF NOT EXISTS nx_mcp_tools (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    tool_name       VARCHAR(128) NOT NULL,   -- e.g. "get_order_status", "get_inventory"
    description     TEXT,
    access          VARCHAR(10) NOT NULL,    -- READ | WRITE
    -- JSON: the REST/GraphQL endpoint this tool maps to (single source of truth for auth)
    endpoint        JSONB,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, tool_name)
);
CREATE INDEX IF NOT EXISTS idx_mcp_tools_tenant ON nx_mcp_tools(tenant_id, is_active);

-- 2. MCP AGENT BUDGETS.
CREATE TABLE IF NOT EXISTS nx_mcp_agent_budgets (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    agent_name      VARCHAR(128) NOT NULL,
    -- Per-agent query budget (cost/volume) so an agent can't run away
    -- with expensive queries.
    query_budget    INTEGER NOT NULL DEFAULT 100,   -- max queries per period
    cost_budget     DECIMAL(12,2) NOT NULL DEFAULT 100.00,
    period          VARCHAR(20) NOT NULL DEFAULT 'DAILY',  -- DAILY | WEEKLY | MONTHLY
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_mcp_budgets_tenant ON nx_mcp_agent_budgets(tenant_id, agent_name);
