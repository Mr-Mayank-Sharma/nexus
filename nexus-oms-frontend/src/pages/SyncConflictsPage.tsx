import { useState } from 'react'
import { clsx } from 'clsx'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  RefreshCw, Loader2, AlertTriangle, Check, ArrowLeftRight, GitMerge,
  Plus, X, Activity, ShieldAlert, Database,
} from 'lucide-react'
import EnterpriseBreadcrumbs from '../components/enterprise/EnterpriseBreadcrumbs'
import EnterpriseKPICard from '../components/enterprise/EnterpriseKPICard'
import EnterpriseStatusBadge from '../components/enterprise/EnterpriseStatusBadge'
import EnterpriseTabs from '../components/enterprise/EnterpriseTabs'
import { useToast } from '../hooks/useToast'
import {
  getSyncConflicts, resolveSyncConflict, getSyncReconciliation,
  getSyncFieldMappings, upsertSyncFieldMapping, triggerSyncRecovery,
  type SyncConflict, type SyncReconciliationReport, type SyncFieldMapping,
} from '../api/sync'

const TABS = [
  { id: 'CONFLICTS', label: 'Conflicts', icon: <AlertTriangle className="w-4 h-4" /> },
  { id: 'MAPPINGS', label: 'Field Mappings', icon: <GitMerge className="w-4 h-4" /> },
  { id: 'RECON', label: 'Reconciliation', icon: <Activity className="w-4 h-4" /> },
]

const RESOLUTION_OPTIONS = [
  { id: 'LOCAL_WINS', label: 'Keep Local', desc: 'Use local value' },
  { id: 'INBOUND_WINS', label: 'Keep Inbound', desc: 'Use remote value' },
  { id: 'MERGED', label: 'Merge', desc: 'Combine values' },
  { id: 'MANUAL', label: 'Manual', desc: 'Custom value' },
]

const DIRECTION_OPTIONS = ['INBOUND', 'OUTBOUND']
const WINNER_OPTIONS = ['LOCAL', 'REMOTE']

export default function SyncConflictsPage() {
  const { toast } = useToast()
  const qc = useQueryClient()
  const [activeTab, setActiveTab] = useState('CONFLICTS')
  const [statusFilter, setStatusFilter] = useState('')

  // Resolve modal state
  const [resolving, setResolving] = useState<SyncConflict | null>(null)
  const [resolution, setResolution] = useState('LOCAL_WINS')
  const [manualValue, setManualValue] = useState('')

  // Mapping form
  const [mappingForm, setMappingForm] = useState({
    entityType: '', fieldName: '', direction: 'INBOUND' as 'INBOUND' | 'OUTBOUND',
    winner: 'LOCAL' as 'LOCAL' | 'REMOTE', isActive: true,
  })

  // ── Queries ──
  const conflictsQuery = useQuery({
    queryKey: ['sync-conflicts', statusFilter],
    queryFn: () => getSyncConflicts(statusFilter ? { status: statusFilter } : undefined),
    enabled: activeTab === 'CONFLICTS',
  })
  const conflicts: SyncConflict[] = conflictsQuery.data?.success
    ? (conflictsQuery.data.data?.content ?? [])
    : []

  const reconQuery = useQuery({
    queryKey: ['sync-recon'],
    queryFn: () => getSyncReconciliation(),
    enabled: activeTab === 'RECON',
  })
  const recon: SyncReconciliationReport | undefined = reconQuery.data?.success ? reconQuery.data.data : undefined

  const mappingsQuery = useQuery({
    queryKey: ['sync-mappings'],
    queryFn: () => getSyncFieldMappings(),
    enabled: activeTab === 'MAPPINGS',
  })
  const mappings: SyncFieldMapping[] = mappingsQuery.data?.success ? (mappingsQuery.data.data ?? []) : []

  // ── Mutations ──
  const resolve = useMutation({
    mutationFn: () => resolveSyncConflict(resolving!.id, {
      resolution,
      mergedFields: resolution === 'MANUAL' ? { value: manualValue } : undefined,
    }),
    onSuccess: (res) => {
      if (res.success) {
        toast('Conflict resolved', 'success')
        setResolving(null)
        setResolution('LOCAL_WINS')
        setManualValue('')
        qc.invalidateQueries({ queryKey: ['sync-conflicts'] })
        qc.invalidateQueries({ queryKey: ['sync-recon'] })
      } else {
        toast(res.error || 'Failed to resolve', 'error')
      }
    },
  })

  const saveMapping = useMutation({
    mutationFn: () => upsertSyncFieldMapping(mappingForm),
    onSuccess: (res) => {
      if (res.success) {
        toast('Field mapping saved', 'success')
        setMappingForm({ entityType: '', fieldName: '', direction: 'INBOUND', winner: 'LOCAL', isActive: true })
        qc.invalidateQueries({ queryKey: ['sync-mappings'] })
      } else {
        toast(res.error || 'Failed to save mapping', 'error')
      }
    },
  })

  const recovery = useMutation({
    mutationFn: () => triggerSyncRecovery(),
    onSuccess: (res) => {
      if (res.success) {
        toast(`Recovery triggered — ${res.data} items requeued`, 'success')
        qc.invalidateQueries({ queryKey: ['sync-recon'] })
      } else {
        toast(res.error || 'Failed to trigger recovery', 'error')
      }
    },
  })

  const open = conflicts.filter((c) => c.status === 'OPEN').length
  const resolved = conflicts.filter((c) => c.status === 'RESOLVED').length

  // Aggregate reconciliation stats across directions
  const reconPending = recon?.directions.reduce((sum, d) => sum + d.pending, 0) ?? 0
  const reconFailed = recon?.directions.reduce((sum, d) => sum + d.failed, 0) ?? 0
  const reconQueued = recon?.directions.reduce((sum, d) => sum + d.queued, 0) ?? 0
  const reconCompleted = recon?.directions.reduce((sum, d) => sum + d.completed, 0) ?? 0

  return (
    <div className="p-6 max-w-[1400px] mx-auto">
      <EnterpriseBreadcrumbs crumbs={[{ label: 'Integrations' }, { label: 'Bidirectional Sync' }]} />

      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-semibold text-[var(--text-primary)] flex items-center gap-2">
            <RefreshCw className="w-6 h-6 text-[var(--text-brand)]" />
            Bidirectional Sync & Conflicts
          </h1>
          <p className="text-sm text-[var(--text-secondary)] mt-1">
            Reconcile two-way sync between your OMS and ERP, resolve field conflicts, and monitor job health.
          </p>
        </div>
      </div>

      {/* KPI row */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <EnterpriseKPICard
          title="Open Conflicts"
          value={open}
          subtitle="Need resolution"
          icon={AlertTriangle}
          color={open > 0 ? 'warning' : 'success'}
        />
        <EnterpriseKPICard
          title="Resolved"
          value={resolved}
          subtitle="Handled"
          icon={Check}
          color="success"
        />
        <EnterpriseKPICard
          title="Field Mappings"
          value={mappings.length}
          subtitle="Configured"
          icon={GitMerge}
          color="info"
        />
        <EnterpriseKPICard
          title="Sync Health"
          value={reconFailed}
          subtitle="Failed items"
          icon={ShieldAlert}
          color={reconFailed > 0 ? 'error' : 'success'}
        />
      </div>

      <EnterpriseTabs tabs={TABS} activeTab={activeTab} onChange={setActiveTab} />

      <div className="mt-6">
        {activeTab === 'CONFLICTS' && (
          <div className="rounded-xl border border-[var(--border-default)] bg-[var(--surface-base)] overflow-hidden">
            <div className="flex items-center justify-between px-5 py-4 border-b border-[var(--border-default)]">
              <div className="flex items-center gap-3">
                <h3 className="text-sm font-semibold text-[var(--text-primary)]">Sync Conflicts</h3>
                <div className="flex gap-1.5">
                  {['', 'OPEN', 'RESOLVED'].map((s) => (
                    <button
                      key={s}
                      onClick={() => setStatusFilter(s)}
                      className={clsx(
                        'rounded-full px-3 py-1 text-xs font-medium transition-all',
                        statusFilter === s
                          ? 'bg-[var(--surface-brand)] text-[var(--text-brand)]'
                          : 'text-[var(--text-secondary)] hover:bg-[var(--interactive-hover)]',
                      )}
                    >
                      {s === '' ? 'All' : s}
                    </button>
                  ))}
                </div>
              </div>
              <button
                onClick={() => qc.invalidateQueries({ queryKey: ['sync-conflicts'] })}
                className="text-xs text-[var(--text-secondary)] hover:text-[var(--text-primary)]"
              >
                Refresh
              </button>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-xs text-[var(--text-tertiary)] border-b border-[var(--border-default)]">
                    <th className="px-5 py-3 font-medium">Entity</th>
                    <th className="px-5 py-3 font-medium">Direction</th>
                    <th className="px-5 py-3 font-medium">Source</th>
                    <th className="px-5 py-3 font-medium">Conflicting Fields</th>
                    <th className="px-5 py-3 font-medium">Status</th>
                    <th className="px-5 py-3 font-medium">Action</th>
                  </tr>
                </thead>
                <tbody>
                  {conflictsQuery.isLoading && (
                    <tr><td colSpan={6} className="px-5 py-8 text-center text-[var(--text-tertiary)]"><Loader2 className="w-5 h-5 animate-spin inline" /></td></tr>
                  )}
                  {!conflictsQuery.isLoading && conflicts.length === 0 && (
                    <tr><td colSpan={6} className="px-5 py-8 text-center text-[var(--text-tertiary)]">No conflicts — sync is clean</td></tr>
                  )}
                  {conflicts.map((c) => (
                    <tr key={c.id} className="border-b border-[var(--border-subtle)] hover:bg-[var(--interactive-hover)]">
                      <td className="px-5 py-3">
                        <div className="font-medium text-[var(--text-primary)]">{c.entityType}</div>
                        <div className="text-xs text-[var(--text-tertiary)]">{c.entityId}</div>
                      </td>
                      <td className="px-5 py-3">
                        <EnterpriseStatusBadge
                          status={c.direction === 'INBOUND' ? 'info' : 'warning'}
                          label={c.direction}
                        />
                      </td>
                      <td className="px-5 py-3 text-[var(--text-secondary)]">{c.sourceSystem || '—'}</td>
                      <td className="px-5 py-3 font-mono text-xs text-[var(--text-brand)]">{c.conflictingFields || '—'}</td>
                      <td className="px-5 py-3">
                        <EnterpriseStatusBadge
                          status={c.status === 'RESOLVED' ? 'success' : 'warning'}
                          label={c.status}
                        />
                      </td>
                      <td className="px-5 py-3">
                        {c.status !== 'RESOLVED' ? (
                          <button
                            onClick={() => { setResolving(c); setResolution('LOCAL_WINS'); setManualValue('') }}
                            className="flex items-center gap-1.5 rounded-lg border border-[var(--border-default)] px-3 py-1.5 text-xs font-medium hover:bg-[var(--interactive-hover)]"
                          >
                            <GitMerge className="w-3.5 h-3.5" /> Resolve
                          </button>
                        ) : (
                          <span className="text-xs text-[var(--text-tertiary)]">{c.resolution}</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {activeTab === 'MAPPINGS' && (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <div className="rounded-xl border border-[var(--border-default)] bg-[var(--surface-base)] p-5">
              <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-4 flex items-center gap-2">
                <Plus className="w-4 h-4 text-[var(--text-brand)]" /> Add Field Mapping
              </h3>
              <div className="space-y-3">
                <div>
                  <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1.5">Entity Type</label>
                  <input
                    value={mappingForm.entityType}
                    onChange={(e) => setMappingForm((f) => ({ ...f, entityType: e.target.value }))}
                    className="w-full rounded-lg border border-[var(--border-default)] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[var(--border-focus)]"
                    placeholder="ORDER"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1.5">Field Name</label>
                  <input
                    value={mappingForm.fieldName}
                    onChange={(e) => setMappingForm((f) => ({ ...f, fieldName: e.target.value }))}
                    className="w-full rounded-lg border border-[var(--border-default)] px-3 py-2 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-[var(--border-focus)]"
                    placeholder="orderDate"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1.5">Direction</label>
                  <div className="grid grid-cols-2 gap-2">
                    {DIRECTION_OPTIONS.map((d) => (
                      <button
                        key={d}
                        onClick={() => setMappingForm((f) => ({ ...f, direction: d as 'INBOUND' | 'OUTBOUND' }))}
                        className={clsx(
                          'rounded-lg border px-3 py-2 text-sm font-medium transition-all',
                          mappingForm.direction === d
                            ? 'border-[var(--border-brand)] bg-[var(--surface-brand)] text-[var(--text-brand)]'
                            : 'border-[var(--border-default)] text-[var(--text-secondary)] hover:bg-[var(--interactive-hover)]',
                        )}
                      >
                        {d}
                      </button>
                    ))}
                  </div>
                </div>
                <div>
                  <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1.5">Winner</label>
                  <div className="grid grid-cols-2 gap-2">
                    {WINNER_OPTIONS.map((w) => (
                      <button
                        key={w}
                        onClick={() => setMappingForm((f) => ({ ...f, winner: w as 'LOCAL' | 'REMOTE' }))}
                        className={clsx(
                          'rounded-lg border px-3 py-2 text-sm font-medium transition-all',
                          mappingForm.winner === w
                            ? 'border-[var(--border-brand)] bg-[var(--surface-brand)] text-[var(--text-brand)]'
                            : 'border-[var(--border-default)] text-[var(--text-secondary)] hover:bg-[var(--interactive-hover)]',
                        )}
                      >
                        {w}
                      </button>
                    ))}
                  </div>
                </div>
                <label className="flex items-center gap-2 text-sm text-[var(--text-secondary)]">
                  <input
                    type="checkbox"
                    checked={mappingForm.isActive}
                    onChange={(e) => setMappingForm((f) => ({ ...f, isActive: e.target.checked }))}
                    className="rounded border-[var(--border-default)]"
                  />
                  Active
                </label>
                <button
                  onClick={() => saveMapping.mutate()}
                  disabled={saveMapping.isPending || !mappingForm.entityType || !mappingForm.fieldName}
                  className="w-full flex items-center justify-center gap-2 rounded-lg bg-[var(--text-brand)] text-white text-sm font-medium py-2.5 hover:opacity-90 disabled:opacity-50"
                >
                  {saveMapping.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />}
                  Add Mapping
                </button>
              </div>
            </div>

            <div className="lg:col-span-2 rounded-xl border border-[var(--border-default)] bg-[var(--surface-base)] overflow-hidden">
              <div className="flex items-center justify-between px-5 py-4 border-b border-[var(--border-default)]">
                <h3 className="text-sm font-semibold text-[var(--text-primary)]">Field Mappings</h3>
                <button
                  onClick={() => qc.invalidateQueries({ queryKey: ['sync-mappings'] })}
                  className="text-xs text-[var(--text-secondary)] hover:text-[var(--text-primary)]"
                >
                  Refresh
                </button>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-left text-xs text-[var(--text-tertiary)] border-b border-[var(--border-default)]">
                      <th className="px-5 py-3 font-medium">Entity</th>
                      <th className="px-5 py-3 font-medium">Field</th>
                      <th className="px-5 py-3 font-medium">Direction</th>
                      <th className="px-5 py-3 font-medium">Winner</th>
                      <th className="px-5 py-3 font-medium">Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {mappingsQuery.isLoading && (
                      <tr><td colSpan={5} className="px-5 py-8 text-center text-[var(--text-tertiary)]"><Loader2 className="w-5 h-5 animate-spin inline" /></td></tr>
                    )}
                    {!mappingsQuery.isLoading && mappings.length === 0 && (
                      <tr><td colSpan={5} className="px-5 py-8 text-center text-[var(--text-tertiary)]">No field mappings configured</td></tr>
                    )}
                    {mappings.map((m) => (
                      <tr key={m.id} className="border-b border-[var(--border-subtle)] hover:bg-[var(--interactive-hover)]">
                        <td className="px-5 py-3 font-medium text-[var(--text-primary)]">{m.entityType}</td>
                        <td className="px-5 py-3 font-mono text-xs text-[var(--text-brand)]">{m.fieldName}</td>
                        <td className="px-5 py-3 text-[var(--text-secondary)]">{m.direction}</td>
                        <td className="px-5 py-3 text-[var(--text-secondary)]">{m.winner}</td>
                        <td className="px-5 py-3">
                          <EnterpriseStatusBadge status={m.isActive ? 'success' : 'neutral'} label={m.isActive ? 'Active' : 'Inactive'} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}

        {activeTab === 'RECON' && (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <div className="lg:col-span-2 rounded-xl border border-[var(--border-default)] bg-[var(--surface-base)] p-5">
              <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-4 flex items-center gap-2">
                <Activity className="w-4 h-4 text-[var(--text-brand)]" /> Sync Reconciliation
              </h3>
              {reconQuery.isLoading ? (
                <div className="py-12 text-center text-[var(--text-tertiary)]"><Loader2 className="w-5 h-5 animate-spin inline" /></div>
              ) : recon ? (
                <div className="space-y-4">
                  <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                    <ReconStat label="Pending" value={reconPending} color="text-[var(--text-warning)]" />
                    <ReconStat label="Queued" value={reconQueued} color="text-[var(--text-info)]" />
                    <ReconStat label="Completed" value={reconCompleted} color="text-[var(--text-success)]" />
                    <ReconStat label="Failed" value={reconFailed} color={reconFailed > 0 ? 'text-[var(--text-error)]' : 'text-[var(--text-success)]'} />
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <ReconStat label="Open Conflicts" value={recon.openConflicts} color={recon.openConflicts > 0 ? 'text-[var(--text-warning)]' : 'text-[var(--text-success)]'} />
                    <ReconStat label="Resolved Conflicts" value={recon.resolvedConflicts} color="text-[var(--text-success)]" />
                  </div>
                  {recon.directions.length > 0 && (
                    <div className="rounded-lg border border-[var(--border-default)] overflow-hidden">
                      <table className="w-full text-sm">
                        <thead>
                          <tr className="text-left text-xs text-[var(--text-tertiary)] border-b border-[var(--border-default)] bg-[var(--surface-sunken)]">
                            <th className="px-4 py-2 font-medium">Direction</th>
                            <th className="px-4 py-2 font-medium">Pending</th>
                            <th className="px-4 py-2 font-medium">Queued</th>
                            <th className="px-4 py-2 font-medium">Completed</th>
                            <th className="px-4 py-2 font-medium">Failed</th>
                          </tr>
                        </thead>
                        <tbody>
                          {recon.directions.map((d) => (
                            <tr key={d.direction} className="border-b border-[var(--border-subtle)]">
                              <td className="px-4 py-2 font-medium text-[var(--text-primary)]">{d.direction}</td>
                              <td className="px-4 py-2 text-[var(--text-secondary)]">{d.pending}</td>
                              <td className="px-4 py-2 text-[var(--text-secondary)]">{d.queued}</td>
                              <td className="px-4 py-2 text-[var(--text-secondary)]">{d.completed}</td>
                              <td className="px-4 py-2 text-[var(--text-secondary)]">{d.failed}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </div>
              ) : (
                <p className="text-sm text-[var(--text-secondary)]">No reconciliation data available</p>
              )}
            </div>

            <div className="rounded-xl border border-[var(--border-default)] bg-[var(--surface-base)] p-5">
              <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-4 flex items-center gap-2">
                <Database className="w-4 h-4 text-[var(--text-brand)]" /> Recovery
              </h3>
              <p className="text-xs text-[var(--text-secondary)] mb-4">
                Requeue failed sync items and retry them automatically. Failed items are re-attempted with
                backoff until they succeed or are manually resolved.
              </p>
              <button
                onClick={() => recovery.mutate()}
                disabled={recovery.isPending}
                className="w-full flex items-center justify-center gap-2 rounded-lg bg-[var(--text-brand)] text-white text-sm font-medium py-2.5 hover:opacity-90 disabled:opacity-50"
              >
                {recovery.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <RefreshCw className="w-4 h-4" />}
                Trigger Recovery
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Resolve modal */}
      {resolving && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-lg rounded-xl bg-[var(--surface-base)] border border-[var(--border-default)] shadow-xl">
            <div className="flex items-center justify-between px-5 py-4 border-b border-[var(--border-default)]">
              <h3 className="text-sm font-semibold text-[var(--text-primary)] flex items-center gap-2">
                <GitMerge className="w-4 h-4 text-[var(--text-brand)]" /> Resolve Conflict
              </h3>
              <button onClick={() => setResolving(null)} className="text-[var(--text-tertiary)] hover:text-[var(--text-primary)]">
                <X className="w-4 h-4" />
              </button>
            </div>
            <div className="p-5 space-y-4">
              <div className="rounded-lg bg-[var(--surface-sunken)] p-3">
                <div className="text-xs text-[var(--text-tertiary)] mb-1">{resolving.entityType} · {resolving.entityId}</div>
                <div className="font-mono text-sm text-[var(--text-brand)]">{resolving.conflictingFields || '—'}</div>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div className="rounded-lg border border-[var(--border-default)] p-3">
                  <div className="text-xs font-medium text-[var(--text-secondary)] mb-1">Direction</div>
                  <div className="text-sm text-[var(--text-primary)]">{resolving.direction}</div>
                </div>
                <div className="rounded-lg border border-[var(--border-default)] p-3">
                  <div className="text-xs font-medium text-[var(--text-secondary)] mb-1">Source</div>
                  <div className="text-sm text-[var(--text-primary)]">{resolving.sourceSystem || '—'}</div>
                </div>
              </div>
              <div>
                <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1.5">Resolution</label>
                <div className="grid grid-cols-2 gap-2">
                  {RESOLUTION_OPTIONS.map((opt) => (
                    <button
                      key={opt.id}
                      onClick={() => setResolution(opt.id)}
                      className={clsx(
                        'rounded-lg border px-3 py-2 text-left transition-all',
                        resolution === opt.id
                          ? 'border-[var(--border-brand)] bg-[var(--surface-brand)]'
                          : 'border-[var(--border-default)] hover:bg-[var(--interactive-hover)]',
                      )}
                    >
                      <div className={clsx('text-xs font-medium', resolution === opt.id ? 'text-[var(--text-brand)]' : 'text-[var(--text-primary)]')}>{opt.label}</div>
                      <div className="text-[11px] text-[var(--text-tertiary)]">{opt.desc}</div>
                    </button>
                  ))}
                </div>
              </div>
              {resolution === 'MANUAL' && (
                <div>
                  <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1.5">Manual Value</label>
                  <input
                    value={manualValue}
                    onChange={(e) => setManualValue(e.target.value)}
                    className="w-full rounded-lg border border-[var(--border-default)] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[var(--border-focus)]"
                    placeholder="Enter resolved value"
                  />
                </div>
              )}
            </div>
            <div className="flex justify-end gap-2 px-5 py-4 border-t border-[var(--border-default)]">
              <button
                onClick={() => setResolving(null)}
                className="rounded-lg border border-[var(--border-default)] px-4 py-2 text-sm font-medium hover:bg-[var(--interactive-hover)]"
              >
                Cancel
              </button>
              <button
                onClick={() => resolve.mutate()}
                disabled={resolve.isPending || (resolution === 'MANUAL' && !manualValue)}
                className="flex items-center gap-2 rounded-lg bg-[var(--text-brand)] text-white text-sm font-medium px-4 py-2 hover:opacity-90 disabled:opacity-50"
              >
                {resolve.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Check className="w-4 h-4" />}
                Resolve
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function ReconStat({ label, value, color }: { label: string; value: string | number; color: string }) {
  return (
    <div className="rounded-lg bg-[var(--surface-sunken)] p-3">
      <div className={clsx('text-xl font-semibold', color)}>{value}</div>
      <div className="text-xs text-[var(--text-tertiary)] mt-0.5">{label}</div>
    </div>
  )
}
