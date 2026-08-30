import client from './client'
import type { ApiResponse } from '../types'

// ── T-11: MCP Server / AI-Agent Access ───────────────────────────────────────

export interface McpTool {
  id: string
  tenantId: string
  toolName: string
  description?: string
  access: 'READ' | 'WRITE'
  endpoint?: string
  isActive: boolean
  createdAt?: string
}

export interface McpToolResult {
  success: boolean
  toolName: string
  data?: any
  error?: string
  latencyMs: number
  budgetExceeded?: boolean
}

export interface McpAgentBudget {
  id: string
  tenantId: string
  agentName: string
  queryBudget: number
  costBudget: number
  period: 'DAILY' | 'WEEKLY' | 'MONTHLY'
  isActive: boolean
  createdAt?: string
}

export interface McpAgentUsage {
  agentName: string
  queriesUsed: number
  queryBudget: number
  costUsed: number
  costBudget: number
  budgetExceeded: boolean
}

export async function executeMcpTool(params: {
  toolName: string
  args?: Record<string, any>
  agentName?: string
}): Promise<ApiResponse<McpToolResult>> {
  try {
    const { data } = await client.post('/mcp/execute', params)
    return data
  } catch (err: any) {
    return { success: false, error: err?.response?.data?.message || err?.message || 'Failed to execute MCP tool' } as any
  }
}

export async function getMcpTools(): Promise<ApiResponse<McpTool[]>> {
  try {
    const { data } = await client.get('/mcp/tools')
    return data
  } catch (err: any) {
    return { success: false, error: err?.response?.data?.message || err?.message || 'Failed to get MCP tools' } as any
  }
}

export async function registerMcpTool(tool: Partial<McpTool>): Promise<ApiResponse<McpTool>> {
  try {
    const { data } = await client.post('/mcp/tools', tool)
    return data
  } catch (err: any) {
    return { success: false, error: err?.response?.data?.message || err?.message || 'Failed to register tool' } as any
  }
}

export async function getMcpBudgets(): Promise<ApiResponse<McpAgentBudget[]>> {
  try {
    const { data } = await client.get('/mcp/agents/budgets')
    return data
  } catch (err: any) {
    return { success: false, error: err?.response?.data?.message || err?.message || 'Failed to get budgets' } as any
  }
}

export async function upsertMcpBudget(budget: Partial<McpAgentBudget>): Promise<ApiResponse<McpAgentBudget>> {
  try {
    const { data } = await client.post('/mcp/agents/budgets', budget)
    return data
  } catch (err: any) {
    return { success: false, error: err?.response?.data?.message || err?.message || 'Failed to save budget' } as any
  }
}

export async function getMcpAgentUsage(agentName: string): Promise<ApiResponse<McpAgentUsage>> {
  try {
    const { data } = await client.get(`/mcp/agents/${encodeURIComponent(agentName)}/usage`)
    return data
  } catch (err: any) {
    return { success: false, error: err?.response?.data?.message || err?.message || 'Failed to get agent usage' } as any
  }
}
