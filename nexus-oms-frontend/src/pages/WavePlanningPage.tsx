import { useState, useMemo } from 'react'
import { clsx } from 'clsx'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Layers, Plus, Play, CheckCircle, XCircle, Clock, Calendar, Zap,
  ChevronDown, ChevronRight, Lightbulb, X, Loader2, TrendingUp, AlertTriangle,
} from 'lucide-react'
import PermissionGate from '../components/rbac/PermissionGate'
import Autocomplete from '../components/common/Autocomplete'
import { useToast } from '../hooks/useToast'
import {
  fetchWavePlans, fetchWaveStats, createWavePlan, planWave, releaseWave, completeWave, cancelWave, optimizeWave,
} from '../api/newBackend'

interface NxWave {
  id: string
  name: string
  warehouseId?: string
  status: string
  priority?: string
  waveType?: string
  orderCount?: number | null
  totalLineItems?: number | null
  releasedLineItems?: number | null
  completedLineItems?: number | null
  zoneFilter?: string | null
  targetCompletionAt?: string | null
  releasedAt?: string | null
  completedAt?: string | null
  optimizationScore?: number | null
  createdAt?: string
  updatedAt?: string
}

const STATUS_LABELS: Record<string, string> = {
  DRAFT: 'Draft',
  PLANNED: 'Planned',
  RELEASING: 'Releasing',
  RELEASING_PAUSED: 'Paused',
  RELEASED: 'Released',
  IN_PROGRESS: 'In Progress',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
}

const STATUS_STYLES: Record<string, string> = {
  DRAFT: 'bg-[var(--surface-muted)] text-[var(--text-secondary)]',
  PLANNED: 'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-700)]',
  RELEASING: 'bg-[var(--nexus-info-100)] text-[var(--nexus-info-700)]',
  RELEASING_PAUSED: 'bg-[var(--nexus-warning-100)] text-[var(--nexus-warning-700)]',
  RELEASED: 'bg-[var(--nexus-info-100)] text-[var(--nexus-info-700)]',
  IN_PROGRESS: 'bg-[var(--nexus-warning-100)] text-[var(--nexus-warning-700)]',
  COMPLETED: 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-700)]',
  CANCELLED: 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-700)]',
}

const PRIORITY_COLORS: Record<string, string> = {
  High: 'text-[var(--nexus-error-600)]',
  Medium: 'text-[var(--nexus-warning-600)]',
  Low: 'text-[var(--text-secondary)]',
}

function StatCard({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <div className="enterprise-card p-4 flex items-center gap-4">
      <div className="w-10 h-10 rounded-lg bg-[var(--nexus-primary-50)] flex items-center justify-center text-[var(--text-brand)] flex-shrink-0">
        {icon}
      </div>
      <div className="min-w-0">
        <p className="text-xs text-[var(--text-secondary)] font-medium">{label}</p>
        <p className="text-xl font-bold text-[var(--text-primary)] mt-0.5">{value}</p>
      </div>
    </div>
  )
}

function formatTime(iso?: string | null) {
  if (!iso) return null
  const d = new Date(iso)
  if (isNaN(d.getTime())) return null
  return d.toLocaleString()
}

export default function WavePlanningPage() {
  const { addToast } = useToast()
  const queryClient = useQueryClient()

  const [search, setSearch] = useState('')
  const [expandedId, setExpandedId] = useState<string | null>(null)
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [selectedWaveId, setSelectedWaveId] = useState<string | null>(null)
  const [showCalendar, setShowCalendar] = useState(false)
  const [processingId, setProcessingId] = useState<string | null>(null)

  const [createForm, setCreateForm] = useState({
    name: '',
    priority: 'Medium',
    waveType: 'ZONE',
    zoneFilter: '',
    targetCompletionAt: '',
  })

  const { data: waves = [], isLoading } = useQuery({
    queryKey: ['waves'],
    queryFn: async () => {
      const res = await fetchWavePlans()
      return Array.isArray(res?.data) ? res.data : []
    },
    refetchInterval: 60_000,
  })

  const { data: stats = {} } = useQuery({
    queryKey: ['wave-stats'],
    queryFn: async () => {
      const res = await fetchWaveStats()
      return (res?.data ?? {}) || {}
    },
    refetchInterval: 60_000,
  })

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['waves'] })
    queryClient.invalidateQueries({ queryKey: ['wave-stats'] })
  }

  const waveAction = useMutation({
    mutationFn: ({ id, action }: { id: string; action: 'plan' | 'release' | 'complete' | 'cancel' | 'optimize' }) => {
      if (action === 'plan') return planWave(id)
      if (action === 'release') return releaseWave(id)
      if (action === 'complete') return completeWave(id)
      if (action === 'cancel') return cancelWave(id)
      return optimizeWave(id)
    },
    onSuccess: (res, vars) => {
      if (res?.success) {
        const verb = vars.action === 'optimize' ? 'optimized' : 'updated'
        addToast({ type: 'success', title: `Wave ${verb}` })
        invalidate()
      } else {
        addToast({ type: 'error', title: res?.message || res?.error || 'Failed to update wave' })
      }
    },
  })

  const createMutation = useMutation({
    mutationFn: () => createWavePlan({
      name: createForm.name,
      priority: createForm.priority,
      waveType: createForm.waveType,
      zoneFilter: createForm.zoneFilter,
      targetCompletionAt: createForm.targetCompletionAt || undefined,
    }),
    onSuccess: (res) => {
      if (res?.success) {
        addToast({ type: 'success', title: `Wave "${res.data?.name || createForm.name}" created` })
        setShowCreateModal(false)
        setCreateForm({ name: '', priority: 'Medium', waveType: 'ZONE', zoneFilter: '', targetCompletionAt: '' })
        invalidate()
      } else {
        addToast({ type: 'error', title: res?.message || res?.error || 'Failed to create wave' })
      }
    },
  })

  function runAction(id: string, action: 'plan' | 'release' | 'complete' | 'cancel' | 'optimize') {
    setProcessingId(id)
    waveAction.mutate(
      { id, action },
      { onSettled: () => setProcessingId(null) },
    )
  }

  const filtered = useMemo(() => {
    if (!search) return waves
    const q = search.toLowerCase()
    return waves.filter(w => w.id.toLowerCase().includes(q) || (w.name || '').toLowerCase().includes(q))
  }, [waves, search])

  const selectedWave = selectedWaveId ? waves.find(w => w.id === selectedWaveId) : null

  const timeBuckets = useMemo(() => {
    const buckets: { label: string; waves: NxWave[] }[] = [
      { label: 'Morning', waves: [] },
      { label: 'Afternoon', waves: [] },
      { label: 'Evening', waves: [] },
      { label: 'Unscheduled', waves: [] },
    ]
    for (const w of waves) {
      const d = new Date(w.createdAt || '')
      const hour = isNaN(d.getTime()) ? -1 : d.getHours()
      if (hour < 0) buckets[3].waves.push(w)
      else if (hour < 12) buckets[0].waves.push(w)
      else if (hour < 17) buckets[1].waves.push(w)
      else buckets[2].waves.push(w)
    }
    return buckets
  }, [waves])

  const progressFor = (w: NxWave) => {
    const total = w.totalLineItems ?? 0
    const done = w.completedLineItems ?? 0
    const pct = total > 0 ? Math.round((done / total) * 100) : 0
    return { done, total, pct }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5">
            <Layers className="w-7 h-7 text-[var(--nexus-primary-500)]" /> Wave Planning
          </h1>
          <p className="text-sm text-[var(--text-secondary)] mt-1">Create and manage picking waves</p>
        </div>
        <div className="flex items-center gap-3">
          <button
            onClick={() => setShowCalendar(!showCalendar)}
            className="enterprise-btn enterprise-btn-secondary text-sm flex items-center gap-1.5"
          >
            <Calendar className="w-4 h-4" /> {showCalendar ? 'Hide Calendar' : 'Wave Calendar'}
          </button>
          <PermissionGate resource="warehouse" action="create">
            <button type="button" onClick={() => setShowCreateModal(true)} className="enterprise-btn enterprise-btn-primary text-sm">
              <Plus className="w-4 h-4" /> Create Wave
            </button>
          </PermissionGate>
        </div>
      </div>

      <div className="grid grid-cols-4 gap-4">
        <StatCard icon={<Zap className="w-5 h-5" />} label="Active Waves" value={String(stats.activeWaves ?? 0)} />
        <StatCard icon={<CheckCircle className="w-5 h-5" />} label="Completed Today" value={String(stats.completedToday ?? 0)} />
        <StatCard icon={<TrendingUp className="w-5 h-5" />} label="Total Waves" value={String(stats.totalWaves ?? 0)} />
        <StatCard icon={<Clock className="w-5 h-5" />} label="Avg Completion" value={stats.avgCompletionTimeMinutes != null ? `${stats.avgCompletionTimeMinutes} min` : '—'} />
      </div>

      {showCalendar && (
        <div className="enterprise-card p-5">
          <h3 className="text-sm font-semibold text-[var(--text-secondary)] mb-4 flex items-center gap-2">
            <Calendar className="w-4 h-4 text-[var(--nexus-primary-500)]" /> Wave Calendar — Today
          </h3>
          <div className="grid grid-cols-4 gap-3">
            {timeBuckets.map(slot => (
              <div key={slot.label} className="border border-[var(--border-default)] rounded-lg p-3 min-h-[100px]">
                <p className="text-xs font-semibold text-[var(--text-secondary)] mb-2">{slot.label}</p>
                {slot.waves.length === 0 ? (
                  <p className="text-[10px] text-[var(--text-tertiary)] italic">Free</p>
                ) : (
                  slot.waves.map(wv => (
                    <div
                      key={wv.id}
                      className={clsx(
                        'text-[10px] font-medium px-1.5 py-0.5 rounded mb-1 truncate',
                        STATUS_STYLES[wv.status] || 'bg-[var(--surface-muted)] text-[var(--text-secondary)]',
                      )}
                      title={formatTime(wv.createdAt) || undefined}
                    >
                      {wv.name || wv.id}
                    </div>
                  ))
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      <Autocomplete value={search} onChange={setSearch} placeholder="Search waves..." minChars={0} className="max-w-md" />

      <div className="enterprise-card overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-[var(--border-default)] bg-[var(--surface-sunken)]/50 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">
              <th className="px-4 py-3 w-8" />
              <th className="px-4 py-3">Wave ID</th>
              <th className="px-4 py-3">Name</th>
              <th className="px-4 py-3">Created</th>
              <th className="px-4 py-3">Orders</th>
              <th className="px-4 py-3">Items</th>
              <th className="px-4 py-3">Status</th>
              <th className="px-4 py-3">Priority</th>
              <th className="px-4 py-3">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-[var(--surface-sunken)]">
            {isLoading ? (
              <tr>
                <td colSpan={9} className="px-4 py-12 text-center">
                  <Loader2 className="w-6 h-6 animate-spin mx-auto text-[var(--text-tertiary)]" />
                </td>
              </tr>
            ) : filtered.length === 0 ? (
              <tr>
                <td colSpan={9} className="px-4 py-12 text-center text-[var(--text-tertiary)]">
                  {waves.length === 0 ? 'No waves found' : 'No waves match the current search'}
                </td>
              </tr>
            ) : (
              filtered.map(wave => {
                const isExpanded = expandedId === wave.id
                const progress = progressFor(wave)
                return (
                  <tr key={wave.id} className={clsx('group', isExpanded && 'bg-[var(--nexus-primary-50)]/30')}>
                    <td className="px-4 py-3">
                      <button
                        onClick={() => setExpandedId(isExpanded ? null : wave.id)}
                        className="p-0.5 hover:bg-[var(--surface-muted)] rounded"
                      >
                        {isExpanded ? (
                          <ChevronDown className="w-4 h-4 text-[var(--text-secondary)]" />
                        ) : (
                          <ChevronRight className="w-4 h-4 text-[var(--text-tertiary)]" />
                        )}
                      </button>
                    </td>
                    <td className="px-4 py-3">
                      <button
                        onClick={() => setSelectedWaveId(wave.id)}
                        className="font-medium text-[var(--text-brand)] hover:underline font-mono"
                      >
                        {wave.id.slice(0, 8)}
                      </button>
                    </td>
                    <td className="px-4 py-3 text-[var(--text-secondary)]">{wave.name || '—'}</td>
                    <td className="px-4 py-3 text-[var(--text-secondary)] text-xs">{formatTime(wave.createdAt) || '—'}</td>
                    <td className="px-4 py-3 text-[var(--text-secondary)]">{wave.orderCount ?? '—'}</td>
                    <td className="px-4 py-3 text-[var(--text-secondary)]">{wave.totalLineItems ?? '—'}</td>
                    <td className="px-4 py-3">
                      <span className={clsx('inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium', STATUS_STYLES[wave.status] || 'bg-[var(--surface-muted)] text-[var(--text-secondary)]')}>
                        {STATUS_LABELS[wave.status] || wave.status}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <span className={clsx('text-xs font-medium', PRIORITY_COLORS[wave.priority || ''] || 'text-[var(--text-secondary)]')}>{wave.priority || '—'}</span>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-1">
                        {wave.status === 'DRAFT' && (
                          <>
                            <PermissionGate resource="warehouse" action="edit">
                              <button
                                onClick={() => runAction(wave.id, 'plan')}
                                disabled={processingId === wave.id}
                                className="p-1.5 hover:bg-[var(--nexus-primary-50)] rounded text-[var(--text-tertiary)] hover:text-[var(--nexus-primary-600)] disabled:opacity-50"
                                title="Plan Wave"
                              >
                                {processingId === wave.id ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Play className="w-3.5 h-3.5" />}
                              </button>
                            </PermissionGate>
                            <PermissionGate resource="warehouse" action="edit">
                              <button
                                onClick={() => runAction(wave.id, 'cancel')}
                                disabled={processingId === wave.id}
                                className="p-1.5 hover:bg-[var(--nexus-error-50)] rounded text-[var(--text-tertiary)] hover:text-[var(--nexus-error-600)] disabled:opacity-50"
                                title="Cancel Wave"
                              >
                                <XCircle className="w-3.5 h-3.5" />
                              </button>
                            </PermissionGate>
                          </>
                        )}
                        {wave.status === 'PLANNED' && (
                          <PermissionGate resource="warehouse" action="edit">
                            <button
                              onClick={() => runAction(wave.id, 'release')}
                              disabled={processingId === wave.id}
                              className="p-1.5 hover:bg-[var(--nexus-success-50)] rounded text-[var(--text-tertiary)] hover:text-[var(--nexus-success-600)] disabled:opacity-50"
                              title="Release Wave"
                            >
                              {processingId === wave.id ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Play className="w-3.5 h-3.5" />}
                            </button>
                          </PermissionGate>
                        )}
                        {wave.status === 'IN_PROGRESS' && (
                          <>
                            <PermissionGate resource="warehouse" action="edit">
                              <button
                                onClick={() => runAction(wave.id, 'complete')}
                                disabled={processingId === wave.id}
                                className="p-1.5 hover:bg-[var(--nexus-success-50)] rounded text-[var(--text-tertiary)] hover:text-[var(--nexus-success-600)] disabled:opacity-50"
                                title="Complete Wave"
                              >
                                {processingId === wave.id ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <CheckCircle className="w-3.5 h-3.5" />}
                              </button>
                            </PermissionGate>
                            <PermissionGate resource="warehouse" action="edit">
                              <button
                                onClick={() => runAction(wave.id, 'cancel')}
                                disabled={processingId === wave.id}
                                className="p-1.5 hover:bg-[var(--nexus-error-50)] rounded text-[var(--text-tertiary)] hover:text-[var(--nexus-error-600)] disabled:opacity-50"
                                title="Cancel Wave"
                              >
                                <XCircle className="w-3.5 h-3.5" />
                              </button>
                            </PermissionGate>
                          </>
                        )}
                        {wave.status === 'RELEASED' && (
                          <span className="text-xs text-[var(--text-tertiary)] italic">Awaiting progress</span>
                        )}
                        {wave.status === 'COMPLETED' && (
                          <span className="text-xs text-[var(--nexus-success-600)] font-medium">Done</span>
                        )}
                        {wave.status === 'CANCELLED' && (
                          <span className="text-xs text-[var(--nexus-error-400)]">Cancelled</span>
                        )}
                      </div>
                    </td>
                  </tr>
                )
              })
            )}
          </tbody>
        </table>
      </div>

      {expandedId && filtered.find(w => w.id === expandedId) && (() => {
        const wave = filtered.find(w => w.id === expandedId)!
        const progress = progressFor(wave)
        return (
          <div className="enterprise-card p-5 border-l-4 border-l-primary-500">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-sm font-semibold text-[var(--text-secondary)]">{wave.name || wave.id} — Details</h3>
              <div className="flex items-center gap-2">
                <span className="text-xs text-[var(--text-secondary)]">
                  {progress.done}/{progress.total} items completed
                </span>
                <div className="w-20 h-1.5 bg-[var(--surface-muted)] rounded-full overflow-hidden">
                  <div className="h-full bg-[var(--nexus-primary-500)] rounded-full" style={{ width: `${progress.pct}%` }} />
                </div>
              </div>
            </div>
            <dl className="grid grid-cols-1 md:grid-cols-3 gap-4 text-sm">
              {[
                ['Status', STATUS_LABELS[wave.status] || wave.status],
                ['Priority', wave.priority || '—'],
                ['Wave Type', wave.waveType || '—'],
                ['Zone Filter', wave.zoneFilter || '—'],
                ['Order Count', wave.orderCount != null ? String(wave.orderCount) : '—'],
                ['Line Items', wave.totalLineItems != null ? String(wave.totalLineItems) : '—'],
                ['Optimization Score', wave.optimizationScore != null ? `${wave.optimizationScore}/100` : '—'],
                ['Target Completion', formatTime(wave.targetCompletionAt) || '—'],
                ['Released At', formatTime(wave.releasedAt) || '—'],
              ].map(([k, v]) => (
                <div key={k} className="flex justify-between py-1.5 px-3 bg-[var(--surface-sunken)]/50 rounded-lg">
                  <span className="text-[var(--text-secondary)]">{k}</span>
                  <span className="font-medium text-[var(--text-primary)]">{v}</span>
                </div>
              ))}
            </dl>
            {(wave.status === 'PLANNED' || wave.status === 'IN_PROGRESS') && (
              <div className="flex items-center justify-end pt-3 border-t border-[var(--border-subtle)] mt-4">
                <PermissionGate resource="warehouse" action="edit">
                  <button
                    onClick={() => runAction(wave.id, 'optimize')}
                    disabled={processingId === wave.id}
                    className="enterprise-btn enterprise-btn-secondary text-xs flex items-center gap-1.5"
                  >
                    {processingId === wave.id ? (
                      <Loader2 className="w-3.5 h-3.5 animate-spin" />
                    ) : (
                      <Lightbulb className="w-3.5 h-3.5" />
                    )}
                    AI Optimize
                  </button>
                </PermissionGate>
              </div>
            )}
          </div>
        )
      })()}

      {/* Wave Details Panel */}
      {selectedWaveId && !expandedId && selectedWave && (() => {
        const progress = progressFor(selectedWave)
        return (
          <div className="enterprise-card p-5 border-l-4 border-l-primary-500">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-sm font-semibold text-[var(--text-secondary)]">{selectedWave.name || selectedWave.id}</h3>
              <button type="button" onClick={() => setSelectedWaveId(null)} className="p-1 hover:bg-[var(--surface-muted)] rounded">
                <X className="w-4 h-4 text-[var(--text-tertiary)]" />
              </button>
            </div>
            <div className="space-y-4">
              <div>
                <p className="text-xs font-medium text-[var(--text-secondary)] mb-1">Progress</p>
                <div className="flex items-center gap-3">
                  <div className="flex-1 h-2 bg-[var(--surface-muted)] rounded-full overflow-hidden">
                    <div className="h-full bg-[var(--nexus-primary-500)] rounded-full" style={{ width: `${progress.pct}%` }} />
                  </div>
                  <span className="text-sm font-semibold text-[var(--text-secondary)]">{progress.done}/{progress.total}</span>
                </div>
              </div>
              <dl className="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm">
                {[
                  ['Status', STATUS_LABELS[selectedWave.status] || selectedWave.status],
                  ['Priority', selectedWave.priority || '—'],
                  ['Wave Type', selectedWave.waveType || '—'],
                  ['Zone Filter', selectedWave.zoneFilter || '—'],
                  ['Order Count', selectedWave.orderCount != null ? String(selectedWave.orderCount) : '—'],
                  ['Optimization Score', selectedWave.optimizationScore != null ? `${selectedWave.optimizationScore}/100` : '—'],
                  ['Target Completion', formatTime(selectedWave.targetCompletionAt) || '—'],
                  ['Released At', formatTime(selectedWave.releasedAt) || '—'],
                ].map(([k, v]) => (
                  <div key={k} className="flex justify-between py-1.5 px-3 bg-[var(--surface-sunken)]/50 rounded-lg">
                    <span className="text-[var(--text-secondary)]">{k}</span>
                    <span className="font-medium text-[var(--text-primary)]">{v}</span>
                  </div>
                ))}
              </dl>
              {(selectedWave.status === 'PLANNED' || selectedWave.status === 'IN_PROGRESS') && (
                <div className="flex justify-end">
                  <PermissionGate resource="warehouse" action="edit">
                    <button
                      onClick={() => runAction(selectedWave.id, 'optimize')}
                      disabled={processingId === selectedWave.id}
                      className="enterprise-btn enterprise-btn-primary text-xs flex items-center gap-1.5"
                    >
                      {processingId === selectedWave.id ? (
                        <Loader2 className="w-3.5 h-3.5 animate-spin" />
                      ) : (
                        <Lightbulb className="w-3.5 h-3.5" />
                      )}
                      {processingId === selectedWave.id ? 'Optimizing...' : 'AI Optimization'}
                    </button>
                  </PermissionGate>
                </div>
              )}
            </div>
          </div>
        )
      })()}

      {/* Create Wave Modal */}
      {showCreateModal && (
        <div className="enterprise-modal-overlay">
          <div className="enterprise-modal max-w-lg">
            <div className="flex items-center justify-between p-6 border-b border-[var(--border-subtle)]">
              <h2 className="text-lg font-semibold text-[var(--text-primary)]">Create Wave</h2>
              <button type="button" onClick={() => setShowCreateModal(false)} className="p-1 hover:bg-[var(--surface-muted)] rounded">
                <X className="w-5 h-5" />
              </button>
            </div>
            <div className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Wave Name</label>
                <input
                  value={createForm.name}
                  onChange={e => setCreateForm({ ...createForm, name: e.target.value })}
                  className="input w-full"
                  placeholder="e.g. Morning Express"
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Priority</label>
                  <select
                    value={createForm.priority}
                    onChange={e => setCreateForm({ ...createForm, priority: e.target.value })}
                    className="input w-full"
                  >
                    <option value="High">High</option>
                    <option value="Medium">Medium</option>
                    <option value="Low">Low</option>
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Wave Type</label>
                  <select
                    value={createForm.waveType}
                    onChange={e => setCreateForm({ ...createForm, waveType: e.target.value })}
                    className="input w-full"
                  >
                    <option value="ZONE">Zone Picking</option>
                    <option value="BATCH">Batch Picking</option>
                  </select>
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Zone Filter (comma-separated)</label>
                <input
                  value={createForm.zoneFilter}
                  onChange={e => setCreateForm({ ...createForm, zoneFilter: e.target.value })}
                  className="input w-full"
                  placeholder="e.g. A, B, C"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Target Completion</label>
                <input
                  type="datetime-local"
                  value={createForm.targetCompletionAt}
                  onChange={e => setCreateForm({ ...createForm, targetCompletionAt: e.target.value })}
                  className="input w-full"
                />
              </div>
              <div className="flex items-start gap-2 bg-[var(--surface-sunken)]/50 rounded-lg p-3 text-xs text-[var(--text-tertiary)]">
                <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
                <p>New waves start in Draft. Use Plan to generate the pick plan, then Release to begin picking. Orders are assigned via wave rules.</p>
              </div>
            </div>
            <div className="p-6 border-t border-[var(--border-subtle)] flex justify-end gap-3">
              <button type="button" onClick={() => setShowCreateModal(false)} className="enterprise-btn enterprise-btn-secondary text-sm">Cancel</button>
              <PermissionGate resource="warehouse" action="create">
                <button type="button" onClick={() => createMutation.mutate()} disabled={!createForm.name || createMutation.isPending} className="enterprise-btn enterprise-btn-primary text-sm">
                  {createMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />} Create Wave
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
