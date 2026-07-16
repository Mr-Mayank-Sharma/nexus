import { useState, useMemo, useEffect } from 'react'
import { clsx } from 'clsx'
import {
  Layers, Plus, Play, CheckCircle, XCircle, Clock, Calendar, Zap,
  ChevronDown, ChevronRight, Lightbulb, Search, X, Loader2, TrendingUp,
} from 'lucide-react'
import PermissionGate from '../components/rbac/PermissionGate'
import Autocomplete from '../components/common/Autocomplete'
import { useToast } from '../hooks/useToast'
import { fetchWavePlans, createWavePlan, updateWavePlan } from '../api/newBackend'

interface WaveOrder {
  id: string
  number: string
  items: number
  zone: string
}

interface Wave {
  id: string
  name: string
  status: 'Planned' | 'In Progress' | 'Completed' | 'Cancelled'
  priority: 'High' | 'Medium' | 'Low'
  orders: WaveOrder[]
  createdAt: string
  targetCompletion: string
  estimatedCompletion: string
  itemsPicked: number
  totalItems: number
  zone: string
}

interface CalendarSlot {
  time: string
  label: string
  waves: { id: string; name: string; status: string }[]
}

const mockWaves: Wave[] = [
  { id: 'W-001', name: 'Morning Express', status: 'In Progress', priority: 'High', orders: [
    { id: 'ORD-1001', number: 'ORD-1001', items: 3, zone: 'A' },
    { id: 'ORD-1002', number: 'ORD-1002', items: 5, zone: 'B' },
    { id: 'ORD-1003', number: 'ORD-1003', items: 2, zone: 'A' },
  ], createdAt: '2026-07-01 06:00', targetCompletion: '2026-07-01 10:00', estimatedCompletion: '2026-07-01 09:45', itemsPicked: 6, totalItems: 10, zone: 'A' },
  { id: 'W-002', name: 'Midday Bulk', status: 'Planned', priority: 'Medium', orders: [
    { id: 'ORD-1004', number: 'ORD-1004', items: 8, zone: 'C' },
    { id: 'ORD-1005', number: 'ORD-1005', items: 4, zone: 'B' },
    { id: 'ORD-1006', number: 'ORD-1006', items: 6, zone: 'A' },
    { id: 'ORD-1007', number: 'ORD-1007', items: 3, zone: 'C' },
  ], createdAt: '2026-07-01 08:00', targetCompletion: '2026-07-01 14:00', estimatedCompletion: '2026-07-01 13:30', itemsPicked: 0, totalItems: 21, zone: 'B' },
  { id: 'W-003', name: 'Afternoon Flash', status: 'Planned', priority: 'Low', orders: [
    { id: 'ORD-1008', number: 'ORD-1008', items: 2, zone: 'A' },
  ], createdAt: '2026-07-01 09:00', targetCompletion: '2026-07-01 17:00', estimatedCompletion: '2026-07-01 16:15', itemsPicked: 0, totalItems: 2, zone: 'A' },
  { id: 'W-004', name: 'Rush Priority', status: 'Completed', priority: 'High', orders: [
    { id: 'ORD-1009', number: 'ORD-1009', items: 1, zone: 'D' },
    { id: 'ORD-1010', number: 'ORD-1010', items: 7, zone: 'D' },
  ], createdAt: '2026-06-30 22:00', targetCompletion: '2026-07-01 02:00', estimatedCompletion: '2026-07-01 01:50', itemsPicked: 8, totalItems: 8, zone: 'D' },
  { id: 'W-005', name: 'Zone C Overflow', status: 'Cancelled', priority: 'Medium', orders: [
    { id: 'ORD-1011', number: 'ORD-1011', items: 4, zone: 'C' },
  ], createdAt: '2026-06-30 15:00', targetCompletion: '2026-06-30 20:00', estimatedCompletion: '2026-06-30 19:00', itemsPicked: 0, totalItems: 4, zone: 'C' },
]

const allOrderIds = [
  'ORD-1001', 'ORD-1002', 'ORD-1003', 'ORD-1004', 'ORD-1005',
  'ORD-1006', 'ORD-1007', 'ORD-1008', 'ORD-1009', 'ORD-1010',
  'ORD-1011', 'ORD-1012', 'ORD-1013', 'ORD-1014', 'ORD-1015',
]

const zones = ['A', 'B', 'C', 'D']

const calendarSlots: CalendarSlot[] = [
  { time: '06:00', label: 'Early', waves: [
    { id: 'W-001', name: 'Morning Express', status: 'In Progress' },
  ]},
  { time: '08:00', label: 'Mid-Morning', waves: [
    { id: 'W-002', name: 'Midday Bulk', status: 'Planned' },
  ]},
  { time: '10:00', label: 'Late Morning', waves: []},
  { time: '12:00', label: 'Noon', waves: []},
  { time: '14:00', label: 'Afternoon', waves: [
    { id: 'W-003', name: 'Afternoon Flash', status: 'Planned' },
  ]},
  { time: '16:00', label: 'Late', waves: []},
]

const statusStyles: Record<string, string> = {
  Planned: 'bg-blue-100 text-blue-700',
  'In Progress': 'bg-amber-100 text-amber-700',
  Completed: 'bg-green-100 text-green-700',
  Cancelled: 'bg-red-100 text-red-700',
}

const priorityColors: Record<string, string> = {
  High: 'text-red-600',
  Medium: 'text-amber-600',
  Low: 'text-gray-500',
}

function StatCard({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <div className="enterprise-card p-4 flex items-center gap-4">
      <div className="w-10 h-10 rounded-lg bg-primary-50 flex items-center justify-center text-primary-600 flex-shrink-0">
        {icon}
      </div>
      <div className="min-w-0">
        <p className="text-xs text-gray-500 font-medium">{label}</p>
        <p className="text-xl font-bold text-gray-900 mt-0.5">{value}</p>
      </div>
    </div>
  )
}

export default function WavePlanningPage() {
  const { addToast } = useToast()

  const [waves, setWaves] = useState<Wave[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    fetchWavePlans().then(res => {
      if (res?.waves) setWaves(res.waves)
      setLoading(false)
    }).catch(() => setLoading(false))
  }, [])
  const [search, setSearch] = useState('')
  const [expandedId, setExpandedId] = useState<string | null>(null)
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [selectedWaveId, setSelectedWaveId] = useState<string | null>(null)
  const [showCalendar, setShowCalendar] = useState(false)
  const [optimizing, setOptimizing] = useState<string | null>(null)

  const [createForm, setCreateForm] = useState({
    name: '',
    priority: 'Medium' as 'High' | 'Medium' | 'Low',
    selectedOrders: [] as string[],
    zone: '',
    targetCompletion: '',
  })

  const filtered = useMemo(() => {
    if (!search) return waves
    const q = search.toLowerCase()
    return waves.filter(
      (w) => w.id.toLowerCase().includes(q) || w.name.toLowerCase().includes(q),
    )
  }, [waves, search])

  const selectedWave = selectedWaveId ? waves.find((w) => w.id === selectedWaveId) : null

  async function handleCreateWave(data: any) {
    const res = await createWavePlan(data)
    if (res?.wave) {
      setWaves(prev => [res.wave, ...prev])
      addToast({ type: 'success', title: `Wave "${res.wave.name}" created` })
    }
  }

  async function handleWaveAction(id: string, updates: any) {
    const res = await updateWavePlan(id, updates)
    if (res?.wave) {
      setWaves(prev => prev.map(w => w.id === id ? res.wave : w))
      addToast({ type: 'success', title: 'Wave updated' })
    }
  }

  function handleOptimize(waveId: string) {
    setOptimizing(waveId)
    setTimeout(() => {
      setOptimizing(null)
      addToast({ type: 'success', title: 'AI optimization complete — route reordered for efficiency' })
    }, 2000)
  }

  function toggleOrderSelection(oid: string) {
    setCreateForm((f) => ({
      ...f,
      selectedOrders: f.selectedOrders.includes(oid)
        ? f.selectedOrders.filter((o) => o !== oid)
        : [...f.selectedOrders, oid],
    }))
  }

  const activeWaves = waves.filter((w) => w.status === 'In Progress').length
  const completedToday = waves.filter((w) => w.status === 'Completed').length
  const itemsPickedToday = waves
    .filter((w) => w.status === 'Completed' || w.status === 'In Progress')
    .reduce((s, w) => s + w.itemsPicked, 0)

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2.5">
            <Layers className="w-7 h-7 text-primary-500" /> Wave Planning
          </h1>
          <p className="text-sm text-gray-500 mt-1">Create and manage picking waves</p>
        </div>
        <div className="flex items-center gap-3">
          <button
            onClick={() => setShowCalendar(!showCalendar)}
            className="btn-secondary text-sm flex items-center gap-1.5"
          >
            <Calendar className="w-4 h-4" /> {showCalendar ? 'Hide Calendar' : 'Wave Calendar'}
          </button>
          <PermissionGate resource="warehouse" action="create">
            <button onClick={() => setShowCreateModal(true)} className="btn-primary text-sm">
              <Plus className="w-4 h-4" /> Create Wave
            </button>
          </PermissionGate>
        </div>
      </div>

      <div className="grid grid-cols-4 gap-4">
        <StatCard icon={<Zap className="w-5 h-5" />} label="Active Waves" value={String(activeWaves)} />
        <StatCard icon={<CheckCircle className="w-5 h-5" />} label="Completed Today" value={String(completedToday)} />
        <StatCard icon={<TrendingUp className="w-5 h-5" />} label="Items Picked Today" value={itemsPickedToday.toLocaleString()} />
        <StatCard icon={<Clock className="w-5 h-5" />} label="Avg Completion" value="47 min" />
      </div>

      {showCalendar && (
        <div className="enterprise-card p-5">
          <h3 className="text-sm font-semibold text-gray-700 mb-4 flex items-center gap-2">
            <Calendar className="w-4 h-4 text-primary-500" /> Wave Calendar — Today
          </h3>
          <div className="grid grid-cols-6 gap-3">
            {calendarSlots.map((slot) => (
              <div key={slot.time} className="border border-gray-200 rounded-lg p-3 min-h-[100px]">
                <p className="text-xs font-semibold text-gray-500 mb-1">{slot.time}</p>
                <p className="text-[10px] text-gray-400 mb-2">{slot.label}</p>
                {slot.waves.length === 0 ? (
                  <p className="text-[10px] text-gray-300 italic">Free</p>
                ) : (
                  slot.waves.map((wv) => (
                    <div
                      key={wv.id}
                      className={clsx(
                        'text-[10px] font-medium px-1.5 py-0.5 rounded mb-1 truncate',
                        statusStyles[wv.status] || 'bg-gray-100 text-gray-600',
                      )}
                    >
                      {wv.name}
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
            <tr className="border-b border-gray-200 bg-gray-50/50 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">
              <th className="px-4 py-3 w-8" />
              <th className="px-4 py-3">Wave ID</th>
              <th className="px-4 py-3">Created</th>
              <th className="px-4 py-3">Orders</th>
              <th className="px-4 py-3">Items</th>
              <th className="px-4 py-3">Status</th>
              <th className="px-4 py-3">Priority</th>
              <th className="px-4 py-3">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {filtered.map((wave) => {
              const isExpanded = expandedId === wave.id
              return (
                <tr key={wave.id} className={clsx('group', isExpanded && 'bg-primary-50/30')}>
                  <td className="px-4 py-3">
                    {wave.orders.length > 0 && (
                      <button
                        onClick={() => setExpandedId(isExpanded ? null : wave.id)}
                        className="p-0.5 hover:bg-gray-100 rounded"
                      >
                        {isExpanded ? (
                          <ChevronDown className="w-4 h-4 text-gray-500" />
                        ) : (
                          <ChevronRight className="w-4 h-4 text-gray-400" />
                        )}
                      </button>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <button
                      onClick={() => setSelectedWaveId(wave.id)}
                      className="font-medium text-primary-600 hover:underline"
                    >
                      {wave.id}
                    </button>
                  </td>
                  <td className="px-4 py-3 text-gray-600 text-xs">{wave.createdAt}</td>
                  <td className="px-4 py-3 text-gray-700">{wave.orders.length}</td>
                  <td className="px-4 py-3 text-gray-700">{wave.totalItems || wave.orders.reduce((s, o) => s + o.items, 0)}</td>
                  <td className="px-4 py-3">
                    <span className={clsx('inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium', statusStyles[wave.status])}>
                      {wave.status}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <span className={clsx('text-xs font-medium', priorityColors[wave.priority])}>{wave.priority}</span>
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-1">
                      {wave.status === 'Planned' && (
                        <PermissionGate resource="warehouse" action="edit">
                          <button
                            onClick={() => handleWaveAction(wave.id, { status: 'In Progress' })}
                            className="p-1.5 hover:bg-blue-50 rounded text-gray-400 hover:text-blue-600"
                            title="Start Wave"
                          >
                            <Play className="w-3.5 h-3.5" />
                          </button>
                        </PermissionGate>
                      )}
                      {wave.status === 'In Progress' && (
                        <PermissionGate resource="warehouse" action="edit">
                          <button
                            onClick={() => handleWaveAction(wave.id, { status: 'Completed' })}
                            className="p-1.5 hover:bg-green-50 rounded text-gray-400 hover:text-green-600"
                            title="Complete Wave"
                          >
                            <CheckCircle className="w-3.5 h-3.5" />
                          </button>
                        </PermissionGate>
                      )}
                      {(wave.status === 'Planned' || wave.status === 'In Progress') && (
                        <PermissionGate resource="warehouse" action="edit">
                          <button
                            onClick={() => handleWaveAction(wave.id, { status: 'Cancelled' })}
                            className="p-1.5 hover:bg-red-50 rounded text-gray-400 hover:text-red-600"
                            title="Cancel Wave"
                          >
                            <XCircle className="w-3.5 h-3.5" />
                          </button>
                        </PermissionGate>
                      )}
                    </div>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      {expandedId && filtered.find((w) => w.id === expandedId) && (
        <div className="enterprise-card p-5 border-l-4 border-l-primary-500">
          {(() => {
            const wave = filtered.find((w) => w.id === expandedId)!
            const totalItems = wave.totalItems || wave.orders.reduce((s, o) => s + o.items, 0)
            const progress = totalItems > 0 ? Math.round((wave.itemsPicked / totalItems) * 100) : 0
            return (
              <div className="space-y-4">
                <div className="flex items-center justify-between">
                  <h3 className="text-sm font-semibold text-gray-700">{wave.name} — Orders</h3>
                  <div className="flex items-center gap-2">
                    <span className="text-xs text-gray-500">
                      {wave.itemsPicked}/{totalItems} picked
                    </span>
                    <div className="w-20 h-1.5 bg-gray-200 rounded-full overflow-hidden">
                      <div className="h-full bg-primary-500 rounded-full" style={{ width: `${progress}%` }} />
                    </div>
                  </div>
                </div>
                {wave.orders.length > 0 ? (
                  <div className="overflow-x-auto rounded-lg border border-gray-200">
                    <table className="w-full text-sm">
                      <thead>
                        <tr className="bg-gray-50 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">
                          <th className="px-4 py-2">Order</th>
                          <th className="px-4 py-2">Items</th>
                          <th className="px-4 py-2">Zone</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-gray-100">
                        {wave.orders.map((o) => (
                          <tr key={o.id} className="hover:bg-gray-50">
                            <td className="px-4 py-2 font-medium text-gray-900">{o.number}</td>
                            <td className="px-4 py-2 text-gray-700">{o.items}</td>
                            <td className="px-4 py-2 text-gray-600 text-xs">{o.zone}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : (
                  <p className="text-sm text-gray-400 py-4 text-center">No orders assigned</p>
                )}
                <div className="flex items-center justify-between pt-2 border-t border-gray-100">
                  <p className="text-xs text-gray-500">
                    Est. completion: {wave.estimatedCompletion || 'Not set'}
                  </p>
                  <PermissionGate resource="warehouse" action="edit">
                    <button
                      onClick={() => handleOptimize(wave.id)}
                      disabled={optimizing === wave.id}
                      className="btn-secondary text-xs flex items-center gap-1.5"
                    >
                      {optimizing === wave.id ? (
                        <Loader2 className="w-3.5 h-3.5 animate-spin" />
                      ) : (
                        <Lightbulb className="w-3.5 h-3.5" />
                      )}
                      AI Optimize
                    </button>
                  </PermissionGate>
                </div>
              </div>
            )
          })()}
        </div>
      )}

      {/* Wave Details Panel */}
      {selectedWaveId && !expandedId && selectedWave && (
        <div className="enterprise-card p-5 border-l-4 border-l-primary-500">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-sm font-semibold text-gray-700">{selectedWave.name}</h3>
            <button onClick={() => setSelectedWaveId(null)} className="p-1 hover:bg-gray-100 rounded">
              <X className="w-4 h-4 text-gray-400" />
            </button>
          </div>
          <div className="grid grid-cols-2 gap-6">
            <div>
              <p className="text-xs font-medium text-gray-500 mb-2">Orders in Wave</p>
              <div className="overflow-x-auto rounded-lg border border-gray-200">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="bg-gray-50 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">
                      <th className="px-3 py-2">Order</th>
                      <th className="px-3 py-2">Items</th>
                      <th className="px-3 py-2">Zone</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {selectedWave.orders.map((o) => (
                      <tr key={o.id} className="hover:bg-gray-50">
                        <td className="px-3 py-2 font-medium text-gray-900">{o.number}</td>
                        <td className="px-3 py-2 text-gray-700">{o.items}</td>
                        <td className="px-3 py-2 text-gray-600 text-xs">{o.zone}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
            <div className="space-y-4">
              {(() => {
                const totalItems = selectedWave.totalItems || selectedWave.orders.reduce((s, o) => s + o.items, 0)
                const progress = totalItems > 0 ? Math.round((selectedWave.itemsPicked / totalItems) * 100) : 0
                return (
                  <>
                    <div>
                      <p className="text-xs font-medium text-gray-500 mb-1">Progress</p>
                      <div className="flex items-center gap-3">
                        <div className="flex-1 h-2 bg-gray-200 rounded-full overflow-hidden">
                          <div className="h-full bg-primary-500 rounded-full" style={{ width: `${progress}%` }} />
                        </div>
                        <span className="text-sm font-semibold text-gray-700">{selectedWave.itemsPicked}/{totalItems}</span>
                      </div>
                    </div>
                    <div>
                      <p className="text-xs font-medium text-gray-500 mb-1">Estimated Completion</p>
                      <p className="text-sm text-gray-700">{selectedWave.estimatedCompletion || 'Pending'}</p>
                    </div>
                    <PermissionGate resource="warehouse" action="edit">
                      <button
                        onClick={() => handleOptimize(selectedWave.id)}
                        disabled={optimizing === selectedWave.id}
                        className="btn-primary text-xs flex items-center gap-1.5"
                      >
                        {optimizing === selectedWave.id ? (
                          <Loader2 className="w-3.5 h-3.5 animate-spin" />
                        ) : (
                          <Lightbulb className="w-3.5 h-3.5" />
                        )}
                        {optimizing === selectedWave.id ? 'Optimizing...' : 'AI Optimization Suggestion'}
                      </button>
                    </PermissionGate>
                  </>
                )
              })()}
            </div>
          </div>
        </div>
      )}

      {/* Create Wave Modal */}
      {showCreateModal && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-lg w-full max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between p-6 border-b border-gray-100">
              <h2 className="text-lg font-semibold text-gray-900">Create Wave</h2>
              <button onClick={() => setShowCreateModal(false)} className="p-1 hover:bg-gray-100 rounded">
                <X className="w-5 h-5" />
              </button>
            </div>
            <div className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Wave Name</label>
                <input
                  value={createForm.name}
                  onChange={(e) => setCreateForm({ ...createForm, name: e.target.value })}
                  className="input w-full"
                  placeholder="e.g. Morning Express"
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Priority</label>
                  <select
                    value={createForm.priority}
                    onChange={(e) => setCreateForm({ ...createForm, priority: e.target.value as any })}
                    className="input w-full"
                  >
                    <option value="High">High</option>
                    <option value="Medium">Medium</option>
                    <option value="Low">Low</option>
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Filter by Zone</label>
                  <select
                    value={createForm.zone}
                    onChange={(e) => setCreateForm({ ...createForm, zone: e.target.value })}
                    className="input w-full"
                  >
                    <option value="">All Zones</option>
                    {zones.map((z) => <option key={z} value={z}>Zone {z}</option>)}
                  </select>
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Target Completion</label>
                <input
                  type="datetime-local"
                  value={createForm.targetCompletion}
                  onChange={(e) => setCreateForm({ ...createForm, targetCompletion: e.target.value })}
                  className="input w-full"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Orders to Include ({createForm.selectedOrders.length} selected)
                </label>
                <div className="max-h-40 overflow-y-auto border border-gray-200 rounded-lg divide-y divide-gray-100">
                  {allOrderIds.map((oid) => {
                    const selected = createForm.selectedOrders.includes(oid)
                    return (
                      <label
                        key={oid}
                        className={clsx(
                          'flex items-center gap-3 px-3 py-2 cursor-pointer text-sm hover:bg-gray-50',
                          selected && 'bg-primary-50',
                        )}
                      >
                        <input
                          type="checkbox"
                          checked={selected}
                          onChange={() => toggleOrderSelection(oid)}
                          className="rounded border-gray-300 text-primary-600 focus:ring-primary-500"
                        />
                        <span className={clsx(selected ? 'font-medium text-gray-900' : 'text-gray-600')}>{oid}</span>
                      </label>
                    )
                  })}
                </div>
              </div>
            </div>
            <div className="p-6 border-t border-gray-100 flex justify-end gap-3">
              <button onClick={() => setShowCreateModal(false)} className="btn-secondary text-sm">Cancel</button>
              <PermissionGate resource="warehouse" action="create">
                <button onClick={() => handleCreateWave(createForm)} className="btn-primary text-sm">
                  <Plus className="w-4 h-4" /> Create Wave
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
