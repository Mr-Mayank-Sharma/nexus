import { useState, useMemo } from 'react'
import { clsx } from 'clsx'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Radio, Plus, X, Check, ScanLine, PackageCheck, AlertTriangle, Loader2,
  ClipboardList, BarChart3, Settings, RefreshCw, Tag, Boxes, ShieldCheck,
} from 'lucide-react'
import EnterpriseBreadcrumbs from '../components/enterprise/EnterpriseBreadcrumbs'
import EnterpriseKPICard from '../components/enterprise/EnterpriseKPICard'
import EnterpriseStatusBadge from '../components/enterprise/EnterpriseStatusBadge'
import EnterpriseTabs from '../components/enterprise/EnterpriseTabs'
import { useToast } from '../hooks/useToast'
import {
  openRfidSession, ingestEpcs, completeRfidSession, receiveRfid,
  runRfidCycleCount, getRfidInventory, retagRfid, decodeEpc,
  type RfidScanSession, type RfidScanResult, type CycleCountResult,
  type SerializedInventory,
} from '../api/rfid'

const SESSION_TYPES = [
  { id: 'RECEIVING', label: 'Receiving', icon: PackageCheck },
  { id: 'CYCLE_COUNT', label: 'Cycle Count', icon: ClipboardList },
  { id: 'FULFILLMENT', label: 'Fulfillment', icon: Boxes },
]

const TABS = [
  { id: 'SCAN', label: 'Scan & Ingest', icon: <ScanLine className="w-4 h-4" /> },
  { id: 'INVENTORY', label: 'Serialized Inventory', icon: <Boxes className="w-4 h-4" /> },
  { id: 'ANALYTICS', label: 'Analytics', icon: <BarChart3 className="w-4 h-4" /> },
]

const STATUS_MAP: Record<string, string> = {
  ACTIVE: 'active',
  COMPLETED: 'completed',
  IN_STOCK: 'active',
  SHIPPED: 'shipped',
  DAMAGED: 'rejected',
}

export default function RfidPage() {
  const { toast } = useToast()
  const qc = useQueryClient()
  const [activeTab, setActiveTab] = useState('SCAN')
  const [sessionType, setSessionType] = useState('RECEIVING')
  const [locationId, setLocationId] = useState('WH-MAIN')
  const [activeSession, setActiveSession] = useState<RfidScanSession | null>(null)
  const [epcInput, setEpcInput] = useState('')
  const [epcs, setEpcs] = useState<string[]>([])
  const [scanResult, setScanResult] = useState<RfidScanResult | null>(null)
  const [cycleResult, setCycleResult] = useState<CycleCountResult | null>(null)
  const [retagForm, setRetagForm] = useState({ oldEpc: '', newEpc: '' })

  // ── Queries ──
  const inventoryQuery = useQuery({
    queryKey: ['rfid-inventory'],
    queryFn: () => getRfidInventory(),
    enabled: activeTab === 'INVENTORY',
  })
  const inventory = inventoryQuery.data?.success ? (inventoryQuery.data.data?.content ?? []) : []

  // ── Mutations ──
  const openSession = useMutation({
    mutationFn: () => openRfidSession({ sessionType, locationId }),
    onSuccess: (res) => {
      if (res.success && res.data) {
        setActiveSession(res.data)
        setEpcs([])
        setScanResult(null)
        setCycleResult(null)
        toast('RFID session opened', 'success')
      } else {
        toast(res.error || 'Failed to open session', 'error')
      }
    },
  })

  const ingest = useMutation({
    mutationFn: (epcList: string[]) => ingestEpcs(activeSession!.id, epcList),
    onSuccess: (res) => {
      if (res.success && res.data) {
        setScanResult(res.data)
        setEpcs([])
        setEpcInput('')
        toast(`Ingested ${res.data.totalReads} EPCs`, 'success')
      } else {
        toast(res.error || 'Failed to ingest', 'error')
      }
    },
  })

  const complete = useMutation({
    mutationFn: () => completeRfidSession(activeSession!.id),
    onSuccess: (res) => {
      if (res.success) {
        setActiveSession(null)
        toast('Session completed', 'success')
        qc.invalidateQueries({ queryKey: ['rfid-inventory'] })
      } else {
        toast(res.error || 'Failed to complete', 'error')
      }
    },
  })

  const receive = useMutation({
    mutationFn: () => receiveRfid(activeSession!.id, locationId, epcs),
    onSuccess: (res) => {
      if (res.success) {
        toast(`Received ${res.data} items`, 'success')
        setEpcs([])
        qc.invalidateQueries({ queryKey: ['rfid-inventory'] })
      } else {
        toast(res.error || 'Failed to receive', 'error')
      }
    },
  })

  const cycleCount = useMutation({
    mutationFn: () => runRfidCycleCount(activeSession!.id, locationId),
    onSuccess: (res) => {
      if (res.success && res.data) {
        setCycleResult(res.data)
        const variance = res.data.expected - res.data.found
        toast(`Cycle count: ${variance} variance`, variance === 0 ? 'success' : 'warning')
      } else {
        toast(res.error || 'Failed to run cycle count', 'error')
      }
    },
  })

  const retag = useMutation({
    mutationFn: () => retagRfid({ ...retagForm, locationId }),
    onSuccess: (res) => {
      if (res.success) {
        toast('EPC retagged', 'success')
        setRetagForm({ oldEpc: '', newEpc: '' })
        qc.invalidateQueries({ queryKey: ['rfid-inventory'] })
      } else {
        toast(res.error || 'Failed to retag', 'error')
      }
    },
  })

  // ── Derived ──
  const accepted = scanResult?.accepted ?? 0
  const rejected = scanResult?.rejected ?? 0
  const totalReads = scanResult?.totalReads ?? 0

  const addEpc = () => {
    const trimmed = epcInput.trim()
    if (!trimmed) return
    if (!epcs.includes(trimmed)) setEpcs((prev) => [...prev, trimmed])
    setEpcInput('')
  }

  const handleIngest = () => {
    if (!activeSession) return
    if (epcs.length === 0) {
      toast('Add at least one EPC', 'warning')
      return
    }
    ingest.mutate(epcs)
  }

  return (
    <div className="p-6 max-w-[1400px] mx-auto">
      <EnterpriseBreadcrumbs crumbs={[{ label: 'Inventory' }, { label: 'RFID & Serialized Inventory' }]} />

      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-semibold text-[var(--text-primary)] flex items-center gap-2">
            <Radio className="w-6 h-6 text-[var(--text-brand)]" />
            RFID & Serialized Inventory
          </h1>
          <p className="text-sm text-[var(--text-secondary)] mt-1">
            Scan, ingest, and track serialized items by EPC across receiving, cycle counts, and fulfillment.
          </p>
        </div>
      </div>

      {/* KPI row */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <EnterpriseKPICard
          title="Total Reads"
          value={totalReads}
          subtitle="This session"
          icon={ScanLine}
          color="primary"
        />
        <EnterpriseKPICard
          title="Accepted"
          value={accepted}
          subtitle="New EPCs"
          icon={PackageCheck}
          color="success"
        />
        <EnterpriseKPICard
          title="Rejected"
          value={rejected}
          subtitle="Invalid / foreign"
          icon={AlertTriangle}
          color={rejected > 0 ? 'warning' : 'success'}
        />
        <EnterpriseKPICard
          title="Serialized Items"
          value={inventory.length}
          subtitle="Tracked by EPC"
          icon={Boxes}
          color="info"
        />
      </div>

      <EnterpriseTabs tabs={TABS} activeTab={activeTab} onChange={setActiveTab} />

      <div className="mt-6">
        {activeTab === 'SCAN' && (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            {/* Session setup */}
            <div className="lg:col-span-1 space-y-6">
              <div className="rounded-xl border border-[var(--border-default)] bg-[var(--surface-base)] p-5">
                <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-4 flex items-center gap-2">
                  <Settings className="w-4 h-4 text-[var(--text-brand)]" /> Session Setup
                </h3>
                {!activeSession ? (
                  <>
                    <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1.5">Session Type</label>
                    <div className="grid grid-cols-3 gap-2 mb-4">
                      {SESSION_TYPES.map((st) => {
                        const Icon = st.icon
                        return (
                          <button
                            key={st.id}
                            onClick={() => setSessionType(st.id)}
                            className={clsx(
                              'flex flex-col items-center gap-1.5 rounded-lg border p-3 text-xs font-medium transition-all',
                              sessionType === st.id
                                ? 'border-[var(--border-brand)] bg-[var(--surface-brand)] text-[var(--text-brand)]'
                                : 'border-[var(--border-default)] text-[var(--text-secondary)] hover:bg-[var(--interactive-hover)]',
                            )}
                          >
                            <Icon className="w-4 h-4" />
                            {st.label}
                          </button>
                        )
                      })}
                    </div>
                    <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1.5">Location</label>
                    <input
                      value={locationId}
                      onChange={(e) => setLocationId(e.target.value)}
                      className="w-full rounded-lg border border-[var(--border-default)] px-3 py-2 text-sm mb-4 focus:outline-none focus:ring-2 focus:ring-[var(--border-focus)]"
                      placeholder="e.g. WH-MAIN"
                    />
                    <button
                      onClick={() => openSession.mutate()}
                      disabled={openSession.isPending}
                      className="w-full flex items-center justify-center gap-2 rounded-lg bg-[var(--text-brand)] text-white text-sm font-medium py-2.5 hover:opacity-90 disabled:opacity-50"
                    >
                      {openSession.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <ScanLine className="w-4 h-4" />}
                      Open Session
                    </button>
                  </>
                ) : (
                  <div className="space-y-3">
                    <div className="flex items-center justify-between rounded-lg bg-[var(--surface-sunken)] px-3 py-2">
                      <span className="text-xs text-[var(--text-secondary)]">Session</span>
                      <span className="text-xs font-medium text-[var(--text-primary)]">{activeSession.id?.slice(0, 8)}</span>
                    </div>
                    <div className="flex items-center justify-between rounded-lg bg-[var(--surface-sunken)] px-3 py-2">
                      <span className="text-xs text-[var(--text-secondary)]">Type</span>
                      <EnterpriseStatusBadge status={activeSession.sessionType} label={activeSession.sessionType} />
                    </div>
                    <div className="flex items-center justify-between rounded-lg bg-[var(--surface-sunken)] px-3 py-2">
                      <span className="text-xs text-[var(--text-secondary)]">Location</span>
                      <span className="text-xs font-medium text-[var(--text-primary)]">{activeSession.locationId}</span>
                    </div>
                    <button
                      onClick={() => complete.mutate()}
                      disabled={complete.isPending}
                      className="w-full flex items-center justify-center gap-2 rounded-lg border border-[var(--border-default)] text-sm font-medium py-2.5 hover:bg-[var(--interactive-hover)] disabled:opacity-50"
                    >
                      {complete.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Check className="w-4 h-4" />}
                      Complete Session
                    </button>
                  </div>
                )}
              </div>

              {/* Retag */}
              <div className="rounded-xl border border-[var(--border-default)] bg-[var(--surface-base)] p-5">
                <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-4 flex items-center gap-2">
                  <Tag className="w-4 h-4 text-[var(--text-brand)]" /> Retag EPC
                </h3>
                <input
                  value={retagForm.oldEpc}
                  onChange={(e) => setRetagForm((f) => ({ ...f, oldEpc: e.target.value }))}
                  className="w-full rounded-lg border border-[var(--border-default)] px-3 py-2 text-sm mb-2 focus:outline-none focus:ring-2 focus:ring-[var(--border-focus)]"
                  placeholder="Old EPC"
                />
                <input
                  value={retagForm.newEpc}
                  onChange={(e) => setRetagForm((f) => ({ ...f, newEpc: e.target.value }))}
                  className="w-full rounded-lg border border-[var(--border-default)] px-3 py-2 text-sm mb-3 focus:outline-none focus:ring-2 focus:ring-[var(--border-focus)]"
                  placeholder="New EPC"
                />
                <button
                  onClick={() => retag.mutate()}
                  disabled={retag.isPending || !retagForm.oldEpc || !retagForm.newEpc}
                  className="w-full flex items-center justify-center gap-2 rounded-lg border border-[var(--border-default)] text-sm font-medium py-2.5 hover:bg-[var(--interactive-hover)] disabled:opacity-50"
                >
                  {retag.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <RefreshCw className="w-4 h-4" />}
                  Retag
                </button>
              </div>
            </div>

            {/* EPC ingestion */}
            <div className="lg:col-span-2 space-y-6">
              <div className="rounded-xl border border-[var(--border-default)] bg-[var(--surface-base)] p-5">
                <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-4 flex items-center gap-2">
                  <ScanLine className="w-4 h-4 text-[var(--text-brand)]" /> EPC Ingestion
                </h3>
                {!activeSession ? (
                  <div className="flex flex-col items-center justify-center py-12 text-center">
                    <ScanLine className="w-12 h-12 text-[var(--text-tertiary)] mb-3" />
                    <p className="text-sm text-[var(--text-secondary)]">Open a session to start scanning EPCs</p>
                  </div>
                ) : (
                  <>
                    <div className="flex gap-2 mb-4">
                      <input
                        value={epcInput}
                        onChange={(e) => setEpcInput(e.target.value)}
                        onKeyDown={(e) => e.key === 'Enter' && addEpc()}
                        className="flex-1 rounded-lg border border-[var(--border-default)] px-3 py-2 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-[var(--border-focus)]"
                        placeholder="Scan or paste EPC, press Enter"
                      />
                      <button
                        onClick={addEpc}
                        className="flex items-center gap-1.5 rounded-lg border border-[var(--border-default)] px-3 py-2 text-sm font-medium hover:bg-[var(--interactive-hover)]"
                      >
                        <Plus className="w-4 h-4" /> Add
                      </button>
                    </div>

                    {epcs.length > 0 && (
                      <div className="mb-4">
                        <div className="flex items-center justify-between mb-2">
                          <span className="text-xs font-medium text-[var(--text-secondary)]">{epcs.length} EPCs queued</span>
                          <button onClick={() => setEpcs([])} className="text-xs text-[var(--text-error)] hover:underline">Clear</button>
                        </div>
                        <div className="flex flex-wrap gap-1.5 max-h-32 overflow-y-auto">
                          {epcs.map((epc) => (
                            <span key={epc} className="inline-flex items-center gap-1 rounded-md bg-[var(--surface-brand)] px-2 py-1 text-xs font-mono text-[var(--text-brand)]">
                              {epc}
                              <button onClick={() => setEpcs((prev) => prev.filter((e) => e !== epc))} className="hover:text-[var(--text-error)]">
                                <X className="w-3 h-3" />
                              </button>
                            </span>
                          ))}
                        </div>
                      </div>
                    )}

                    <div className="flex gap-2">
                      <button
                        onClick={handleIngest}
                        disabled={ingest.isPending || epcs.length === 0}
                        className="flex-1 flex items-center justify-center gap-2 rounded-lg bg-[var(--text-brand)] text-white text-sm font-medium py-2.5 hover:opacity-90 disabled:opacity-50"
                      >
                        {ingest.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <ScanLine className="w-4 h-4" />}
                        Ingest EPCs
                      </button>
                      {sessionType === 'RECEIVING' && (
                        <button
                          onClick={() => receive.mutate()}
                          disabled={receive.isPending || epcs.length === 0}
                          className="flex items-center justify-center gap-2 rounded-lg border border-[var(--border-success)] text-[var(--text-success)] text-sm font-medium px-4 py-2.5 hover:bg-[var(--nexus-success-50)] disabled:opacity-50"
                        >
                          {receive.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <PackageCheck className="w-4 h-4" />}
                          Receive
                        </button>
                      )}
                      {sessionType === 'CYCLE_COUNT' && (
                        <button
                          onClick={() => cycleCount.mutate()}
                          disabled={cycleCount.isPending}
                          className="flex items-center justify-center gap-2 rounded-lg border border-[var(--border-info)] text-[var(--text-info)] text-sm font-medium px-4 py-2.5 hover:bg-[var(--nexus-info-50)] disabled:opacity-50"
                        >
                          {cycleCount.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <ClipboardList className="w-4 h-4" />}
                          Run Count
                        </button>
                      )}
                    </div>
                  </>
                )}
              </div>

              {/* Results */}
              {(scanResult || cycleResult) && (
                <div className="rounded-xl border border-[var(--border-default)] bg-[var(--surface-base)] p-5">
                  <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-4">Scan Results</h3>
                  {scanResult && (
                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                      <ResultStat label="Total Reads" value={scanResult.totalReads} color="text-[var(--text-primary)]" />
                      <ResultStat label="Accepted" value={scanResult.accepted} color="text-[var(--text-success)]" />
                      <ResultStat label="Duplicates" value={scanResult.duplicates} color="text-[var(--text-info)]" />
                      <ResultStat label="Rejected" value={scanResult.rejected} color={scanResult.rejected > 0 ? 'text-[var(--text-warning)]' : 'text-[var(--text-success)]'} />
                    </div>
                  )}
                  {cycleResult && (
                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                      <ResultStat label="Expected" value={cycleResult.expected} color="text-[var(--text-primary)]" />
                      <ResultStat label="Found" value={cycleResult.found} color="text-[var(--text-success)]" />
                      <ResultStat label="Missing" value={cycleResult.missing} color={cycleResult.missing > 0 ? 'text-[var(--text-error)]' : 'text-[var(--text-success)]'} />
                      <ResultStat label="New EPCs" value={cycleResult.newEpcs} color={cycleResult.newEpcs > 0 ? 'text-[var(--text-warning)]' : 'text-[var(--text-success)]'} />
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>
        )}

        {activeTab === 'INVENTORY' && (
          <div className="rounded-xl border border-[var(--border-default)] bg-[var(--surface-base)] overflow-hidden">
            <div className="flex items-center justify-between px-5 py-4 border-b border-[var(--border-default)]">
              <h3 className="text-sm font-semibold text-[var(--text-primary)]">Serialized Inventory</h3>
              <button
                onClick={() => qc.invalidateQueries({ queryKey: ['rfid-inventory'] })}
                className="flex items-center gap-1.5 text-xs text-[var(--text-secondary)] hover:text-[var(--text-primary)]"
              >
                <RefreshCw className="w-3.5 h-3.5" /> Refresh
              </button>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-xs text-[var(--text-tertiary)] border-b border-[var(--border-default)]">
                    <th className="px-5 py-3 font-medium">EPC</th>
                    <th className="px-5 py-3 font-medium">SKU</th>
                    <th className="px-5 py-3 font-medium">Location</th>
                    <th className="px-5 py-3 font-medium">Status</th>
                    <th className="px-5 py-3 font-medium">Mode</th>
                    <th className="px-5 py-3 font-medium">Last Seen</th>
                  </tr>
                </thead>
                <tbody>
                  {inventoryQuery.isLoading && (
                    <tr><td colSpan={6} className="px-5 py-8 text-center text-[var(--text-tertiary)]"><Loader2 className="w-5 h-5 animate-spin inline" /></td></tr>
                  )}
                  {!inventoryQuery.isLoading && inventory.length === 0 && (
                    <tr><td colSpan={6} className="px-5 py-8 text-center text-[var(--text-tertiary)]">No serialized inventory yet</td></tr>
                  )}
                  {inventory.map((item: SerializedInventory) => (
                    <tr key={item.id} className="border-b border-[var(--border-subtle)] hover:bg-[var(--interactive-hover)]">
                      <td className="px-5 py-3 font-mono text-xs text-[var(--text-brand)]">{item.epc}</td>
                      <td className="px-5 py-3 text-[var(--text-primary)]">{item.sku || '—'}</td>
                      <td className="px-5 py-3 text-[var(--text-secondary)]">{item.locationId || '—'}</td>
                      <td className="px-5 py-3">
                        <EnterpriseStatusBadge status={STATUS_MAP[item.status] || 'neutral'} label={item.status} />
                      </td>
                      <td className="px-5 py-3 text-xs text-[var(--text-secondary)]">{item.mode || '—'}</td>
                      <td className="px-5 py-3 text-xs text-[var(--text-tertiary)]">{item.lastSeenAt ? new Date(item.lastSeenAt).toLocaleString() : '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {activeTab === 'ANALYTICS' && (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <div className="rounded-xl border border-[var(--border-default)] bg-[var(--surface-base)] p-5">
              <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-3 flex items-center gap-2">
                <ShieldCheck className="w-4 h-4 text-[var(--text-brand)]" /> Traceability
              </h3>
              <p className="text-sm text-[var(--text-secondary)]">
                Every serialized item is tracked by EPC from receiving through fulfillment, giving you full
                item-level traceability that HotWax's batch-level tracking can't match.
              </p>
            </div>
            <div className="rounded-xl border border-[var(--border-default)] bg-[var(--surface-base)] p-5">
              <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-3 flex items-center gap-2">
                <BarChart3 className="w-4 h-4 text-[var(--text-brand)]" /> Accuracy
              </h3>
              <p className="text-sm text-[var(--text-secondary)]">
                RFID cycle counts reconcile physical stock against system records automatically, surfacing
                variance in real time for manager approval.
              </p>
            </div>
            <div className="rounded-xl border border-[var(--border-default)] bg-[var(--surface-base)] p-5">
              <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-3 flex items-center gap-2">
                <Boxes className="w-4 h-4 text-[var(--text-brand)]" /> Serialized Fulfillment
              </h3>
              <p className="text-sm text-[var(--text-secondary)]">
                Ship the exact serial number a customer ordered — no substitutions, no ambiguity, no
                fulfillment mismatches.
              </p>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

function ResultStat({ label, value, color }: { label: string; value: string | number; color: string }) {
  return (
    <div className="rounded-lg bg-[var(--surface-sunken)] p-3">
      <div className={clsx('text-xl font-semibold', color)}>{value}</div>
      <div className="text-xs text-[var(--text-tertiary)] mt-0.5">{label}</div>
    </div>
  )
}
