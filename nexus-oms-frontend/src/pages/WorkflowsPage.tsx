import { useState, useEffect } from 'react'
import { GitBranch, Play, List, Plus, X, Clock, Loader2, ChevronDown, ChevronRight } from 'lucide-react'
import clsx from 'clsx'
import { useToast } from '../hooks/useToast'
import * as workflowsApi from '../api/workflows'
import Autocomplete from '../components/common/Autocomplete'
import PermissionGate from '../components/rbac/PermissionGate'

const categoryStyles: Record<string, string> = {
  ORDER: 'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-800)]',
  INVENTORY: 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-800)]',
  SHIPPING: 'bg-[var(--nexus-ai-100)] text-[var(--nexus-ai-800)]',
  PROCUREMENT: 'bg-orange-100 text-orange-800',
  CUSTOM: 'bg-[var(--surface-muted)] text-[var(--text-primary)]',
}

const statusStyles: Record<string, string> = {
  DRAFT: 'bg-[var(--surface-muted)] text-[var(--text-secondary)]',
  ACTIVE: 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-800)]',
  INACTIVE: 'bg-[var(--surface-muted)] text-[var(--text-secondary)]',
  ARCHIVED: 'bg-[var(--surface-muted)] text-[var(--text-secondary)]',
}

const executionStatusStyles: Record<string, string> = {
  RUNNING: 'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-800)]',
  COMPLETED: 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-800)]',
  FAILED: 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-800)]',
  CANCELLED: 'bg-[var(--surface-muted)] text-[var(--text-secondary)]',
}

export default function WorkflowsPage() {
  const [workflows, setWorkflows] = useState<workflowsApi.Workflow[]>([])
  const [loading, setLoading] = useState(true)
  const { addToast } = useToast()

  // Create modal
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [saving, setSaving] = useState(false)
  const [createForm, setCreateForm] = useState({ name: '', description: '', category: 'ORDER', triggerType: 'EVENT', triggerConfig: '{}', isActive: true })

  // Expanded rows
  const [expandedId, setExpandedId] = useState<string | null>(null)
  const [steps, setSteps] = useState<workflowsApi.WorkflowStep[]>([])
  const [stepsLoading, setStepsLoading] = useState(false)
  const [executions, setExecutions] = useState<workflowsApi.WorkflowExecution[]>([])
  const [executionsLoading, setExecutionsLoading] = useState(false)

  // Execute modal
  const [showExecuteModal, setShowExecuteModal] = useState(false)
  const [executeWorkflowId, setExecuteWorkflowId] = useState<string | null>(null)
  const [executing, setExecuting] = useState(false)
  const [executeForm, setExecuteForm] = useState({ entityType: '', entityId: '', inputData: '{}' })

  const categoryOptions = [
    { value: 'ORDER', label: 'Order' },
    { value: 'INVENTORY', label: 'Inventory' },
    { value: 'SHIPPING', label: 'Shipping' },
    { value: 'PROCUREMENT', label: 'Procurement' },
    { value: 'CUSTOM', label: 'Custom' },
  ]

  const triggerTypeOptions = [
    { value: 'EVENT', label: 'Event' },
    { value: 'SCHEDULED', label: 'Schedule' },
    { value: 'MANUAL', label: 'Manual' },
  ]

  useEffect(() => { fetchWorkflows() }, [])

  async function fetchWorkflows() {
    try {
      setLoading(true)
      const res = await workflowsApi.getWorkflows(0, 100)
      setWorkflows(res.data?.content ?? [])
    } catch { addToast({ type: 'error', title: 'Failed to load workflows' })
    } finally { setLoading(false) }
  }

  async function fetchSteps(workflowId: string) {
    try {
      setStepsLoading(true)
      const res = await workflowsApi.getWorkflowSteps(workflowId)
      setSteps(Array.isArray(res.data) ? res.data : res.data?.content ?? [])
    } catch { addToast({ type: 'error', title: 'Failed to load steps' })
    } finally { setStepsLoading(false) }
  }

  async function fetchExecutions(workflowId: string) {
    try {
      setExecutionsLoading(true)
      const res = await workflowsApi.getExecutions(workflowId, 0, 20)
      setExecutions(res.data?.content ?? [])
    } catch { addToast({ type: 'error', title: 'Failed to load executions' })
    } finally { setExecutionsLoading(false) }
  }

  async function handleExpand(id: string) {
    if (expandedId === id) {
      setExpandedId(null)
      return
    }
    setExpandedId(id)
    await Promise.all([fetchSteps(id), fetchExecutions(id)])
  }

  function openCreate() {
    setCreateForm({ name: '', description: '', category: 'ORDER', triggerType: 'EVENT', triggerConfig: '{}', isActive: true })
    setShowCreateModal(true)
  }

  async function handleCreate() {
    if (!createForm.name.trim()) {
      addToast({ type: 'warning', title: 'Workflow name is required' }); return
    }
    let triggerConfig = {}
    try { triggerConfig = JSON.parse(createForm.triggerConfig) }
    catch { addToast({ type: 'warning', title: 'Invalid trigger config JSON' }); return }
    setSaving(true)
    try {
      await workflowsApi.createWorkflow({
        name: createForm.name,
        description: createForm.description,
        category: createForm.category,
        triggerType: createForm.triggerType,
        triggerConfig,
        isActive: createForm.isActive,
      })
      addToast({ type: 'success', title: 'Workflow created' })
      setShowCreateModal(false)
      await fetchWorkflows()
    } catch { addToast({ type: 'error', title: 'Failed to create workflow' })
    } finally { setSaving(false) }
  }

  async function handleToggleActive(wf: workflowsApi.Workflow) {
    try {
      if (wf.status === 'ACTIVE') {
        await workflowsApi.updateWorkflowStatus(wf.id, 'INACTIVE')
      } else {
        await workflowsApi.updateWorkflowStatus(wf.id, 'ACTIVE')
      }
      await fetchWorkflows()
      addToast({ type: 'success', title: `Workflow ${wf.status === 'ACTIVE' ? 'deactivated' : 'activated'}` })
    } catch { addToast({ type: 'error', title: 'Failed to update workflow' }) }
  }

  function openExecute(wfId: string) {
    setExecuteWorkflowId(wfId)
    setExecuteForm({ entityType: '', entityId: '', inputData: '{}' })
    setShowExecuteModal(true)
  }

  async function handleExecute() {
    if (!executeWorkflowId) return
    let inputData = {}
    try { inputData = JSON.parse(executeForm.inputData) }
    catch { addToast({ type: 'warning', title: 'Invalid input data JSON' }); return }
    setExecuting(true)
    try {
      await workflowsApi.executeWorkflow(executeWorkflowId, { entityType: executeForm.entityType, entityId: executeForm.entityId, ...inputData })
      addToast({ type: 'success', title: 'Workflow execution started' })
      setShowExecuteModal(false)
      if (expandedId === executeWorkflowId) await fetchExecutions(executeWorkflowId)
    } catch { addToast({ type: 'error', title: 'Failed to execute workflow' })
    } finally { setExecuting(false) }
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div>
            <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5"><GitBranch className="w-6 h-6 text-[var(--text-secondary)]" />Workflows</h1>
            <p className="text-sm text-[var(--text-secondary)] mt-1">Automate order processing, inventory, and shipping workflows</p>
          </div>
        </div>
        <PermissionGate resource="workflows" action="create">
          <button onClick={openCreate} className="btn-primary text-sm">
            <Plus className="w-4 h-4" /> Create Workflow
          </button>
        </PermissionGate>
      </div>

      {/* Content */}
      {loading ? (
        <div className="flex items-center justify-center h-64">
          <Loader2 className="w-8 h-8 animate-spin text-[var(--text-brand)]" />
        </div>
      ) : workflows.length === 0 ? (
        <div className="text-center py-16 card">
          <GitBranch className="w-12 h-12 text-[var(--text-tertiary)] mx-auto mb-3" />
          <p className="text-[var(--text-secondary)] text-sm">No workflows configured. Create your first automation workflow.</p>
        </div>
      ) : (
        <div className="space-y-2">
          {workflows.map(wf => (
            <div key={wf.id} className="card overflow-hidden">
              {/* Main row */}
              <div
                className="flex items-center gap-4 p-4 hover:bg-[var(--surface-sunken)] cursor-pointer transition-colors"
                onClick={() => handleExpand(wf.id)}
              >
                <button className="text-[var(--text-tertiary)]" onClick={() => handleExpand(wf.id)}>
                  {expandedId === wf.id ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
                </button>
                <div className="flex-1 min-w-0 grid grid-cols-6 gap-4 items-center">
                  <div className="col-span-2">
                    <p className="text-sm font-medium text-[var(--text-primary)]">{wf.name}</p>
                    {wf.description && <p className="text-xs text-[var(--text-secondary)] mt-0.5 truncate">{wf.description}</p>}
                  </div>
                  <div>
                    <span className={clsx('inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium', categoryStyles[wf.category] || 'bg-[var(--surface-muted)] text-[var(--text-secondary)]')}>
                      {wf.category}
                    </span>
                  </div>
                  <div>
                    <span className="text-xs bg-[var(--surface-muted)] px-2 py-0.5 rounded text-[var(--text-secondary)]">{wf.triggerType}</span>
                  </div>
                  <div>
                    <span className={clsx('inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium', statusStyles[wf.status] || 'bg-[var(--surface-muted)] text-[var(--text-secondary)]')}>
                      {wf.status}
                    </span>
                  </div>
                  <div className="text-xs text-[var(--text-tertiary)]">v{wf.version}</div>
                </div>
                <div className="flex items-center gap-1 flex-shrink-0">
                  {wf.status !== 'DRAFT' && (
                    <PermissionGate resource="workflows" action="edit">
                      <button
                        onClick={(e) => { e.stopPropagation(); handleToggleActive(wf) }}
                        className={clsx(
                          'p-1.5 rounded text-xs font-medium transition-colors',
                          wf.status === 'ACTIVE'
                            ? 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-600)] hover:bg-[var(--nexus-error-50)]'
                            : 'bg-[var(--nexus-success-50)] text-[var(--nexus-success-600)] hover:bg-[var(--nexus-success-100)]'
                        )}
                      >
                        {wf.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
                      </button>
                    </PermissionGate>
                  )}
                  <PermissionGate resource="workflows" action="create">
                    <button
                      onClick={(e) => { e.stopPropagation(); openExecute(wf.id) }}
                      className="p-1.5 hover:bg-[var(--surface-muted)] rounded text-[var(--text-secondary)] hover:text-[var(--text-secondary)]"
                      title="Execute Now"
                    >
                      <Play className="w-4 h-4" />
                    </button>
                  </PermissionGate>
                </div>
              </div>

              {/* Expanded detail */}
              {expandedId === wf.id && (
                <div className="border-t border-[var(--border-subtle)] bg-[var(--surface-sunken)]">
                  <div className="p-4 space-y-4">
                    {/* Steps sub-table */}
                    <div>
                      <div className="flex items-center gap-2 mb-2">
                        <List className="w-4 h-4 text-[var(--text-secondary)]" />
                        <h4 className="text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Steps</h4>
                      </div>
                      {stepsLoading ? (
                        <div className="flex items-center justify-center py-6">
                          <Loader2 className="w-5 h-5 animate-spin text-[var(--text-brand)]" />
                        </div>
                      ) : steps.length === 0 ? (
                        <p className="text-xs text-[var(--text-tertiary)] py-2">No steps configured.</p>
                      ) : (
                        <div className="overflow-x-auto">
                          <table className="w-full text-xs">
                            <thead>
                              <tr className="border-b border-[var(--border-default)] bg-[var(--surface-muted)]">
                                <th className="text-left px-3 py-1.5 font-medium text-[var(--text-secondary)]">Order</th>
                                <th className="text-left px-3 py-1.5 font-medium text-[var(--text-secondary)]">Type</th>
                                <th className="text-left px-3 py-1.5 font-medium text-[var(--text-secondary)]">Name</th>
                                <th className="text-left px-3 py-1.5 font-medium text-[var(--text-secondary)]">Condition</th>
                                <th className="text-left px-3 py-1.5 font-medium text-[var(--text-secondary)]">On Failure</th>
                                <th className="text-left px-3 py-1.5 font-medium text-[var(--text-secondary)]">Timeout</th>
                              </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-100">
                              {steps.map(step => (
                                <tr key={step.id} className="hover:bg-white transition-colors">
                                  <td className="px-3 py-1.5 text-[var(--text-secondary)]">{step.order}</td>
                                  <td className="px-3 py-1.5">
                                    <span className="bg-[var(--surface-muted)] px-1.5 py-0.5 rounded text-[var(--text-secondary)]">{step.type}</span>
                                  </td>
                                  <td className="px-3 py-1.5 font-medium text-[var(--text-primary)]">{step.name}</td>
                                  <td className="px-3 py-1.5 text-[var(--text-secondary)] font-mono max-w-[120px] truncate">
                                    {step.config?.condition || '-'}
                                  </td>
                                  <td className="px-3 py-1.5 text-[var(--text-secondary)]">
                                    {step.config?.onFailure || '-'}
                                  </td>
                                  <td className="px-3 py-1.5 text-[var(--text-secondary)]">
                                    {step.config?.timeout ? `${step.config.timeout}s` : '-'}
                                  </td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      )}
                    </div>

                    {/* Executions sub-table */}
                    <div>
                      <div className="flex items-center justify-between mb-2">
                        <div className="flex items-center gap-2">
                          <Clock className="w-4 h-4 text-[var(--text-secondary)]" />
                          <h4 className="text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Executions</h4>
                        </div>
                      </div>
                      {executionsLoading ? (
                        <div className="flex items-center justify-center py-6">
                          <Loader2 className="w-5 h-5 animate-spin text-[var(--text-brand)]" />
                        </div>
                      ) : executions.length === 0 ? (
                        <p className="text-xs text-[var(--text-tertiary)] py-2">No executions yet.</p>
                      ) : (
                        <div className="overflow-x-auto">
                          <table className="w-full text-xs">
                            <thead>
                              <tr className="border-b border-[var(--border-default)] bg-[var(--surface-muted)]">
                                <th className="text-left px-3 py-1.5 font-medium text-[var(--text-secondary)]">Status</th>
                                <th className="text-left px-3 py-1.5 font-medium text-[var(--text-secondary)]">Steps Progress</th>
                                <th className="text-left px-3 py-1.5 font-medium text-[var(--text-secondary)]">Duration</th>
                                <th className="text-left px-3 py-1.5 font-medium text-[var(--text-secondary)]">Started At</th>
                              </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-100">
                              {executions.map(ex => (
                                <tr key={ex.id} className="hover:bg-white transition-colors">
                                  <td className="px-3 py-1.5">
                                    <span className={clsx('inline-flex items-center px-1.5 py-0.5 rounded-full text-xs font-medium', executionStatusStyles[ex.status] || 'bg-[var(--surface-muted)] text-[var(--text-secondary)]')}>
                                      {ex.status}
                                    </span>
                                  </td>
                                  <td className="px-3 py-1.5 text-[var(--text-secondary)]">-</td>
                                  <td className="px-3 py-1.5 text-[var(--text-secondary)]">
                                    {ex.duration ? `${ex.duration}ms` : '-'}
                                  </td>
                                  <td className="px-3 py-1.5 text-[var(--text-secondary)]">
                                    {ex.startedAt ? new Date(ex.startedAt).toLocaleString() : '-'}
                                  </td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {/* Create Workflow Modal */}
      {showCreateModal && (
        <div className="enterprise-modal-overlay">
          <div className="enterprise-modal max-w-lg">
            <div className="flex items-center justify-between p-6 border-b border-[var(--border-subtle)]">
              <h2 className="text-lg font-semibold text-[var(--text-primary)]">Create Workflow</h2>
              <button onClick={() => setShowCreateModal(false)} className="p-1 hover:bg-[var(--surface-muted)] rounded"><X className="w-5 h-5" /></button>
            </div>
            <div className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Workflow Name</label>
                <Autocomplete value={createForm.name} onChange={(value) => setCreateForm({ ...createForm, name: value })} inputClassName="input w-full" placeholder="e.g. Order Fulfillment Pipeline" minChars={0} showSearchIcon={false} clearable={false} />
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Description</label>
                <Autocomplete value={createForm.description} onChange={(value) => setCreateForm({ ...createForm, description: value })} inputClassName="input w-full" placeholder="Describe the workflow purpose" minChars={0} showSearchIcon={false} clearable={false} />
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Category</label>
                <Autocomplete
                  value={createForm.category}
                  onChange={(value) => setCreateForm({ ...createForm, category: value })}
                  suggestions={categoryOptions}
                  getOptionLabel={o => o.label}
                  getOptionValue={o => o.value}
                  inputClassName="input w-full"
                  minChars={0}
                  showSearchIcon={false}
                  clearable={false}
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Trigger Type</label>
                <Autocomplete
                  value={createForm.triggerType}
                  onChange={(value) => setCreateForm({ ...createForm, triggerType: value })}
                  suggestions={triggerTypeOptions}
                  getOptionLabel={o => o.label}
                  getOptionValue={o => o.value}
                  inputClassName="input w-full"
                  minChars={0}
                  showSearchIcon={false}
                  clearable={false}
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Trigger Config (JSON)</label>
                <Autocomplete value={createForm.triggerConfig} onChange={(value) => setCreateForm({ ...createForm, triggerConfig: value })} inputClassName="input w-full font-mono text-xs" placeholder='{"cron": "0 0 * * *", "eventType": "ORDER_CREATED"}' minChars={0} showSearchIcon={false} clearable={false} />
              </div>
              <div className="flex items-center gap-2">
                <label className="text-sm font-medium text-[var(--text-secondary)]">Active</label>
                <button
                  onClick={() => setCreateForm({ ...createForm, isActive: !createForm.isActive })}
                  className={clsx('relative inline-flex h-5 w-9 items-center rounded-full transition-colors', createForm.isActive ? 'bg-[var(--nexus-success-50)]0' : 'bg-[var(--surface-muted)]')}
                >
                  <span className={clsx('inline-block h-3.5 w-3.5 transform rounded-full bg-white transition-transform', createForm.isActive ? 'translate-x-[18px]' : 'translate-x-[3px]')} />
                </button>
              </div>
            </div>
            <div className="p-6 border-t border-[var(--border-subtle)] flex justify-end gap-3">
              <button onClick={() => setShowCreateModal(false)} className="btn-secondary text-sm">Cancel</button>
              <PermissionGate resource="workflows" action="create">
                <button onClick={handleCreate} disabled={saving} className="btn-primary text-sm">
                  {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />}
                  Create
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}

      {/* Execute Workflow Modal */}
      {showExecuteModal && (
        <div className="enterprise-modal-overlay">
          <div className="enterprise-modal max-w-lg">
            <div className="flex items-center justify-between p-6 border-b border-[var(--border-subtle)]">
              <h2 className="text-lg font-semibold text-[var(--text-primary)]">Execute Workflow</h2>
              <button onClick={() => setShowExecuteModal(false)} className="p-1 hover:bg-[var(--surface-muted)] rounded"><X className="w-5 h-5" /></button>
            </div>
            <div className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Entity Type</label>
                <Autocomplete value={executeForm.entityType} onChange={(value) => setExecuteForm({ ...executeForm, entityType: value })} inputClassName="input w-full" placeholder="e.g. ORDER, INVENTORY" minChars={0} showSearchIcon={false} clearable={false} />
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Entity ID</label>
                <Autocomplete value={executeForm.entityId} onChange={(value) => setExecuteForm({ ...executeForm, entityId: value })} inputClassName="input w-full" placeholder="e.g. order-123" minChars={0} showSearchIcon={false} clearable={false} />
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Input Data (JSON)</label>
                <Autocomplete value={executeForm.inputData} onChange={(value) => setExecuteForm({ ...executeForm, inputData: value })} inputClassName="input w-full font-mono text-xs" placeholder='{"priority": "HIGH", "notes": "Manual trigger"}' minChars={0} showSearchIcon={false} clearable={false} />
              </div>
            </div>
            <div className="p-6 border-t border-[var(--border-subtle)] flex justify-end gap-3">
              <button onClick={() => setShowExecuteModal(false)} className="btn-secondary text-sm">Cancel</button>
              <PermissionGate resource="workflows" action="create">
                <button onClick={handleExecute} disabled={executing} className="btn-primary text-sm">
                  {executing && <Loader2 className="w-4 h-4 animate-spin" />}
                  <Play className="w-4 h-4" /> Execute
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
