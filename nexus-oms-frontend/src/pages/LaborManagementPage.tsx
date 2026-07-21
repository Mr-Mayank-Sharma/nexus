import { useState, useEffect, useCallback } from 'react'
import { clsx } from 'clsx'
import {
  Users, Play, Pause, LogOut, TrendingUp, Clock, UserCheck, Package,
  Truck, Gauge, BarChart3, Calendar, AlertTriangle, Plus, X, Loader2,
  RefreshCw, Target, Activity, Settings, Check, ArrowUp, ArrowDown,
} from 'lucide-react'
import PermissionGate from '../components/rbac/PermissionGate'
import Autocomplete from '../components/common/Autocomplete'
import { useToast } from '../hooks/useToast'
import * as laborApi from '../api/laborManagement'

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

interface WorkloadRule {
  id: string
  warehouseId: string
  taskType: string
  maxWorkloadWeight: number
  priorityWeight: number
  skillRequired?: string
  isActive: boolean
}

interface WorkloadBalance {
  staffId: string
  staffName: string
  currentWeight: number
  maxWeight: number
  taskCount: number
  tasks: string[]
}

interface PerformanceVsStandard {
  staffId: string
  staffName: string
  taskType: string
  actualRate: number
  standardRate: number
  variancePercent: number
  date: string
}

interface ProductivityLog {
  id: string
  staffId: string
  staffName: string
  taskType: string
  itemsCompleted: number
  timeSpentMinutes: number
  itemsPerHour: number
  qualityScore: number
  vsStandardPct: number
  loggedAt: string
}

interface ProductivityByTask {
  taskType: string
  totalItems: number
  totalTimeMinutes: number
  avgItemsPerHour: number
  avgQualityScore: number
  logCount: number
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
  Picker: 'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-700)]',
  Packer: 'bg-[var(--nexus-ai-100)] text-[var(--nexus-ai-700)]',
  Loader: 'bg-[var(--nexus-warning-100)] text-[var(--nexus-warning-700)]',
}

const statusColors: Record<string, string> = {
  Active: 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-700)]',
  Break: 'bg-[var(--nexus-warning-100)] text-[var(--nexus-warning-700)]',
  Off: 'bg-[var(--surface-muted)] text-[var(--text-secondary)]',
}

const taskTypeColors: Record<string, string> = {
  PICKING: 'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-700)]',
  PACKING: 'bg-[var(--nexus-ai-100)] text-[var(--nexus-ai-700)]',
  LOADING: 'bg-[var(--nexus-warning-100)] text-[var(--nexus-warning-700)]',
  RECEIVING: 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-700)]',
  PUTAWAY: 'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-700)]',
}

function generateMockWorkloadRules(): WorkloadRule[] {
  return [
    { id: 'wr-1', warehouseId: 'wh-main', taskType: 'PICKING', maxWorkloadWeight: 100, priorityWeight: 1, skillRequired: 'picker-cert', isActive: true },
    { id: 'wr-2', warehouseId: 'wh-main', taskType: 'PACKING', maxWorkloadWeight: 80, priorityWeight: 2, skillRequired: 'packer-cert', isActive: true },
    { id: 'wr-3', warehouseId: 'wh-main', taskType: 'LOADING', maxWorkloadWeight: 60, priorityWeight: 1, skillRequired: 'loader-cert', isActive: true },
    { id: 'wr-4', warehouseId: 'wh-main', taskType: 'RECEIVING', maxWorkloadWeight: 50, priorityWeight: 3, isActive: true },
  ]
}

function generateMockWorkloadBalance(): WorkloadBalance[] {
  return [
    { staffId: 'E-001', staffName: 'Alex M.', currentWeight: 85, maxWeight: 100, taskCount: 4, tasks: ['Pick W-001', 'Pick W-002', 'Putaway A-12', 'Stage D1'] },
    { staffId: 'E-002', staffName: 'Sarah K.', currentWeight: 92, maxWeight: 100, taskCount: 5, tasks: ['Pick W-002', 'Pick W-003', 'Putaway B-05', 'Stage D2', 'QC Check'] },
    { staffId: 'E-003', staffName: 'James R.', currentWeight: 65, maxWeight: 80, taskCount: 3, tasks: ['Pack ORD-1004', 'Pack ORD-1005', 'Label D3'] },
    { staffId: 'E-005', staffName: 'Tom W.', currentWeight: 55, maxWeight: 60, taskCount: 2, tasks: ['Load D1', 'Load D2'] },
    { staffId: 'E-007', staffName: 'David R.', currentWeight: 78, maxWeight: 100, taskCount: 4, tasks: ['Pick W-001', 'Pick W-004', 'Putaway C-08', 'Replenish A3'] },
    { staffId: 'E-008', staffName: 'Emily W.', currentWeight: 48, maxWeight: 60, taskCount: 2, tasks: ['Stage D2', 'Load D4'] },
  ]
}

function generateMockPerformanceVsStandard(): PerformanceVsStandard[] {
  return [
    { staffId: 'E-001', staffName: 'Alex M.', taskType: 'PICKING', actualRate: 142, standardRate: 120, variancePercent: 18.3, date: new Date().toISOString().slice(0, 10) },
    { staffId: 'E-002', staffName: 'Sarah K.', taskType: 'PICKING', actualRate: 156, standardRate: 120, variancePercent: 30.0, date: new Date().toISOString().slice(0, 10) },
    { staffId: 'E-003', staffName: 'James R.', taskType: 'PACKING', actualRate: 88, standardRate: 90, variancePercent: -2.2, date: new Date().toISOString().slice(0, 10) },
    { staffId: 'E-005', staffName: 'Tom W.', taskType: 'LOADING', actualRate: 42, standardRate: 40, variancePercent: 5.0, date: new Date().toISOString().slice(0, 10) },
    { staffId: 'E-007', staffName: 'David R.', taskType: 'PICKING', actualRate: 128, standardRate: 120, variancePercent: 6.7, date: new Date().toISOString().slice(0, 10) },
  ]
}

function generateMockProductivityLogs(): ProductivityLog[] {
  const now = new Date().toISOString()
  return [
    { id: 'pl-1', staffId: 'E-001', staffName: 'Alex M.', taskType: 'PICKING', itemsCompleted: 284, timeSpentMinutes: 360, itemsPerHour: 47.3, qualityScore: 98, vsStandardPct: 18.3, loggedAt: now },
    { id: 'pl-2', staffId: 'E-002', staffName: 'Sarah K.', taskType: 'PICKING', itemsCompleted: 312, timeSpentMinutes: 360, itemsPerHour: 52.0, qualityScore: 96, vsStandardPct: 30.0, loggedAt: now },
    { id: 'pl-3', staffId: 'E-003', staffName: 'James R.', taskType: 'PACKING', itemsCompleted: 176, timeSpentMinutes: 360, itemsPerHour: 29.3, qualityScore: 94, vsStandardPct: -2.2, loggedAt: now },
    { id: 'pl-4', staffId: 'E-005', staffName: 'Tom W.', taskType: 'LOADING', itemsCompleted: 84, timeSpentMinutes: 360, itemsPerHour: 14.0, qualityScore: 100, vsStandardPct: 5.0, loggedAt: now },
    { id: 'pl-5', staffId: 'E-007', staffName: 'David R.', taskType: 'PICKING', itemsCompleted: 256, timeSpentMinutes: 360, itemsPerHour: 42.7, qualityScore: 92, vsStandardPct: 6.7, loggedAt: now },
    { id: 'pl-6', staffId: 'E-008', staffName: 'Emily W.', taskType: 'LOADING', itemsCompleted: 56, timeSpentMinutes: 360, itemsPerHour: 9.3, qualityScore: 87, vsStandardPct: -12.5, loggedAt: now },
  ]
}

function generateMockProductivityByTask(): ProductivityByTask[] {
  return [
    { taskType: 'PICKING', totalItems: 852, totalTimeMinutes: 1080, avgItemsPerHour: 47.3, avgQualityScore: 95.3, logCount: 3 },
    { taskType: 'PACKING', totalItems: 176, totalTimeMinutes: 360, avgItemsPerHour: 29.3, avgQualityScore: 94.0, logCount: 1 },
    { taskType: 'LOADING', totalItems: 140, totalTimeMinutes: 720, avgItemsPerHour: 11.7, avgQualityScore: 93.5, logCount: 2 },
  ]
}

type Tab = 'overview' | 'staff' | 'shifts' | 'performance' | 'workload'

export default function LaborManagementPage() {
  const { addToast } = useToast()
  const [activeTab, setActiveTab] = useState<Tab>('overview')

  const [employees, setEmployees] = useState<any[]>([])
  const [shifts, setShifts] = useState<any[]>([])
  const [waves, setWaves] = useState<any[]>([])
  const [loading, setLoading] = useState(true)
  const [selectedAssignWave, setSelectedAssignWave] = useState<string | null>(null)

  const [workloadRules, setWorkloadRules] = useState<WorkloadRule[]>([])
  const [workloadBalance, setWorkloadBalance] = useState<WorkloadBalance[]>([])
  const [performanceVsStandard, setPerformanceVsStandard] = useState<PerformanceVsStandard[]>([])
  const [productivityLogs, setProductivityLogs] = useState<ProductivityLog[]>([])
  const [productivityByTask, setProductivityByTask] = useState<ProductivityByTask[]>([])
  const [workloadLoading, setWorkloadLoading] = useState(false)
  const [rebalancing, setRebalancing] = useState(false)
  const [showRuleModal, setShowRuleModal] = useState(false)
  const [ruleForm, setRuleForm] = useState({ taskType: 'PICKING', maxWorkloadWeight: '100', priorityWeight: '1', skillRequired: '' })

  useEffect(() => {
    Promise.all([
      laborApi.getActiveWorkers('wh-main').catch(() => null),
      laborApi.getShiftSchedules('wh-main').catch(() => null),
    ]).then(([empRes, shiftRes]) => {
      if (empRes?.data && Array.isArray(empRes.data) && empRes.data.length > 0) setEmployees(empRes.data)
      if (shiftRes?.data && Array.isArray(shiftRes.data) && shiftRes.data.length > 0) setShifts(shiftRes.data)
      setLoading(false)
    }).catch(() => {
      setLoading(false)
    })
    setEmployees(mockEmployees)
    setShifts(mockShifts)
    setWaves(mockWaves)
  }, [])

  const loadWorkloadData = useCallback(async () => {
    setWorkloadLoading(true)
    try {
      const [rulesRes, balanceRes, perfRes, prodRes, prodByTaskRes] = await Promise.allSettled([
        laborApi.getWorkloadRules('wh-main'),
        laborApi.getWorkloadBalance('wh-main'),
        laborApi.calculatePerformanceVsStandard('wh-main', new Date().toISOString().slice(0, 10)),
        laborApi.getProductivityLogs('wh-main', 7),
        laborApi.getProductivityByTaskType('wh-main', 7),
      ])

      setWorkloadRules(rulesRes.status === 'fulfilled' && rulesRes.value.data?.length > 0 ? rulesRes.value.data : generateMockWorkloadRules())
      setWorkloadBalance(balanceRes.status === 'fulfilled' && balanceRes.value.data?.length > 0 ? balanceRes.value.data : generateMockWorkloadBalance())
      setPerformanceVsStandard(perfRes.status === 'fulfilled' && perfRes.value.data?.length > 0 ? perfRes.value.data : generateMockPerformanceVsStandard())
      setProductivityLogs(prodRes.status === 'fulfilled' && prodRes.value.data?.length > 0 ? prodRes.value.data : generateMockProductivityLogs())
      setProductivityByTask(prodByTaskRes.status === 'fulfilled' && prodByTaskRes.value.data?.length > 0 ? prodByTaskRes.value.data : generateMockProductivityByTask())
    } catch {
      setWorkloadRules(generateMockWorkloadRules())
      setWorkloadBalance(generateMockWorkloadBalance())
      setPerformanceVsStandard(generateMockPerformanceVsStandard())
      setProductivityLogs(generateMockProductivityLogs())
      setProductivityByTask(generateMockProductivityByTask())
    } finally {
      setWorkloadLoading(false)
    }
  }, [])

  useEffect(() => {
    if (activeTab === 'workload') loadWorkloadData()
  }, [activeTab, loadWorkloadData])

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
    try {
      await laborApi.assignTask(assignEmployeeId || '', taskData.taskType || 'PICKING', taskData.waveId)
      addToast({ type: 'success', title: 'Task assigned' })
      setShowAssignModal(false)
    } catch {
      addToast({ type: 'error', title: 'Failed to assign task' })
    }
  }

  async function handleRebalance() {
    setRebalancing(true)
    try {
      const res = await laborApi.rebalanceWorkload('wh-main')
      addToast({ type: 'success', title: 'Workload rebalanced', description: res?.data?.message || 'Recommendations generated' })
      await loadWorkloadData()
    } catch {
      addToast({ type: 'error', title: 'Failed to rebalance workload' })
    } finally {
      setRebalancing(false)
    }
  }

  async function handleCreateRule() {
    try {
      await laborApi.createWorkloadRule({
        warehouseId: 'wh-main',
        taskType: ruleForm.taskType,
        maxWorkloadWeight: Number(ruleForm.maxWorkloadWeight),
        priorityWeight: Number(ruleForm.priorityWeight),
        skillRequired: ruleForm.skillRequired || undefined,
        isActive: true,
      })
      addToast({ type: 'success', title: 'Workload rule created' })
      setShowRuleModal(false)
      setRuleForm({ taskType: 'PICKING', maxWorkloadWeight: '100', priorityWeight: '1', skillRequired: '' })
      await loadWorkloadData()
    } catch {
      addToast({ type: 'error', title: 'Failed to create rule' })
    }
  }

  const displayShifts = shifts.length > 0 ? shifts : mockShifts
  const activeWorkers = employees.filter((e) => e.status === 'Active').length
  const laborUtil = Math.round((activeWorkers / employees.length) * 100)

  const tabs: { key: Tab; label: string; icon: React.ReactNode }[] = [
    { key: 'overview', label: 'Overview', icon: <BarChart3 className="w-4 h-4" /> },
    { key: 'staff', label: 'Staff', icon: <Users className="w-4 h-4" /> },
    { key: 'shifts', label: 'Shifts', icon: <Calendar className="w-4 h-4" /> },
    { key: 'performance', label: 'Performance', icon: <TrendingUp className="w-4 h-4" /> },
    { key: 'workload', label: 'Workload & Performance', icon: <Target className="w-4 h-4" /> },
  ]

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5">
            <Users className="w-7 h-7 text-[var(--nexus-primary-500)]" /> Labor Management
          </h1>
          <p className="text-sm text-[var(--text-secondary)] mt-1">Workforce tracking, workload balancing & efficiency</p>
        </div>
      </div>

      <div className="grid grid-cols-5 gap-4">
        <KpiCard icon={<UserCheck className="w-5 h-5" />} label="Active Workers" value={String(activeWorkers)} />
        <KpiCard icon={<TrendingUp className="w-5 h-5" />} label="Lines Picked/hr" value="142" />
        <KpiCard icon={<Package className="w-5 h-5" />} label="Packing Efficiency" value="96%" />
        <KpiCard icon={<Truck className="w-5 h-5" />} label="Loading Efficiency" value="88%" />
        <KpiCard icon={<Gauge className="w-5 h-5" />} label="Labor Utilization" value={`${laborUtil}%`} />
      </div>

      <div className="enterprise-card p-4">
        <div className="flex items-center rounded-lg border border-[var(--border-default)] overflow-hidden">
          {tabs.map((tab) => (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key)}
              className={clsx(
                'flex items-center gap-1.5 px-3 py-2 text-xs font-medium transition-colors',
                activeTab === tab.key
                  ? 'bg-[var(--nexus-primary-600)] text-white'
                  : 'text-[var(--text-secondary)] hover:bg-[var(--surface-muted)] dark:hover:bg-[var(--surface-muted)]',
              )}
            >
              {tab.icon}
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {activeTab === 'overview' && (
        <div className="space-y-6">
          <div className="grid grid-cols-3 gap-6">
            <div className="enterprise-card p-5 col-span-2">
              <h3 className="text-sm font-semibold text-[var(--text-secondary)] mb-4 flex items-center gap-2">
                <BarChart3 className="w-4 h-4 text-[var(--nexus-primary-500)]" /> Efficiency by Day of Week
              </h3>
              <div className="flex items-end gap-3 h-40">
                {barData.map((val, i) => (
                  <div key={dayLabels[i]} className="flex-1 flex flex-col items-center gap-1">
                    <span className="text-[10px] font-medium text-[var(--text-secondary)]">{val}%</span>
                    <div className="w-full bg-[var(--nexus-primary-100)] rounded-t-md relative" style={{ height: `${val}%` }}>
                      <div className="absolute bottom-0 w-full bg-[var(--nexus-primary-50)]0 rounded-t-md transition-all" style={{ height: '100%' }} />
                    </div>
                    <span className="text-[10px] text-[var(--text-secondary)] font-medium">{dayLabels[i]}</span>
                  </div>
                ))}
              </div>
            </div>
            <div className="space-y-4">
              <div className="enterprise-card p-5">
                <h3 className="text-sm font-semibold text-[var(--text-secondary)] mb-3 flex items-center gap-2">
                  <AlertTriangle className="w-4 h-4 text-[var(--nexus-warning-500)]" /> Alerts
                </h3>
                <div className="space-y-2">
                  <div className="flex items-start gap-2 p-2.5 rounded-lg bg-[var(--nexus-error-50)] border border-[var(--nexus-error-100)]">
                    <AlertTriangle className="w-4 h-4 text-[var(--nexus-error-500)] flex-shrink-0 mt-0.5" />
                    <p className="text-xs text-[var(--nexus-error-700)]">3 workers approaching overtime</p>
                  </div>
                  <div className="flex items-start gap-2 p-2.5 rounded-lg bg-[var(--nexus-warning-50)] border border-[var(--nexus-warning-100)]">
                    <AlertTriangle className="w-4 h-4 text-[var(--nexus-warning-500)] flex-shrink-0 mt-0.5" />
                    <p className="text-xs text-[var(--nexus-warning-700)]">1 absent without notice</p>
                  </div>
                  <div className="flex items-start gap-2 p-2.5 rounded-lg bg-[var(--nexus-primary-50)] border border-[var(--nexus-primary-100)]">
                    <Clock className="w-4 h-4 text-[var(--nexus-primary-500)] flex-shrink-0 mt-0.5" />
                    <p className="text-xs text-[var(--nexus-primary-700)]">Next shift change in 45 min</p>
                  </div>
                </div>
              </div>
              <div className="enterprise-card p-5">
                <h3 className="text-sm font-semibold text-[var(--text-secondary)] mb-3">Today's Summary</h3>
                <div className="space-y-2 text-xs">
                  <div className="flex justify-between"><span className="text-[var(--text-secondary)]">Scheduled</span><span className="font-medium text-[var(--text-primary)]">8</span></div>
                  <div className="flex justify-between"><span className="text-[var(--text-secondary)]">Present</span><span className="font-medium text-[var(--nexus-success-600)]">7</span></div>
                  <div className="flex justify-between"><span className="text-[var(--text-secondary)]">On Break</span><span className="font-medium text-[var(--nexus-warning-600)]">1</span></div>
                  <div className="flex justify-between"><span className="text-[var(--text-secondary)]">Absent</span><span className="font-medium text-[var(--nexus-error-600)]">1</span></div>
                  <div className="pt-2 border-t border-[var(--border-subtle)] flex justify-between"><span className="text-[var(--text-secondary)] font-medium">Utilization</span><span className="font-bold text-[var(--text-primary)]">{laborUtil}%</span></div>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {activeTab === 'staff' && (
        <div className="enterprise-card p-5">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-sm font-semibold text-[var(--text-secondary)] flex items-center gap-2">
              <Users className="w-4 h-4 text-[var(--nexus-primary-500)]" /> Staff Overview
            </h3>
            <PermissionGate resource="warehouse" action="edit">
              <button onClick={() => setShowAssignModal(true)} className="btn-primary text-xs flex items-center gap-1.5">
                <Plus className="w-3.5 h-3.5" /> Assign to Wave
              </button>
            </PermissionGate>
          </div>
          <div className="grid grid-cols-4 gap-4">
            {employees.map((emp) => (
              <div key={emp.id} className="enterprise-card p-4 border border-[var(--border-default)] hover:shadow-sm transition-shadow">
                <div className="flex items-start justify-between mb-3">
                  <div className="flex items-center gap-3">
                    <div className="w-9 h-9 rounded-full bg-[var(--nexus-primary-100)] flex items-center justify-center text-xs font-bold text-[var(--nexus-primary-700)]">{emp.avatar}</div>
                    <div>
                      <p className="text-sm font-semibold text-[var(--text-primary)]">{emp.name}</p>
                      <span className={clsx('inline-flex items-center rounded-full px-1.5 py-0.5 text-[10px] font-medium', roleColors[emp.role])}>{emp.role}</span>
                    </div>
                  </div>
                  <span className={clsx('inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-medium', statusColors[emp.status])}>{emp.status}</span>
                </div>
                <div className="space-y-1 text-xs text-[var(--text-secondary)] mb-3">
                  <p><span className="font-medium text-[var(--text-secondary)]">Task:</span> {emp.currentTask}</p>
                  <p><span className="font-medium text-[var(--text-secondary)]">Efficiency:</span> {emp.efficiency}%</p>
                  <p><span className="font-medium text-[var(--text-secondary)]">Hours:</span> {emp.hoursToday}h</p>
                </div>
                <div className="flex items-center gap-2">
                  {emp.status === 'Active' && (
                    <PermissionGate resource="warehouse" action="edit">
                      <button onClick={() => handleStartBreak(emp.id)} className="btn-secondary text-[10px] px-2 py-1 flex items-center gap-1">
                        <Pause className="w-3 h-3" /> Break
                      </button>
                    </PermissionGate>
                  )}
                  {emp.status === 'Break' && (
                    <PermissionGate resource="warehouse" action="edit">
                      <button onClick={() => handleEndBreak(emp.id)} className="btn-primary text-[10px] px-2 py-1 flex items-center gap-1">
                        <Play className="w-3 h-3" /> Resume
                      </button>
                    </PermissionGate>
                  )}
                  {emp.status !== 'Off' && (
                    <PermissionGate resource="warehouse" action="edit">
                      <button onClick={() => handleEndShift(emp.id)} className="btn-secondary text-[10px] px-2 py-1 flex items-center gap-1">
                        <LogOut className="w-3 h-3" /> End Shift
                      </button>
                    </PermissionGate>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {activeTab === 'shifts' && (
        <div className="enterprise-card p-5">
          <h3 className="text-sm font-semibold text-[var(--text-secondary)] mb-4 flex items-center gap-2">
            <Calendar className="w-4 h-4 text-[var(--nexus-primary-500)]" /> Shift Schedule — Today
          </h3>
          <div className="overflow-x-auto rounded-lg border border-[var(--border-default)]">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-[var(--surface-sunken)] text-left text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">
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
                  <tr key={s.id} className="hover:bg-[var(--surface-sunken)]">
                    <td className="px-4 py-2.5 font-medium text-[var(--text-primary)]">{s.employee}</td>
                    <td className="px-4 py-2.5">
                      <span className={clsx('inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium', roleColors[s.role])}>{s.role}</span>
                    </td>
                    <td className="px-4 py-2.5 text-[var(--text-secondary)]">{s.shiftStart}</td>
                    <td className="px-4 py-2.5 text-[var(--text-secondary)]">{s.shiftEnd}</td>
                    <td className="px-4 py-2.5 text-[var(--text-secondary)] text-xs">{s.breakTimes}</td>
                    <td className="px-4 py-2.5">
                      <span className={clsx('text-xs font-medium', s.overtime !== '0h' ? 'text-[var(--nexus-error-600)]' : 'text-[var(--text-secondary)]')}>{s.overtime}</span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {activeTab === 'performance' && (
        <div className="enterprise-card p-5">
          <h3 className="text-sm font-semibold text-[var(--text-secondary)] mb-4 flex items-center gap-2">
            <TrendingUp className="w-4 h-4 text-[var(--nexus-primary-500)]" /> Performance Metrics
          </h3>
          <div className="space-y-4">
            <div className="grid grid-cols-3 gap-4">
              {[
                { label: 'Avg Pick Rate', value: '142 lines/hr', color: 'text-[var(--nexus-primary-600)]' },
                { label: 'Avg Pack Rate', value: '88 units/hr', color: 'text-[var(--nexus-ai-600)]' },
                { label: 'Avg Load Rate', value: '42 pallets/hr', color: 'text-[var(--nexus-warning-600)]' },
              ].map((stat) => (
                <div key={stat.label} className="enterprise-card p-4 text-center">
                  <p className={clsx('text-2xl font-bold', stat.color)}>{stat.value}</p>
                  <p className="text-xs text-[var(--text-secondary)] mt-1">{stat.label}</p>
                </div>
              ))}
            </div>
            <div className="enterprise-card p-4">
              <h4 className="text-sm font-semibold text-[var(--text-secondary)] mb-3">Efficiency by Day of Week</h4>
              <div className="flex items-end gap-3 h-40">
                {barData.map((val, i) => (
                  <div key={dayLabels[i]} className="flex-1 flex flex-col items-center gap-1">
                    <span className="text-[10px] font-medium text-[var(--text-secondary)]">{val}%</span>
                    <div className="w-full bg-[var(--nexus-primary-100)] rounded-t-md relative" style={{ height: `${val}%` }}>
                      <div className="absolute bottom-0 w-full bg-[var(--nexus-primary-50)]0 rounded-t-md transition-all" style={{ height: '100%' }} />
                    </div>
                    <span className="text-[10px] text-[var(--text-secondary)] font-medium">{dayLabels[i]}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}

      {activeTab === 'workload' && (
        <div className="space-y-6">
          {workloadLoading ? (
            <div className="flex items-center justify-center py-20">
              <Loader2 className="w-8 h-8 animate-spin text-[var(--nexus-primary-500)]" />
            </div>
          ) : (
            <>
              <div className="enterprise-card p-5">
                <div className="flex items-center justify-between mb-4">
                  <h3 className="text-sm font-semibold text-[var(--text-secondary)] flex items-center gap-2">
                    <Settings className="w-4 h-4 text-[var(--nexus-primary-500)]" /> Workload Rules
                  </h3>
                  <div className="flex items-center gap-2">
                    <button onClick={loadWorkloadData} className="btn-secondary text-xs flex items-center gap-1.5">
                      <RefreshCw className="w-3.5 h-3.5" /> Refresh
                    </button>
                    <button onClick={() => setShowRuleModal(true)} className="btn-primary text-xs flex items-center gap-1.5">
                      <Plus className="w-3.5 h-3.5" /> Add Rule
                    </button>
                  </div>
                </div>
                <div className="overflow-x-auto rounded-lg border border-[var(--border-default)]">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="bg-[var(--surface-sunken)] text-left text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">
                        <th className="px-4 py-2.5">Task Type</th>
                        <th className="px-4 py-2.5">Max Weight</th>
                        <th className="px-4 py-2.5">Priority</th>
                        <th className="px-4 py-2.5">Skill Required</th>
                        <th className="px-4 py-2.5">Status</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                      {workloadRules.map((rule) => (
                        <tr key={rule.id} className="hover:bg-[var(--surface-sunken)]">
                          <td className="px-4 py-2.5">
                            <span className={clsx('inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium', taskTypeColors[rule.taskType] || 'bg-[var(--surface-muted)] text-[var(--text-secondary)]')}>
                              {rule.taskType}
                            </span>
                          </td>
                          <td className="px-4 py-2.5 font-medium text-[var(--text-primary)]">{rule.maxWorkloadWeight}</td>
                          <td className="px-4 py-2.5 text-[var(--text-secondary)]">{rule.priorityWeight}</td>
                          <td className="px-4 py-2.5 text-[var(--text-secondary)] text-xs">{rule.skillRequired || '—'}</td>
                          <td className="px-4 py-2.5">
                            <span className={clsx('inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium', rule.isActive ? 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-700)]' : 'bg-[var(--surface-muted)] text-[var(--text-secondary)]')}>
                              {rule.isActive ? 'Active' : 'Inactive'}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>

              <div className="enterprise-card p-5">
                <div className="flex items-center justify-between mb-4">
                  <h3 className="text-sm font-semibold text-[var(--text-secondary)] flex items-center gap-2">
                    <Activity className="w-4 h-4 text-[var(--nexus-primary-500)]" /> Current Workload Balance
                  </h3>
                  <button onClick={handleRebalance} disabled={rebalancing} className="btn-primary text-xs flex items-center gap-1.5">
                    {rebalancing ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <RefreshCw className="w-3.5 h-3.5" />} Rebalance
                  </button>
                </div>
                <div className="space-y-3">
                  {workloadBalance.map((wb) => {
                    const pct = wb.maxWeight > 0 ? Math.round((wb.currentWeight / wb.maxWeight) * 100) : 0
                    return (
                      <div key={wb.staffId} className="flex items-center gap-4 p-3 rounded-lg bg-[var(--surface-sunken)] border border-[var(--border-subtle)]">
                        <div className="w-8 h-8 rounded-full bg-[var(--nexus-primary-100)] flex items-center justify-center text-xs font-bold text-[var(--nexus-primary-700)]">
                          {wb.staffName.split(' ').map((n) => n[0]).join('')}
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center justify-between mb-1">
                            <span className="text-sm font-medium text-[var(--text-primary)]">{wb.staffName}</span>
                            <span className="text-xs text-[var(--text-secondary)]">{wb.currentWeight}/{wb.maxWeight} ({pct}%)</span>
                          </div>
                          <div className="w-full h-2 bg-[var(--surface-muted)] rounded-full overflow-hidden">
                            <div className={clsx('h-full rounded-full transition-all', pct > 90 ? 'bg-[var(--nexus-error-50)]0' : pct > 70 ? 'bg-[var(--nexus-warning-50)]0' : 'bg-[var(--nexus-success-50)]0')} style={{ width: `${Math.min(pct, 100)}%` }} />
                          </div>
                          <div className="mt-1 flex flex-wrap gap-1">
                            {wb.tasks.map((task, i) => (
                              <span key={i} className="text-[10px] px-1.5 py-0.5 bg-white rounded border border-[var(--border-default)] text-[var(--text-secondary)]">{task}</span>
                            ))}
                          </div>
                        </div>
                      </div>
                    )
                  })}
                </div>
              </div>

              <div className="enterprise-card p-5">
                <h3 className="text-sm font-semibold text-[var(--text-secondary)] mb-4 flex items-center gap-2">
                  <Target className="w-4 h-4 text-[var(--nexus-primary-500)]" /> Performance vs Engineered Standards
                </h3>
                <div className="overflow-x-auto rounded-lg border border-[var(--border-default)]">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="bg-[var(--surface-sunken)] text-left text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">
                        <th className="px-4 py-2.5">Worker</th>
                        <th className="px-4 py-2.5">Task Type</th>
                        <th className="px-4 py-2.5 text-right">Actual Rate</th>
                        <th className="px-4 py-2.5 text-right">Standard Rate</th>
                        <th className="px-4 py-2.5 text-right">Variance</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                      {performanceVsStandard.map((pvs) => (
                        <tr key={pvs.staffId} className="hover:bg-[var(--surface-sunken)]">
                          <td className="px-4 py-2.5 font-medium text-[var(--text-primary)]">{pvs.staffName}</td>
                          <td className="px-4 py-2.5">
                            <span className={clsx('inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium', taskTypeColors[pvs.taskType] || 'bg-[var(--surface-muted)] text-[var(--text-secondary)]')}>
                              {pvs.taskType}
                            </span>
                          </td>
                          <td className="px-4 py-2.5 text-right font-medium text-[var(--text-primary)]">{pvs.actualRate}</td>
                          <td className="px-4 py-2.5 text-right text-[var(--text-secondary)]">{pvs.standardRate}</td>
                          <td className="px-4 py-2.5 text-right">
                            <span className={clsx('flex items-center justify-end gap-1 text-xs font-medium', pvs.variancePercent >= 0 ? 'text-[var(--nexus-success-600)]' : 'text-[var(--nexus-error-600)]')}>
                              {pvs.variancePercent >= 0 ? <ArrowUp className="w-3 h-3" /> : <ArrowDown className="w-3 h-3" />}
                              {pvs.variancePercent >= 0 ? '+' : ''}{pvs.variancePercent.toFixed(1)}%
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-6">
                <div className="enterprise-card p-5">
                  <h3 className="text-sm font-semibold text-[var(--text-secondary)] mb-4 flex items-center gap-2">
                    <Package className="w-4 h-4 text-[var(--nexus-primary-500)]" /> Productivity by Task Type
                  </h3>
                  <div className="space-y-3">
                    {productivityByTask.map((pbt) => (
                      <div key={pbt.taskType} className="p-3 rounded-lg bg-[var(--surface-sunken)] border border-[var(--border-subtle)]">
                        <div className="flex items-center justify-between mb-2">
                          <span className={clsx('inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium', taskTypeColors[pbt.taskType] || 'bg-[var(--surface-muted)] text-[var(--text-secondary)]')}>
                            {pbt.taskType}
                          </span>
                          <span className="text-xs text-[var(--text-secondary)]">{pbt.logCount} logs</span>
                        </div>
                        <div className="grid grid-cols-3 gap-2 text-center">
                          <div>
                            <p className="text-lg font-bold text-[var(--text-primary)]">{pbt.totalItems}</p>
                            <p className="text-[10px] text-[var(--text-secondary)]">Total Items</p>
                          </div>
                          <div>
                            <p className="text-lg font-bold text-[var(--nexus-primary-600)]">{pbt.avgItemsPerHour.toFixed(1)}</p>
                            <p className="text-[10px] text-[var(--text-secondary)]">Avg/hr</p>
                          </div>
                          <div>
                            <p className="text-lg font-bold text-[var(--nexus-success-600)]">{pbt.avgQualityScore.toFixed(0)}%</p>
                            <p className="text-[10px] text-[var(--text-secondary)]">Quality</p>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>

                <div className="enterprise-card p-5">
                  <h3 className="text-sm font-semibold text-[var(--text-secondary)] mb-4 flex items-center gap-2">
                    <Activity className="w-4 h-4 text-[var(--nexus-primary-500)]" /> Recent Productivity Logs
                  </h3>
                  <div className="overflow-x-auto rounded-lg border border-[var(--border-default)]">
                    <table className="w-full text-sm">
                      <thead>
                        <tr className="bg-[var(--surface-sunken)] text-left text-[10px] font-semibold text-[var(--text-secondary)] uppercase tracking-wider">
                          <th className="px-3 py-2">Worker</th>
                          <th className="px-3 py-2">Task</th>
                          <th className="px-3 py-2 text-right">Items</th>
                          <th className="px-3 py-2 text-right">/hr</th>
                          <th className="px-3 py-2 text-right">Quality</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-gray-100">
                        {productivityLogs.map((log) => (
                          <tr key={log.id} className="hover:bg-[var(--surface-sunken)]">
                            <td className="px-3 py-2 font-medium text-[var(--text-primary)] text-xs">{log.staffName}</td>
                            <td className="px-3 py-2">
                              <span className={clsx('inline-flex items-center rounded-full px-1.5 py-0.5 text-[10px] font-medium', taskTypeColors[log.taskType] || 'bg-[var(--surface-muted)] text-[var(--text-secondary)]')}>
                                {log.taskType}
                              </span>
                            </td>
                            <td className="px-3 py-2 text-right text-xs font-medium text-[var(--text-primary)]">{log.itemsCompleted}</td>
                            <td className="px-3 py-2 text-right text-xs text-[var(--text-secondary)]">{log.itemsPerHour.toFixed(1)}</td>
                            <td className="px-3 py-2 text-right">
                              <span className={clsx('text-xs font-medium', log.qualityScore >= 95 ? 'text-[var(--nexus-success-600)]' : log.qualityScore >= 85 ? 'text-[var(--nexus-warning-600)]' : 'text-[var(--nexus-error-600)]')}>
                                {log.qualityScore}%
                              </span>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              </div>
            </>
          )}
        </div>
      )}

      {showAssignModal && (
        <div className="enterprise-modal-overlay">
          <div className="enterprise-modal max-w-md">
            <div className="flex items-center justify-between p-6 border-b border-[var(--border-subtle)]">
              <h2 className="text-lg font-semibold text-[var(--text-primary)]">Assign Picker to Wave</h2>
              <button onClick={() => setShowAssignModal(false)} className="p-1 hover:bg-[var(--surface-muted)] rounded">
                <X className="w-5 h-5" />
              </button>
            </div>
            <div className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Select Employee</label>
                <Autocomplete
                  value={assignEmployeeId || ''}
                  onChange={(val) => setAssignEmployeeId(val || null)}
                  minChars={0}
                  suggestions={employees.filter((e) => e.role === 'Picker' && e.status === 'Active').map((emp) => emp.id)}
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Select Wave</label>
                <Autocomplete
                  value={selectedAssignWave || ''}
                  onChange={(val) => setSelectedAssignWave(val || null)}
                  minChars={0}
                  suggestions={waves.map((w: any) => w.waveId || w.id)}
                />
              </div>
            </div>
            <div className="p-6 border-t border-[var(--border-subtle)] flex justify-end gap-3">
              <button onClick={() => setShowAssignModal(false)} className="btn-secondary text-sm">Cancel</button>
              <PermissionGate resource="warehouse" action="edit">
                <button onClick={handleAssign} className="btn-primary text-sm">
                  <UserCheck className="w-4 h-4" /> Assign
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}

      {showRuleModal && (
        <div className="enterprise-modal-overlay">
          <div className="enterprise-modal max-w-md">
            <div className="flex items-center justify-between p-6 border-b border-[var(--border-subtle)]">
              <h2 className="text-lg font-semibold text-[var(--text-primary)]">New Workload Rule</h2>
              <button onClick={() => setShowRuleModal(false)} className="p-1 hover:bg-[var(--surface-muted)] rounded">
                <X className="w-5 h-5" />
              </button>
            </div>
            <div className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Task Type</label>
                <select value={ruleForm.taskType} onChange={(e) => setRuleForm({ ...ruleForm, taskType: e.target.value })} className="enterprise-input w-full text-sm">
                  <option value="PICKING">Picking</option>
                  <option value="PACKING">Packing</option>
                  <option value="LOADING">Loading</option>
                  <option value="RECEIVING">Receiving</option>
                  <option value="PUTAWAY">Putaway</option>
                </select>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Max Workload Weight</label>
                  <input type="number" min={1} value={ruleForm.maxWorkloadWeight} onChange={(e) => setRuleForm({ ...ruleForm, maxWorkloadWeight: e.target.value })} className="enterprise-input w-full text-sm" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Priority Weight</label>
                  <input type="number" min={1} max={10} value={ruleForm.priorityWeight} onChange={(e) => setRuleForm({ ...ruleForm, priorityWeight: e.target.value })} className="enterprise-input w-full text-sm" />
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Skill Required</label>
                <input value={ruleForm.skillRequired} onChange={(e) => setRuleForm({ ...ruleForm, skillRequired: e.target.value })} className="enterprise-input w-full text-sm" placeholder="e.g. picker-cert" />
              </div>
            </div>
            <div className="p-6 border-t border-[var(--border-subtle)] flex justify-end gap-3">
              <button onClick={() => setShowRuleModal(false)} className="btn-secondary text-sm">Cancel</button>
              <button onClick={handleCreateRule} className="btn-primary text-sm">
                <Plus className="w-4 h-4" /> Create Rule
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function KpiCard({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
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
