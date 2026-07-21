import { useState, useEffect, useCallback, useRef } from 'react'
import { clsx } from 'clsx'
import {
  Bot, Move, ArrowUpDown, Navigation, Plane, Mic, ScanLine,
  Plus, RefreshCw, Send, X, AlertTriangle, CheckCircle, Clock,
  Wifi, WifiOff, Settings, ShieldAlert, XCircle, Loader2,
  ChevronDown, ChevronRight, ToggleLeft, ToggleRight, Eye,
  Play, Square, RotateCcw, BarChart3, Activity, Zap, Bell,
  Terminal, Filter, Search,
} from 'lucide-react'
import * as automationApi from '../api/automation'
import { useToast } from '../hooks/useToast'
import EnterpriseBreadcrumbs from '../components/enterprise/EnterpriseBreadcrumbs'
import EnterpriseKPICard from '../components/enterprise/EnterpriseKPICard'

// ─── Types ───────────────────────────────────────────────────────────────────

interface AutomationSystem {
  id: string
  name: string
  type: string
  vendor: string
  model: string
  status: 'ONLINE' | 'OFFLINE' | 'MAINTENANCE' | 'ERROR' | 'DEGRADED'
  lastHealthCheck: string
  connectionProtocol: string
  isActive: boolean
  warehouseId: string
  config?: Record<string, unknown>
}

interface Command {
  id: string
  systemId: string
  systemName: string
  commandType: string
  status: 'PENDING' | 'SENT' | 'ACKNOWLEDGED' | 'EXECUTING' | 'COMPLETED' | 'FAILED' | 'TIMEOUT'
  priority: number
  sentAt: string
  completedAt?: string
  executionTimeMs?: number
  parameters?: Record<string, unknown>
}

interface LogEntry {
  id: string
  systemId: string
  systemName: string
  level: 'INFO' | 'WARN' | 'ERROR' | 'DEBUG'
  event: string
  message: string
  timestamp: string
}

interface Alert {
  id: string
  systemId: string
  systemName: string
  type: string
  title: string
  description: string
  severity: 'CRITICAL' | 'WARNING' | 'INFO'
  status: 'ACTIVE' | 'ACKNOWLEDGED' | 'RESOLVED'
  threshold?: string
  currentValue?: string
  createdAt: string
}

interface HealthData {
  total: number
  online: number
  offline: number
  error: number
  maintenance: number
  healthScore: number
}

// ─── Constants ───────────────────────────────────────────────────────────────

const TYPE_ICONS: Record<string, typeof Bot> = {
  ASRS: Bot,
  CONVEYOR: Move,
  SORTATION: ArrowUpDown,
  ROBOT: Bot,
  AMR: Navigation,
  DRONE: Plane,
  VOICE_PICK: Mic,
  SCAN_SYSTEM: ScanLine,
}

const STATUS_STYLES: Record<string, string> = {
  ONLINE: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400',
  OFFLINE: 'bg-[var(--surface-muted)] text-[var(--text-secondary)] bg-[var(--surface-muted)] dark:text-[var(--text-tertiary)]',
  MAINTENANCE: 'bg-[var(--nexus-warning-100)] text-[var(--nexus-warning-700)] dark:bg-[var(--nexus-warning-900)]/30 dark:text-[var(--nexus-warning-400)]',
  ERROR: 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-700)] dark:bg-[var(--nexus-error-900)]/30 dark:text-[var(--nexus-error-400)]',
  DEGRADED: 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400',
}

const CMD_STATUS_STYLES: Record<string, string> = {
  PENDING: 'bg-[var(--nexus-warning-100)] text-[var(--nexus-warning-700)] dark:bg-[var(--nexus-warning-900)]/30 dark:text-[var(--nexus-warning-400)]',
  SENT: 'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-700)] dark:bg-[var(--nexus-primary-900)]/30 dark:text-[var(--nexus-primary-400)]',
  ACKNOWLEDGED: 'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-700)] dark:bg-[var(--nexus-primary-900)]/30 dark:text-[var(--nexus-primary-400)]',
  EXECUTING: 'bg-[var(--nexus-ai-100)] text-[var(--nexus-ai-700)] dark:bg-[var(--nexus-ai-900)]/30 dark:text-[var(--nexus-ai-400)]',
  COMPLETED: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400',
  FAILED: 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-700)] dark:bg-[var(--nexus-error-900)]/30 dark:text-[var(--nexus-error-400)]',
  TIMEOUT: 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400',
}

const LOG_LEVEL_BORDER: Record<string, string> = {
  INFO: 'border-l-blue-500',
  WARN: 'border-l-yellow-500',
  ERROR: 'border-l-red-500',
  DEBUG: 'border-l-gray-400',
}

const LOG_LEVEL_BG: Record<string, string> = {
  INFO: 'bg-[var(--nexus-primary-50)]0',
  WARN: 'bg-[var(--nexus-warning-50)]0',
  ERROR: 'bg-[var(--nexus-error-50)]0',
  DEBUG: 'bg-[var(--surface-muted)]',
}

const ALERT_SEVERITY_BORDER: Record<string, string> = {
  CRITICAL: 'border-l-red-500 bg-[var(--nexus-error-50)]/50 dark:bg-[var(--nexus-error-900)]/10',
  WARNING: 'border-l-yellow-500 bg-[var(--nexus-warning-50)]/50 dark:bg-[var(--nexus-warning-900)]/10',
  INFO: 'border-l-blue-500 bg-[var(--nexus-primary-50)]/50 dark:bg-[var(--nexus-primary-900)]/10',
}

const ALERT_SEVERITY_ICON: Record<string, typeof AlertTriangle> = {
  CRITICAL: XCircle,
  WARNING: AlertTriangle,
  INFO: Bell,
}

const COMMAND_TYPES = ['PICK', 'PUTAWAY', 'SORT', 'CONVEY', 'STOP', 'RESET', 'HOME', 'CALIBRATE', 'STATUS']
const LOG_LEVELS = ['INFO', 'WARN', 'ERROR', 'DEBUG']
const TIME_RANGES = ['1h', '6h', '24h', '7d', '30d']

// ─── Mock Data ───────────────────────────────────────────────────────────────

const MOCK_SYSTEMS: AutomationSystem[] = [
  { id: 'SYS-001', name: 'ASRS Alpha', type: 'ASRS', vendor: 'AutoStore', model: 'R5-400', status: 'ONLINE', lastHealthCheck: '2026-07-21T14:32:00Z', connectionProtocol: 'OPC-UA', isActive: true, warehouseId: 'WH-001' },
  { id: 'SYS-002', name: 'Main Conveyor', type: 'CONVEYOR', vendor: 'Dematic', model: 'FlexConv 3000', status: 'ONLINE', lastHealthCheck: '2026-07-21T14:31:00Z', connectionProtocol: 'Modbus TCP', isActive: true, warehouseId: 'WH-001' },
  { id: 'SYS-003', name: 'Sorter Unit A', type: 'SORTATION', vendor: 'Honeywell', model: 'Intellisort HBS', status: 'DEGRADED', lastHealthCheck: '2026-07-21T14:28:00Z', connectionProtocol: 'EtherNet/IP', isActive: true, warehouseId: 'WH-001' },
  { id: 'SYS-004', name: 'Pick Robot 1', type: 'ROBOT', vendor: 'Locus Robotics', model: 'LocusBot O50', status: 'ONLINE', lastHealthCheck: '2026-07-21T14:30:00Z', connectionProtocol: 'REST API', isActive: true, warehouseId: 'WH-001' },
  { id: 'SYS-005', name: 'AMR Fleet Hub', type: 'AMR', vendor: 'MiR', model: 'MiR600', status: 'ONLINE', lastHealthCheck: '2026-07-21T14:29:00Z', connectionProtocol: 'REST API', isActive: true, warehouseId: 'WH-001' },
  { id: 'SYS-006', name: 'Drone Scout', type: 'DRONE', vendor: 'DJI', model: 'Matrice 30T', status: 'OFFLINE', lastHealthCheck: '2026-07-21T10:15:00Z', connectionProtocol: 'MQTT', isActive: false, warehouseId: 'WH-001' },
  { id: 'SYS-007', name: 'Voice Pick Zone B', type: 'VOICE_PICK', vendor: 'Voxware', model: 'Voxcloud 4.0', status: 'MAINTENANCE', lastHealthCheck: '2026-07-21T12:00:00Z', connectionProtocol: 'WebSocket', isActive: true, warehouseId: 'WH-001' },
  { id: 'SYS-008', name: 'Scanner Array', type: 'SCAN_SYSTEM', vendor: 'Zebra', model: 'FX9600', status: 'ONLINE', lastHealthCheck: '2026-07-21T14:33:00Z', connectionProtocol: 'TCP/IP', isActive: true, warehouseId: 'WH-001' },
]

const MOCK_COMMANDS: Command[] = [
  { id: 'CMD-001', systemId: 'SYS-001', systemName: 'ASRS Alpha', commandType: 'PICK', status: 'COMPLETED', priority: 2, sentAt: '2026-07-21T14:00:00Z', completedAt: '2026-07-21T14:00:03Z', executionTimeMs: 3200 },
  { id: 'CMD-002', systemId: 'SYS-002', systemName: 'Main Conveyor', commandType: 'CONVEY', status: 'COMPLETED', priority: 1, sentAt: '2026-07-21T13:55:00Z', completedAt: '2026-07-21T13:55:08Z', executionTimeMs: 8100 },
  { id: 'CMD-003', systemId: 'SYS-003', systemName: 'Sorter Unit A', commandType: 'SORT', status: 'FAILED', priority: 3, sentAt: '2026-07-21T13:40:00Z', completedAt: '2026-07-21T13:40:12Z', executionTimeMs: 12000 },
  { id: 'CMD-004', systemId: 'SYS-004', systemName: 'Pick Robot 1', commandType: 'PICK', status: 'EXECUTING', priority: 2, sentAt: '2026-07-21T14:30:00Z' },
  { id: 'CMD-005', systemId: 'SYS-001', systemName: 'ASRS Alpha', commandType: 'PUTAWAY', status: 'SENT', priority: 4, sentAt: '2026-07-21T14:32:00Z' },
  { id: 'CMD-006', systemId: 'SYS-005', systemName: 'AMR Fleet Hub', commandType: 'HOME', status: 'PENDING', priority: 1, sentAt: '2026-07-21T14:33:00Z' },
  { id: 'CMD-007', systemId: 'SYS-008', systemName: 'Scanner Array', commandType: 'STATUS', status: 'COMPLETED', priority: 5, sentAt: '2026-07-21T13:20:00Z', completedAt: '2026-07-21T13:20:01Z', executionTimeMs: 850 },
  { id: 'CMD-008', systemId: 'SYS-001', systemName: 'ASRS Alpha', commandType: 'CALIBRATE', status: 'ACKNOWLEDGED', priority: 3, sentAt: '2026-07-21T14:25:00Z' },
  { id: 'CMD-009', systemId: 'SYS-002', systemName: 'Main Conveyor', commandType: 'STOP', status: 'TIMEOUT', priority: 5, sentAt: '2026-07-21T12:10:00Z', completedAt: '2026-07-21T12:10:30Z', executionTimeMs: 30000 },
]

const MOCK_LOGS: LogEntry[] = [
  { id: 'LOG-001', systemId: 'SYS-001', systemName: 'ASRS Alpha', level: 'INFO', event: 'HEALTH_CHECK', message: 'System health check passed. All subsystems nominal.', timestamp: '2026-07-21T14:32:00Z' },
  { id: 'LOG-002', systemId: 'SYS-003', systemName: 'Sorter Unit A', level: 'WARN', event: 'THROUGHPUT_DROP', message: 'Sort throughput dropped below 80% threshold. Current: 72%.', timestamp: '2026-07-21T14:28:15Z' },
  { id: 'LOG-003', systemId: 'SYS-003', systemName: 'Sorter Unit A', level: 'ERROR', event: 'CHUTE_JAM', message: 'Chute C-07 jam detected. Manual intervention required.', timestamp: '2026-07-21T14:25:00Z' },
  { id: 'LOG-004', systemId: 'SYS-002', systemName: 'Main Conveyor', level: 'INFO', event: 'COMMAND_ACK', message: 'Conveyor belt speed adjusted to 2.5 m/s per command CMD-002.', timestamp: '2026-07-21T13:55:05Z' },
  { id: 'LOG-005', systemId: 'SYS-004', systemName: 'Pick Robot 1', level: 'INFO', event: 'NAVIGATION', message: 'Robot navigated to zone A3, bay 12 successfully.', timestamp: '2026-07-21T14:30:10Z' },
  { id: 'LOG-006', systemId: 'SYS-006', systemName: 'Drone Scout', level: 'ERROR', event: 'CONNECTION_LOST', message: 'MQTT connection lost. Last heartbeat 4h 17m ago.', timestamp: '2026-07-21T10:15:00Z' },
  { id: 'LOG-007', systemId: 'SYS-007', systemName: 'Voice Pick Zone B', level: 'INFO', event: 'MAINTENANCE_START', message: 'Scheduled maintenance window initiated. Firmware update in progress.', timestamp: '2026-07-21T12:00:00Z' },
  { id: 'LOG-008', systemId: 'SYS-005', systemName: 'AMR Fleet Hub', level: 'DEBUG', event: 'PATH_PLANNING', message: 'Recomputed optimal path for AMR-03 avoiding congested zone B2.', timestamp: '2026-07-21T14:29:30Z' },
  { id: 'LOG-009', systemId: 'SYS-008', systemName: 'Scanner Array', level: 'INFO', event: 'SCAN_COMPLETE', message: 'Batch scan completed: 1,247 barcodes decoded. 0 errors.', timestamp: '2026-07-21T14:33:05Z' },
  { id: 'LOG-010', systemId: 'SYS-001', systemName: 'ASRS Alpha', level: 'WARN', event: 'QUEUE_DEPTH', message: 'Command queue depth at 87%. Consider throttling inbound commands.', timestamp: '2026-07-21T14:31:20Z' },
]

const MOCK_ALERTS: Alert[] = [
  { id: 'ALT-001', systemId: 'SYS-003', systemName: 'Sorter Unit A', type: 'THROUGHPUT_DEGRADATION', title: 'Sort Throughput Below Threshold', description: 'Sort throughput has fallen below 80% of expected capacity due to chute jam.', severity: 'CRITICAL', status: 'ACTIVE', threshold: '80%', currentValue: '72%', createdAt: '2026-07-21T14:25:00Z' },
  { id: 'ALT-002', systemId: 'SYS-006', systemName: 'Drone Scout', type: 'CONNECTION_LOST', title: 'Drone System Offline', description: 'MQTT heartbeat lost. Drone system has been offline for over 4 hours.', severity: 'CRITICAL', status: 'ACTIVE', createdAt: '2026-07-21T10:15:00Z' },
  { id: 'ALT-003', systemId: 'SYS-001', systemName: 'ASRS Alpha', type: 'QUEUE_WARNING', title: 'Command Queue Depth High', description: 'Command queue approaching capacity. Risk of command timeouts if not addressed.', severity: 'WARNING', status: 'ACKNOWLEDGED', threshold: '50%', currentValue: '87%', createdAt: '2026-07-21T14:31:20Z' },
  { id: 'ALT-004', systemId: 'SYS-002', systemName: 'Main Conveyor', type: 'SPEED_VARIATION', title: 'Conveyor Speed Variance', description: 'Minor speed variance detected on belt section 3. Self-correcting.', severity: 'INFO', status: 'RESOLVED', threshold: '±5%', currentValue: '±3.2%', createdAt: '2026-07-21T13:10:00Z' },
  { id: 'ALT-005', systemId: 'SYS-007', systemName: 'Voice Pick Zone B', type: 'MAINTENANCE_SCHEDULED', title: 'Firmware Update in Progress', description: 'Scheduled maintenance for firmware update to Voxcloud 4.1. Expected completion 15:00.', severity: 'INFO', status: 'ACKNOWLEDGED', createdAt: '2026-07-21T12:00:00Z' },
]

const MOCK_HEALTH: HealthData = { total: 8, online: 4, offline: 1, error: 0, maintenance: 1, healthScore: 87 }

const MOCK_COMMAND_STATS = { totalToday: 342, avgExecutionMs: 4200, successRate: 96.8, failedToday: 8 }

const MOCK_ALERT_STATS = { active: 2, acknowledged: 2, resolved: 1, bySeverity: { CRITICAL: 2, WARNING: 1, INFO: 2 } }

// ─── Helpers ─────────────────────────────────────────────────────────────────

function formatTime(iso: string) {
  const d = new Date(iso)
  return d.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false })
}

function formatDateTime(iso: string) {
  const d = new Date(iso)
  return d.toLocaleString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false })
}

function timeAgo(iso: string) {
  const diff = Date.now() - new Date(iso).getTime()
  const mins = Math.floor(diff / 60000)
  if (mins < 1) return 'just now'
  if (mins < 60) return `${mins}m ago`
  const hrs = Math.floor(mins / 60)
  if (hrs < 24) return `${hrs}h ago`
  return `${Math.floor(hrs / 24)}d ago`
}

// ─── Component ───────────────────────────────────────────────────────────────

export default function AutomationSystemsPage() {
  const { addToast } = useToast()

  // Data
  const [systems, setSystems] = useState<AutomationSystem[]>([])
  const [commands, setCommands] = useState<Command[]>([])
  const [logs, setLogs] = useState<LogEntry[]>([])
  const [alerts, setAlerts] = useState<Alert[]>([])
  const [healthData, setHealthData] = useState<HealthData>(MOCK_HEALTH)
  const [commandStats, setCommandStats] = useState(MOCK_COMMAND_STATS)
  const [alertStats, setAlertStats] = useState(MOCK_ALERT_STATS)

  // Loading
  const [loading, setLoading] = useState(true)
  const [cmdLoading, setCmdLoading] = useState(false)
  const [logLoading, setLogLoading] = useState(false)
  const [alertLoading, setAlertLoading] = useState(false)

  // UI State
  const [selectedWarehouse] = useState('WH-001')
  const [activeTab, setActiveTab] = useState('systems')
  const [showCommandModal, setShowCommandModal] = useState(false)
  const [showResolveModal, setShowResolveModal] = useState<string | null>(null)
  const [resolveNotes, setResolveNotes] = useState('')
  const mountedRef = useRef(false)

  // Command Form
  const [commandForm, setCommandForm] = useState({
    systemId: '',
    commandType: 'PICK',
    priority: 3,
    timeout: 30000,
    parameters: [{ key: '', value: '' }] as { key: string; value: string }[],
  })

  // Log Filters
  const [logFilter, setLogFilter] = useState({ level: '', timeRange: '1h' })
  const [autoRefresh, setAutoRefresh] = useState(false)

  // Alert Filter
  const [alertFilter, setAlertFilter] = useState<'ACTIVE' | 'ACKNOWLEDGED' | 'RESOLVED' | 'ALL'>('ALL')

  // ─── Data Fetching ───────────────────────────────────────────────────────

  const fetchSystems = useCallback(async () => {
    try {
      const res = await automationApi.getAutomationSystems(selectedWarehouse)
      setSystems(res.data?.content || res.data || MOCK_SYSTEMS)
    } catch {
      setSystems(MOCK_SYSTEMS)
    }
  }, [selectedWarehouse])

  const fetchCommands = useCallback(async () => {
    try {
      const res = await automationApi.getCommands({})
      setCommands(res.data?.content || res.data || MOCK_COMMANDS)
    } catch {
      setCommands(MOCK_COMMANDS)
    }
  }, [])

  const fetchLogs = useCallback(async () => {
    setLogLoading(true)
    try {
      const params: Record<string, string> = {}
      if (logFilter.level) params.level = logFilter.level
      const res = await automationApi.getLogs(params)
      setLogs(res.data?.content || res.data || MOCK_LOGS)
    } catch {
      setLogs(MOCK_LOGS)
    } finally {
      setLogLoading(false)
    }
  }, [logFilter.level])

  const fetchAlerts = useCallback(async () => {
    setAlertLoading(true)
    try {
      const statusParam = alertFilter === 'ALL' ? undefined : alertFilter
      const res = await automationApi.getAlerts(selectedWarehouse, statusParam)
      setAlerts(res.data?.content || res.data || MOCK_ALERTS)
    } catch {
      setAlerts(MOCK_ALERTS)
    } finally {
      setAlertLoading(false)
    }
  }, [selectedWarehouse, alertFilter])

  const fetchHealth = useCallback(async () => {
    try {
      const res = await automationApi.getSystemHealth(selectedWarehouse)
      setHealthData(res.data || MOCK_HEALTH)
    } catch {
      setHealthData(MOCK_HEALTH)
    }
  }, [selectedWarehouse])

  useEffect(() => {
    mountedRef.current = true
    setLoading(true)
    Promise.all([fetchSystems(), fetchCommands(), fetchHealth()]).finally(() => {
      if (mountedRef.current) setLoading(false)
    })
    return () => { mountedRef.current = false }
  }, [fetchSystems, fetchCommands, fetchHealth])

  useEffect(() => {
    if (activeTab === 'logs') fetchLogs()
    if (activeTab === 'alerts') fetchAlerts()
  }, [activeTab, fetchLogs, fetchAlerts])

  useEffect(() => {
    if (!autoRefresh || activeTab !== 'logs') return
    const iv = setInterval(fetchLogs, 5000)
    return () => clearInterval(iv)
  }, [autoRefresh, activeTab, fetchLogs])

  // ─── Handlers ────────────────────────────────────────────────────────────

  async function handleToggleSystem(id: string, isActive: boolean) {
    try {
      await automationApi.toggleAutomationSystem(id, isActive)
      setSystems(prev => prev.map(s => s.id === id ? { ...s, isActive, status: isActive ? 'ONLINE' : 'OFFLINE' } : s))
      addToast({ type: 'success', title: `System ${isActive ? 'enabled' : 'disabled'}` })
    } catch {
      setSystems(prev => prev.map(s => s.id === id ? { ...s, isActive, status: isActive ? 'ONLINE' : 'OFFLINE' } : s))
      addToast({ type: 'info', title: `System ${isActive ? 'enabled' : 'disabled'} (local)` })
    }
  }

  async function handleStatusCheck(systemId: string) {
    try {
      await automationApi.getAutomationStatus(systemId)
      addToast({ type: 'success', title: 'Status check sent' })
    } catch {
      addToast({ type: 'info', title: 'Status check sent (simulated)' })
    }
  }

  async function handleSendCommand() {
    if (!commandForm.systemId) {
      addToast({ type: 'warning', title: 'Select a system' })
      return
    }
    setCmdLoading(true)
    try {
      const params: Record<string, unknown> = {}
      commandForm.parameters.forEach(p => {
        if (p.key.trim()) params[p.key.trim()] = p.value
      })
      await automationApi.sendCommand({
        systemId: commandForm.systemId,
        commandType: commandForm.commandType,
        priority: commandForm.priority,
        timeout: commandForm.timeout,
        parameters: params,
      })
      addToast({ type: 'success', title: 'Command sent successfully' })
      setShowCommandModal(false)
      setCommandForm({ systemId: '', commandType: 'PICK', priority: 3, timeout: 30000, parameters: [{ key: '', value: '' }] })
      fetchCommands()
    } catch {
      const sys = systems.find(s => s.id === commandForm.systemId)
      const newCmd: Command = {
        id: `CMD-${String(commands.length + 1).padStart(3, '0')}`,
        systemId: commandForm.systemId,
        systemName: sys?.name || 'Unknown',
        commandType: commandForm.commandType,
        status: 'PENDING',
        priority: commandForm.priority,
        sentAt: new Date().toISOString(),
      }
      setCommands(prev => [newCmd, ...prev])
      addToast({ type: 'success', title: 'Command queued (simulated)' })
      setShowCommandModal(false)
      setCommandForm({ systemId: '', commandType: 'PICK', priority: 3, timeout: 30000, parameters: [{ key: '', value: '' }] })
    } finally {
      setCmdLoading(false)
    }
  }

  async function handleCancelCommand(id: string) {
    try {
      await automationApi.cancelCommand(id)
      setCommands(prev => prev.map(c => c.id === id ? { ...c, status: 'FAILED' as const } : c))
      addToast({ type: 'success', title: 'Command cancelled' })
    } catch {
      setCommands(prev => prev.map(c => c.id === id ? { ...c, status: 'FAILED' as const } : c))
      addToast({ type: 'info', title: 'Command cancelled (simulated)' })
    }
  }

  async function handleRetryCommand(id: string) {
    try {
      await automationApi.retryCommand(id)
      setCommands(prev => prev.map(c => c.id === id ? { ...c, status: 'SENT' as const } : c))
      addToast({ type: 'success', title: 'Command retried' })
    } catch {
      setCommands(prev => prev.map(c => c.id === id ? { ...c, status: 'SENT' as const } : c))
      addToast({ type: 'info', title: 'Command retried (simulated)' })
    }
  }

  async function handleAcknowledgeAlert(id: string) {
    try {
      await automationApi.acknowledgeAlert(id, 'current-user')
      setAlerts(prev => prev.map(a => a.id === id ? { ...a, status: 'ACKNOWLEDGED' as const } : a))
      addToast({ type: 'success', title: 'Alert acknowledged' })
    } catch {
      setAlerts(prev => prev.map(a => a.id === id ? { ...a, status: 'ACKNOWLEDGED' as const } : a))
      addToast({ type: 'info', title: 'Alert acknowledged (simulated)' })
    }
  }

  async function handleResolveAlert(id: string) {
    try {
      await automationApi.resolveAlert(id, resolveNotes)
      setAlerts(prev => prev.map(a => a.id === id ? { ...a, status: 'RESOLVED' as const } : a))
      addToast({ type: 'success', title: 'Alert resolved' })
    } catch {
      setAlerts(prev => prev.map(a => a.id === id ? { ...a, status: 'RESOLVED' as const } : a))
      addToast({ type: 'info', title: 'Alert resolved (simulated)' })
    }
    setShowResolveModal(null)
    setResolveNotes('')
  }

  function addParameter() {
    setCommandForm(prev => ({ ...prev, parameters: [...prev.parameters, { key: '', value: '' }] }))
  }

  function removeParameter(index: number) {
    setCommandForm(prev => ({ ...prev, parameters: prev.parameters.filter((_, i) => i !== index) }))
  }

  function updateParameter(index: number, field: 'key' | 'value', val: string) {
    setCommandForm(prev => ({
      ...prev,
      parameters: prev.parameters.map((p, i) => i === index ? { ...p, [field]: val } : p),
    }))
  }

  // ─── Computed ─────────────────────────────────────────────────────────────

  const filteredAlerts = alertFilter === 'ALL' ? alerts : alerts.filter(a => a.status === alertFilter)
  const activeAlertCount = alerts.filter(a => a.status === 'ACTIVE').length

  // ─── Render ───────────────────────────────────────────────────────────────

  return (
    <div className="space-y-6">
      {/* Breadcrumbs */}
      <EnterpriseBreadcrumbs crumbs={[
        { label: 'Warehouse', path: '/warehouse' },
        { label: 'Automation Systems' },
      ]} />

      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5">
            <Activity className="w-7 h-7 text-violet-500" />
            Automation & Integration
          </h1>
          <p className="text-sm text-[var(--text-secondary)] mt-1">Warehouse automation systems monitoring and control</p>
        </div>
      </div>

      {/* ─── Health Banner ───────────────────────────────────────────────── */}
      <div className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] p-5">
        <div className="flex items-center justify-between mb-3">
          <h3 className="text-sm font-semibold text-[var(--text-secondary)] flex items-center gap-2">
            <Activity className="w-4 h-4 text-violet-500" /> System Health Overview
          </h3>
          <div className="flex items-center gap-2">
            <span className="text-xs text-[var(--text-secondary)]">Overall Health</span>
            <span className={clsx(
              'text-lg font-bold',
              healthData.healthScore >= 80 ? 'text-emerald-600 dark:text-emerald-400' :
              healthData.healthScore >= 50 ? 'text-[var(--nexus-warning-600)] dark:text-[var(--nexus-warning-400)]' :
              'text-[var(--nexus-error-600)] dark:text-[var(--nexus-error-400)]',
            )}>
              {healthData.healthScore}%
            </span>
          </div>
        </div>
        <div className="flex items-center gap-6">
          {[
            { label: 'Total', value: healthData.total, color: 'text-[var(--text-primary)]' },
            { label: 'Online', value: healthData.online, color: 'text-emerald-600 dark:text-emerald-400', dot: 'bg-emerald-500' },
            { label: 'Offline', value: healthData.offline, color: 'text-[var(--text-secondary)]', dot: 'bg-[var(--surface-muted)]' },
            { label: 'Error', value: healthData.error, color: 'text-[var(--nexus-error-600)] dark:text-[var(--nexus-error-400)]', dot: 'bg-[var(--nexus-error-50)]0' },
            { label: 'Maintenance', value: healthData.maintenance, color: 'text-[var(--nexus-warning-600)] dark:text-[var(--nexus-warning-400)]', dot: 'bg-[var(--nexus-warning-50)]0' },
          ].map((s, i) => (
            <div key={i} className="flex items-center gap-2">
              {s.dot && <span className={clsx('w-2.5 h-2.5 rounded-full', s.dot)} />}
              <span className="text-xs text-[var(--text-secondary)]">{s.label}</span>
              <span className={clsx('text-sm font-bold', s.color)}>{s.value}</span>
            </div>
          ))}
          <div className="ml-auto flex-1 max-w-xs">
            <div className="w-full bg-[var(--surface-muted)] bg-[var(--surface-muted)] rounded-full h-2.5">
              <div
                className={clsx(
                  'h-2.5 rounded-full transition-all duration-500',
                  healthData.healthScore >= 80 ? 'bg-emerald-500' :
                  healthData.healthScore >= 50 ? 'bg-[var(--nexus-warning-50)]0' :
                  'bg-[var(--nexus-error-50)]0',
                )}
                style={{ width: `${healthData.healthScore}%` }}
              />
            </div>
          </div>
        </div>
      </div>

      {/* ─── KPI Cards ───────────────────────────────────────────────────── */}
      <div className="grid grid-cols-5 gap-4">
        <EnterpriseKPICard
          title="Systems Online"
          value={healthData.online}
          icon={<Wifi className="w-5 h-5" />}
          color="success"
          trend="up"
          trendValue="+1 today"
          loading={loading}
        />
        <EnterpriseKPICard
          title="Commands Today"
          value={commandStats.totalToday}
          icon={<Terminal className="w-5 h-5" />}
          color="primary"
          trend="up"
          trendValue="+12%"
          loading={loading}
        />
        <EnterpriseKPICard
          title="Avg Execution"
          value={`${commandStats.avgExecutionMs}ms`}
          icon={<Clock className="w-5 h-5" />}
          color="info"
          trend="down"
          trendValue="-8%"
          loading={loading}
        />
        <EnterpriseKPICard
          title="Active Alerts"
          value={activeAlertCount}
          icon={<ShieldAlert className="w-5 h-5" />}
          color={activeAlertCount > 0 ? 'error' : 'success'}
          subtitle={activeAlertCount > 0 ? 'Requires attention' : 'All clear'}
          loading={loading}
        />
        <EnterpriseKPICard
          title="Throughput"
          value={`${Math.round(commandStats.totalToday / 14)}c/hr`}
          icon={<Zap className="w-5 h-5" />}
          color="ai"
          trend="up"
          trendValue="+5%"
          subtitle="Simulated rate"
          loading={loading}
        />
      </div>

      {/* ─── Toolbar ─────────────────────────────────────────────────────── */}
      <div className="flex items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="relative">
            <select
              className="enterprise-input pl-3 pr-8 py-2 text-sm appearance-none min-w-[180px]"
              value={selectedWarehouse}
              onChange={() => {}}
            >
              <option value="WH-001">Main DC — WH-001</option>
              <option value="WH-002">East Hub — WH-002</option>
              <option value="WH-003">West Hub — WH-003</option>
            </select>
            <ChevronDown className="absolute right-2.5 top-1/2 -translate-y-1/2 w-4 h-4 text-[var(--text-tertiary)] pointer-events-none" />
          </div>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={() => { fetchSystems(); fetchCommands(); fetchHealth(); addToast({ type: 'info', title: 'Refreshed' }) }}
            className="enterprise-btn enterprise-btn-secondary text-sm flex items-center gap-1.5"
          >
            <RefreshCw className="w-4 h-4" /> Refresh
          </button>
          <button
            onClick={() => setShowCommandModal(true)}
            className="enterprise-btn enterprise-btn-primary text-sm flex items-center gap-1.5"
          >
            <Plus className="w-4 h-4" /> Add System
          </button>
        </div>
      </div>

      {/* ─── Tabs ────────────────────────────────────────────────────────── */}
      <div className="border-b border-[var(--border-default)]">
        <div className="flex gap-0 -mb-px">
          {[
            { id: 'systems', label: 'Systems', icon: <Settings className="w-4 h-4" /> },
            { id: 'commands', label: 'Command Center', icon: <Terminal className="w-4 h-4" /> },
            { id: 'logs', label: 'Logs', icon: <Eye className="w-4 h-4" /> },
            { id: 'alerts', label: 'Alerts', icon: <AlertTriangle className="w-4 h-4" />, badge: activeAlertCount || undefined },
          ].map(tab => (
            <button
              key={tab.id}
              className={clsx(
                'flex items-center gap-1.5 px-4 py-3 text-sm font-medium border-b-2 transition-all whitespace-nowrap',
                activeTab === tab.id
                  ? 'border-violet-500 text-violet-600 dark:text-violet-400'
                  : 'border-transparent text-[var(--text-secondary)] hover:text-[var(--text-secondary)] dark:hover:text-[var(--text-tertiary)] hover:border-[var(--border-default)]',
              )}
              onClick={() => setActiveTab(tab.id)}
            >
              {tab.icon} {tab.label}
              {tab.badge != null && (
                <span className={clsx(
                  'ml-1 text-xs px-1.5 py-0.5 rounded-full font-semibold',
                  activeTab === tab.id
                    ? 'bg-violet-100 text-violet-700 dark:bg-violet-900/30 dark:text-violet-400'
                    : 'bg-[var(--surface-muted)] text-[var(--text-secondary)]',
                )}>
                  {tab.badge}
                </span>
              )}
            </button>
          ))}
        </div>
      </div>

      {/* ════════════════════════════════════════════════════════════════════ */}
      {/* TAB: Systems                                                       */}
      {/* ════════════════════════════════════════════════════════════════════ */}
      {activeTab === 'systems' && (
        <div className="grid grid-cols-2 gap-4">
          {loading ? (
            Array.from({ length: 6 }).map((_, i) => (
              <div key={i} className="enterprise-card p-5 animate-pulse">
                <div className="enterprise-skeleton h-6 w-48 mb-3" />
                <div className="enterprise-skeleton h-4 w-32 mb-2" />
                <div className="enterprise-skeleton h-4 w-24" />
              </div>
            ))
          ) : systems.map(sys => {
            const Icon = TYPE_ICONS[sys.type] || Settings
            return (
              <div
                key={sys.id}
                className={clsx(
                  'bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] p-5 transition-all duration-150 hover:shadow-md',
                  !sys.isActive && 'opacity-60',
                )}
              >
                {/* Header */}
                <div className="flex items-start justify-between mb-3">
                  <div className="flex items-center gap-3">
                    <div className={clsx(
                      'w-10 h-10 rounded-xl flex items-center justify-center ring-1',
                      sys.status === 'ONLINE' ? 'bg-emerald-50 dark:bg-emerald-900/20 text-emerald-600 dark:text-emerald-400 ring-emerald-500/20' :
                      sys.status === 'ERROR' ? 'bg-[var(--nexus-error-50)] dark:bg-[var(--nexus-error-900)]/20 text-[var(--nexus-error-600)] dark:text-[var(--nexus-error-400)] ring-red-500/20' :
                      sys.status === 'MAINTENANCE' ? 'bg-[var(--nexus-warning-50)] dark:bg-[var(--nexus-warning-900)]/20 text-[var(--nexus-warning-600)] dark:text-[var(--nexus-warning-400)] ring-amber-500/20' :
                      sys.status === 'DEGRADED' ? 'bg-orange-50 dark:bg-orange-900/20 text-orange-600 dark:text-orange-400 ring-orange-500/20' :
                      'bg-[var(--surface-sunken)] bg-[var(--surface-muted)] text-[var(--text-secondary)] ring-gray-500/20',
                    )}>
                      <Icon className="w-5 h-5" />
                    </div>
                    <div>
                      <h4 className="text-sm font-semibold text-[var(--text-primary)]">{sys.name}</h4>
                      <p className="text-xs text-[var(--text-secondary)]">{sys.vendor} · {sys.model}</p>
                    </div>
                  </div>
                  <span className={clsx('text-xs font-semibold px-2.5 py-1 rounded-full', STATUS_STYLES[sys.status])}>
                    {sys.status}
                  </span>
                </div>

                {/* Details */}
                <div className="grid grid-cols-3 gap-2 mb-4">
                  <div>
                    <p className="text-[10px] text-[var(--text-tertiary)] dark:text-[var(--text-secondary)] uppercase tracking-wider">Last Check</p>
                    <p className="text-xs font-medium text-[var(--text-secondary)] mt-0.5">{timeAgo(sys.lastHealthCheck)}</p>
                  </div>
                  <div>
                    <p className="text-[10px] text-[var(--text-tertiary)] dark:text-[var(--text-secondary)] uppercase tracking-wider">Protocol</p>
                    <p className="text-xs font-medium text-[var(--text-secondary)] mt-0.5">{sys.connectionProtocol}</p>
                  </div>
                  <div>
                    <p className="text-[10px] text-[var(--text-tertiary)] dark:text-[var(--text-secondary)] uppercase tracking-wider">Type</p>
                    <p className="text-xs font-medium text-[var(--text-secondary)] mt-0.5">{sys.type}</p>
                  </div>
                </div>

                {/* Actions Row */}
                <div className="flex items-center justify-between pt-3 border-t border-[var(--border-subtle)]/50">
                  <button
                    onClick={() => handleToggleSystem(sys.id, !sys.isActive)}
                    className={clsx(
                      'flex items-center gap-1.5 text-xs font-medium px-3 py-1.5 rounded-lg transition-colors',
                      sys.isActive
                        ? 'text-emerald-700 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-900/20 hover:bg-emerald-100 dark:hover:bg-emerald-900/30'
                        : 'text-[var(--text-secondary)] bg-[var(--surface-sunken)] bg-[var(--surface-muted)] hover:bg-[var(--surface-muted)] hover:bg-[var(--interactive-hover)]',
                    )}
                  >
                    {sys.isActive ? <ToggleRight className="w-4 h-4" /> : <ToggleLeft className="w-4 h-4" />}
                    {sys.isActive ? 'Enabled' : 'Disabled'}
                  </button>
                  <div className="flex items-center gap-1.5">
                    <button
                      onClick={() => handleStatusCheck(sys.id)}
                      className="text-xs font-medium px-3 py-1.5 rounded-lg bg-[var(--surface-sunken)] bg-[var(--surface-muted)] text-[var(--text-secondary)] dark:text-[var(--text-tertiary)] hover:bg-[var(--surface-muted)] hover:bg-[var(--interactive-hover)] transition-colors flex items-center gap-1"
                    >
                      <Eye className="w-3.5 h-3.5" /> Status
                    </button>
                    <button
                      onClick={() => {
                        setCommandForm(prev => ({ ...prev, systemId: sys.id }))
                        setShowCommandModal(true)
                      }}
                      className="text-xs font-medium px-3 py-1.5 rounded-lg bg-violet-50 dark:bg-violet-900/20 text-violet-700 dark:text-violet-400 hover:bg-violet-100 dark:hover:bg-violet-900/30 transition-colors flex items-center gap-1"
                    >
                      <Send className="w-3.5 h-3.5" /> Command
                    </button>
                  </div>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* ════════════════════════════════════════════════════════════════════ */}
      {/* TAB: Command Center                                                 */}
      {/* ════════════════════════════════════════════════════════════════════ */}
      {activeTab === 'commands' && (
        <div className="space-y-4">
          {/* Command Stats */}
          <div className="grid grid-cols-4 gap-4">
            {[
              { label: 'Total Today', value: commandStats.totalToday, icon: <Terminal className="w-4 h-4" />, color: 'text-[var(--nexus-primary-600)]' },
              { label: 'Avg Exec Time', value: `${commandStats.avgExecutionMs}ms`, icon: <Clock className="w-4 h-4" />, color: 'text-[var(--text-secondary)]' },
              { label: 'Success Rate', value: `${commandStats.successRate}%`, icon: <CheckCircle className="w-4 h-4" />, color: 'text-emerald-600' },
              { label: 'Failed Today', value: commandStats.failedToday, icon: <XCircle className="w-4 h-4" />, color: 'text-[var(--nexus-error-600)]' },
            ].map((s, i) => (
              <div key={i} className="bg-[var(--surface-base)] rounded-lg border border-[var(--border-default)] p-3 flex items-center gap-3">
                <span className={s.color}>{s.icon}</span>
                <div>
                  <p className="text-[10px] text-[var(--text-tertiary)] uppercase tracking-wider">{s.label}</p>
                  <p className="text-lg font-bold text-[var(--text-primary)]">{s.value}</p>
                </div>
              </div>
            ))}
          </div>

          {/* Send Command Button */}
          <div className="flex justify-end">
            <button
              onClick={() => setShowCommandModal(true)}
              className="enterprise-btn enterprise-btn-primary text-sm flex items-center gap-1.5"
            >
              <Send className="w-4 h-4" /> Send Command
            </button>
          </div>

          {/* Commands Table */}
          <div className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b border-[var(--border-default)]">
                    <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">ID</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">System</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Type</th>
                    <th className="px-4 py-3 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Status</th>
                    <th className="px-4 py-3 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Priority</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Sent At</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Completed</th>
                    <th className="px-4 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Exec Time</th>
                    <th className="px-4 py-3 text-center text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100 dark:divide-gray-700/50">
                  {commands.map(cmd => (
                    <tr key={cmd.id} className="hover:bg-[var(--surface-sunken)] dark:hover:bg-[var(--surface-muted)]/30 transition-colors">
                      <td className="px-4 py-3 text-sm font-mono font-medium text-[var(--text-primary)]">{cmd.id}</td>
                      <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{cmd.systemName}</td>
                      <td className="px-4 py-3">
                        <span className="text-xs font-medium px-2 py-1 rounded-md bg-[var(--surface-muted)] text-[var(--text-secondary)]">
                          {cmd.commandType}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-center">
                        <span className={clsx('text-xs font-semibold px-2.5 py-1 rounded-full', CMD_STATUS_STYLES[cmd.status])}>
                          {cmd.status}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-center">
                        <span className={clsx(
                          'text-xs font-bold',
                          cmd.priority <= 2 ? 'text-[var(--nexus-error-500)]' : cmd.priority <= 3 ? 'text-[var(--nexus-warning-500)]' : 'text-[var(--text-tertiary)]',
                        )}>
                          P{cmd.priority}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-xs text-[var(--text-secondary)]">{formatDateTime(cmd.sentAt)}</td>
                      <td className="px-4 py-3 text-xs text-[var(--text-secondary)]">
                        {cmd.completedAt ? formatDateTime(cmd.completedAt) : '—'}
                      </td>
                      <td className="px-4 py-3 text-right text-sm font-medium text-[var(--text-secondary)]">
                        {cmd.executionTimeMs ? `${cmd.executionTimeMs.toLocaleString()}ms` : '—'}
                      </td>
                      <td className="px-4 py-3 text-center">
                        <div className="flex items-center justify-center gap-1">
                          {(cmd.status === 'PENDING' || cmd.status === 'SENT') && (
                            <button
                              onClick={() => handleCancelCommand(cmd.id)}
                              className="text-xs font-medium px-2.5 py-1 rounded-lg text-[var(--nexus-error-600)] dark:text-[var(--nexus-error-400)] hover:bg-[var(--nexus-error-50)] dark:hover:bg-[var(--nexus-error-900)]/20 transition-colors"
                            >
                              Cancel
                            </button>
                          )}
                          {cmd.status === 'FAILED' && (
                            <button
                              onClick={() => handleRetryCommand(cmd.id)}
                              className="text-xs font-medium px-2.5 py-1 rounded-lg text-[var(--nexus-primary-600)] dark:text-[var(--nexus-primary-400)] hover:bg-[var(--nexus-primary-50)] dark:hover:bg-[var(--nexus-primary-900)]/20 transition-colors flex items-center gap-1"
                            >
                              <RotateCcw className="w-3 h-3" /> Retry
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {commands.length === 0 && (
              <div className="text-center py-12 text-[var(--text-tertiary)] dark:text-[var(--text-secondary)]">
                <Terminal className="w-10 h-10 mx-auto mb-3 opacity-40" />
                <p className="text-sm">No commands found</p>
              </div>
            )}
          </div>
        </div>
      )}

      {/* ════════════════════════════════════════════════════════════════════ */}
      {/* TAB: Logs                                                          */}
      {/* ════════════════════════════════════════════════════════════════════ */}
      {activeTab === 'logs' && (
        <div className="space-y-4">
          {/* Filters */}
          <div className="flex items-center gap-3 flex-wrap">
            <div className="flex items-center gap-2">
              <Filter className="w-4 h-4 text-[var(--text-tertiary)]" />
              <span className="text-xs font-medium text-[var(--text-secondary)]">Level:</span>
              <div className="flex gap-1">
                {['', ...LOG_LEVELS].map(level => (
                  <button
                    key={level}
                    onClick={() => setLogFilter(prev => ({ ...prev, level }))}
                    className={clsx(
                      'text-xs font-medium px-2.5 py-1 rounded-md transition-colors',
                      logFilter.level === level
                        ? 'bg-violet-100 text-violet-700 dark:bg-violet-900/30 dark:text-violet-400'
                        : 'bg-[var(--surface-muted)] text-[var(--text-secondary)] hover:bg-[var(--surface-muted)] hover:bg-[var(--interactive-hover)]',
                    )}
                  >
                    {level || 'All'}
                  </button>
                ))}
              </div>
            </div>

            <div className="h-4 w-px bg-[var(--surface-muted)] bg-[var(--surface-muted)]" />

            <div className="flex items-center gap-2">
              <Clock className="w-4 h-4 text-[var(--text-tertiary)]" />
              <span className="text-xs font-medium text-[var(--text-secondary)]">Range:</span>
              <div className="flex gap-1">
                {TIME_RANGES.map(range => (
                  <button
                    key={range}
                    onClick={() => setLogFilter(prev => ({ ...prev, timeRange: range }))}
                    className={clsx(
                      'text-xs font-medium px-2.5 py-1 rounded-md transition-colors',
                      logFilter.timeRange === range
                        ? 'bg-violet-100 text-violet-700 dark:bg-violet-900/30 dark:text-violet-400'
                        : 'bg-[var(--surface-muted)] text-[var(--text-secondary)] hover:bg-[var(--surface-muted)] hover:bg-[var(--interactive-hover)]',
                    )}
                  >
                    {range}
                  </button>
                ))}
              </div>
            </div>

            <div className="h-4 w-px bg-[var(--surface-muted)] bg-[var(--surface-muted)]" />

            <button
              onClick={() => setAutoRefresh(!autoRefresh)}
              className={clsx(
                'flex items-center gap-1.5 text-xs font-medium px-3 py-1.5 rounded-lg transition-colors',
                autoRefresh
                  ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400'
                  : 'bg-[var(--surface-muted)] text-[var(--text-secondary)] hover:bg-[var(--surface-muted)] hover:bg-[var(--interactive-hover)]',
              )}
            >
              <RefreshCw className={clsx('w-3.5 h-3.5', autoRefresh && 'animate-spin')} />
              Auto-refresh {autoRefresh ? 'ON' : 'OFF'}
            </button>

            <button
              onClick={fetchLogs}
              className="enterprise-btn enterprise-btn-secondary text-xs flex items-center gap-1.5 ml-auto"
            >
              <RefreshCw className="w-3.5 h-3.5" /> Fetch Logs
            </button>
          </div>

          {/* Log List */}
          <div className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] overflow-hidden">
            {logLoading ? (
              <div className="p-12 text-center">
                <Loader2 className="w-8 h-8 mx-auto text-[var(--text-tertiary)] animate-spin mb-3" />
                <p className="text-sm text-[var(--text-secondary)]">Loading logs...</p>
              </div>
            ) : (
              <div className="max-h-[500px] overflow-y-auto divide-y divide-gray-100 dark:divide-gray-700/50">
                {logs.map(log => (
                  <div
                    key={log.id}
                    className={clsx(
                      'flex items-start gap-4 p-4 border-l-4 hover:bg-[var(--surface-sunken)] dark:hover:bg-[var(--surface-muted)]/30 transition-colors',
                      LOG_LEVEL_BORDER[log.level],
                    )}
                  >
                    <div className="flex items-center gap-2 min-w-0 flex-shrink-0">
                      <span className={clsx('w-2 h-2 rounded-full flex-shrink-0', LOG_LEVEL_BG[log.level])} />
                      <span className="text-[11px] font-mono text-[var(--text-tertiary)] dark:text-[var(--text-secondary)] whitespace-nowrap">
                        {formatTime(log.timestamp)}
                      </span>
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2 mb-0.5">
                        <span className="text-xs font-semibold text-[var(--text-secondary)]">{log.systemName}</span>
                        <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-[var(--surface-muted)] text-[var(--text-secondary)]">
                          {log.event}
                        </span>
                      </div>
                      <p className="text-sm text-[var(--text-secondary)] leading-relaxed">{log.message}</p>
                    </div>
                    <span className={clsx(
                      'text-[10px] font-bold uppercase tracking-wider px-1.5 py-0.5 rounded flex-shrink-0',
                      log.level === 'ERROR' ? 'text-[var(--nexus-error-600)] bg-[var(--nexus-error-50)] dark:bg-[var(--nexus-error-900)]/20' :
                      log.level === 'WARN' ? 'text-[var(--nexus-warning-600)] bg-[var(--nexus-warning-50)] dark:bg-[var(--nexus-warning-900)]/20' :
                      log.level === 'DEBUG' ? 'text-[var(--text-secondary)] bg-[var(--surface-muted)]' :
                      'text-[var(--nexus-primary-600)] bg-[var(--nexus-primary-50)] dark:bg-[var(--nexus-primary-900)]/20',
                    )}>
                      {log.level}
                    </span>
                  </div>
                ))}
                {logs.length === 0 && (
                  <div className="text-center py-12 text-[var(--text-tertiary)] dark:text-[var(--text-secondary)]">
                    <Eye className="w-10 h-10 mx-auto mb-3 opacity-40" />
                    <p className="text-sm">No logs found</p>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      )}

      {/* ════════════════════════════════════════════════════════════════════ */}
      {/* TAB: Alerts                                                        */}
      {/* ════════════════════════════════════════════════════════════════════ */}
      {activeTab === 'alerts' && (
        <div className="space-y-4">
          {/* Alert Filters & Stats */}
          <div className="flex items-center justify-between gap-4">
            <div className="flex items-center gap-3">
              <div className="flex gap-1">
                {(['ALL', 'ACTIVE', 'ACKNOWLEDGED', 'RESOLVED'] as const).map(filter => (
                  <button
                    key={filter}
                    onClick={() => setAlertFilter(filter)}
                    className={clsx(
                      'text-xs font-medium px-3 py-1.5 rounded-lg transition-colors',
                      alertFilter === filter
                        ? 'bg-violet-100 text-violet-700 dark:bg-violet-900/30 dark:text-violet-400'
                        : 'bg-[var(--surface-muted)] text-[var(--text-secondary)] hover:bg-[var(--surface-muted)] hover:bg-[var(--interactive-hover)]',
                    )}
                  >
                    {filter.charAt(0) + filter.slice(1).toLowerCase()}
                  </button>
                ))}
              </div>
            </div>
            <div className="flex items-center gap-4 text-xs text-[var(--text-secondary)]">
              <span className="flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-[var(--nexus-error-50)]0" />
                Critical: {alertStats.bySeverity.CRITICAL}
              </span>
              <span className="flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-[var(--nexus-warning-50)]0" />
                Warning: {alertStats.bySeverity.WARNING}
              </span>
              <span className="flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-[var(--nexus-primary-50)]0" />
                Info: {alertStats.bySeverity.INFO}
              </span>
            </div>
          </div>

          {/* Alert Cards */}
          {alertLoading ? (
            <div className="text-center py-12">
              <Loader2 className="w-8 h-8 mx-auto text-[var(--text-tertiary)] animate-spin mb-3" />
              <p className="text-sm text-[var(--text-secondary)]">Loading alerts...</p>
            </div>
          ) : (
            <div className="space-y-3">
              {filteredAlerts.map(alert => {
                const SevIcon = ALERT_SEVERITY_ICON[alert.severity] || AlertTriangle
                return (
                  <div
                    key={alert.id}
                    className={clsx(
                      'bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] border-l-4 p-5 transition-all',
                      ALERT_SEVERITY_BORDER[alert.severity],
                      alert.status === 'RESOLVED' && 'opacity-60',
                    )}
                  >
                    <div className="flex items-start justify-between gap-4">
                      <div className="flex items-start gap-3 min-w-0">
                        <div className={clsx(
                          'w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0',
                          alert.severity === 'CRITICAL' ? 'bg-[var(--nexus-error-50)] dark:bg-[var(--nexus-error-900)]/20 text-[var(--nexus-error-600)] dark:text-[var(--nexus-error-400)]' :
                          alert.severity === 'WARNING' ? 'bg-[var(--nexus-warning-100)] dark:bg-[var(--nexus-warning-900)]/20 text-[var(--nexus-warning-600)] dark:text-[var(--nexus-warning-400)]' :
                          'bg-[var(--nexus-primary-100)] dark:bg-[var(--nexus-primary-900)]/20 text-[var(--nexus-primary-600)] dark:text-[var(--nexus-primary-400)]',
                        )}>
                          <SevIcon className="w-5 h-5" />
                        </div>
                        <div className="min-w-0">
                          <div className="flex items-center gap-2 mb-0.5">
                            <h4 className="text-sm font-semibold text-[var(--text-primary)]">{alert.title}</h4>
                            <span className={clsx(
                              'text-[10px] font-bold uppercase px-1.5 py-0.5 rounded',
                              alert.status === 'ACTIVE' ? 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-700)] dark:bg-[var(--nexus-error-900)]/30 dark:text-[var(--nexus-error-400)]' :
                              alert.status === 'ACKNOWLEDGED' ? 'bg-[var(--nexus-warning-100)] text-[var(--nexus-warning-700)] dark:bg-[var(--nexus-warning-900)]/30 dark:text-[var(--nexus-warning-400)]' :
                              'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400',
                            )}>
                              {alert.status}
                            </span>
                          </div>
                          <p className="text-sm text-[var(--text-secondary)] mb-2">{alert.description}</p>
                          <div className="flex items-center gap-4 text-xs text-[var(--text-secondary)]">
                            <span className="flex items-center gap-1">
                              <Settings className="w-3 h-3" /> {alert.systemName}
                            </span>
                            <span>{alert.type}</span>
                            {alert.threshold && (
                              <span>
                                Threshold: <span className="font-medium text-[var(--text-secondary)]">{alert.threshold}</span>
                                {' → '}
                                Current: <span className="font-medium text-[var(--text-secondary)]">{alert.currentValue}</span>
                              </span>
                            )}
                            <span>{timeAgo(alert.createdAt)}</span>
                          </div>
                        </div>
                      </div>

                      {/* Actions */}
                      {alert.status !== 'RESOLVED' && (
                        <div className="flex items-center gap-2 flex-shrink-0">
                          {alert.status === 'ACTIVE' && (
                            <button
                              onClick={() => handleAcknowledgeAlert(alert.id)}
                              className="text-xs font-medium px-3 py-1.5 rounded-lg bg-[var(--nexus-warning-50)] dark:bg-[var(--nexus-warning-900)]/20 text-[var(--nexus-warning-700)] dark:text-[var(--nexus-warning-400)] hover:bg-[var(--nexus-warning-100)] dark:hover:bg-[var(--nexus-warning-900)]/30 transition-colors"
                            >
                              Acknowledge
                            </button>
                          )}
                          <button
                            onClick={() => setShowResolveModal(alert.id)}
                            className="text-xs font-medium px-3 py-1.5 rounded-lg bg-emerald-50 dark:bg-emerald-900/20 text-emerald-700 dark:text-emerald-400 hover:bg-emerald-100 dark:hover:bg-emerald-900/30 transition-colors"
                          >
                            Resolve
                          </button>
                        </div>
                      )}
                    </div>
                  </div>
                )
              })}
              {filteredAlerts.length === 0 && (
                <div className="text-center py-12 text-[var(--text-tertiary)] dark:text-[var(--text-secondary)]">
                  <CheckCircle className="w-10 h-10 mx-auto mb-3 opacity-40" />
                  <p className="text-sm">No alerts {alertFilter !== 'ALL' ? `with status "${alertFilter.toLowerCase()}"` : ''}</p>
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* ════════════════════════════════════════════════════════════════════ */}
      {/* MODAL: Send Command                                                */}
      {/* ════════════════════════════════════════════════════════════════════ */}
      {showCommandModal && (
        <div className="enterprise-modal-overlay">
          <div className="enterprise-modal max-w-lg">
            {/* Modal Header */}
            <div className="flex items-center justify-between px-6 py-4 border-b border-[var(--border-default)]">
              <div className="flex items-center gap-2">
                <Send className="w-5 h-5 text-violet-500" />
                <h2 className="text-lg font-semibold text-[var(--text-primary)]">Send Command</h2>
              </div>
              <button
                onClick={() => setShowCommandModal(false)}
                className="w-8 h-8 rounded-lg flex items-center justify-center text-[var(--text-tertiary)] hover:text-[var(--text-secondary)] hover:bg-[var(--surface-muted)] dark:hover:bg-[var(--surface-muted)] transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Modal Body */}
            <div className="p-6 space-y-4">
              {/* System */}
              <div>
                <label className="block text-xs font-semibold text-[var(--text-secondary)] mb-1.5">Target System</label>
                <div className="relative">
                  <select
                    className="enterprise-input w-full pr-8 appearance-none"
                    value={commandForm.systemId}
                    onChange={e => setCommandForm(prev => ({ ...prev, systemId: e.target.value }))}
                  >
                    <option value="">Select a system...</option>
                    {systems.filter(s => s.isActive).map(s => (
                      <option key={s.id} value={s.id}>{s.name} ({s.type})</option>
                    ))}
                  </select>
                  <ChevronDown className="absolute right-2.5 top-1/2 -translate-y-1/2 w-4 h-4 text-[var(--text-tertiary)] pointer-events-none" />
                </div>
              </div>

              {/* Command Type */}
              <div>
                <label className="block text-xs font-semibold text-[var(--text-secondary)] mb-1.5">Command Type</label>
                <div className="relative">
                  <select
                    className="enterprise-input w-full pr-8 appearance-none"
                    value={commandForm.commandType}
                    onChange={e => setCommandForm(prev => ({ ...prev, commandType: e.target.value }))}
                  >
                    {COMMAND_TYPES.map(ct => (
                      <option key={ct} value={ct}>{ct}</option>
                    ))}
                  </select>
                  <ChevronDown className="absolute right-2.5 top-1/2 -translate-y-1/2 w-4 h-4 text-[var(--text-tertiary)] pointer-events-none" />
                </div>
              </div>

              {/* Priority & Timeout */}
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-[var(--text-secondary)] mb-1.5">
                    Priority
                    <span className="ml-1 text-[var(--text-tertiary)] font-normal">(1=highest)</span>
                  </label>
                  <div className="flex gap-1">
                    {[1, 2, 3, 4, 5].map(p => (
                      <button
                        key={p}
                        onClick={() => setCommandForm(prev => ({ ...prev, priority: p }))}
                        className={clsx(
                          'w-10 h-9 rounded-lg text-sm font-bold transition-all',
                          commandForm.priority === p
                            ? p <= 2 ? 'bg-[var(--nexus-error-50)]0 text-white shadow-sm' : p === 3 ? 'bg-[var(--nexus-warning-50)]0 text-white shadow-sm' : 'bg-[var(--surface-muted)] bg-[var(--surface-muted)] text-[var(--text-secondary)] shadow-sm'
                            : 'bg-[var(--surface-muted)] text-[var(--text-secondary)] hover:bg-[var(--surface-muted)] hover:bg-[var(--interactive-hover)]',
                        )}
                      >
                        {p}
                      </button>
                    ))}
                  </div>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-[var(--text-secondary)] mb-1.5">Timeout (ms)</label>
                  <input
                    type="number"
                    className="enterprise-input w-full"
                    value={commandForm.timeout}
                    onChange={e => setCommandForm(prev => ({ ...prev, timeout: Number(e.target.value) }))}
                    min={1000}
                    step={1000}
                  />
                </div>
              </div>

              {/* Parameters */}
              <div>
                <div className="flex items-center justify-between mb-1.5">
                  <label className="text-xs font-semibold text-[var(--text-secondary)]">Parameters</label>
                  <button
                    onClick={addParameter}
                    className="text-xs font-medium text-violet-600 dark:text-violet-400 hover:text-violet-700 flex items-center gap-1"
                  >
                    <Plus className="w-3 h-3" /> Add
                  </button>
                </div>
                <div className="space-y-2">
                  {commandForm.parameters.map((param, i) => (
                    <div key={i} className="flex items-center gap-2">
                      <input
                        type="text"
                        className="enterprise-input flex-1 text-sm"
                        placeholder="Key"
                        value={param.key}
                        onChange={e => updateParameter(i, 'key', e.target.value)}
                      />
                      <input
                        type="text"
                        className="enterprise-input flex-1 text-sm"
                        placeholder="Value"
                        value={param.value}
                        onChange={e => updateParameter(i, 'value', e.target.value)}
                      />
                      {commandForm.parameters.length > 1 && (
                        <button
                          onClick={() => removeParameter(i)}
                          className="w-8 h-8 rounded-lg flex items-center justify-center text-[var(--text-tertiary)] hover:text-[var(--nexus-error-500)] hover:bg-[var(--nexus-error-50)] dark:hover:bg-[var(--nexus-error-900)]/20 transition-colors"
                        >
                          <X className="w-4 h-4" />
                        </button>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            </div>

            {/* Modal Footer */}
            <div className="flex items-center justify-end gap-3 px-6 py-4 border-t border-[var(--border-default)]">
              <button
                onClick={() => setShowCommandModal(false)}
                className="enterprise-btn enterprise-btn-secondary text-sm"
              >
                Cancel
              </button>
              <button
                onClick={handleSendCommand}
                disabled={cmdLoading || !commandForm.systemId}
                className={clsx(
                  'enterprise-btn enterprise-btn-primary text-sm flex items-center gap-1.5',
                  (!commandForm.systemId || cmdLoading) && 'opacity-50 cursor-not-allowed',
                )}
              >
                {cmdLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
                Send Command
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ════════════════════════════════════════════════════════════════════ */}
      {/* MODAL: Resolve Alert                                               */}
      {/* ════════════════════════════════════════════════════════════════════ */}
      {showResolveModal && (
        <div className="enterprise-modal-overlay">
          <div className="enterprise-modal max-w-md">
            <div className="flex items-center justify-between px-6 py-4 border-b border-[var(--border-default)]">
              <div className="flex items-center gap-2">
                <CheckCircle className="w-5 h-5 text-emerald-500" />
                <h2 className="text-lg font-semibold text-[var(--text-primary)]">Resolve Alert</h2>
              </div>
              <button
                onClick={() => { setShowResolveModal(null); setResolveNotes('') }}
                className="w-8 h-8 rounded-lg flex items-center justify-center text-[var(--text-tertiary)] hover:text-[var(--text-secondary)] hover:bg-[var(--surface-muted)] dark:hover:bg-[var(--surface-muted)] transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
            <div className="p-6">
              <label className="block text-xs font-semibold text-[var(--text-secondary)] mb-1.5">Resolution Notes</label>
              <textarea
                className="enterprise-input w-full h-28 resize-none"
                placeholder="Describe how the alert was resolved..."
                value={resolveNotes}
                onChange={e => setResolveNotes(e.target.value)}
              />
            </div>
            <div className="flex items-center justify-end gap-3 px-6 py-4 border-t border-[var(--border-default)]">
              <button
                onClick={() => { setShowResolveModal(null); setResolveNotes('') }}
                className="enterprise-btn enterprise-btn-secondary text-sm"
              >
                Cancel
              </button>
              <button
                onClick={() => handleResolveAlert(showResolveModal)}
                className="enterprise-btn enterprise-btn-primary text-sm flex items-center gap-1.5"
              >
                <CheckCircle className="w-4 h-4" /> Resolve Alert
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
