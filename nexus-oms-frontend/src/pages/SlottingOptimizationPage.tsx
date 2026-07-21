import { useState, useEffect, useMemo } from 'react'
import { clsx } from 'clsx'
import {
  Warehouse, Search, Zap, BarChart3, Archive, ClipboardList,
  Target, Plus, X, Loader2, Package, ArrowRight,
  Edit2, Trash2, MoveVertical, ShieldCheck,
} from 'lucide-react'
import EnterpriseBreadcrumbs from '../components/enterprise/EnterpriseBreadcrumbs'
import EnterpriseKPICard from '../components/enterprise/EnterpriseKPICard'
import PermissionGate from '../components/rbac/PermissionGate'
import { useToast } from '../hooks/useToast'
import * as slottingApi from '../api/slotting'

interface Assignment {
  id: string
  sku: string
  productName: string
  currentBin: string
  zone: string
  velocityClass: 'A' | 'B' | 'C' | 'D'
  pickFrequency: number
  slottingScore: number
  lastPicked: string
}

interface SlottingRule {
  id: string
  name: string
  type: string
  targetZone: string
  targetBinClass: string
  priority: number
  isActive: boolean
  effectiveness: number
  criteria: string
}

interface VelocityData {
  class: 'A' | 'B' | 'C' | 'D'
  count: number
  avgPickFrequency: number
  recommendedBinClass: string
  percentage: number
}

interface SpaceData {
  overallUtilization: number
  totalBins: number
  usedBins: number
  zones: { name: string; usedBins: number; totalBins: number; utilization: number }[]
}

interface AuditEntry {
  id: string
  date: string
  sku: string
  product: string
  fromBin: string
  toBin: string
  reason: string
  movedQty: number
  performedBy: string
}

interface SlottingStats {
  overallScore: number
  totalSkusSlotted: number
  spaceUtilization: number
  optimizationsRun: number
}

type Tab = 'assignments' | 'velocity' | 'rules' | 'space' | 'audit'

const VELOCITY_CLASS_BADGE: Record<string, string> = {
  A: 'enterprise-badge-success',
  B: 'enterprise-badge-info',
  C: 'enterprise-badge-warning',
  D: 'enterprise-badge-danger',
}

const VELOCITY_BG: Record<string, string> = {
  A: 'bg-emerald-50 dark:bg-emerald-900/20',
  B: 'bg-[var(--nexus-primary-50)] dark:bg-[var(--nexus-primary-900)]/20',
  C: 'bg-[var(--nexus-warning-50)] dark:bg-[var(--nexus-warning-900)]/20',
  D: 'bg-[var(--nexus-error-50)] dark:bg-[var(--nexus-error-900)]/20',
}

const VELOCITY_TEXT: Record<string, string> = {
  A: 'text-emerald-700 dark:text-emerald-300',
  B: 'text-[var(--nexus-primary-700)] dark:text-[var(--nexus-primary-300)]',
  C: 'text-[var(--nexus-warning-700)] dark:text-[var(--nexus-warning-300)]',
  D: 'text-[var(--nexus-error-700)] dark:text-[var(--nexus-error-300)]',
}

const VELOCITY_BAR: Record<string, string> = {
  A: 'bg-emerald-500',
  B: 'bg-[var(--nexus-primary-50)]0',
  C: 'bg-[var(--nexus-warning-50)]0',
  D: 'bg-[var(--nexus-error-50)]0',
}

const WAREHOUSES = [
  { id: 'wh-main', name: 'Main Warehouse' },
  { id: 'wh-east', name: 'East DC' },
  { id: 'wh-west', name: 'West DC' },
]

const RULE_TYPES = ['Velocity-Based', 'Size-Based', 'Weight-Based', 'Affinity', 'Seasonal', 'Hazardous']

function ScoreBar({ score }: { score: number }) {
  const barColor = score > 80 ? 'bg-emerald-500' : score >= 60 ? 'bg-[var(--nexus-warning-50)]0' : 'bg-[var(--nexus-error-50)]0'
  return (
    <div className="flex items-center gap-2">
      <div className="flex-1 h-2 rounded-full bg-[var(--surface-muted)] bg-[var(--surface-muted)] overflow-hidden">
        <div className={clsx('h-full rounded-full transition-all duration-500', barColor)} style={{ width: `${Math.min(100, Math.max(0, score))}%` }} />
      </div>
      <span className="text-xs font-medium text-[var(--text-secondary)] w-8 text-right">{score}</span>
    </div>
  )
}

function SkeletonRows({ count = 5 }: { count?: number }) {
  return (
    <div className="space-y-3">
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] p-4">
          <div className="enterprise-skeleton h-4 w-3/4 mb-2" />
          <div className="enterprise-skeleton h-3 w-1/2" />
        </div>
      ))}
    </div>
  )
}

export default function SlottingOptimizationPage() {
  const { addToast } = useToast()

  const [warehouseId, setWarehouseId] = useState('wh-main')
  const [activeTab, setActiveTab] = useState<Tab>('assignments')
  const [search, setSearch] = useState('')

  const [assignments, setAssignments] = useState<Assignment[]>([])
  const [rules, setRules] = useState<SlottingRule[]>([])
  const [velocityData, setVelocityData] = useState<VelocityData[]>([])
  const [spaceData, setSpaceData] = useState<SpaceData | null>(null)
  const [auditLog, setAuditLog] = useState<AuditEntry[]>([])
  const [stats, setStats] = useState<SlottingStats>({ overallScore: 0, totalSkusSlotted: 0, spaceUtilization: 0, optimizationsRun: 0 })

  const [loadingAssignments, setLoadingAssignments] = useState(true)
  const [loadingRules, setLoadingRules] = useState(true)
  const [loadingVelocity, setLoadingVelocity] = useState(true)
  const [loadingSpace, setLoadingSpace] = useState(true)
  const [loadingAudit, setLoadingAudit] = useState(true)
  const [loadingStats, setLoadingStats] = useState(true)
  const [optimizing, setOptimizing] = useState(false)
  const [analyzing, setAnalyzing] = useState(false)

  const [selectedAssignment, setSelectedAssignment] = useState<Assignment | null>(null)
  const [showReassignModal, setShowReassignModal] = useState(false)
  const [reassignBin, setReassignBin] = useState('')
  const [reassignQty, setReassignQty] = useState(1)
  const [reassigning, setReassigning] = useState(false)

  const [showAddRuleModal, setShowAddRuleModal] = useState(false)
  const [ruleForm, setRuleForm] = useState({ name: '', type: 'Velocity-Based', criteria: '', targetZone: '', targetBinClass: '', priority: 1 })
  const [savingRule, setSavingRule] = useState(false)

  const [auditFrom, setAuditFrom] = useState('')
  const [auditTo, setAuditTo] = useState('')

  const fetchAssignments = async () => {
    setLoadingAssignments(true)
    try {
      const res = await slottingApi.getSlottingAssignments(warehouseId)
      setAssignments(Array.isArray(res.data) ? res.data : res.data?.content ?? [])
    } catch { setAssignments([]) } finally { setLoadingAssignments(false) }
  }

  const fetchRules = async () => {
    setLoadingRules(true)
    try {
      const res = await slottingApi.getSlottingRules(warehouseId)
      setRules(Array.isArray(res.data) ? res.data : res.data?.content ?? [])
    } catch { setRules([]) } finally { setLoadingRules(false) }
  }

  const fetchVelocity = async () => {
    setLoadingVelocity(true)
    try {
      const res = await slottingApi.getVelocityAnalysis(warehouseId)
      setVelocityData(Array.isArray(res.data) ? res.data : res.data?.velocityData ?? [])
    } catch { setVelocityData([]) } finally { setLoadingVelocity(false) }
  }

  const fetchSpace = async () => {
    setLoadingSpace(true)
    try {
      const res = await slottingApi.getSpaceUtilization(warehouseId)
      setSpaceData(res.data ?? null)
    } catch { setSpaceData(null) } finally { setLoadingSpace(false) }
  }

  const fetchAudit = async () => {
    setLoadingAudit(true)
    try {
      const res = await slottingApi.getSlottingAuditLog(warehouseId, auditFrom || undefined, auditTo || undefined)
      setAuditLog(Array.isArray(res.data) ? res.data : res.data?.content ?? [])
    } catch { setAuditLog([]) } finally { setLoadingAudit(false) }
  }

  const fetchStats = async () => {
    setLoadingStats(true)
    try {
      const res = await slottingApi.analyzeSlotting(warehouseId)
      if (res.data) {
        setStats({
          overallScore: res.data.overallScore ?? 0,
          totalSkusSlotted: res.data.totalSkusSlotted ?? 0,
          spaceUtilization: res.data.spaceUtilization ?? 0,
          optimizationsRun: res.data.optimizationsRun ?? 0,
        })
      }
    } catch { /* keep defaults */ } finally { setLoadingStats(false) }
  }

  useEffect(() => {
    fetchAssignments()
    fetchRules()
    fetchVelocity()
    fetchSpace()
    fetchStats()
  }, [warehouseId])

  useEffect(() => { fetchAudit() }, [warehouseId, auditFrom, auditTo])

  const filteredAssignments = useMemo(() => {
    if (!search) return assignments
    const q = search.toLowerCase()
    return assignments.filter((a) => a.sku.toLowerCase().includes(q) || a.productName.toLowerCase().includes(q) || a.zone.toLowerCase().includes(q))
  }, [assignments, search])

  const filteredRules = useMemo(() => {
    if (!search) return rules
    const q = search.toLowerCase()
    return rules.filter((r) => r.name.toLowerCase().includes(q) || r.type.toLowerCase().includes(q) || r.targetZone.toLowerCase().includes(q))
  }, [rules, search])

  const handleOptimize = async () => {
    setOptimizing(true)
    try {
      await slottingApi.optimizeSlotting(warehouseId)
      addToast({ type: 'success', title: 'Optimization complete', message: 'Slotting assignments have been optimized' })
      await Promise.all([fetchAssignments(), fetchStats(), fetchVelocity()])
    } catch {
      addToast({ type: 'error', title: 'Optimization failed', message: 'Could not run optimization' })
    } finally { setOptimizing(false) }
  }

  const handleAnalyze = async () => {
    setAnalyzing(true)
    try {
      await slottingApi.analyzeSlotting(warehouseId)
      addToast({ type: 'success', title: 'Analysis complete', message: 'Slotting analysis has been refreshed' })
      await Promise.all([fetchStats(), fetchVelocity(), fetchSpace()])
    } catch {
      addToast({ type: 'error', title: 'Analysis failed', message: 'Could not run analysis' })
    } finally { setAnalyzing(false) }
  }

  const handleReassign = async () => {
    if (!selectedAssignment || !reassignBin) return
    setReassigning(true)
    try {
      await slottingApi.reassignSku({ warehouseId, assignmentId: selectedAssignment.id, sku: selectedAssignment.sku, targetBin: reassignBin, quantity: reassignQty })
      addToast({ type: 'success', title: 'SKU reassigned', message: `${selectedAssignment.sku} moved to ${reassignBin}` })
      setShowReassignModal(false)
      setSelectedAssignment(null)
      await fetchAssignments()
    } catch {
      addToast({ type: 'error', title: 'Reassignment failed', message: 'Could not reassign SKU' })
    } finally { setReassigning(false) }
  }

  const handleAddRule = async () => {
    if (!ruleForm.name || !ruleForm.targetZone) {
      addToast({ type: 'warning', title: 'Required fields', message: 'Rule name and target zone are required' })
      return
    }
    setSavingRule(true)
    try {
      await slottingApi.createSlottingRule({ warehouseId, ...ruleForm, isActive: true })
      addToast({ type: 'success', title: 'Rule created', message: `"${ruleForm.name}" has been added` })
      setShowAddRuleModal(false)
      setRuleForm({ name: '', type: 'Velocity-Based', criteria: '', targetZone: '', targetBinClass: '', priority: 1 })
      await fetchRules()
    } catch {
      addToast({ type: 'error', title: 'Failed to create rule', message: 'Could not create slotting rule' })
    } finally { setSavingRule(false) }
  }

  const handleToggleRule = async (rule: SlottingRule) => {
    try {
      await slottingApi.toggleSlottingRule(rule.id, !rule.isActive)
      setRules((prev) => prev.map((r) => (r.id === rule.id ? { ...r, isActive: !r.isActive } : r)))
      addToast({ type: 'success', title: rule.isActive ? 'Rule deactivated' : 'Rule activated' })
    } catch {
      addToast({ type: 'error', title: 'Failed to toggle rule' })
    }
  }

  const scoreColor = stats.overallScore > 80 ? 'success' : stats.overallScore >= 60 ? 'warning' : 'error'

  const targetBinOptions = useMemo(() => {
    const wh = WAREHOUSES.find((w) => w.id === warehouseId)
    if (wh?.name.includes('East')) return ['E-A1', 'E-A2', 'E-B1', 'E-B2', 'E-C1', 'E-C2', 'E-D1', 'E-D2']
    if (wh?.name.includes('West')) return ['W-A1', 'W-A2', 'W-B1', 'W-B2', 'W-C1', 'W-C2', 'W-D1', 'W-D2']
    return ['A-01-01', 'A-01-02', 'A-02-01', 'B-01-01', 'B-01-02', 'C-01-01', 'C-01-02', 'D-01-01']
  }, [warehouseId])

  const tabs: { key: Tab; label: string; icon: typeof Package; count?: number }[] = [
    { key: 'assignments', label: 'Assignments', icon: Package, count: assignments.length },
    { key: 'velocity', label: 'Velocity Analysis', icon: BarChart3 },
    { key: 'rules', label: 'Rules', icon: ShieldCheck, count: rules.length },
    { key: 'space', label: 'Space Utilization', icon: Archive },
    { key: 'audit', label: 'Audit Log', icon: ClipboardList, count: auditLog.length },
  ]

  return (
    <div className="space-y-6">
      <EnterpriseBreadcrumbs crumbs={[{ label: 'Home', path: '/' }, { label: 'Warehouse', path: '/warehouse' }, { label: 'Slotting Optimization' }]} />

      {/* Page Header */}
      <div className="enterprise-page-header">
        <div className="flex items-center gap-4">
          <div className="w-12 h-12 rounded-2xl bg-violet-50 dark:bg-violet-900/20 flex items-center justify-center">
            <Warehouse className="w-6 h-6 text-violet-600" />
          </div>
          <div>
            <h1>Slotting Optimization</h1>
            <p>AI-powered bin assignment optimization for maximum picking efficiency</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <button onClick={handleAnalyze} disabled={analyzing} className="enterprise-btn enterprise-btn-secondary enterprise-btn-sm">
            {analyzing ? <Loader2 className="w-4 h-4 animate-spin" /> : <BarChart3 className="w-4 h-4" />}
            Analyze
          </button>
          <PermissionGate resource="warehouse" action="edit">
            <button onClick={handleOptimize} disabled={optimizing} className="enterprise-btn enterprise-btn-primary enterprise-btn-sm">
              {optimizing ? <Loader2 className="w-4 h-4 animate-spin" /> : <Zap className="w-4 h-4" />}
              Run Optimization
            </button>
          </PermissionGate>
        </div>
      </div>

      {/* KPI Cards */}
      <div className="enterprise-kpi-grid">
        <EnterpriseKPICard title="Overall Slotting Score" value={`${stats.overallScore}%`} icon={<Target className="w-5 h-5" />} color={scoreColor} loading={loadingStats} trend={stats.overallScore > 80 ? 'up' : stats.overallScore >= 60 ? 'neutral' : 'down'} trendValue={stats.overallScore > 80 ? 'Excellent' : stats.overallScore >= 60 ? 'Needs improvement' : 'Critical'} />
        <EnterpriseKPICard title="Total SKUs Slotted" value={stats.totalSkusSlotted.toLocaleString()} icon={<Package className="w-5 h-5" />} color="primary" loading={loadingStats} trend="neutral" trendValue="Across all zones" />
        <EnterpriseKPICard title="Space Utilization" value={`${stats.spaceUtilization}%`} icon={<Archive className="w-5 h-5" />} color={stats.spaceUtilization > 85 ? 'warning' : 'success'} loading={loadingStats} trend={stats.spaceUtilization > 85 ? 'up' : 'neutral'} trendValue={stats.spaceUtilization > 85 ? 'Near capacity' : 'Healthy level'} />
        <EnterpriseKPICard title="Optimizations Run" value={stats.optimizationsRun} icon={<Zap className="w-5 h-5" />} color="ai" loading={loadingStats} trend="neutral" trendValue="Total runs" />
      </div>

      {/* Toolbar */}
      <div className="enterprise-toolbar">
        <div className="enterprise-toolbar-left">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-[var(--text-tertiary)]" />
            <input type="text" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search SKU, product, or zone..." className="enterprise-input pl-9 w-72" />
          </div>
          <select value={warehouseId} onChange={(e) => setWarehouseId(e.target.value)} className="enterprise-select w-48">
            {WAREHOUSES.map((wh) => (<option key={wh.id} value={wh.id}>{wh.name}</option>))}
          </select>
        </div>
        <div className="enterprise-toolbar-right">
          <span className="text-xs text-[var(--text-tertiary)]">{filteredAssignments.length} assignments</span>
        </div>
      </div>

      {/* Tabs */}
      <div className="enterprise-tabs">
        {tabs.map((tab) => {
          const Icon = tab.icon
          return (
            <button key={tab.key} onClick={() => setActiveTab(tab.key)} className={clsx('enterprise-tab', activeTab === tab.key && 'enterprise-tab-active')}>
              <Icon className="w-4 h-4" />
              {tab.label}
              {tab.count !== undefined && tab.count > 0 && (
                <span className="ml-1.5 px-1.5 py-0.5 text-[10px] font-bold bg-[var(--surface-muted)] text-[var(--text-secondary)] rounded-full">{tab.count}</span>
              )}
            </button>
          )
        })}
      </div>

      {/* ===== Assignments Tab ===== */}
      {activeTab === 'assignments' && (
        <div className="enterprise-card">
          {loadingAssignments ? (
            <SkeletonRows count={8} />
          ) : filteredAssignments.length === 0 ? (
            <div className="enterprise-empty-state py-16">
              <Package className="w-12 h-12 text-[var(--text-tertiary)] dark:text-[var(--text-secondary)]" />
              <h3>No assignments found</h3>
              <p>{search ? 'No assignments match your search criteria' : 'Run an optimization to generate slotting assignments'}</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="enterprise-table">
                <thead>
                  <tr>
                    <th>SKU</th>
                    <th>Product Name</th>
                    <th>Current Bin</th>
                    <th>Zone</th>
                    <th>Velocity Class</th>
                    <th>Pick Frequency</th>
                    <th>Slotting Score</th>
                    <th>Last Picked</th>
                    <th className="w-12"></th>
                  </tr>
                </thead>
                <tbody>
                  {filteredAssignments.map((a) => (
                    <tr key={a.id} className="cursor-pointer hover:bg-[var(--surface-sunken)] hover:bg-[var(--surface-base)]/50 transition-colors" onClick={() => { setSelectedAssignment(a); setReassignBin(''); setReassignQty(1); setShowReassignModal(true) }}>
                      <td><span className="font-medium text-[var(--text-primary)]">{a.sku}</span></td>
                      <td><span className="text-[var(--text-secondary)]">{a.productName}</span></td>
                      <td><span className="font-mono text-xs bg-[var(--surface-muted)] px-2 py-1 rounded">{a.currentBin}</span></td>
                      <td><span className="enterprise-badge-neutral">{a.zone}</span></td>
                      <td><span className={VELOCITY_CLASS_BADGE[a.velocityClass]}>{a.velocityClass}</span></td>
                      <td><span className="text-[var(--text-primary)]">{a.pickFrequency.toLocaleString()}/day</span></td>
                      <td className="min-w-[140px]"><ScoreBar score={a.slottingScore} /></td>
                      <td><span className="text-xs text-[var(--text-tertiary)]">{a.lastPicked ? new Date(a.lastPicked).toLocaleDateString() : 'Never'}</span></td>
                      <td>
                        <button onClick={(e) => { e.stopPropagation(); setSelectedAssignment(a); setReassignBin(''); setReassignQty(1); setShowReassignModal(true) }} className="p-1.5 rounded-lg hover:bg-[var(--surface-muted)] dark:hover:bg-[var(--surface-muted)] text-[var(--text-tertiary)] hover:text-[var(--text-primary)] transition-colors" title="Reassign">
                          <Edit2 className="w-4 h-4" />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* ===== Velocity Analysis Tab ===== */}
      {activeTab === 'velocity' && (
        <div className="space-y-6">
          {loadingVelocity ? (
            <SkeletonRows count={4} />
          ) : velocityData.length === 0 ? (
            <div className="enterprise-empty-state py-16">
              <BarChart3 className="w-12 h-12 text-[var(--text-tertiary)] dark:text-[var(--text-secondary)]" />
              <h3>No velocity data</h3>
              <p>Run an analysis to generate velocity class data</p>
            </div>
          ) : (
            <>
              <div className="enterprise-card p-6">
                <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-4">Velocity Distribution</h3>
                <div className="flex h-8 rounded-lg overflow-hidden bg-[var(--surface-muted)]">
                  {velocityData.map((v) => (
                    <div key={v.class} className={clsx('flex items-center justify-center text-xs font-bold text-white transition-all duration-700', VELOCITY_BAR[v.class])} style={{ width: `${v.percentage}%` }} title={`Class ${v.class}: ${v.count} SKUs (${v.percentage}%)`}>
                      {v.percentage >= 10 && `${v.class} ${v.percentage}%`}
                    </div>
                  ))}
                </div>
                <div className="flex gap-4 mt-3">
                  {velocityData.map((v) => (
                    <div key={v.class} className="flex items-center gap-1.5 text-xs text-[var(--text-secondary)]">
                      <div className={clsx('w-3 h-3 rounded', VELOCITY_BAR[v.class])} />
                      Class {v.class}
                    </div>
                  ))}
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                {velocityData.map((v) => (
                  <div key={v.class} className={clsx('enterprise-card p-5 border transition-all hover:shadow-sm', VELOCITY_BG[v.class])}>
                    <div className="flex items-center justify-between mb-3">
                      <span className={clsx('text-2xl font-bold', VELOCITY_TEXT[v.class])}>Class {v.class}</span>
                      <span className={clsx(VELOCITY_CLASS_BADGE[v.class], 'text-lg font-bold')}>{v.count}</span>
                    </div>
                    <div className="space-y-2 text-sm">
                      <div className="flex justify-between"><span className="text-[var(--text-secondary)]">SKUs</span><span className="font-medium text-[var(--text-primary)]">{v.count}</span></div>
                      <div className="flex justify-between"><span className="text-[var(--text-secondary)]">Avg Pick Freq</span><span className="font-medium text-[var(--text-primary)]">{v.avgPickFrequency.toLocaleString()}/day</span></div>
                      <div className="flex justify-between"><span className="text-[var(--text-secondary)]">Recommended Bin</span><span className="font-medium text-[var(--text-primary)]">{v.recommendedBinClass}</span></div>
                      <div className="flex justify-between"><span className="text-[var(--text-secondary)]">Share</span><span className="font-medium text-[var(--text-primary)]">{v.percentage}%</span></div>
                    </div>
                    <div className="mt-3 pt-3 border-t border-[var(--border-default)]/50 border-[var(--border-default)]/50">
                      <div className="h-2 rounded-full bg-[var(--surface-muted)]/50 bg-[var(--surface-muted)]/50 overflow-hidden">
                        <div className={clsx('h-full rounded-full transition-all duration-700', VELOCITY_BAR[v.class])} style={{ width: `${v.percentage}%` }} />
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </>
          )}
        </div>
      )}

      {/* ===== Rules Tab ===== */}
      {activeTab === 'rules' && (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-semibold text-[var(--text-primary)]">Slotting Rules ({rules.length})</h3>
            <PermissionGate resource="warehouse" action="edit">
              <button onClick={() => setShowAddRuleModal(true)} className="enterprise-btn enterprise-btn-primary enterprise-btn-sm">
                <Plus className="w-4 h-4" /> Add Rule
              </button>
            </PermissionGate>
          </div>
          {loadingRules ? (
            <SkeletonRows count={5} />
          ) : filteredRules.length === 0 ? (
            <div className="enterprise-empty-state py-16">
              <ShieldCheck className="w-12 h-12 text-[var(--text-tertiary)] dark:text-[var(--text-secondary)]" />
              <h3>No rules configured</h3>
              <p>Add slotting rules to automate bin assignments</p>
            </div>
          ) : (
            <div className="enterprise-card">
              <div className="overflow-x-auto">
                <table className="enterprise-table">
                  <thead>
                    <tr>
                      <th>Rule Name</th>
                      <th>Type</th>
                      <th>Target Zone</th>
                      <th>Target Bin Class</th>
                      <th>Priority</th>
                      <th>Status</th>
                      <th>Effectiveness</th>
                      <th className="w-24"></th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredRules.map((rule) => (
                      <tr key={rule.id}>
                        <td><span className="font-medium text-[var(--text-primary)]">{rule.name}</span></td>
                        <td><span className="enterprise-badge-info">{rule.type}</span></td>
                        <td><span className="text-[var(--text-secondary)]">{rule.targetZone}</span></td>
                        <td><span className="font-mono text-xs bg-[var(--surface-muted)] px-2 py-1 rounded">{rule.targetBinClass || '—'}</span></td>
                        <td><span className="text-[var(--text-primary)] font-medium">{rule.priority}</span></td>
                        <td>
                          <button onClick={() => handleToggleRule(rule)} className={clsx('relative inline-flex h-6 w-11 items-center rounded-full transition-colors duration-200 focus:outline-none focus:ring-2 focus:ring-violet-500 focus:ring-offset-2', rule.isActive ? 'bg-violet-600' : 'bg-[var(--surface-muted)] bg-[var(--surface-muted)]')}>
                            <span className={clsx('inline-block h-4 w-4 transform rounded-full bg-white transition-transform duration-200', rule.isActive ? 'translate-x-6' : 'translate-x-1')} />
                          </button>
                        </td>
                        <td>
                          <div className="flex items-center gap-2">
                            <div className="flex-1 h-2 rounded-full bg-[var(--surface-muted)] bg-[var(--surface-muted)] overflow-hidden max-w-[80px]">
                              <div className={clsx('h-full rounded-full', rule.effectiveness > 80 ? 'bg-emerald-500' : rule.effectiveness > 60 ? 'bg-[var(--nexus-warning-50)]0' : 'bg-[var(--nexus-error-50)]0')} style={{ width: `${rule.effectiveness}%` }} />
                            </div>
                            <span className="text-xs font-medium text-[var(--text-secondary)]">{rule.effectiveness}%</span>
                          </div>
                        </td>
                        <td>
                          <div className="flex items-center gap-1">
                            <button className="p-1.5 rounded-lg hover:bg-[var(--surface-muted)] dark:hover:bg-[var(--surface-muted)] text-[var(--text-tertiary)] hover:text-[var(--text-primary)] transition-colors"><Edit2 className="w-3.5 h-3.5" /></button>
                            <button className="p-1.5 rounded-lg hover:bg-[var(--nexus-error-50)] dark:hover:bg-[var(--nexus-error-900)]/20 text-[var(--text-tertiary)] hover:text-[var(--nexus-error-500)] transition-colors"><Trash2 className="w-3.5 h-3.5" /></button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      )}

      {/* ===== Space Utilization Tab ===== */}
      {activeTab === 'space' && (
        <div className="space-y-6">
          {loadingSpace ? (
            <SkeletonRows count={4} />
          ) : !spaceData ? (
            <div className="enterprise-empty-state py-16">
              <Archive className="w-12 h-12 text-[var(--text-tertiary)] dark:text-[var(--text-secondary)]" />
              <h3>No space data available</h3>
              <p>Run an analysis to view space utilization</p>
            </div>
          ) : (
            <>
              <div className="enterprise-card p-6">
                <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-4">Overall Space Utilization</h3>
                <div className="flex items-center gap-6">
                  <div className="flex-1">
                    <div className="flex items-end justify-between mb-2">
                      <span className="text-sm text-[var(--text-secondary)]">{spaceData.usedBins.toLocaleString()} / {spaceData.totalBins.toLocaleString()} bins used</span>
                      <span className="text-2xl font-bold text-[var(--text-primary)]">{spaceData.overallUtilization}%</span>
                    </div>
                    <div className="h-4 rounded-full bg-[var(--surface-muted)] bg-[var(--surface-muted)] overflow-hidden">
                      <div className={clsx('h-full rounded-full transition-all duration-700', spaceData.overallUtilization > 90 ? 'bg-[var(--nexus-error-50)]0' : spaceData.overallUtilization > 75 ? 'bg-[var(--nexus-warning-50)]0' : 'bg-emerald-500')} style={{ width: `${spaceData.overallUtilization}%` }} />
                    </div>
                  </div>
                  <div className="flex-shrink-0 w-20 h-20 relative">
                    <svg className="w-20 h-20 -rotate-90" viewBox="0 0 36 36">
                      <path d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" fill="none" stroke="var(--border-color)" strokeWidth="3" />
                      <path d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" fill="none" stroke={spaceData.overallUtilization > 90 ? '#ef4444' : spaceData.overallUtilization > 75 ? '#f59e0b' : '#10b981'} strokeWidth="3" strokeDasharray={`${spaceData.overallUtilization}, 100`} strokeLinecap="round" />
                    </svg>
                    <div className="absolute inset-0 flex items-center justify-center">
                      <span className="text-sm font-bold text-[var(--text-primary)]">{spaceData.overallUtilization}%</span>
                    </div>
                  </div>
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {spaceData.zones.map((zone) => (
                  <div key={zone.name} className="enterprise-card p-5 hover:shadow-sm transition-shadow">
                    <div className="flex items-center justify-between mb-3">
                      <div className="flex items-center gap-2">
                        <div className="w-8 h-8 rounded-lg bg-violet-50 dark:bg-violet-900/20 flex items-center justify-center">
                          <span className="text-sm font-bold text-violet-600">{zone.name}</span>
                        </div>
                        <span className="text-sm font-semibold text-[var(--text-primary)]">Zone {zone.name}</span>
                      </div>
                      <span className={clsx('text-lg font-bold', zone.utilization > 90 ? 'text-[var(--nexus-error-600)] dark:text-[var(--nexus-error-400)]' : zone.utilization > 75 ? 'text-[var(--nexus-warning-600)] dark:text-[var(--nexus-warning-400)]' : 'text-emerald-600 dark:text-emerald-400')}>
                        {zone.utilization}%
                      </span>
                    </div>
                    <div className="space-y-2 text-sm">
                      <div className="flex justify-between"><span className="text-[var(--text-secondary)]">Used Bins</span><span className="font-medium text-[var(--text-primary)]">{zone.usedBins.toLocaleString()}</span></div>
                      <div className="flex justify-between"><span className="text-[var(--text-secondary)]">Total Bins</span><span className="font-medium text-[var(--text-primary)]">{zone.totalBins.toLocaleString()}</span></div>
                      <div className="flex justify-between"><span className="text-[var(--text-secondary)]">Available</span><span className="font-medium text-[var(--text-primary)]">{(zone.totalBins - zone.usedBins).toLocaleString()}</span></div>
                    </div>
                    <div className="mt-3 pt-3 border-t border-[var(--border-subtle)]">
                      <div className="h-2 rounded-full bg-[var(--surface-muted)] bg-[var(--surface-muted)] overflow-hidden">
                        <div className={clsx('h-full rounded-full transition-all duration-700', zone.utilization > 90 ? 'bg-[var(--nexus-error-50)]0' : zone.utilization > 75 ? 'bg-[var(--nexus-warning-50)]0' : 'bg-emerald-500')} style={{ width: `${zone.utilization}%` }} />
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </>
          )}
        </div>
      )}

      {/* ===== Audit Log Tab ===== */}
      {activeTab === 'audit' && (
        <div className="space-y-4">
          <div className="enterprise-card p-4">
            <div className="flex items-center gap-4">
              <div className="flex items-center gap-2">
                <label className="text-xs font-medium text-[var(--text-secondary)]">From</label>
                <input type="date" value={auditFrom} onChange={(e) => setAuditFrom(e.target.value)} className="enterprise-input w-40 text-sm" />
              </div>
              <div className="flex items-center gap-2">
                <label className="text-xs font-medium text-[var(--text-secondary)]">To</label>
                <input type="date" value={auditTo} onChange={(e) => setAuditTo(e.target.value)} className="enterprise-input w-40 text-sm" />
              </div>
              {(auditFrom || auditTo) && (
                <button onClick={() => { setAuditFrom(''); setAuditTo('') }} className="enterprise-btn enterprise-btn-secondary enterprise-btn-sm">
                  <X className="w-3.5 h-3.5" /> Clear
                </button>
              )}
            </div>
          </div>
          {loadingAudit ? (
            <SkeletonRows count={6} />
          ) : auditLog.length === 0 ? (
            <div className="enterprise-empty-state py-16">
              <ClipboardList className="w-12 h-12 text-[var(--text-tertiary)] dark:text-[var(--text-secondary)]" />
              <h3>No audit entries</h3>
              <p>Slotting changes will appear here</p>
            </div>
          ) : (
            <div className="enterprise-card">
              <div className="overflow-x-auto">
                <table className="enterprise-table">
                  <thead>
                    <tr>
                      <th>Date</th>
                      <th>SKU</th>
                      <th>Product</th>
                      <th>From Bin</th>
                      <th>To Bin</th>
                      <th>Reason</th>
                      <th>Moved Qty</th>
                      <th>Performed By</th>
                    </tr>
                  </thead>
                  <tbody>
                    {auditLog.map((entry) => (
                      <tr key={entry.id}>
                        <td><span className="text-xs text-[var(--text-secondary)]">{new Date(entry.date).toLocaleString()}</span></td>
                        <td><span className="font-medium text-[var(--text-primary)]">{entry.sku}</span></td>
                        <td><span className="text-[var(--text-secondary)]">{entry.product}</span></td>
                        <td><span className="font-mono text-xs bg-[var(--surface-muted)] px-2 py-1 rounded">{entry.fromBin}</span></td>
                        <td>
                          <div className="flex items-center gap-1.5">
                            <ArrowRight className="w-3.5 h-3.5 text-violet-500" />
                            <span className="font-mono text-xs bg-violet-50 dark:bg-violet-900/20 text-violet-700 dark:text-violet-300 px-2 py-1 rounded">{entry.toBin}</span>
                          </div>
                        </td>
                        <td><span className="text-sm text-[var(--text-secondary)]">{entry.reason}</span></td>
                        <td><span className="text-[var(--text-primary)]">{entry.movedQty}</span></td>
                        <td><span className="text-xs text-[var(--text-tertiary)]">{entry.performedBy}</span></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      )}

      {/* ===== Reassign Modal ===== */}
      {showReassignModal && selectedAssignment && (
        <div className="enterprise-modal-overlay" onClick={() => { setShowReassignModal(false); setSelectedAssignment(null) }}>
          <div className="enterprise-modal max-w-lg" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between p-6 border-b border-[var(--border-default)]">
              <div>
                <h2 className="text-lg font-semibold text-[var(--text-primary)]">Reassign SKU</h2>
                <p className="text-sm text-[var(--text-secondary)] mt-0.5">{selectedAssignment.sku} — {selectedAssignment.productName}</p>
              </div>
              <button onClick={() => { setShowReassignModal(false); setSelectedAssignment(null) }} className="p-2 rounded-lg hover:bg-[var(--surface-muted)] dark:hover:bg-[var(--surface-muted)] transition-colors">
                <X className="w-5 h-5 text-[var(--text-tertiary)]" />
              </button>
            </div>
            <div className="p-6 space-y-5">
              <div>
                <label className="block text-sm font-medium text-[var(--text-primary)] mb-1.5">Current Bin</label>
                <div className="flex items-center gap-3">
                  <span className="font-mono text-sm bg-[var(--surface-muted)] px-3 py-2 rounded-lg text-[var(--text-primary)]">{selectedAssignment.currentBin}</span>
                  <ArrowRight className="w-4 h-4 text-[var(--text-tertiary)]" />
                  <span className="text-sm text-[var(--text-secondary)]">Zone {selectedAssignment.zone}</span>
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-primary)] mb-1.5">Target Bin</label>
                <select value={reassignBin} onChange={(e) => setReassignBin(e.target.value)} className="enterprise-select w-full">
                  <option value="">Select target bin...</option>
                  {targetBinOptions.map((bin) => (<option key={bin} value={bin}>{bin}</option>))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-primary)] mb-1.5">Quantity</label>
                <input type="number" min={1} value={reassignQty} onChange={(e) => setReassignQty(Math.max(1, parseInt(e.target.value) || 1))} className="enterprise-input w-full" />
              </div>
              <div className="p-3 rounded-lg bg-[var(--nexus-primary-50)] dark:bg-[var(--nexus-primary-900)]/20 border border-[var(--nexus-primary-200)] dark:border-[var(--nexus-primary-800)]">
                <div className="flex items-start gap-2">
                  <Target className="w-4 h-4 text-[var(--nexus-primary-600)] dark:text-[var(--nexus-primary-400)] mt-0.5 flex-shrink-0" />
                  <div className="text-sm text-[var(--nexus-primary-700)] dark:text-[var(--nexus-primary-300)]">
                    <span className="font-medium">Current score: {selectedAssignment.slottingScore}</span>
                    <span className="mx-1.5">·</span>
                    <span>Velocity Class {selectedAssignment.velocityClass}</span>
                    <span className="mx-1.5">·</span>
                    <span>{selectedAssignment.pickFrequency.toLocaleString()} picks/day</span>
                  </div>
                </div>
              </div>
            </div>
            <div className="flex items-center justify-end gap-3 p-6 border-t border-[var(--border-default)]">
              <button onClick={() => { setShowReassignModal(false); setSelectedAssignment(null) }} className="enterprise-btn enterprise-btn-secondary">Cancel</button>
              <button onClick={handleReassign} disabled={!reassignBin || reassigning} className="enterprise-btn enterprise-btn-primary">
                {reassigning ? <Loader2 className="w-4 h-4 animate-spin" /> : <MoveVertical className="w-4 h-4" />}
                Confirm Reassignment
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ===== Add Rule Modal ===== */}
      {showAddRuleModal && (
        <div className="enterprise-modal-overlay" onClick={() => setShowAddRuleModal(false)}>
          <div className="enterprise-modal max-w-lg" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between p-6 border-b border-[var(--border-default)]">
              <div>
                <h2 className="text-lg font-semibold text-[var(--text-primary)]">Add Slotting Rule</h2>
                <p className="text-sm text-[var(--text-secondary)] mt-0.5">Define rules for automatic bin assignments</p>
              </div>
              <button onClick={() => setShowAddRuleModal(false)} className="p-2 rounded-lg hover:bg-[var(--surface-muted)] dark:hover:bg-[var(--surface-muted)] transition-colors">
                <X className="w-5 h-5 text-[var(--text-tertiary)]" />
              </button>
            </div>
            <div className="p-6 space-y-5">
              <div>
                <label className="block text-sm font-medium text-[var(--text-primary)] mb-1.5">Rule Name *</label>
                <input type="text" value={ruleForm.name} onChange={(e) => setRuleForm({ ...ruleForm, name: e.target.value })} placeholder="e.g., High-velocity near dock" className="enterprise-input w-full" />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-[var(--text-primary)] mb-1.5">Type</label>
                  <select value={ruleForm.type} onChange={(e) => setRuleForm({ ...ruleForm, type: e.target.value })} className="enterprise-select w-full">
                    {RULE_TYPES.map((t) => (<option key={t} value={t}>{t}</option>))}
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-primary)] mb-1.5">Priority</label>
                  <input type="number" min={1} max={100} value={ruleForm.priority} onChange={(e) => setRuleForm({ ...ruleForm, priority: Math.max(1, parseInt(e.target.value) || 1) })} className="enterprise-input w-full" />
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-primary)] mb-1.5">Criteria</label>
                <input type="text" value={ruleForm.criteria} onChange={(e) => setRuleForm({ ...ruleForm, criteria: e.target.value })} placeholder="e.g., velocityClass = A AND pickFrequency > 50" className="enterprise-input w-full" />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-[var(--text-primary)] mb-1.5">Target Zone *</label>
                  <input type="text" value={ruleForm.targetZone} onChange={(e) => setRuleForm({ ...ruleForm, targetZone: e.target.value })} placeholder="e.g., A" className="enterprise-input w-full" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-primary)] mb-1.5">Target Bin Class</label>
                  <input type="text" value={ruleForm.targetBinClass} onChange={(e) => setRuleForm({ ...ruleForm, targetBinClass: e.target.value })} placeholder="e.g., Prime" className="enterprise-input w-full" />
                </div>
              </div>
            </div>
            <div className="flex items-center justify-end gap-3 p-6 border-t border-[var(--border-default)]">
              <button onClick={() => setShowAddRuleModal(false)} className="enterprise-btn enterprise-btn-secondary">Cancel</button>
              <button onClick={handleAddRule} disabled={!ruleForm.name || !ruleForm.targetZone || savingRule} className="enterprise-btn enterprise-btn-primary">
                {savingRule ? <Loader2 className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />}
                Create Rule
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
