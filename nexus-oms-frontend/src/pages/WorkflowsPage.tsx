import { useState, useEffect } from 'react'
import { GitBranch, Play, Square, List, Settings, Plus, Search, X, Check, Clock, Loader2, ChevronDown, ChevronRight, ExternalLink } from 'lucide-react'
import clsx from 'clsx'
import { useToast } from '../hooks/useToast'
import * as workflowsApi from '../api/workflows'

const categoryStyles: Record<string, string> = {
  ORDER: 'bg-blue-100 text-blue-800',
  INVENTORY: 'bg-green-100 text-green-800',
  SHIPPING: 'bg-purple-100 text-purple-800',
  PROCUREMENT: 'bg-orange-100 text-orange-800',
  CUSTOM: 'bg-gray-100 text-gray-800',
}

const statusStyles: Record<string, string> = {
  DRAFT: 'bg-gray-100 text-gray-700',
  ACTIVE: 'bg-green-100 text-green-800',
  INACTIVE: 'bg-gray-100 text-gray-700',
  ARCHIVED: 'bg-gray-100 text-gray-500',
}

const executionStatusStyles: Record<string, string> = {
  RUNNING: 'bg-blue-100 text-blue-800',
  COMPLETED: 'bg-green-100 text-green-800',
  FAILED: 'bg-red-100 text-red-800',
  CANCELLED: 'bg-gray-100 text-gray-700',
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

  useEffect(() => { fetchWorkflows() }, [])

  async function fetchWorkflows() {
    try {
      setLoading(true)
      const res = await workflowsApi.getWorkflows(0, 100)
      setWorkflows(res.data.content)
    } catch { addToast({ type: 'error', title: 'Failed to load workflows' })
    } finally { setLoading(false) }
  }

  async function fetchSteps(workflowId: string) {
    try {
      setStepsLoading(true)
      const res = await workflowsApi.getWorkflowSteps(workflowId)
      setSteps(res.data)
    } catch { addToast({ type: 'error', title: 'Failed to load steps' })
    } finally { setStepsLoading(false) }
  }

  async function fetchExecutions(workflowId: string) {
    try {
      setExecutionsLoading(true)
      const res = await workflowsApi.getExecutions(workflowId, 0, 20)
      setExecutions(res.data.content)
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
          <GitBranch className="w-6 h-6 text-gray-700" />
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Workflows</h1>
            <p className="text-sm text-gray-500 mt-1">Automate order processing, inventory, and shipping workflows</p>
          </div>
        </div>
        <button onClick={openCreate} className="btn-primary text-sm">
          <Plus className="w-4 h-4" /> Create Workflow
        </button>
      </div>

      {/* Content */}
      {loading ? (
        <div className="flex items-center justify-center h-64">
          <Loader2 className="w-8 h-8 animate-spin text-primary-600" />
        </div>
      ) : workflows.length === 0 ? (
        <div className="text-center py-16 card">
          <GitBranch className="w-12 h-12 text-gray-300 mx-auto mb-3" />
          <p className="text-gray-500 text-sm">No workflows configured. Create your first automation workflow.</p>
        </div>
      ) : (
        <div className="space-y-2">
          {workflows.map(wf => (
            <div key={wf.id} className="card overflow-hidden">
              {/* Main row */}
              <div
                className="flex items-center gap-4 p-4 hover:bg-gray-50 cursor-pointer transition-colors"
                onClick={() => handleExpand(wf.id)}
              >
                <button className="text-gray-400">
                  {expandedId === wf.id ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
                </button>
                <div className="flex-1 min-w-0 grid grid-cols-6 gap-4 items-center">
                  <div className="col-span-2">
                    <p className="text-sm font-medium text-gray-900">{wf.name}</p>
                    {wf.description && <p className="text-xs text-gray-500 mt-0.5 truncate">{wf.description}</p>}
                  </div>
                  <div>
                    <span className={clsx('inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium', categoryStyles[wf.category] || 'bg-gray-100 text-gray-700')}>
                      {wf.category}
                    </span>
                  </div>
                  <div>
                    <span className="text-xs bg-gray-100 px-2 py-0.5 rounded text-gray-600">{wf.triggerType}</span>
                  </div>
                  <div>
                    <span className={clsx('inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium', statusStyles[wf.status] || 'bg-gray-100 text-gray-700')}>
                      {wf.status}
                    </span>
                  </div>
                  <div className="text-xs text-gray-400">v{wf.version}</div>
                </div>
                <div className="flex items-center gap-1 flex-shrink-0">
                  {wf.status !== 'DRAFT' && (
                    <button
                      onClick={(e) => { e.stopPropagation(); handleToggleActive(wf) }}
                      className={clsx(
                        'p-1.5 rounded text-xs font-medium transition-colors',
                        wf.status === 'ACTIVE'
                          ? 'bg-red-50 text-red-600 hover:bg-red-100'
                          : 'bg-green-50 text-green-600 hover:bg-green-100'
                      )}
                    >
                      {wf.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
                    </button>
                  )}
                  <button
                    onClick={(e) => { e.stopPropagation(); openExecute(wf.id) }}
                    className="p-1.5 hover:bg-gray-100 rounded text-gray-500 hover:text-gray-700"
                    title="Execute Now"
                  >
                    <Play className="w-4 h-4" />
                  </button>
                </div>
              </div>

              {/* Expanded detail */}
              {expandedId === wf.id && (
                <div className="border-t border-gray-100 bg-gray-50">
                  <div className="p-4 space-y-4">
                    {/* Steps sub-table */}
                    <div>
                      <div className="flex items-center gap-2 mb-2">
                        <List className="w-4 h-4 text-gray-500" />
                        <h4 className="text-xs font-semibold text-gray-700 uppercase tracking-wider">Steps</h4>
                      </div>
                      {stepsLoading ? (
                        <div className="flex items-center justify-center py-6">
                          <Loader2 className="w-5 h-5 animate-spin text-primary-600" />
                        </div>
                      ) : steps.length === 0 ? (
                        <p className="text-xs text-gray-400 py-2">No steps configured.</p>
                      ) : (
                        <div className="overflow-x-auto">
                          <table className="w-full text-xs">
                            <thead>
                              <tr className="border-b border-gray-200 bg-gray-100">
                                <th className="text-left px-3 py-1.5 font-medium text-gray-600">Order</th>
                                <th className="text-left px-3 py-1.5 font-medium text-gray-600">Type</th>
                                <th className="text-left px-3 py-1.5 font-medium text-gray-600">Name</th>
                                <th className="text-left px-3 py-1.5 font-medium text-gray-600">Condition</th>
                                <th className="text-left px-3 py-1.5 font-medium text-gray-600">On Failure</th>
                                <th className="text-left px-3 py-1.5 font-medium text-gray-600">Timeout</th>
                              </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-100">
                              {steps.map(step => (
                                <tr key={step.id} className="hover:bg-white transition-colors">
                                  <td className="px-3 py-1.5 text-gray-500">{step.order}</td>
                                  <td className="px-3 py-1.5">
                                    <span className="bg-gray-100 px-1.5 py-0.5 rounded text-gray-600">{step.type}</span>
                                  </td>
                                  <td className="px-3 py-1.5 font-medium text-gray-800">{step.name}</td>
                                  <td className="px-3 py-1.5 text-gray-500 font-mono max-w-[120px] truncate">
                                    {step.config?.condition || '-'}
                                  </td>
                                  <td className="px-3 py-1.5 text-gray-500">
                                    {step.config?.onFailure || '-'}
                                  </td>
                                  <td className="px-3 py-1.5 text-gray-500">
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
                          <Clock className="w-4 h-4 text-gray-500" />
                          <h4 className="text-xs font-semibold text-gray-700 uppercase tracking-wider">Executions</h4>
                        </div>
                      </div>
                      {executionsLoading ? (
                        <div className="flex items-center justify-center py-6">
                          <Loader2 className="w-5 h-5 animate-spin text-primary-600" />
                        </div>
                      ) : executions.length === 0 ? (
                        <p className="text-xs text-gray-400 py-2">No executions yet.</p>
                      ) : (
                        <div className="overflow-x-auto">
                          <table className="w-full text-xs">
                            <thead>
                              <tr className="border-b border-gray-200 bg-gray-100">
                                <th className="text-left px-3 py-1.5 font-medium text-gray-600">Status</th>
                                <th className="text-left px-3 py-1.5 font-medium text-gray-600">Steps Progress</th>
                                <th className="text-left px-3 py-1.5 font-medium text-gray-600">Duration</th>
                                <th className="text-left px-3 py-1.5 font-medium text-gray-600">Started At</th>
                              </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-100">
                              {executions.map(ex => (
                                <tr key={ex.id} className="hover:bg-white transition-colors">
                                  <td className="px-3 py-1.5">
                                    <span className={clsx('inline-flex items-center px-1.5 py-0.5 rounded-full text-xs font-medium', executionStatusStyles[ex.status] || 'bg-gray-100 text-gray-700')}>
                                      {ex.status}
                                    </span>
                                  </td>
                                  <td className="px-3 py-1.5 text-gray-500">-</td>
                                  <td className="px-3 py-1.5 text-gray-500">
                                    {ex.duration ? `${ex.duration}ms` : '-'}
                                  </td>
                                  <td className="px-3 py-1.5 text-gray-500">
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
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-lg w-full max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between p-6 border-b border-gray-100">
              <h2 className="text-lg font-semibold text-gray-900">Create Workflow</h2>
              <button onClick={() => setShowCreateModal(false)} className="p-1 hover:bg-gray-100 rounded"><X className="w-5 h-5" /></button>
            </div>
            <div className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Workflow Name</label>
                <input value={createForm.name} onChange={e => setCreateForm({ ...createForm, name: e.target.value })} className="input w-full" placeholder="e.g. Order Fulfillment Pipeline" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Description</label>
                <textarea value={createForm.description} onChange={e => setCreateForm({ ...createForm, description: e.target.value })} className="input w-full" rows={2} placeholder="Describe the workflow purpose" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Category</label>
                <select value={createForm.category} onChange={e => setCreateForm({ ...createForm, category: e.target.value })} className="input w-full">
                  <option value="ORDER">Order</option>
                  <option value="INVENTORY">Inventory</option>
                  <option value="SHIPPING">Shipping</option>
                  <option value="PROCUREMENT">Procurement</option>
                  <option value="CUSTOM">Custom</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Trigger Type</label>
                <select value={createForm.triggerType} onChange={e => setCreateForm({ ...createForm, triggerType: e.target.value })} className="input w-full">
                  <option value="EVENT">Event</option>
                  <option value="SCHEDULE">Schedule</option>
                  <option value="MANUAL">Manual</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Trigger Config (JSON)</label>
                <textarea value={createForm.triggerConfig} onChange={e => setCreateForm({ ...createForm, triggerConfig: e.target.value })} className="input w-full font-mono text-xs" rows={3} placeholder='{"cron": "0 0 * * *", "eventType": "ORDER_CREATED"}' />
              </div>
              <div className="flex items-center gap-2">
                <label className="text-sm font-medium text-gray-700">Active</label>
                <button
                  onClick={() => setCreateForm({ ...createForm, isActive: !createForm.isActive })}
                  className={clsx('relative inline-flex h-5 w-9 items-center rounded-full transition-colors', createForm.isActive ? 'bg-green-500' : 'bg-gray-300')}
                >
                  <span className={clsx('inline-block h-3.5 w-3.5 transform rounded-full bg-white transition-transform', createForm.isActive ? 'translate-x-[18px]' : 'translate-x-[3px]')} />
                </button>
              </div>
            </div>
            <div className="p-6 border-t border-gray-100 flex justify-end gap-3">
              <button onClick={() => setShowCreateModal(false)} className="btn-secondary text-sm">Cancel</button>
              <button onClick={handleCreate} disabled={saving} className="btn-primary text-sm">
                {saving && <Loader2 className="w-4 h-4 animate-spin" />}
                Create
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Execute Workflow Modal */}
      {showExecuteModal && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-lg w-full max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between p-6 border-b border-gray-100">
              <h2 className="text-lg font-semibold text-gray-900">Execute Workflow</h2>
              <button onClick={() => setShowExecuteModal(false)} className="p-1 hover:bg-gray-100 rounded"><X className="w-5 h-5" /></button>
            </div>
            <div className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Entity Type</label>
                <input value={executeForm.entityType} onChange={e => setExecuteForm({ ...executeForm, entityType: e.target.value })} className="input w-full" placeholder="e.g. ORDER, INVENTORY" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Entity ID</label>
                <input value={executeForm.entityId} onChange={e => setExecuteForm({ ...executeForm, entityId: e.target.value })} className="input w-full" placeholder="e.g. order-123" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Input Data (JSON)</label>
                <textarea value={executeForm.inputData} onChange={e => setExecuteForm({ ...executeForm, inputData: e.target.value })} className="input w-full font-mono text-xs" rows={4} placeholder='{"priority": "HIGH", "notes": "Manual trigger"}' />
              </div>
            </div>
            <div className="p-6 border-t border-gray-100 flex justify-end gap-3">
              <button onClick={() => setShowExecuteModal(false)} className="btn-secondary text-sm">Cancel</button>
              <button onClick={handleExecute} disabled={executing} className="btn-primary text-sm">
                {executing && <Loader2 className="w-4 h-4 animate-spin" />}
                <Play className="w-4 h-4" /> Execute
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
