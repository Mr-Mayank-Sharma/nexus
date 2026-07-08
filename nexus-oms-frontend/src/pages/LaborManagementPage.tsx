import { useState, useEffect } from 'react'
import { clsx } from 'clsx'
import { fetchEmployees, fetchShifts, assignTask, fetchWavePlans } from '../api/newBackend'
import {
  Users, Play, Pause, LogOut, TrendingUp, Clock, UserCheck, Package,
  Truck, Gauge, BarChart3, Calendar, AlertTriangle, Plus, X,
} from 'lucide-react'
import Autocomplete from '../components/common/Autocomplete'
import { useToast } from '../hooks/useToast'

interface Employee {
  id: string
  name: string
  role: 'Picker' | 'Packer' | 'Loader'
  status: 'Active' | 'Break' | 'Off'
  currentTask: string
  efficiency: number
  hoursToday: number
  avatar: string
}

interface Shift {
  id: string
  employee: string
  role: string
  shiftStart: string
  shiftEnd: string
  breakTimes: string
  overtime: string
}

interface WaveAssign {
  waveId: string
  waveName: string
}

const mockEmployees: Employee[] = [
  { id: 'E-001', name: 'Alex M.', role: 'Picker', status: 'Active', currentTask: 'W-001 — Morning Express', efficiency: 98, hoursToday: 6.5, avatar: 'AM' },
  { id: 'E-002', name: 'Sarah K.', role: 'Picker', status: 'Active', currentTask: 'W-002 — Midday Bulk', efficiency: 104, hoursToday: 7.0, avatar: 'SK' },
  { id: 'E-003', name: 'James R.', role: 'Packer', status: 'Active', currentTask: 'Order #ORD-1004', efficiency: 95, hoursToday: 6.5, avatar: 'JR' },
  { id: 'E-004', name: 'Maria G.', role: 'Packer', status: 'Break', currentTask: 'Break (15 min)', efficiency: 88, hoursToday: 4.0, avatar: 'MG' },
  { id: 'E-005', name: 'Tom W.', role: 'Loader', status: 'Active', currentTask: 'Dock D1 — Loading', efficiency: 100, hoursToday: 6.0, avatar: 'TW' },
  { id: 'E-006', name: 'Lisa C.', role: 'Picker', status: 'Off', currentTask: '—', efficiency: 50, hoursToday: 0, avatar: 'LC' },
  { id: 'E-007', name: 'David R.', role: 'Picker', status: 'Active', currentTask: 'W-001 — Morning Express', efficiency: 92, hoursToday: 5.5, avatar: 'DR' },
  { id: 'E-008', name: 'Emily W.', role: 'Loader', status: 'Active', currentTask: 'Dock D2 — Staging', efficiency: 87, hoursToday: 7.5, avatar: 'EW' },
]

const mockShifts: Shift[] = [
  { id: 'S-001', employee: 'Alex M.', role: 'Picker', shiftStart: '06:00', shiftEnd: '14:00', breakTimes: '09:00-09:15, 12:00-12:30', overtime: '0.5h' },
  { id: 'S-002', employee: 'Sarah K.', role: 'Picker', shiftStart: '06:00', shiftEnd: '14:00', breakTimes: '09:30-09:45, 12:00-12:30', overtime: '1.0h' },
  { id: 'S-003', employee: 'James R.', role: 'Packer', shiftStart: '07:00', shiftEnd: '15:00', breakTimes: '10:00-10:15, 13:00-13:30', overtime: '0h' },
  { id: 'S-004', employee: 'Maria G.', role: 'Packer', shiftStart: '07:00', shiftEnd: '15:00', breakTimes: '10:30-10:45, 13:00-13:15', overtime: '0h' },
  { id: 'S-005', employee: 'Tom W.', role: 'Loader', shiftStart: '06:00', shiftEnd: '14:00', breakTimes: '09:00-09:15, 12:00-12:30', overtime: '1.5h' },
  { id: 'S-006', employee: 'Lisa C.', role: 'Picker', shiftStart: '—', shiftEnd: '—', breakTimes: '—', overtime: '0h' },
]

const mockWaves: WaveAssign[] = [
  { waveId: 'W-001', waveName: 'Morning Express' },
  { waveId: 'W-002', waveName: 'Midday Bulk' },
  { waveId: 'W-003', waveName: 'Afternoon Flash' },
]

const dayLabels = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

const barData = [92, 88, 95, 91, 97, 85, 78]

const roleColors: Record<string, string> = {
  Picker: 'bg-blue-100 text-blue-700',
  Packer: 'bg-purple-100 text-purple-700',
  Loader: 'bg-amber-100 text-amber-700',
}

const statusColors: Record<string, string> = {
  Active: 'bg-green-100 text-green-700',
  Break: 'bg-yellow-100 text-yellow-700',
  Off: 'bg-gray-100 text-gray-500',
}

function KpiCard({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
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

export default function LaborManagementPage() {
  const { addToast } = useToast()

  const [employees, setEmployees] = useState<any[]>([])
  const [shifts, setShifts] = useState<any[]>([])
  const [waves, setWaves] = useState<any[]>([])
  const [loading, setLoading] = useState(true)
  const [selectedAssignWave, setSelectedAssignWave] = useState<string | null>(null)

  useEffect(() => {
    Promise.all([
      fetchEmployees(),
      fetchShifts(),
      fetchWavePlans(),
    ]).then(([empRes, shiftRes, waveRes]) => {
      if (empRes?.employees) setEmployees(empRes.employees)
      if (shiftRes?.shifts) setShifts(shiftRes.shifts)
      if (waveRes?.waves) setWaves(waveRes.waves)
      setLoading(false)
    }).catch((err) => {
      addToast({ type: 'error', title: 'Failed to load labor data', description: err?.message })
      setLoading(false)
    })
  }, [addToast])
  const [assignEmployeeId, setAssignEmployeeId] = useState<string | null>(null)
  const [showAssignModal, setShowAssignModal] = useState(false)

  function handleStartBreak(id: string) {
    setEmployees(employees.map((e) =>
      e.id === id ? { ...e, status: 'Break', currentTask: 'Break (15 min)' } : e,
    ))
    addToast({ type: 'info', title: 'Break started' })
  }

  function handleEndBreak(id: string) {
    setEmployees(employees.map((e) =>
      e.id === id ? { ...e, status: 'Active', currentTask: 'Returned from break' } : e,
    ))
    addToast({ type: 'success', title: 'Break ended' })
  }

  function handleEndShift(id: string) {
    setEmployees(employees.map((e) =>
      e.id === id ? { ...e, status: 'Off', currentTask: '—', hoursToday: e.hoursToday } : e,
    ))
    addToast({ type: 'success', title: 'Shift ended' })
  }

  async function handleAssign(taskData: any) {
    const res = await assignTask(taskData)
    if (res?.task) {
      addToast({ type: 'success', title: 'Task assigned' })
    }
  }

  const displayShifts = shifts.length > 0 ? shifts : mockShifts
  const displayWaves = waves.length > 0 ? waves : mockWaves
  const activeWorkers = employees.filter((e) => e.status === 'Active').length
  const avgPickRate = employees.length > 0 ? Math.round(employees.reduce((s: number, e: any) => s + (e.efficiency || 0), 0) / employees.length) : 0
  const packEfficiency = Math.round(employees.filter((e: any) => e.role === 'Packer').reduce((s: number, e: any) => s + (e.efficiency || 0), 0) / Math.max(1, employees.filter((e: any) => e.role === 'Packer').length))
  const loadEfficiency = Math.round(employees.filter((e: any) => e.role === 'Loader').reduce((s: number, e: any) => s + (e.efficiency || 0), 0) / Math.max(1, employees.filter((e: any) => e.role === 'Loader').length))
  const laborUtil = Math.round((activeWorkers / employees.length) * 100)

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2.5">
            <Users className="w-7 h-7 text-primary-500" /> Labor Management
          </h1>
          <p className="text-sm text-gray-500 mt-1">Workforce tracking & efficiency</p>
        </div>
      </div>

      {/* KPIs */}
      <div className="grid grid-cols-5 gap-4">
        <KpiCard icon={<UserCheck className="w-5 h-5" />} label="Active Workers" value={String(activeWorkers)} />
        <KpiCard icon={<TrendingUp className="w-5 h-5" />} label="Lines Picked/hr" value="142" />
        <KpiCard icon={<Package className="w-5 h-5" />} label="Packing Efficiency" value="96%" />
        <KpiCard icon={<Truck className="w-5 h-5" />} label="Loading Efficiency" value="88%" />
        <KpiCard icon={<Gauge className="w-5 h-5" />} label="Labor Utilization" value={`${laborUtil}%`} />
      </div>

      {/* Staff Overview */}
      <div className="enterprise-card p-5">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-sm font-semibold text-gray-700 flex items-center gap-2">
            <Users className="w-4 h-4 text-primary-500" /> Staff Overview
          </h3>
          <button
            onClick={() => setShowAssignModal(true)}
            className="btn-primary text-xs flex items-center gap-1.5"
          >
            <Plus className="w-3.5 h-3.5" /> Assign to Wave
          </button>
        </div>
        <div className="grid grid-cols-4 gap-4">
          {employees.map((emp) => (
            <div key={emp.id} className="enterprise-card p-4 border border-gray-200 hover:shadow-sm transition-shadow">
              <div className="flex items-start justify-between mb-3">
                <div className="flex items-center gap-3">
                  <div className="w-9 h-9 rounded-full bg-primary-100 flex items-center justify-center text-xs font-bold text-primary-700">
                    {emp.avatar}
                  </div>
                  <div>
                    <p className="text-sm font-semibold text-gray-900">{emp.name}</p>
                    <span className={clsx('inline-flex items-center rounded-full px-1.5 py-0.5 text-[10px] font-medium', roleColors[emp.role])}>
                      {emp.role}
                    </span>
                  </div>
                </div>
                <span className={clsx('inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-medium', statusColors[emp.status])}>
                  {emp.status}
                </span>
              </div>
              <div className="space-y-1 text-xs text-gray-500 mb-3">
                <p><span className="font-medium text-gray-700">Task:</span> {emp.currentTask}</p>
                <p><span className="font-medium text-gray-700">Efficiency:</span> {emp.efficiency}%</p>
                <p><span className="font-medium text-gray-700">Hours:</span> {emp.hoursToday}h</p>
              </div>
              <div className="flex items-center gap-2">
                {emp.status === 'Active' && (
                  <button
                    onClick={() => handleStartBreak(emp.id)}
                    className="btn-secondary text-[10px] px-2 py-1 flex items-center gap-1"
                  >
                    <Pause className="w-3 h-3" /> Break
                  </button>
                )}
                {emp.status === 'Break' && (
                  <button
                    onClick={() => handleEndBreak(emp.id)}
                    className="btn-primary text-[10px] px-2 py-1 flex items-center gap-1"
                  >
                    <Play className="w-3 h-3" /> Resume
                  </button>
                )}
                {emp.status !== 'Off' && (
                  <button
                    onClick={() => handleEndShift(emp.id)}
                    className="btn-secondary text-[10px] px-2 py-1 flex items-center gap-1"
                  >
                    <LogOut className="w-3 h-3" /> End Shift
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Shift Schedule */}
      <div className="enterprise-card p-5">
        <h3 className="text-sm font-semibold text-gray-700 mb-4 flex items-center gap-2">
          <Calendar className="w-4 h-4 text-primary-500" /> Shift Schedule — Today
        </h3>
        <div className="overflow-x-auto rounded-lg border border-gray-200">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-gray-50 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">
                <th className="px-4 py-2.5">Employee</th>
                <th className="px-4 py-2.5">Role</th>
                <th className="px-4 py-2.5">Shift Start</th>
                <th className="px-4 py-2.5">Shift End</th>
                <th className="px-4 py-2.5">Break Times</th>
                <th className="px-4 py-2.5">Overtime</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {displayShifts.map((s) => (
                <tr key={s.id} className="hover:bg-gray-50">
                  <td className="px-4 py-2.5 font-medium text-gray-900">{s.employee}</td>
                  <td className="px-4 py-2.5">
                    <span className={clsx('inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium', roleColors[s.role])}>
                      {s.role}
                    </span>
                  </td>
                  <td className="px-4 py-2.5 text-gray-700">{s.shiftStart}</td>
                  <td className="px-4 py-2.5 text-gray-700">{s.shiftEnd}</td>
                  <td className="px-4 py-2.5 text-gray-500 text-xs">{s.breakTimes}</td>
                  <td className="px-4 py-2.5">
                    <span className={clsx(
                      'text-xs font-medium',
                      s.overtime !== '0h' ? 'text-red-600' : 'text-gray-500',
                    )}>
                      {s.overtime}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Performance Trend */}
      <div className="grid grid-cols-3 gap-6">
        <div className="enterprise-card p-5 col-span-2">
          <h3 className="text-sm font-semibold text-gray-700 mb-4 flex items-center gap-2">
            <BarChart3 className="w-4 h-4 text-primary-500" /> Efficiency by Day of Week
          </h3>
          <div className="flex items-end gap-3 h-40">
            {barData.map((val, i) => (
              <div key={dayLabels[i]} className="flex-1 flex flex-col items-center gap-1">
                <span className="text-[10px] font-medium text-gray-500">{val}%</span>
                <div className="w-full bg-primary-100 rounded-t-md relative" style={{ height: `${val}%` }}>
                  <div
                    className="absolute bottom-0 w-full bg-primary-500 rounded-t-md transition-all"
                    style={{ height: '100%' }}
                  />
                </div>
                <span className="text-[10px] text-gray-500 font-medium">{dayLabels[i]}</span>
              </div>
            ))}
          </div>
        </div>
        <div className="space-y-4">
          {/* Alerts */}
          <div className="enterprise-card p-5">
            <h3 className="text-sm font-semibold text-gray-700 mb-3 flex items-center gap-2">
              <AlertTriangle className="w-4 h-4 text-amber-500" /> Alerts
            </h3>
            <div className="space-y-2">
              <div className="flex items-start gap-2 p-2.5 rounded-lg bg-red-50 border border-red-100">
                <AlertTriangle className="w-4 h-4 text-red-500 flex-shrink-0 mt-0.5" />
                <p className="text-xs text-red-700">3 workers approaching overtime</p>
              </div>
              <div className="flex items-start gap-2 p-2.5 rounded-lg bg-amber-50 border border-amber-100">
                <AlertTriangle className="w-4 h-4 text-amber-500 flex-shrink-0 mt-0.5" />
                <p className="text-xs text-amber-700">1 absent without notice</p>
              </div>
              <div className="flex items-start gap-2 p-2.5 rounded-lg bg-blue-50 border border-blue-100">
                <Clock className="w-4 h-4 text-blue-500 flex-shrink-0 mt-0.5" />
                <p className="text-xs text-blue-700">Next shift change in 45 min</p>
              </div>
            </div>
          </div>
          {/* Quick Stats */}
          <div className="enterprise-card p-5">
            <h3 className="text-sm font-semibold text-gray-700 mb-3">Today's Summary</h3>
            <div className="space-y-2 text-xs">
              <div className="flex justify-between"><span className="text-gray-500">Scheduled</span><span className="font-medium text-gray-900">8</span></div>
              <div className="flex justify-between"><span className="text-gray-500">Present</span><span className="font-medium text-green-600">7</span></div>
              <div className="flex justify-between"><span className="text-gray-500">On Break</span><span className="font-medium text-amber-600">1</span></div>
              <div className="flex justify-between"><span className="text-gray-500">Absent</span><span className="font-medium text-red-600">1</span></div>
              <div className="pt-2 border-t border-gray-100 flex justify-between"><span className="text-gray-500 font-medium">Utilization</span><span className="font-bold text-gray-900">{laborUtil}%</span></div>
            </div>
          </div>
        </div>
      </div>

      {/* Assign to Wave Modal */}
      {showAssignModal && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-md w-full">
            <div className="flex items-center justify-between p-6 border-b border-gray-100">
              <h2 className="text-lg font-semibold text-gray-900">Assign Picker to Wave</h2>
              <button onClick={() => setShowAssignModal(false)} className="p-1 hover:bg-gray-100 rounded">
                <X className="w-5 h-5" />
              </button>
            </div>
            <div className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Select Employee</label>
                <Autocomplete
                  value={assignEmployeeId || ''}
                  onChange={(val) => setAssignEmployeeId(val || null)}
                  minChars={0}
                  suggestions={employees.filter((e) => e.role === 'Picker' && e.status === 'Active').map((emp) => emp.id)}
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Select Wave</label>
                <Autocomplete
                  value={selectedAssignWave || ''}
                  onChange={(val) => setSelectedAssignWave(val || null)}
                  minChars={0}
                    suggestions={displayWaves.map((w: any) => w.waveId || w.id)}
                />
              </div>
            </div>
            <div className="p-6 border-t border-gray-100 flex justify-end gap-3">
              <button onClick={() => setShowAssignModal(false)} className="btn-secondary text-sm">Cancel</button>
              <button onClick={handleAssign} className="btn-primary text-sm">
                <UserCheck className="w-4 h-4" /> Assign
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
