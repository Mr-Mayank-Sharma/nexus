import { useState } from 'react'
import { clsx } from 'clsx'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Bot, Terminal, Loader2, Plus, X, ShieldCheck, Coins, Activity,
  Check, AlertTriangle, Cpu, Zap,
} from 'lucide-react'
import EnterpriseBreadcrumbs from '../components/enterprise/EnterpriseBreadcrumbs'
import EnterpriseKPICard from '../components/enterprise/EnterpriseKPICard'
import EnterpriseStatusBadge from '../components/enterprise/EnterpriseStatusBadge'
import EnterpriseTabs from '../components/enterprise/EnterpriseTabs'
import { useToast } from '../hooks/useToast'
import {
  executeMcpTool, getMcpTools, registerMcpTool, getMcpBudgets,
  upsertMcpBudget, getMcpAgentUsage,
  type McpTool, type McpAgentBudget, type McpToolResult,
} from '../api/mcp'

const TABS = [
  { id: 'EXECUTE', label: 'Execute', icon: <Terminal className="w-4 h-4" /> },
  { id: 'TOOLS', label: 'Tool Registry', icon: <Cpu className="w-4 h-4" /> },
  { id: 'BUDGETS', label: 'Agent Budgets', icon: <Coins className="w-4 h-4" /> },
]

const ACCESS_OPTIONS = ['READ', 'WRITE']

export default function McpServerPage() {
  const { toast } = useToast()
  const qc = useQueryClient()
  const [activeTab, setActiveTab] = useState('EXECUTE')

  // Execute form
  const [execForm, setExecForm] = useState({ toolName: '', agentName: '', args: '{}' })
  const [execResult, setExecResult] = useState<McpToolResult | null>(null)

  // Register tool form
  const [toolForm, setToolForm] = useState({
    toolName: '', description: '', access: 'READ' as 'READ' | 'WRITE', endpoint: '',
  })

  // Budget form
  const [budgetForm, setBudgetForm] = useState({
    agentName: '', queryBudget: 100, costBudget: 10, period: 'MONTHLY' as 'DAILY' | 'WEEKLY' | 'MONTHLY',
  })

  // ── Queries ──
  const toolsQuery = useQuery({
    queryKey: ['mcp-tools'],
    queryFn: () => getMcpTools(),
    enabled: activeTab === 'TOOLS',
  })
  const tools: McpTool[] = toolsQuery.data?.success ? (toolsQuery.data.data ?? []) : []

  const budgetsQuery = useQuery({
    queryKey: ['mcp-budgets'],
    queryFn: () => getMcpBudgets(),
    enabled: activeTab === 'BUDGETS',
  })
  const budgets: McpAgentBudget[] = budgetsQuery.data?.success ? (budgetsQuery.data.data ?? []) : []

  // ── Mutations ──
  const execute = useMutation({
    mutationFn: () => {
      let args: Record<string, any> = {}
      try {
        args = execForm.args.trim() ? JSON.parse(execForm.args) : {}
      } catch {
        throw new Error('Invalid JSON in args')
      }
      return executeMcpTool({
        toolName: execForm.toolName,
        agentName: execForm.agentName || undefined,
        args,
      })
    },
    onSuccess: (res) => {
      if (res.success && res.data) {
        setExecResult(res.data)
        if (res.data.success) toast('Tool executed', 'success')
        else toast(res.data.error || 'Tool execution failed', 'error')
      } else {
        toast(res.error || 'Failed to execute', 'error')
      }
    },
    onError: (err: any) => toast(err?.message || 'Invalid request', 'error'),
  })

  const register = useMutation({
    mutationFn: () => registerMcpTool(toolForm),
    onSuccess: (res) => {
      if (res.success) {
        toast('Tool registered', 'success')
        setToolForm({ toolName: '', description: '', access: 'READ', endpoint: '' })
        qc.invalidateQueries({ queryKey: ['mcp-tools'] })
      } else {
        toast(res.error || 'Failed to register', 'error')
      }
    },
  })

  const saveBudget = useMutation({
    mutationFn: () => upsertMcpBudget(budgetForm),
    onSuccess: (res) => {
      if (res.success) {
        toast('Budget saved', 'success')
        setBudgetForm({ agentName: '', queryBudget: 100, costBudget: 10, period: 'MONTHLY' })
        qc.invalidateQueries({ queryKey: ['mcp-budgets'] })
      } else {
        toast(res.error || 'Failed to save budget', 'error')
      }
    },
  })

  const activeTools = tools.filter((t) => t.isActive).length
  const writeTools = tools.filter((t) => t.access === 'WRITE').length
  const activeBudgets = budgets.filter((b) => b.isActive).length

  return (
    <div className="p-6 max-w-[1400px] mx-auto">
      <EnterpriseBreadcrumbs crumbs={[{ label: 'AI' }, { label: 'MCP Server' }]} />

      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-semibold text-[var(--text-primary)] flex items-center gap-2">
            <Bot className="w-6 h-6 text-[var(--text-brand)]" />
            MCP Server & AI Agents
          </h1>
          <p className="text-sm text-[var(--text-secondary)] mt-1">
            Expose OMS capabilities to AI agents via a governed Model Context Protocol server with per-agent budgets.
          </p>
        </div>
      </div>

      {/* KPI row */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <EnterpriseKPICard
          title="Registered Tools"
          value={tools.length}
          subtitle="Total in registry"
          icon={Cpu}
          color="primary"
        />
        <EnterpriseKPICard
          title="Active Tools"
          value={activeTools}
          subtitle="Enabled"
          icon={Zap}
          color="success"
        />
        <EnterpriseKPICard
          title="Write Access"
          value={writeTools}
          subtitle="Can mutate data"
          icon={ShieldCheck}
          color="warning"
        />
        <EnterpriseKPICard
          title="Agent Budgets"
          value={activeBudgets}
          subtitle="Governed agents"
          icon={Coins}
          color="info"
        />
      </div>

      <EnterpriseTabs tabs={TABS} activeTab={activeTab} onChange={setActiveTab} />

      <div className="mt-6">
        {activeTab === 'EXECUTE' && (
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <div className="rounded-xl border border-[var(--border-default)] bg-[var(--surface-base)] p-5">
              <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-4 flex items-center gap-2">
                <Terminal className="w-4 h-4 text-[var(--text-brand)]" /> Execute Tool
              </h3>
              <div className="space-y-3">
                <div>
                  <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1.5">Tool Name</label>
                  <input
                    value={execForm.toolName}
                    onChange={(e) => setExecForm((f) => ({ ...f, toolName: e.target.value }))}
                    className="w-full rounded-lg border border-[var(--border-default)] px-3 py-2 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-[var(--border-focus)]"
                    placeholder="e.g. get_order"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1.5">Agent Name (optional)</label>
                  <input
                    value={execForm.agentName}
                    onChange={(e) => setExecForm((f) => ({ ...f, agentName: e.target.value }))}
                    className="w-full rounded-lg border border-[var(--border-default)] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[var(--border-focus)]"
                    placeholder="e.g. support-agent"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1.5">Arguments (JSON)</label>
                  <textarea
                    value={execForm.args}
                    onChange={(e) => setExecForm((f) => ({ ...f, args: e.target.value }))}
                    className="w-full rounded-lg border border-[var(--border-default)] px-3 py-2 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-[var(--border-focus)]"
                    rows={5}
                    placeholder='{ "orderId": "ORD-001" }'
                  />
                </div>
                <button
                  onClick={() => execute.mutate()}
                  disabled={execute.isPending || !execForm.toolName}
                  className="w-full flex items-center justify-center gap-2 rounded-lg bg-[var(--text-brand)] text-white text-sm font-medium py-2.5 hover:opacity-90 disabled:opacity-50"
                >
                  {execute.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Terminal className="w-4 h-4" />}
                  Execute
                </button>
              </div>
            </div>

            <div className="rounded-xl border border-[var(--border-default)] bg-[var(--surface-base)] p-5">
              <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-4 flex items-center gap-2">
                <Activity className="w-4 h-4 text-[var(--text-brand)]" /> Result
              </h3>
              {!execResult ? (
                <div className="flex flex-col items-center justify-center py-12 text-center">
                  <Terminal className="w-12 h-12 text-[var(--text-tertiary)] mb-3" />
                  <p className="text-sm text-[var(--text-secondary)]">Execute a tool to see results here</p>
                </div>
              ) : (
                <div className="space-y-3">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-medium text-[var(--text-secondary)]">{execResult.toolName}</span>
                    <EnterpriseStatusBadge
                      status={execResult.success ? 'success' : 'error'}
                      label={execResult.success ? 'Success' : 'Failed'}
                    />
                  </div>
                  {execResult.budgetExceeded && (
                    <div className="flex items-center gap-2 rounded-lg bg-[var(--nexus-warning-50)] border border-[var(--border-warning)] px-3 py-2 text-xs text-[var(--text-warning)]">
                      <AlertTriangle className="w-4 h-4" /> Agent budget exceeded — execution blocked
                    </div>
                  )}
                  <div className="flex items-center gap-2 text-xs text-[var(--text-tertiary)]">
                    <Zap className="w-3.5 h-3.5" /> Latency: {execResult.latencyMs}ms
                  </div>
                  <pre className="rounded-lg bg-[var(--surface-sunken)] p-3 text-xs font-mono text-[var(--text-primary)] overflow-x-auto max-h-64">
                    {JSON.stringify(execResult.data ?? execResult.error, null, 2)}
                  </pre>
                </div>
              )}
            </div>
          </div>
        )}

        {activeTab === 'TOOLS' && (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <div className="rounded-xl border border-[var(--border-default)] bg-[var(--surface-base)] p-5">
              <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-4 flex items-center gap-2">
                <Plus className="w-4 h-4 text-[var(--text-brand)]" /> Register Tool
              </h3>
              <div className="space-y-3">
                <div>
                  <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1.5">Tool Name</label>
                  <input
                    value={toolForm.toolName}
                    onChange={(e) => setToolForm((f) => ({ ...f, toolName: e.target.value }))}
                    className="w-full rounded-lg border border-[var(--border-default)] px-3 py-2 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-[var(--border-focus)]"
                    placeholder="get_order"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1.5">Description</label>
                  <textarea
                    value={toolForm.description}
                    onChange={(e) => setToolForm((f) => ({ ...f, description: e.target.value }))}
                    className="w-full rounded-lg border border-[var(--border-default)] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[var(--border-focus)]"
                    rows={2}
                    placeholder="What does this tool do?"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1.5">Access</label>
                  <div className="grid grid-cols-2 gap-2">
                    {ACCESS_OPTIONS.map((acc) => (
                      <button
                        key={acc}
                        onClick={() => setToolForm((f) => ({ ...f, access: acc as 'READ' | 'WRITE' }))}
                        className={clsx(
                          'rounded-lg border px-3 py-2 text-sm font-medium transition-all',
                          toolForm.access === acc
                            ? 'border-[var(--border-brand)] bg-[var(--surface-brand)] text-[var(--text-brand)]'
                            : 'border-[var(--border-default)] text-[var(--text-secondary)] hover:bg-[var(--interactive-hover)]',
                        )}
                      >
                        {acc}
                      </button>
                    ))}
                  </div>
                </div>
                <div>
                  <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1.5">Endpoint (optional)</label>
                  <input
                    value={toolForm.endpoint}
                    onChange={(e) => setToolForm((f) => ({ ...f, endpoint: e.target.value }))}
                    className="w-full rounded-lg border border-[var(--border-default)] px-3 py-2 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-[var(--border-focus)]"
                    placeholder="/api/orders/{id}"
                  />
                </div>
                <button
                  onClick={() => register.mutate()}
                  disabled={register.isPending || !toolForm.toolName}
                  className="w-full flex items-center justify-center gap-2 rounded-lg bg-[var(--text-brand)] text-white text-sm font-medium py-2.5 hover:opacity-90 disabled:opacity-50"
                >
                  {register.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />}
                  Register Tool
                </button>
              </div>
            </div>

            <div className="lg:col-span-2 rounded-xl border border-[var(--border-default)] bg-[var(--surface-base)] overflow-hidden">
              <div className="flex items-center justify-between px-5 py-4 border-b border-[var(--border-default)]">
                <h3 className="text-sm font-semibold text-[var(--text-primary)]">Tool Registry</h3>
                <button
                  onClick={() => qc.invalidateQueries({ queryKey: ['mcp-tools'] })}
                  className="text-xs text-[var(--text-secondary)] hover:text-[var(--text-primary)]"
                >
                  Refresh
                </button>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-left text-xs text-[var(--text-tertiary)] border-b border-[var(--border-default)]">
                      <th className="px-5 py-3 font-medium">Tool</th>
                      <th className="px-5 py-3 font-medium">Description</th>
                      <th className="px-5 py-3 font-medium">Access</th>
                      <th className="px-5 py-3 font-medium">Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {toolsQuery.isLoading && (
                      <tr><td colSpan={4} className="px-5 py-8 text-center text-[var(--text-tertiary)]"><Loader2 className="w-5 h-5 animate-spin inline" /></td></tr>
                    )}
                    {!toolsQuery.isLoading && tools.length === 0 && (
                      <tr><td colSpan={4} className="px-5 py-8 text-center text-[var(--text-tertiary)]">No tools registered yet</td></tr>
                    )}
                    {tools.map((tool) => (
                      <tr key={tool.id} className="border-b border-[var(--border-subtle)] hover:bg-[var(--interactive-hover)]">
                        <td className="px-5 py-3 font-mono text-xs text-[var(--text-brand)]">{tool.toolName}</td>
                        <td className="px-5 py-3 text-[var(--text-secondary)]">{tool.description || '—'}</td>
                        <td className="px-5 py-3">
                          <EnterpriseStatusBadge
                            status={tool.access === 'WRITE' ? 'warning' : 'info'}
                            label={tool.access}
                          />
                        </td>
                        <td className="px-5 py-3">
                          <EnterpriseStatusBadge status={tool.isActive ? 'success' : 'neutral'} label={tool.isActive ? 'Active' : 'Inactive'} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}

        {activeTab === 'BUDGETS' && (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <div className="rounded-xl border border-[var(--border-default)] bg-[var(--surface-base)] p-5">
              <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-4 flex items-center gap-2">
                <Coins className="w-4 h-4 text-[var(--text-brand)]" /> Set Agent Budget
              </h3>
              <div className="space-y-3">
                <div>
                  <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1.5">Agent Name</label>
                  <input
                    value={budgetForm.agentName}
                    onChange={(e) => setBudgetForm((f) => ({ ...f, agentName: e.target.value }))}
                    className="w-full rounded-lg border border-[var(--border-default)] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[var(--border-focus)]"
                    placeholder="support-agent"
                  />
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1.5">Query Budget</label>
                    <input
                      type="number"
                      value={budgetForm.queryBudget}
                      onChange={(e) => setBudgetForm((f) => ({ ...f, queryBudget: Number(e.target.value) }))}
                      className="w-full rounded-lg border border-[var(--border-default)] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[var(--border-focus)]"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1.5">Cost Budget ($)</label>
                    <input
                      type="number"
                      value={budgetForm.costBudget}
                      onChange={(e) => setBudgetForm((f) => ({ ...f, costBudget: Number(e.target.value) }))}
                      className="w-full rounded-lg border border-[var(--border-default)] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[var(--border-focus)]"
                    />
                  </div>
                </div>
                <div>
                  <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1.5">Period</label>
                  <div className="grid grid-cols-3 gap-2">
                    {(['DAILY', 'WEEKLY', 'MONTHLY'] as const).map((p) => (
                      <button
                        key={p}
                        onClick={() => setBudgetForm((f) => ({ ...f, period: p }))}
                        className={clsx(
                          'rounded-lg border px-3 py-2 text-xs font-medium transition-all',
                          budgetForm.period === p
                            ? 'border-[var(--border-brand)] bg-[var(--surface-brand)] text-[var(--text-brand)]'
                            : 'border-[var(--border-default)] text-[var(--text-secondary)] hover:bg-[var(--interactive-hover)]',
                        )}
                      >
                        {p}
                      </button>
                    ))}
                  </div>
                </div>
                <button
                  onClick={() => saveBudget.mutate()}
                  disabled={saveBudget.isPending || !budgetForm.agentName}
                  className="w-full flex items-center justify-center gap-2 rounded-lg bg-[var(--text-brand)] text-white text-sm font-medium py-2.5 hover:opacity-90 disabled:opacity-50"
                >
                  {saveBudget.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Check className="w-4 h-4" />}
                  Save Budget
                </button>
              </div>
            </div>

            <div className="lg:col-span-2 rounded-xl border border-[var(--border-default)] bg-[var(--surface-base)] overflow-hidden">
              <div className="flex items-center justify-between px-5 py-4 border-b border-[var(--border-default)]">
                <h3 className="text-sm font-semibold text-[var(--text-primary)]">Agent Budgets</h3>
                <button
                  onClick={() => qc.invalidateQueries({ queryKey: ['mcp-budgets'] })}
                  className="text-xs text-[var(--text-secondary)] hover:text-[var(--text-primary)]"
                >
                  Refresh
                </button>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-left text-xs text-[var(--text-tertiary)] border-b border-[var(--border-default)]">
                      <th className="px-5 py-3 font-medium">Agent</th>
                      <th className="px-5 py-3 font-medium">Query Budget</th>
                      <th className="px-5 py-3 font-medium">Cost Budget</th>
                      <th className="px-5 py-3 font-medium">Period</th>
                      <th className="px-5 py-3 font-medium">Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {budgetsQuery.isLoading && (
                      <tr><td colSpan={5} className="px-5 py-8 text-center text-[var(--text-tertiary)]"><Loader2 className="w-5 h-5 animate-spin inline" /></td></tr>
                    )}
                    {!budgetsQuery.isLoading && budgets.length === 0 && (
                      <tr><td colSpan={5} className="px-5 py-8 text-center text-[var(--text-tertiary)]">No budgets configured yet</td></tr>
                    )}
                    {budgets.map((b) => (
                      <tr key={b.id} className="border-b border-[var(--border-subtle)] hover:bg-[var(--interactive-hover)]">
                        <td className="px-5 py-3 font-medium text-[var(--text-primary)]">{b.agentName}</td>
                        <td className="px-5 py-3 text-[var(--text-secondary)]">{b.queryBudget}</td>
                        <td className="px-5 py-3 text-[var(--text-secondary)]">${b.costBudget.toFixed(2)}</td>
                        <td className="px-5 py-3 text-[var(--text-secondary)]">{b.period}</td>
                        <td className="px-5 py-3">
                          <EnterpriseStatusBadge status={b.isActive ? 'success' : 'neutral'} label={b.isActive ? 'Active' : 'Inactive'} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
