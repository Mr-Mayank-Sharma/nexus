import { useState, useEffect } from 'react'
import { Brain, FlaskConical, Play, CheckCircle2, XCircle, RotateCcw, AlertTriangle, Plus, Loader2 } from 'lucide-react'
import { AiExperiment } from '../types'
import {
  getExperiments, createExperiment, updateExperiment,
  startExperiment, completeExperiment, rollbackExperiment, failExperiment,
} from '../api/experimentApi'
import { getModels, AiModel } from '../api/aiPlatform'
import EnterpriseBreadcrumbs from '../components/enterprise/EnterpriseBreadcrumbs'
import EnterpriseKPICard from '../components/enterprise/EnterpriseKPICard'
import EnterpriseStatusBadge from '../components/enterprise/EnterpriseStatusBadge'
import { useToast } from '../hooks/useToast'

const EXPERIMENT_TYPES = ['A_B_TEST', 'CHAMPION_CHALLENGER', 'MULTIVARIATE', 'CANARY'] as const
const STATUS_OPTIONS = ['DRAFT', 'RUNNING', 'COMPLETED', 'ROLLED_BACK', 'FAILED'] as const

const STATUS_COLORS: Record<string, string> = {
  DRAFT: 'info',
  RUNNING: 'warning',
  COMPLETED: 'success',
  ROLLED_BACK: 'error',
  FAILED: 'error',
}

export default function AiExperimentsPage() {
  const { addToast } = useToast()
  const [experiments, setExperiments] = useState<AiExperiment[]>([])
  const [models, setModels] = useState<AiModel[]>([])
  const [loading, setLoading] = useState(true)
  const [statusFilter, setStatusFilter] = useState('')
  const [modelFilter, setModelFilter] = useState('')
  const [page, setPage] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [showCreate, setShowCreate] = useState(false)
  const [editing, setEditing] = useState<AiExperiment | null>(null)
  const [saving, setSaving] = useState(false)
  const [completingId, setCompletingId] = useState<string | null>(null)

  function loadExperiments() {
    setLoading(true)
    getExperiments(modelFilter || undefined, statusFilter || undefined, page, 20)
      .then(res => {
        setExperiments(res.data?.content ?? [])
        setTotalElements(res.data?.totalElements ?? 0)
      })
      .catch(() => addToast({ type: 'error', title: 'Failed to load experiments' }))
      .finally(() => setLoading(false))
  }

  useEffect(() => { loadExperiments() }, [page, statusFilter, modelFilter])

  useEffect(() => {
    getModels(0, 100)
      .then(res => setModels(res.data?.content ?? []))
      .catch(() => {})
  }, [])

  async function handleCreate(data: Partial<AiExperiment>) {
    setSaving(true)
    try {
      await createExperiment(data)
      addToast({ type: 'success', title: 'Experiment created' })
      setShowCreate(false)
      loadExperiments()
    } catch { addToast({ type: 'error', title: 'Failed to create experiment' }) }
    finally { setSaving(false) }
  }

  async function handleUpdate(id: string, data: Partial<AiExperiment>) {
    setSaving(true)
    try {
      await updateExperiment(id, data)
      addToast({ type: 'success', title: 'Experiment updated' })
      setEditing(null)
      loadExperiments()
    } catch { addToast({ type: 'error', title: 'Failed to update experiment' }) }
    finally { setSaving(false) }
  }

  async function handleStart(id: string) {
    try {
      await startExperiment(id)
      addToast({ type: 'success', title: 'Experiment started' })
      loadExperiments()
    } catch { addToast({ type: 'error', title: 'Failed to start experiment' }) }
  }

  async function handleComplete(id: string) {
    setCompletingId(id)
    try {
      await completeExperiment(id, '')
      addToast({ type: 'success', title: 'Experiment completed without a winner' })
      loadExperiments()
    } catch { addToast({ type: 'error', title: 'Failed to complete experiment' }) }
    finally { setCompletingId(null) }
  }

  async function handleRollback(id: string) {
    try {
      await rollbackExperiment(id)
      addToast({ type: 'success', title: 'Experiment rolled back' })
      loadExperiments()
    } catch { addToast({ type: 'error', title: 'Failed to rollback experiment' }) }
  }

  async function handleFail(id: string) {
    try {
      await failExperiment(id, 'Manually failed')
      addToast({ type: 'success', title: 'Experiment marked as failed' })
      loadExperiments()
    } catch { addToast({ type: 'error', title: 'Failed to fail experiment' }) }
  }

  const statusCounts: Record<string, number> = {}
  experiments.forEach(e => { statusCounts[e.status] = (statusCounts[e.status] || 0) + 1 })

  function getModelName(modelId?: string) {
    if (!modelId) return '-'
    return models.find(m => m.id === modelId)?.displayName || models.find(m => m.id === modelId)?.name || modelId.slice(0, 8)
  }

  return (
    <div className="p-6 space-y-6">
      <EnterpriseBreadcrumbs crumbs={[{ label: 'Home', href: '/' }, { label: 'AI Platform', href: '/ai-platform' }, { label: 'Experiments' }]} />

      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)]">Experiments</h1>
          <p className="text-sm text-[var(--text-secondary)] mt-0.5">A/B test and champion/challenger experiments for AI models</p>
        </div>
        <button onClick={() => setShowCreate(true)} className="enterprise-btn enterprise-btn-primary flex items-center gap-2">
          <Plus className="w-4 h-4" /> New Experiment
        </button>
      </div>

      <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
        <EnterpriseKPICard title="Total" value={String(experiments.length)} icon={<FlaskConical className="w-4 h-4" />} variant="primary" />
        <EnterpriseKPICard title="Draft" value={String(statusCounts.DRAFT || 0)} icon={<Plus className="w-4 h-4" />} variant="info" />
        <EnterpriseKPICard title="Running" value={String(statusCounts.RUNNING || 0)} icon={<Play className="w-4 h-4" />} variant="warning" />
        <EnterpriseKPICard title="Completed" value={String(statusCounts.COMPLETED || 0)} icon={<CheckCircle2 className="w-4 h-4" />} variant="success" />
        <EnterpriseKPICard title="Failed" value={String((statusCounts.FAILED || 0) + (statusCounts.ROLLED_BACK || 0))} icon={<XCircle className="w-4 h-4" />} variant="error" />
      </div>

      <div className="flex gap-2 flex-wrap items-center">
        <select
          value={modelFilter}
          onChange={e => { setModelFilter(e.target.value); setPage(0) }}
          className="enterprise-input text-sm w-auto"
        >
          <option value="">All models</option>
          {models.map(m => (
            <option key={m.id} value={m.id}>{m.displayName || m.name || m.modelType}</option>
          ))}
        </select>
        {['', ...STATUS_OPTIONS].map(s => (
          <button key={s}
            onClick={() => { setStatusFilter(s); setPage(0) }}
            className={`px-3 py-1.5 text-sm rounded-lg transition-colors ${
              statusFilter === s
                ? 'bg-primary-600 text-white'
                : 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-300 hover:bg-gray-200'
            }`}
          >
            {s || 'All'}
          </button>
        ))}
      </div>

      {loading ? (
        <div className="flex justify-center py-12"><Loader2 className="w-8 h-8 animate-spin text-primary-600" /></div>
      ) : experiments.length === 0 ? (
        <div className="text-center py-12 text-gray-400">No experiments found</div>
      ) : (
        <div className="space-y-3">
          {experiments.map(exp => (
            <div key={exp.id} className="bg-white dark:bg-[#162033] rounded-xl border border-gray-200 dark:border-gray-700">
              <div className="p-4">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-lg bg-purple-100 dark:bg-purple-900/30 flex items-center justify-center">
                      <FlaskConical className="w-5 h-5 text-purple-600 dark:text-purple-400" />
                    </div>
                    <div>
                      <div className="flex items-center gap-2">
                        <p className="font-medium text-gray-900 dark:text-white">{exp.name}</p>
                        <EnterpriseStatusBadge status={exp.status} />
                      </div>
                      <p className="text-xs text-gray-400 mt-0.5">
                        {getModelName(exp.modelId)} &middot; {exp.experimentType || '-'} &middot; {exp.trafficSplit != null ? `${(exp.trafficSplit * 100).toFixed(0)}%` : '-'} traffic split
                      </p>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    {exp.status === 'DRAFT' && (
                      <>
                        <button onClick={() => setEditing(exp)} className="px-3 py-1.5 text-xs bg-primary-600 text-white rounded-lg hover:bg-primary-700">Edit</button>
                        <button onClick={() => handleStart(exp.id)} className="px-3 py-1.5 text-xs bg-amber-600 text-white rounded-lg hover:bg-amber-700 flex items-center gap-1">
                          <Play className="w-3 h-3" /> Start
                        </button>
                      </>
                    )}
                    {exp.status === 'RUNNING' && (
                      <>
                        <button onClick={() => handleComplete(exp.id)} disabled={completingId === exp.id}
                          className="px-3 py-1.5 text-xs bg-green-600 text-white rounded-lg hover:bg-green-700 flex items-center gap-1">
                          {completingId === exp.id ? <Loader2 className="w-3 h-3 animate-spin" /> : <CheckCircle2 className="w-3 h-3" />}
                          Complete
                        </button>
                        <button onClick={() => handleFail(exp.id)} className="px-3 py-1.5 text-xs bg-red-600 text-white rounded-lg hover:bg-red-700 flex items-center gap-1">
                          <XCircle className="w-3 h-3" /> Fail
                        </button>
                        <button onClick={() => handleRollback(exp.id)} className="px-3 py-1.5 text-xs bg-gray-600 text-white rounded-lg hover:bg-gray-700 flex items-center gap-1">
                          <RotateCcw className="w-3 h-3" /> Rollback
                        </button>
                      </>
                    )}
                    {(exp.status === 'COMPLETED' || exp.status === 'ROLLED_BACK' || exp.status === 'FAILED') && (
                      <button onClick={() => handleRollback(exp.id)} className="px-3 py-1.5 text-xs bg-gray-600 text-white rounded-lg hover:bg-gray-700 flex items-center gap-1">
                        <RotateCcw className="w-3 h-3" /> Rollback
                      </button>
                    )}
                  </div>
                </div>
                {exp.description && (
                  <p className="text-sm text-gray-500 dark:text-gray-400 mt-2 ml-[52px]">{exp.description}</p>
                )}
                <div className="flex gap-6 mt-3 ml-[52px] text-xs text-gray-400">
                  {exp.championVersionId && <span>Champion: {exp.championVersionId.slice(0, 8)}</span>}
                  {exp.challengerVersionId && <span>Challenger: {exp.challengerVersionId.slice(0, 8)}</span>}
                  {exp.winnerVersionId && <span className="text-green-600 dark:text-green-400 font-medium">Winner: {exp.winnerVersionId.slice(0, 8)}</span>}
                  {exp.successMetric && <span>Metric: {exp.successMetric}</span>}
                  {exp.startDate && <span>Started: {new Date(exp.startDate).toLocaleDateString()}</span>}
                  {exp.endDate && <span>Ended: {new Date(exp.endDate).toLocaleDateString()}</span>}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Create Modal */}
      {showCreate && (
        <ExperimentFormModal
          models={models}
          saving={saving}
          onSave={handleCreate}
          onClose={() => setShowCreate(false)}
        />
      )}

      {/* Edit Modal */}
      {editing && (
        <ExperimentFormModal
          models={models}
          experiment={editing}
          saving={saving}
          onSave={(data) => handleUpdate(editing.id, data)}
          onClose={() => setEditing(null)}
        />
      )}
    </div>
  )
}

function ExperimentFormModal({ models, experiment, saving, onSave, onClose }: {
  models: AiModel[]
  experiment?: AiExperiment | null
  saving: boolean
  onSave: (data: Partial<AiExperiment>) => void
  onClose: () => void
}) {
  const [name, setName] = useState(experiment?.name || '')
  const [description, setDescription] = useState(experiment?.description || '')
  const [modelId, setModelId] = useState(experiment?.modelId || '')
  const [experimentType, setExperimentType] = useState(experiment?.experimentType || 'A_B_TEST')
  const [championVersionId, setChampionVersionId] = useState(experiment?.championVersionId || '')
  const [challengerVersionId, setChallengerVersionId] = useState(experiment?.challengerVersionId || '')
  const [trafficSplit, setTrafficSplit] = useState(experiment?.trafficSplit != null ? String(experiment.trafficSplit) : '0.50')
  const [successMetric, setSuccessMetric] = useState(experiment?.successMetric || '')

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!name.trim() || !modelId) return
    onSave({
      name: name.trim(),
      description: description.trim() || undefined,
      modelId,
      experimentType,
      championVersionId: championVersionId.trim() || undefined,
      challengerVersionId: challengerVersionId.trim() || undefined,
      trafficSplit: parseFloat(trafficSplit),
      successMetric: successMetric.trim() || undefined,
    })
  }

  return (
    <div className="fixed inset-0 bg-black/30 z-50 flex items-center justify-center" onClick={onClose}>
      <div className="bg-white dark:bg-gray-900 rounded-xl shadow-2xl border border-gray-200 dark:border-gray-700 w-full max-w-lg mx-4 max-h-[90vh] overflow-y-auto" onClick={e => e.stopPropagation()}>
        <div className="p-5 border-b border-gray-200 dark:border-gray-700">
          <h2 className="text-lg font-semibold text-gray-900 dark:text-white">
            {experiment ? 'Edit Experiment' : 'New Experiment'}
          </h2>
        </div>
        <form onSubmit={handleSubmit} className="p-5 space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Name *</label>
            <input value={name} onChange={e => setName(e.target.value)} required
              className="enterprise-input w-full" placeholder="e.g. Demand V2 vs V3 A/B Test" />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Description</label>
            <textarea value={description} onChange={e => setDescription(e.target.value)} rows={3}
              className="enterprise-input w-full" placeholder="Describe the experiment hypothesis" />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Model *</label>
            <select value={modelId} onChange={e => setModelId(e.target.value)} required className="enterprise-input w-full">
              <option value="">Select model</option>
              {models.map(m => (
                <option key={m.id} value={m.id}>{m.displayName || m.name || m.modelType}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Experiment Type</label>
            <select value={experimentType} onChange={e => setExperimentType(e.target.value)} className="enterprise-input w-full">
              {EXPERIMENT_TYPES.map(t => (
                <option key={t} value={t}>{t.replace(/_/g, ' ')}</option>
              ))}
            </select>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Champion Version ID</label>
              <input value={championVersionId} onChange={e => setChampionVersionId(e.target.value)}
                className="enterprise-input w-full font-mono text-xs" placeholder="UUID" />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Challenger Version ID</label>
              <input value={challengerVersionId} onChange={e => setChallengerVersionId(e.target.value)}
                className="enterprise-input w-full font-mono text-xs" placeholder="UUID" />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Traffic Split</label>
              <input value={trafficSplit} onChange={e => setTrafficSplit(e.target.value)} type="number" step="0.01" min="0" max="1"
                className="enterprise-input w-full" />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Success Metric</label>
              <input value={successMetric} onChange={e => setSuccessMetric(e.target.value)}
                className="enterprise-input w-full" placeholder="e.g. accuracy, latency" />
            </div>
          </div>
          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={onClose} className="enterprise-btn enterprise-btn-secondary">Cancel</button>
            <button type="submit" disabled={saving || !name.trim() || !modelId}
              className="enterprise-btn enterprise-btn-primary flex items-center gap-2">
              {saving && <Loader2 className="w-4 h-4 animate-spin" />}
              {experiment ? 'Update' : 'Create'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
