import { useState, useEffect, useCallback } from 'react'
import { clsx } from 'clsx'
import {
  Truck, Calendar, Clock, MapPin, Package, Plus, X, Check, AlertTriangle,
  Eye, ArrowRight, CircleDot, Users, BarChart3, CalendarDays, Loader2, Play,
  ChevronLeft, ChevronRight as ChevronRightIcon, Phone, Car, PackageCheck,
} from 'lucide-react'
import EnterpriseBreadcrumbs from '../components/enterprise/EnterpriseBreadcrumbs'
import EnterpriseKPICard from '../components/enterprise/EnterpriseKPICard'
import { useToast } from '../hooks/useToast'
import * as yardApi from '../api/yardManagement'

// ── Types ──

interface DockDoor {
  id: string
  doorNumber: string
  type: 'INBOUND' | 'OUTBOUND' | 'BOTH'
  status: 'AVAILABLE' | 'OCCUPIED' | 'MAINTENANCE' | 'CLOSED'
  currentVehicle?: string
  driverName?: string
  driverPhone?: string
  appointmentId?: string
}

interface YardLocation {
  id: string
  locationCode: string
  type: string
  status: 'AVAILABLE' | 'OCCUPIED' | 'RESERVED' | 'CLOSED'
  currentCount: number
  maxCapacity: number
  zone: string
  vehicleInfo?: string
}

interface Appointment {
  id: string
  appointmentNumber: string
  type: 'INBOUND_DELIVERY' | 'OUTBOUND_PICKUP' | 'RETURN' | 'CROSS_DOCK'
  carrierName: string
  carrierCode: string
  driverName: string
  driverPhone?: string
  trailerNumber?: string
  licensePlate?: string
  status: 'REQUESTED' | 'CONFIRMED' | 'CHECKED_IN' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW'
  dockDoor?: string
  arrivalStart: string
  arrivalEnd: string
  estimatedArrival: string
  estimatedDeparture?: string
  loadCount?: number
  palletCount?: number
  pieceCount?: number
  specialInstructions?: string
}

interface CalendarSlot {
  hour: number
  appointments: Appointment[]
}

interface YardStats {
  dockUtilization: number
  appointmentsToday: number
  inProgressNow: number
  completedToday: number
  noShows: number
}

type ViewMode = 'docks' | 'yard' | 'appointments' | 'calendar' | 'trailers'

interface Trailer {
  id: string
  trailerNumber: string
  carrierId: string
  warehouseId: string
  licensePlate?: string
  status: 'IN_YARD' | 'DOCKED' | 'IN_TRANSIT' | 'MAINTENANCE'
  currentDockDoor?: string
  checkInTime?: string
  dockTime?: string
  checkOutTime?: string
  loaded?: boolean
  palletCount?: number
  sealNumber?: string
  notes?: string
}

interface TrailerEvent {
  id: string
  trailerId: string
  eventType: string
  performedBy?: string
  performedAt: string
  details?: string
}

const warehouses = [
  { id: 'wh-main', name: 'Main Warehouse' },
  { id: 'wh-east', name: 'East DC' },
  { id: 'wh-west', name: 'West DC' },
]

const dockStatusColors: Record<string, string> = {
  AVAILABLE: 'bg-emerald-100 text-emerald-700 ring-emerald-600/20 dark:bg-emerald-900/30 dark:text-emerald-400',
  OCCUPIED: 'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-700)] ring-blue-600/20 dark:bg-[var(--nexus-primary-900)]/30 dark:text-[var(--nexus-primary-400)]',
  MAINTENANCE: 'bg-[var(--nexus-warning-100)] text-[var(--nexus-warning-700)] ring-amber-600/20 dark:bg-[var(--nexus-warning-900)]/30 dark:text-[var(--nexus-warning-400)]',
  CLOSED: 'bg-[var(--surface-muted)] text-[var(--text-secondary)] ring-gray-500/20 bg-[var(--surface-muted)] dark:text-[var(--text-tertiary)]',
}

const dockStatusBg: Record<string, string> = {
  AVAILABLE: 'border-emerald-300 bg-emerald-50/50 dark:border-emerald-700 dark:bg-emerald-900/10',
  OCCUPIED: 'border-[var(--nexus-primary-300)] bg-[var(--nexus-primary-50)]/50 dark:border-[var(--nexus-primary-700)] dark:bg-[var(--nexus-primary-900)]/10',
  MAINTENANCE: 'border-[var(--nexus-warning-300)] bg-[var(--nexus-warning-50)]/50 dark:border-[var(--nexus-warning-700)] dark:bg-[var(--nexus-warning-900)]/10',
  CLOSED: 'border-[var(--border-default)] bg-[var(--surface-sunken)]/50 border-[var(--border-default)] bg-[var(--surface-base)]/50',
}

const appointmentStatusColors: Record<string, string> = {
  REQUESTED: 'bg-[var(--nexus-warning-100)] text-[var(--nexus-warning-700)] dark:bg-[var(--nexus-warning-900)]/30 dark:text-[var(--nexus-warning-400)]',
  CONFIRMED: 'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-700)] dark:bg-[var(--nexus-primary-900)]/30 dark:text-[var(--nexus-primary-400)]',
  CHECKED_IN: 'bg-[var(--nexus-ai-100)] text-[var(--nexus-ai-700)] dark:bg-[var(--nexus-ai-900)]/30 dark:text-[var(--nexus-ai-400)]',
  IN_PROGRESS: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400',
  COMPLETED: 'bg-[var(--surface-muted)] text-[var(--text-secondary)] bg-[var(--surface-muted)] dark:text-[var(--text-tertiary)]',
  CANCELLED: 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-700)] dark:bg-[var(--nexus-error-900)]/30 dark:text-[var(--nexus-error-400)]',
  NO_SHOW: 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-700)] dark:bg-[var(--nexus-error-900)]/30 dark:text-[var(--nexus-error-400)]',
}

const yardStatusColors: Record<string, string> = {
  AVAILABLE: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400',
  OCCUPIED: 'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-700)] dark:bg-[var(--nexus-primary-900)]/30 dark:text-[var(--nexus-primary-400)]',
  RESERVED: 'bg-[var(--nexus-warning-100)] text-[var(--nexus-warning-700)] dark:bg-[var(--nexus-warning-900)]/30 dark:text-[var(--nexus-warning-400)]',
  CLOSED: 'bg-[var(--surface-muted)] text-[var(--text-secondary)] bg-[var(--surface-muted)] dark:text-[var(--text-tertiary)]',
}

const typeLabels: Record<string, string> = {
  INBOUND_DELIVERY: 'Inbound Delivery',
  OUTBOUND_PICKUP: 'Outbound Pickup',
  RETURN: 'Return',
  CROSS_DOCK: 'Cross Dock',
  INBOUND: 'Inbound',
  OUTBOUND: 'Outbound',
  BOTH: 'Both',
}

const defaultAppointmentForm = () => ({
  type: 'INBOUND_DELIVERY',
  carrierName: '',
  carrierCode: '',
  trailerNumber: '',
  licensePlate: '',
  driverName: '',
  driverPhone: '',
  estimatedArrival: '',
  estimatedDeparture: '',
  loadCount: 0,
  palletCount: 0,
  pieceCount: 0,
  specialInstructions: '',
})

// ── Mock data generators (for when API returns empty) ──

function generateMockDoors(): DockDoor[] {
  const statuses: DockDoor['status'][] = ['AVAILABLE', 'OCCUPIED', 'MAINTENANCE', 'AVAILABLE', 'AVAILABLE', 'OCCUPIED', 'AVAILABLE', 'CLOSED', 'AVAILABLE', 'OCCUPIED', 'AVAILABLE', 'AVAILABLE']
  const types: DockDoor['type'][] = ['INBOUND', 'OUTBOUND', 'BOTH', 'INBOUND', 'OUTBOUND', 'INBOUND', 'BOTH', 'OUTBOUND', 'INBOUND', 'OUTBOUND', 'INBOUND', 'BOTH']
  const vehicles = ['TRK-4521', 'TRK-8834', 'TRK-1192', '', '', 'TRK-6671', '', '', 'TRK-3340', '', '', '']
  const drivers = ['Mike Johnson', 'Sarah Lee', 'Carlos Ruiz', '', '', 'Tom Chen', '', '', 'Anna Kim', '', '', '']

  return Array.from({ length: 12 }, (_, i) => ({
    id: `door-${i + 1}`,
    doorNumber: `D-${String(i + 1).padStart(2, '0')}`,
    type: types[i],
    status: statuses[i],
    currentVehicle: vehicles[i] || undefined,
    driverName: drivers[i] || undefined,
    driverPhone: drivers[i] ? '555-01' + String(i).padStart(2, '0') : undefined,
  }))
}

function generateMockYardLocations(): YardLocation[] {
  const zones = ['A', 'A', 'A', 'B', 'B', 'B', 'C', 'C', 'D', 'D']
  const types = ['TRAILER', 'TRAILER', 'STAGING', 'STAGING', 'PARKING', 'PARKING', 'TRAILER', 'STAGING', 'PARKING', 'TRAILER']
  const statuses: YardLocation['status'][] = ['AVAILABLE', 'OCCUPIED', 'OCCUPIED', 'AVAILABLE', 'RESERVED', 'AVAILABLE', 'OCCUPIED', 'AVAILABLE', 'CLOSED', 'AVAILABLE']
  const counts = [0, 3, 5, 0, 2, 0, 4, 0, 0, 1]
  const maxes = [8, 8, 6, 8, 4, 10, 8, 6, 12, 8]
  const vehicles = ['', 'TRK-4521', 'TRK-8834', '', 'TRK-1192', '', 'TRK-6671', '', '', 'TRK-3340']

  return Array.from({ length: 10 }, (_, i) => ({
    id: `yard-${i + 1}`,
    locationCode: `${zones[i]}-${String(i + 1).padStart(2, '0')}`,
    type: types[i],
    status: statuses[i],
    currentCount: counts[i],
    maxCapacity: maxes[i],
    zone: zones[i],
    vehicleInfo: vehicles[i] || undefined,
  }))
}

function generateMockAppointments(): Appointment[] {
  const now = new Date()
  return [
    {
      id: 'apt-1', appointmentNumber: 'APT-2026-001', type: 'INBOUND_DELIVERY',
      carrierName: 'FastFreight Logistics', carrierCode: 'FFL', driverName: 'Mike Johnson',
      driverPhone: '555-0101', trailerNumber: 'TRL-9921', licensePlate: 'TRK-4521',
      status: 'IN_PROGRESS', dockDoor: 'D-03', arrivalStart: `${now.toISOString().slice(0, 10)}T08:00`,
      arrivalEnd: `${now.toISOString().slice(0, 10)}T09:00`, estimatedArrival: `${now.toISOString().slice(0, 10)}T08:15`,
      estimatedDeparture: `${now.toISOString().slice(0, 10)}T11:00`, loadCount: 2, palletCount: 18, pieceCount: 340,
    },
    {
      id: 'apt-2', appointmentNumber: 'APT-2026-002', type: 'OUTBOUND_PICKUP',
      carrierName: 'Swift Transport', carrierCode: 'SWF', driverName: 'Sarah Lee',
      driverPhone: '555-0102', trailerNumber: 'TRL-4412', licensePlate: 'TRK-8834',
      status: 'CHECKED_IN', dockDoor: 'D-05', arrivalStart: `${now.toISOString().slice(0, 10)}T09:00`,
      arrivalEnd: `${now.toISOString().slice(0, 10)}T10:00`, estimatedArrival: `${now.toISOString().slice(0, 10)}T09:30`,
      loadCount: 3, palletCount: 22, pieceCount: 520,
    },
    {
      id: 'apt-3', appointmentNumber: 'APT-2026-003', type: 'CROSS_DOCK',
      carrierName: 'Regional Haulers', carrierCode: 'RHL', driverName: 'Carlos Ruiz',
      driverPhone: '555-0103', trailerNumber: 'TRL-7733', licensePlate: 'TRK-1192',
      status: 'CONFIRMED', arrivalStart: `${now.toISOString().slice(0, 10)}T10:00`,
      arrivalEnd: `${now.toISOString().slice(0, 10)}T11:00`, estimatedArrival: `${now.toISOString().slice(0, 10)}T10:30`,
      loadCount: 1, palletCount: 12, pieceCount: 200,
    },
    {
      id: 'apt-4', appointmentNumber: 'APT-2026-004', type: 'RETURN',
      carrierName: 'Metro Delivery', carrierCode: 'MTD', driverName: 'Lisa Park',
      driverPhone: '555-0104', licensePlate: 'TRK-5567',
      status: 'REQUESTED', arrivalStart: `${now.toISOString().slice(0, 10)}T11:00`,
      arrivalEnd: `${now.toISOString().slice(0, 10)}T12:00`, estimatedArrival: `${now.toISOString().slice(0, 10)}T11:30`,
      palletCount: 6, pieceCount: 85,
    },
    {
      id: 'apt-5', appointmentNumber: 'APT-2026-005', type: 'INBOUND_DELIVERY',
      carrierName: 'Pacific Coast Freight', carrierCode: 'PCF', driverName: 'Tom Chen',
      driverPhone: '555-0105', trailerNumber: 'TRL-2288', licensePlate: 'TRK-6671',
      status: 'COMPLETED', dockDoor: 'D-02', arrivalStart: `${now.toISOString().slice(0, 10)}T06:00`,
      arrivalEnd: `${now.toISOString().slice(0, 10)}T07:00`, estimatedArrival: `${now.toISOString().slice(0, 10)}T06:30`,
      loadCount: 4, palletCount: 30, pieceCount: 610,
    },
    {
      id: 'apt-6', appointmentNumber: 'APT-2026-006', type: 'OUTBOUND_PICKUP',
      carrierName: 'National Express', carrierCode: 'NEX', driverName: 'Anna Kim',
      driverPhone: '555-0106', licensePlate: 'TRK-3340',
      status: 'CONFIRMED', arrivalStart: `${now.toISOString().slice(0, 10)}T13:00`,
      arrivalEnd: `${now.toISOString().slice(0, 10)}T14:00`, estimatedArrival: `${now.toISOString().slice(0, 10)}T13:15`,
      loadCount: 2, palletCount: 14, pieceCount: 280,
    },
  ]
}

const trailerStatusColors: Record<string, string> = {
  IN_YARD: 'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-700)] dark:bg-[var(--nexus-primary-900)]/30 dark:text-[var(--nexus-primary-400)]',
  DOCKED: 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-700)] dark:bg-[var(--nexus-success-900)]/30 dark:text-[var(--nexus-success-400)]',
  IN_TRANSIT: 'bg-[var(--nexus-ai-100)] text-[var(--nexus-ai-700)] dark:bg-[var(--nexus-ai-900)]/30 dark:text-[var(--nexus-ai-400)]',
  MAINTENANCE: 'bg-[var(--nexus-warning-100)] text-[var(--nexus-warning-700)] dark:bg-[var(--nexus-warning-900)]/30 dark:text-[var(--nexus-warning-400)]',
}

const defaultTrailerForm = () => ({
  trailerNumber: '',
  carrierId: '',
  licensePlate: '',
})

function generateMockTrailers(): Trailer[] {
  const now = new Date().toISOString()
  const earlier = new Date(Date.now() - 3600000).toISOString()
  return [
    { id: 'trl-1', trailerNumber: 'TRL-9921', carrierId: 'FFL', warehouseId: 'wh-main', licensePlate: 'TRK-4521', status: 'DOCKED', currentDockDoor: 'D-03', checkInTime: earlier, dockTime: earlier, loaded: true, palletCount: 18, sealNumber: 'SL-2201' },
    { id: 'trl-2', trailerNumber: 'TRL-4412', carrierId: 'SWF', warehouseId: 'wh-main', licensePlate: 'TRK-8834', status: 'DOCKED', currentDockDoor: 'D-05', checkInTime: earlier, dockTime: earlier, loaded: false },
    { id: 'trl-3', trailerNumber: 'TRL-7733', carrierId: 'RHL', warehouseId: 'wh-main', licensePlate: 'TRK-1192', status: 'IN_YARD', checkInTime: earlier, loaded: false },
    { id: 'trl-4', trailerNumber: 'TRL-2288', carrierId: 'PCF', warehouseId: 'wh-main', licensePlate: 'TRK-6671', status: 'IN_YARD', checkInTime: now, loaded: true, palletCount: 30 },
    { id: 'trl-5', trailerNumber: 'TRL-5567', carrierId: 'MTD', warehouseId: 'wh-main', licensePlate: 'TRK-5567', status: 'MAINTENANCE', notes: 'Tire replacement needed' },
    { id: 'trl-6', trailerNumber: 'TRL-1100', carrierId: 'NEX', warehouseId: 'wh-main', status: 'IN_TRANSIT', checkOutTime: now, loaded: true, palletCount: 14, sealNumber: 'SL-2205' },
  ]
}

function generateMockTrailerEvents(): TrailerEvent[] {
  const now = new Date().toISOString()
  const earlier = new Date(Date.now() - 3600000).toISOString()
  const earlier2 = new Date(Date.now() - 7200000).toISOString()
  return [
    { id: 'te-1', trailerId: 'trl-1', eventType: 'CHECKED_IN', performedBy: 'yard-manager', performedAt: earlier2, details: 'Arrived at gate A' },
    { id: 'te-2', trailerId: 'trl-1', eventType: 'DOCKED', performedBy: 'yard-manager', performedAt: earlier, details: 'Assigned to D-03' },
    { id: 'te-3', trailerId: 'trl-3', eventType: 'CHECKED_IN', performedBy: 'gate-guard', performedAt: earlier, details: 'Arrived at gate B' },
    { id: 'te-4', trailerId: 'trl-5', eventType: 'MAINTENANCE_FLAG', performedBy: 'yard-manager', performedAt: now, details: 'Tire damage reported' },
  ]
}

function generateMockTrailerStats() {
  return { total: 6, inYard: 2, docked: 2, inTransit: 1, maintenance: 1 }
}

function generateMockStats(): YardStats {
  return { dockUtilization: 72, appointmentsToday: 12, inProgressNow: 3, completedToday: 7, noShows: 1 }
}

// ── Helpers ──

function todayString() {
  return new Date().toISOString().slice(0, 10)
}

function formatTime(dateStr: string) {
  try {
    const d = new Date(dateStr)
    return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  } catch {
    return dateStr
  }
}

// ── Component ──

export default function YardDockPage() {
  const { addToast } = useToast()

  const [loading, setLoading] = useState(true)
  const [selectedWarehouse, setSelectedWarehouse] = useState('wh-main')
  const [selectedDate, setSelectedDate] = useState(todayString())
  const [activeView, setActiveView] = useState<ViewMode>('docks')

  const [dockDoors, setDockDoors] = useState<DockDoor[]>([])
  const [yardLocations, setYardLocations] = useState<YardLocation[]>([])
  const [appointments, setAppointments] = useState<Appointment[]>([])
  const [calendarSlots, setCalendarSlots] = useState<CalendarSlot[]>([])
  const [stats, setStats] = useState<YardStats>(generateMockStats())

  const [showAppointmentModal, setShowAppointmentModal] = useState(false)
  const [aptForm, setAptForm] = useState(defaultAppointmentForm())
  const [saving, setSaving] = useState(false)

  const [selectedSlotHour, setSelectedSlotHour] = useState<number | null>(null)

  // ── Trailer state ──
  const [trailers, setTrailers] = useState<Trailer[]>([])
  const [trailerStats, setTrailerStats] = useState({ total: 0, inYard: 0, docked: 0, inTransit: 0, maintenance: 0 })
  const [trailerSearch, setTrailerSearch] = useState('')
  const [trailerStatusFilter, setTrailerStatusFilter] = useState<string>('ALL')
  const [showCheckInModal, setShowCheckInModal] = useState(false)
  const [checkInForm, setCheckInForm] = useState(defaultTrailerForm())
  const [selectedTrailerId, setSelectedTrailerId] = useState<string | null>(null)
  const [trailerEvents, setTrailerEvents] = useState<TrailerEvent[]>([])
  const [showEventsModal, setShowEventsModal] = useState(false)

  const loadData = useCallback(async () => {
    setLoading(true)
    try {
      const [doorsRes, yardRes, aptsRes, statsRes, calRes] = await Promise.allSettled([
        yardApi.getDockDoors(selectedWarehouse),
        yardApi.getYardLocations(selectedWarehouse),
        yardApi.getAppointments(selectedWarehouse),
        yardApi.getAppointmentStats(selectedWarehouse),
        yardApi.getAppointmentCalendar(selectedWarehouse, selectedDate),
      ])

      const doorsData = doorsRes.status === 'fulfilled' ? (doorsRes.value.data as DockDoor[]) : null
      const yardData = yardRes.status === 'fulfilled' ? (yardRes.value.data as YardLocation[]) : null
      const aptsData = aptsRes.status === 'fulfilled' ? (aptsRes.value.data as Appointment[]) : null
      const statsData = statsRes.status === 'fulfilled' ? statsRes.value.data : null
      const calData = calRes.status === 'fulfilled' ? calRes.value.data : null

      setDockDoors(doorsData && doorsData.length > 0 ? doorsData : generateMockDoors())
      setYardLocations(yardData && yardData.length > 0 ? yardData : generateMockYardLocations())
      setAppointments(aptsData && aptsData.length > 0 ? aptsData : generateMockAppointments())
      setStats(statsData && typeof statsData.dockUtilization === 'number' ? statsData : generateMockStats())

      if (calData && Array.isArray(calData)) {
        setCalendarSlots(calData)
      } else {
        buildCalendarSlots(aptsData && aptsData.length > 0 ? aptsData : generateMockAppointments())
      }

      setTrailers(generateMockTrailers())
      setTrailerStats(generateMockTrailerStats())
    } catch {
      setDockDoors(generateMockDoors())
      setYardLocations(generateMockYardLocations())
      setAppointments(generateMockAppointments())
      setStats(generateMockStats())
      buildCalendarSlots(generateMockAppointments())
      setTrailers(generateMockTrailers())
      setTrailerStats(generateMockTrailerStats())
    } finally {
      setLoading(false)
    }
  }, [selectedWarehouse, selectedDate])

  useEffect(() => { loadData() }, [loadData])

  function buildCalendarSlots(apts: Appointment[]) {
    const today = selectedDate
    const slots: CalendarSlot[] = Array.from({ length: 17 }, (_, i) => ({ hour: 6 + i, appointments: [] }))
    apts.forEach((apt) => {
      const aptDate = apt.estimatedArrival?.slice(0, 10)
      if (aptDate === today) {
        const hour = new Date(apt.estimatedArrival).getHours()
        const idx = hour - 6
        if (idx >= 0 && idx < slots.length) {
          slots[idx].appointments.push(apt)
        }
      }
    })
    setCalendarSlots(slots)
  }

  // ── Appointment actions ──

  async function handleConfirmAppointment(id: string) {
    try {
      await yardApi.confirmAppointment(id)
      addToast({ type: 'success', title: 'Appointment confirmed' })
      await loadData()
    } catch {
      addToast({ type: 'error', title: 'Failed to confirm appointment' })
    }
  }

  async function handleCheckIn(id: string) {
    try {
      await yardApi.checkInAppointment(id, 'yard-manager')
      addToast({ type: 'success', title: 'Driver checked in' })
      await loadData()
    } catch {
      addToast({ type: 'error', title: 'Failed to check in' })
    }
  }

  async function handleStartAppointment(id: string) {
    try {
      await yardApi.startAppointment(id)
      addToast({ type: 'success', title: 'Appointment started' })
      await loadData()
    } catch {
      addToast({ type: 'error', title: 'Failed to start appointment' })
    }
  }

  async function handleCompleteAppointment(id: string) {
    try {
      await yardApi.completeAppointment(id, 'yard-manager')
      addToast({ type: 'success', title: 'Appointment completed' })
      await loadData()
    } catch {
      addToast({ type: 'error', title: 'Failed to complete appointment' })
    }
  }

  async function handleCancelAppointment(id: string) {
    try {
      await yardApi.cancelAppointment(id)
      addToast({ type: 'success', title: 'Appointment cancelled' })
      await loadData()
    } catch {
      addToast({ type: 'error', title: 'Failed to cancel appointment' })
    }
  }

  async function handleNoShow(id: string) {
    try {
      await yardApi.markNoShow(id)
      addToast({ type: 'success', title: 'Marked as no show' })
      await loadData()
    } catch {
      addToast({ type: 'error', title: 'Failed to mark no show' })
    }
  }

  async function handleReleaseDoor(doorId: string) {
    try {
      await yardApi.releaseDoor(doorId)
      addToast({ type: 'success', title: 'Dock door released' })
      await loadData()
    } catch {
      addToast({ type: 'error', title: 'Failed to release door' })
    }
  }

  async function handleReleaseYard(locId: string) {
    try {
      await yardApi.releaseYard(locId)
      addToast({ type: 'success', title: 'Yard location released' })
      await loadData()
    } catch {
      addToast({ type: 'error', title: 'Failed to release yard location' })
    }
  }

  async function handleCreateAppointment() {
    if (!aptForm.carrierName.trim() || !aptForm.driverName.trim() || !aptForm.estimatedArrival) {
      addToast({ type: 'warning', title: 'Carrier, driver, and arrival time are required' })
      return
    }
    setSaving(true)
    try {
      await yardApi.requestAppointment({
        warehouseId: selectedWarehouse,
        ...aptForm,
        loadCount: Number(aptForm.loadCount),
        palletCount: Number(aptForm.palletCount),
        pieceCount: Number(aptForm.pieceCount),
      })
      addToast({ type: 'success', title: 'Appointment created' })
      setShowAppointmentModal(false)
      setAptForm(defaultAppointmentForm())
      await loadData()
    } catch {
      addToast({ type: 'error', title: 'Failed to create appointment' })
    } finally {
      setSaving(false)
    }
  }

  async function handleTrailerCheckIn() {
    if (!checkInForm.trailerNumber.trim() || !checkInForm.carrierId.trim()) {
      addToast({ type: 'warning', title: 'Trailer number and carrier are required' })
      return
    }
    setSaving(true)
    try {
      await yardApi.checkInTrailer(selectedWarehouse, {
        trailerNumber: checkInForm.trailerNumber,
        carrierId: checkInForm.carrierId,
        licensePlate: checkInForm.licensePlate,
      })
      addToast({ type: 'success', title: 'Trailer checked in' })
      setShowCheckInModal(false)
      setCheckInForm(defaultTrailerForm())
      setTrailers((prev) => [
        {
          id: `trl-${Date.now()}`,
          trailerNumber: checkInForm.trailerNumber,
          carrierId: checkInForm.carrierId,
          warehouseId: selectedWarehouse,
          licensePlate: checkInForm.licensePlate,
          status: 'IN_YARD' as const,
          checkInTime: new Date().toISOString(),
          loaded: false,
        },
        ...prev,
      ])
      setTrailerStats((prev) => ({ ...prev, total: prev.total + 1, inYard: prev.inYard + 1 }))
    } catch {
      addToast({ type: 'error', title: 'Failed to check in trailer' })
    } finally {
      setSaving(false)
    }
  }

  async function handleDockTrailer(trailerId: string) {
    const doorNum = prompt('Enter dock door number (e.g. D-01):')
    if (!doorNum) return
    try {
      await yardApi.dockTrailer(trailerId, doorNum)
      addToast({ type: 'success', title: `Trailer docked at ${doorNum}` })
      setTrailers((prev) =>
        prev.map((t) =>
          t.id === trailerId
            ? { ...t, status: 'DOCKED' as const, currentDockDoor: doorNum, dockTime: new Date().toISOString() }
            : t,
        ),
      )
      setTrailerStats((prev) => ({ ...prev, inYard: Math.max(0, prev.inYard - 1), docked: prev.docked + 1 }))
    } catch {
      addToast({ type: 'error', title: 'Failed to dock trailer' })
    }
  }

  async function handleCheckOutTrailer(trailerId: string) {
    try {
      await yardApi.checkOutTrailer(trailerId)
      addToast({ type: 'success', title: 'Trailer checked out' })
      setTrailers((prev) =>
        prev.map((t) =>
          t.id === trailerId
            ? { ...t, status: 'IN_TRANSIT' as const, checkOutTime: new Date().toISOString() }
            : t,
        ),
      )
      setTrailerStats((prev) => ({ ...prev, docked: Math.max(0, prev.docked - 1), inTransit: prev.inTransit + 1 }))
    } catch {
      addToast({ type: 'error', title: 'Failed to check out trailer' })
    }
  }

  function handleShowTrailerEvents(trailerId: string) {
    setSelectedTrailerId(trailerId)
    setTrailerEvents(
      generateMockTrailerEvents().filter((e) => e.trailerId === trailerId),
    )
    setShowEventsModal(true)
  }

  // ── Dock Doors View ──

  function renderDockDoorsView() {
    if (loading) {
      return (
        <div className="flex items-center justify-center py-20">
          <Loader2 className="w-8 h-8 animate-spin text-[var(--nexus-primary-500)]" />
        </div>
      )
    }

    return (
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <h3 className="text-sm font-semibold text-[var(--text-secondary)]">Dock Doors</h3>
            <span className="text-xs text-[var(--text-secondary)]">{dockDoors.length} doors</span>
            <span className="text-xs text-emerald-600 dark:text-emerald-400 font-medium">
              {dockDoors.filter((d) => d.status === 'AVAILABLE').length} available
            </span>
          </div>
          <button className="enterprise-btn enterprise-btn-primary text-xs" onClick={() => addToast({ type: 'info', title: 'Add Dock Door' })}>
            <Plus className="w-3.5 h-3.5" /> Add Dock Door
          </button>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {dockDoors.map((door) => (
            <div
              key={door.id}
              className={clsx(
                'rounded-xl border-2 p-4 transition-all duration-150',
                dockStatusBg[door.status],
                door.status === 'OCCUPIED' && 'hover:shadow-md',
              )}
            >
              <div className="flex items-start justify-between mb-3">
                <div className="flex items-center gap-2">
                  <div className={clsx(
                    'w-9 h-9 rounded-lg flex items-center justify-center',
                    door.status === 'AVAILABLE' && 'bg-emerald-100 dark:bg-emerald-900/40',
                    door.status === 'OCCUPIED' && 'bg-[var(--nexus-primary-100)] dark:bg-[var(--nexus-primary-900)]/40',
                    door.status === 'MAINTENANCE' && 'bg-[var(--nexus-warning-100)] dark:bg-[var(--nexus-warning-900)]/40',
                    door.status === 'CLOSED' && 'bg-[var(--surface-muted)]',
                  )}>
                    <Truck className={clsx(
                      'w-4.5 h-4.5',
                      door.status === 'AVAILABLE' && 'text-emerald-600 dark:text-emerald-400',
                      door.status === 'OCCUPIED' && 'text-[var(--nexus-primary-600)] dark:text-[var(--nexus-primary-400)]',
                      door.status === 'MAINTENANCE' && 'text-[var(--nexus-warning-600)] dark:text-[var(--nexus-warning-400)]',
                      door.status === 'CLOSED' && 'text-[var(--text-tertiary)]',
                    )} />
                  </div>
                  <div>
                    <p className="text-sm font-bold text-[var(--text-primary)]">{door.doorNumber}</p>
                    <p className="text-xs text-[var(--text-secondary)]">{typeLabels[door.type] || door.type}</p>
                  </div>
                </div>
                <span className={clsx(
                  'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ring-1 ring-inset',
                  dockStatusColors[door.status],
                )}>
                  {door.status}
                </span>
              </div>

              {door.status === 'OCCUPIED' && door.currentVehicle && (
                <div className="mt-3 pt-3 border-t border-[var(--nexus-primary-200)]/50 dark:border-[var(--nexus-primary-700)]/30 space-y-2">
                  <div className="flex items-center gap-2 text-sm">
                    <Car className="w-4 h-4 text-[var(--nexus-primary-500)]" />
                    <span className="font-medium text-[var(--text-primary)]">{door.currentVehicle}</span>
                  </div>
                  {door.driverName && (
                    <div className="flex items-center gap-2 text-sm">
                      <Users className="w-4 h-4 text-[var(--nexus-primary-500)]" />
                      <span className="text-[var(--text-secondary)]">{door.driverName}</span>
                    </div>
                  )}
                  {door.driverPhone && (
                    <div className="flex items-center gap-2 text-sm">
                      <Phone className="w-4 h-4 text-[var(--nexus-primary-500)]" />
                      <span className="text-[var(--text-secondary)] text-xs">{door.driverPhone}</span>
                    </div>
                  )}
                  <button
                    onClick={() => handleReleaseDoor(door.id)}
                    className="w-full mt-2 px-3 py-1.5 text-xs font-medium rounded-lg bg-[var(--nexus-primary-600)] text-white hover:bg-[var(--nexus-primary-700)] transition-colors"
                  >
                    Release Door
                  </button>
                </div>
              )}

              {door.status === 'MAINTENANCE' && (
                <div className="mt-3 pt-3 border-t border-[var(--nexus-warning-200)]/50 dark:border-[var(--nexus-warning-700)]/30">
                  <div className="flex items-center gap-2 text-xs text-[var(--nexus-warning-600)] dark:text-[var(--nexus-warning-400)]">
                    <AlertTriangle className="w-3.5 h-3.5" />
                    Under maintenance
                  </div>
                </div>
              )}

              {door.status === 'AVAILABLE' && (
                <div className="mt-3 pt-3 border-t border-emerald-200/50 dark:border-emerald-700/30">
                  <div className="flex items-center gap-2 text-xs text-emerald-600 dark:text-emerald-400">
                    <CircleDot className="w-3.5 h-3.5" />
                    Ready for assignment
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      </div>
    )
  }

  // ── Yard View ──

  function renderYardView() {
    const zones = [...new Set(yardLocations.map((y) => y.zone))].sort()

    return (
      <div className="space-y-6">
        {zones.map((zone) => {
          const zoneLocs = yardLocations.filter((y) => y.zone === zone)
          const occupied = zoneLocs.filter((y) => y.status === 'OCCUPIED').length
          return (
            <div key={zone}>
              <div className="flex items-center gap-3 mb-3">
                <div className="w-8 h-8 rounded-lg bg-[var(--nexus-primary-100)] dark:bg-[var(--nexus-primary-900)]/40 flex items-center justify-center">
                  <span className="text-sm font-bold text-[var(--nexus-primary-600)] dark:text-[var(--nexus-primary-400)]">{zone}</span>
                </div>
                <h3 className="text-sm font-semibold text-[var(--text-secondary)]">Zone {zone}</h3>
                <span className="text-xs text-[var(--text-secondary)]">
                  {occupied}/{zoneLocs.length} occupied
                </span>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
                {zoneLocs.map((loc) => {
                  const utilPct = loc.maxCapacity > 0 ? Math.round((loc.currentCount / loc.maxCapacity) * 100) : 0
                  return (
                    <div
                      key={loc.id}
                      className={clsx(
                        'rounded-xl border p-4 transition-all hover:shadow-sm',
                        loc.status === 'AVAILABLE' && 'border-emerald-200 dark:border-emerald-800',
                        loc.status === 'OCCUPIED' && 'border-[var(--nexus-primary-200)] dark:border-[var(--nexus-primary-800)] bg-[var(--nexus-primary-50)]/30 dark:bg-[var(--nexus-primary-900)]/10',
                        loc.status === 'RESERVED' && 'border-[var(--nexus-warning-200)] dark:border-[var(--nexus-warning-800)] bg-[var(--nexus-warning-50)]/30 dark:bg-[var(--nexus-warning-900)]/10',
                        loc.status === 'CLOSED' && 'border-[var(--border-default)] opacity-60',
                      )}
                    >
                      <div className="flex items-start justify-between mb-2">
                        <div>
                          <p className="text-sm font-bold text-[var(--text-primary)]">{loc.locationCode}</p>
                          <p className="text-xs text-[var(--text-secondary)]">{loc.type}</p>
                        </div>
                        <span className={clsx(
                          'inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium',
                          yardStatusColors[loc.status],
                        )}>
                          {loc.status}
                        </span>
                      </div>

                      <div className="mt-3">
                        <div className="flex items-center justify-between text-xs mb-1">
                          <span className="text-[var(--text-secondary)]">Capacity</span>
                          <span className="font-medium text-[var(--text-secondary)]">
                            {loc.currentCount}/{loc.maxCapacity}
                          </span>
                        </div>
                        <div className="w-full h-2 bg-[var(--surface-muted)] bg-[var(--surface-muted)] rounded-full overflow-hidden">
                          <div
                            className={clsx(
                              'h-full rounded-full transition-all',
                              utilPct > 80 ? 'bg-[var(--nexus-error-50)]0' : utilPct > 50 ? 'bg-[var(--nexus-warning-50)]0' : 'bg-emerald-500',
                            )}
                            style={{ width: `${Math.min(utilPct, 100)}%` }}
                          />
                        </div>
                      </div>

                      {loc.vehicleInfo && (
                        <div className="mt-2 flex items-center gap-1.5 text-xs text-[var(--nexus-primary-600)] dark:text-[var(--nexus-primary-400)]">
                          <Car className="w-3.5 h-3.5" />
                          {loc.vehicleInfo}
                        </div>
                      )}

                      <div className="mt-3 flex gap-2">
                        {loc.status === 'AVAILABLE' && (
                          <button className="flex-1 px-2 py-1 text-xs font-medium rounded-lg bg-emerald-600 text-white hover:bg-emerald-700 transition-colors">
                            Assign
                          </button>
                        )}
                        {loc.status === 'OCCUPIED' && (
                          <button
                            onClick={() => handleReleaseYard(loc.id)}
                            className="flex-1 px-2 py-1 text-xs font-medium rounded-lg bg-[var(--surface-muted)] text-white hover:bg-[var(--surface-muted)] transition-colors"
                          >
                            Release
                          </button>
                        )}
                        {loc.status === 'RESERVED' && (
                          <button
                            onClick={() => handleReleaseYard(loc.id)}
                            className="flex-1 px-2 py-1 text-xs font-medium rounded-lg bg-[var(--nexus-warning-600)] text-white hover:bg-[var(--nexus-warning-700)] transition-colors"
                          >
                            Cancel Reserve
                          </button>
                        )}
                      </div>
                    </div>
                  )
                })}
              </div>
            </div>
          )
        })}
      </div>
    )
  }

  // ── Appointments View ──

  function renderAppointmentsView() {
    return (
      <div className="space-y-4">
        <div className="overflow-x-auto rounded-xl border border-[var(--border-default)]">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-[var(--surface-sunken)] bg-[var(--surface-base)]/50 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">
                <th className="px-4 py-3">Appointment #</th>
                <th className="px-4 py-3">Type</th>
                <th className="px-4 py-3">Carrier</th>
                <th className="px-4 py-3">Driver</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Dock</th>
                <th className="px-4 py-3">Arrival Window</th>
                <th className="px-4 py-3">ETA</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-gray-700/50">
              {appointments.map((apt) => (
                <tr key={apt.id} className="hover:bg-[var(--surface-sunken)]/50 hover:bg-[var(--surface-base)]/30 transition-colors">
                  <td className="px-4 py-3 font-medium text-[var(--text-primary)]">{apt.appointmentNumber}</td>
                  <td className="px-4 py-3">
                    <span className="text-xs font-medium text-[var(--text-secondary)]">
                      {typeLabels[apt.type] || apt.type}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <div>
                      <p className="text-sm text-[var(--text-primary)]">{apt.carrierName}</p>
                      <p className="text-xs text-[var(--text-secondary)]">{apt.carrierCode}</p>
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <div>
                      <p className="text-sm text-[var(--text-primary)]">{apt.driverName}</p>
                      {apt.driverPhone && (
                        <p className="text-xs text-[var(--text-secondary)]">{apt.driverPhone}</p>
                      )}
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <span className={clsx(
                      'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold',
                      appointmentStatusColors[apt.status],
                    )}>
                      {apt.status.replace(/_/g, ' ')}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-sm font-medium text-[var(--text-secondary)]">{apt.dockDoor || '—'}</td>
                  <td className="px-4 py-3 text-xs text-[var(--text-secondary)]">
                    {formatTime(apt.arrivalStart)} – {formatTime(apt.arrivalEnd)}
                  </td>
                  <td className="px-4 py-3 text-sm font-medium text-[var(--text-secondary)]">
                    {formatTime(apt.estimatedArrival)}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center justify-end gap-1.5">
                      {apt.status === 'REQUESTED' && (
                        <button
                          onClick={() => handleConfirmAppointment(apt.id)}
                          className="px-2.5 py-1 text-xs font-medium rounded-lg bg-[var(--nexus-primary-600)] text-white hover:bg-[var(--nexus-primary-700)] transition-colors"
                        >
                          <Check className="w-3 h-3 inline mr-1" /> Confirm
                        </button>
                      )}
                      {apt.status === 'CONFIRMED' && (
                        <button
                          onClick={() => handleCheckIn(apt.id)}
                          className="px-2.5 py-1 text-xs font-medium rounded-lg bg-[var(--nexus-ai-600)] text-white hover:bg-[var(--nexus-ai-700)] transition-colors"
                        >
                          <Eye className="w-3 h-3 inline mr-1" /> Check In
                        </button>
                      )}
                      {apt.status === 'CHECKED_IN' && (
                        <button
                          onClick={() => handleStartAppointment(apt.id)}
                          className="px-2.5 py-1 text-xs font-medium rounded-lg bg-emerald-600 text-white hover:bg-emerald-700 transition-colors"
                        >
                          <Play className="w-3 h-3 inline mr-1" /> Start
                        </button>
                      )}
                      {apt.status === 'IN_PROGRESS' && (
                        <button
                          onClick={() => handleCompleteAppointment(apt.id)}
                          className="px-2.5 py-1 text-xs font-medium rounded-lg bg-emerald-700 text-white hover:bg-emerald-800 transition-colors"
                        >
                          <PackageCheck className="w-3 h-3 inline mr-1" /> Complete
                        </button>
                      )}
                      {['REQUESTED', 'CONFIRMED', 'CHECKED_IN', 'IN_PROGRESS'].includes(apt.status) && (
                        <>
                          <button
                            onClick={() => handleCancelAppointment(apt.id)}
                            className="px-2.5 py-1 text-xs font-medium rounded-lg bg-[var(--nexus-error-50)] text-[var(--nexus-error-700)] hover:bg-[var(--nexus-error-200)] dark:bg-[var(--nexus-error-900)]/30 dark:text-[var(--nexus-error-400)] dark:hover:bg-[var(--nexus-error-900)]/50 transition-colors"
                          >
                            Cancel
                          </button>
                          <button
                            onClick={() => handleNoShow(apt.id)}
                            className="px-2.5 py-1 text-xs font-medium rounded-lg bg-[var(--surface-muted)] text-[var(--text-secondary)] hover:bg-[var(--surface-muted)] bg-[var(--surface-muted)] dark:text-[var(--text-tertiary)] hover:bg-[var(--interactive-hover)] transition-colors"
                          >
                            No Show
                          </button>
                        </>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {appointments.length === 0 && (
          <div className="enterprise-empty-state py-16">
            <Calendar className="w-12 h-12 text-[var(--text-tertiary)] dark:text-[var(--text-secondary)] mx-auto mb-3" />
            <p className="text-[var(--text-secondary)] text-sm">No appointments found for this date</p>
          </div>
        )}
      </div>
    )
  }

  // ── Calendar View ──

  function renderCalendarView() {
    return (
      <div className="space-y-2">
        {calendarSlots.map((slot) => (
          <div
            key={slot.hour}
            onClick={() => setSelectedSlotHour(selectedSlotHour === slot.hour ? null : slot.hour)}
            className={clsx(
              'flex items-stretch rounded-xl border transition-all cursor-pointer',
              slot.appointments.length > 0
                ? 'border-[var(--nexus-primary-200)] dark:border-[var(--nexus-primary-800)] hover:border-[var(--nexus-primary-300)] dark:hover:border-[var(--nexus-primary-700)]'
                : 'border-[var(--border-default)] hover:border-[var(--border-default)] dark:hover:border-[var(--border-default)]',
              selectedSlotHour === slot.hour && 'ring-2 ring-blue-500/30 border-[var(--nexus-primary-400)] dark:border-[var(--nexus-primary-600)]',
            )}
          >
            <div className="w-20 flex-shrink-0 flex items-center justify-center bg-[var(--surface-sunken)] bg-[var(--surface-base)]/50 rounded-l-xl border-r border-[var(--border-default)]">
              <span className="text-sm font-semibold text-[var(--text-secondary)]">
                {String(slot.hour).padStart(2, '0')}:00
              </span>
            </div>

            <div className="flex-1 p-3 min-h-[52px]">
              {slot.appointments.length === 0 ? (
                <p className="text-xs text-[var(--text-tertiary)] dark:text-[var(--text-secondary)] py-1">No appointments</p>
              ) : (
                <div className="space-y-1.5">
                  {slot.appointments.map((apt) => (
                    <div
                      key={apt.id}
                      className="flex items-center gap-2 px-2.5 py-1.5 rounded-lg bg-[var(--nexus-primary-50)] dark:bg-[var(--nexus-primary-900)]/20 border border-[var(--nexus-primary-200)]/50 dark:border-[var(--nexus-primary-800)]/50"
                    >
                      <span className={clsx(
                        'w-1.5 h-1.5 rounded-full flex-shrink-0',
                        apt.status === 'IN_PROGRESS' && 'bg-emerald-500',
                        apt.status === 'CONFIRMED' && 'bg-[var(--nexus-primary-50)]0',
                        apt.status === 'CHECKED_IN' && 'bg-[var(--nexus-ai-50)]0',
                        apt.status === 'REQUESTED' && 'bg-[var(--nexus-warning-50)]0',
                        apt.status === 'COMPLETED' && 'bg-[var(--surface-muted)]',
                      )} />
                      <span className="text-xs font-medium text-[var(--text-primary)]">{apt.appointmentNumber}</span>
                      <span className="text-xs text-[var(--text-secondary)]">{apt.carrierName}</span>
                      <span className={clsx(
                        'ml-auto text-xs font-medium px-1.5 py-0.5 rounded',
                        appointmentStatusColors[apt.status],
                      )}>
                        {apt.status.replace(/_/g, ' ')}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {slot.appointments.length > 0 && (
              <div className="w-10 flex-shrink-0 flex items-center justify-center">
                <span className="text-xs font-bold text-[var(--nexus-primary-600)] dark:text-[var(--nexus-primary-400)] bg-[var(--nexus-primary-100)] dark:bg-[var(--nexus-primary-900)]/40 w-6 h-6 rounded-full flex items-center justify-center">
                  {slot.appointments.length}
                </span>
              </div>
            )}
          </div>
        ))}

        {selectedSlotHour !== null && (
          <div className="mt-4 p-4 rounded-xl border border-[var(--nexus-primary-200)] dark:border-[var(--nexus-primary-800)] bg-[var(--nexus-primary-50)]/50 dark:bg-[var(--nexus-primary-900)]/10">
            <div className="flex items-center justify-between mb-3">
              <h4 className="text-sm font-semibold text-[var(--text-primary)]">
                {String(selectedSlotHour).padStart(2, '0')}:00 — {String(selectedSlotHour + 1).padStart(2, '0')}:00
              </h4>
              <button
                onClick={() => {
                  setShowAppointmentModal(true)
                  const now = new Date()
                  now.setHours(selectedSlotHour, 0, 0, 0)
                  setAptForm({ ...aptForm, estimatedArrival: now.toISOString().slice(0, 16) })
                }}
                className="enterprise-btn enterprise-btn-primary text-xs"
              >
                <Plus className="w-3.5 h-3.5" /> New Appointment
              </button>
            </div>
            {calendarSlots.find((s) => s.hour === selectedSlotHour)?.appointments.length === 0 && (
              <p className="text-xs text-[var(--text-secondary)]">No appointments in this slot. Click "New Appointment" to schedule one.</p>
            )}
          </div>
        )}
      </div>
    )
  }

  // ── Trailers View ──

  function renderTrailersView() {
    const filtered = trailers.filter((t) => {
      const matchSearch =
        !trailerSearch ||
        t.trailerNumber.toLowerCase().includes(trailerSearch.toLowerCase()) ||
        t.carrierId.toLowerCase().includes(trailerSearch.toLowerCase()) ||
        (t.licensePlate ?? '').toLowerCase().includes(trailerSearch.toLowerCase())
      const matchStatus = trailerStatusFilter === 'ALL' || t.status === trailerStatusFilter
      return matchSearch && matchStatus
    })

    return (
      <div className="space-y-4">
        <div className="flex items-center justify-between flex-wrap gap-3">
          <div className="flex items-center gap-3">
            <input
              type="text"
              value={trailerSearch}
              onChange={(e) => setTrailerSearch(e.target.value)}
              placeholder="Search trailers..."
              className="enterprise-input text-sm py-1.5 w-52"
            />
            <select
              value={trailerStatusFilter}
              onChange={(e) => setTrailerStatusFilter(e.target.value)}
              className="enterprise-input text-sm py-1.5"
            >
              <option value="ALL">All Status</option>
              <option value="IN_YARD">In Yard</option>
              <option value="DOCKED">Docked</option>
              <option value="IN_TRANSIT">In Transit</option>
              <option value="MAINTENANCE">Maintenance</option>
            </select>
          </div>
          <button
            onClick={() => setShowCheckInModal(true)}
            className="enterprise-btn enterprise-btn-primary text-xs"
          >
            <Plus className="w-3.5 h-3.5" /> Check In Trailer
          </button>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
          {[
            { label: 'Total', value: trailerStats.total, color: 'text-[var(--text-primary)]' },
            { label: 'In Yard', value: trailerStats.inYard, color: 'text-[var(--nexus-primary-600)] dark:text-[var(--nexus-primary-400)]' },
            { label: 'Docked', value: trailerStats.docked, color: 'text-[var(--nexus-success-600)] dark:text-[var(--nexus-success-400)]' },
            { label: 'In Transit', value: trailerStats.inTransit, color: 'text-[var(--nexus-ai-600)] dark:text-[var(--nexus-ai-400)]' },
          ].map((s) => (
            <div key={s.label} className="rounded-lg border border-[var(--border-default)] p-3 text-center">
              <p className={clsx('text-xl font-bold', s.color)}>{s.value}</p>
              <p className="text-xs text-[var(--text-secondary)]">{s.label}</p>
            </div>
          ))}
        </div>

        <div className="overflow-x-auto rounded-xl border border-[var(--border-default)]">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-[var(--surface-sunken)] bg-[var(--surface-base)]/50 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">
                <th className="px-4 py-3">Trailer #</th>
                <th className="px-4 py-3">Carrier</th>
                <th className="px-4 py-3">License Plate</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Dock</th>
                <th className="px-4 py-3">Check In</th>
                <th className="px-4 py-3">Pallets</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-gray-700/50">
              {filtered.map((t) => (
                <tr key={t.id} className="hover:bg-[var(--surface-sunken)]/50 hover:bg-[var(--surface-base)]/30 transition-colors">
                  <td className="px-4 py-3 font-medium text-[var(--text-primary)]">{t.trailerNumber}</td>
                  <td className="px-4 py-3 text-[var(--text-secondary)]">{t.carrierId}</td>
                  <td className="px-4 py-3 text-[var(--text-secondary)] text-xs">{t.licensePlate ?? '—'}</td>
                  <td className="px-4 py-3">
                    <span className={clsx(
                      'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold',
                      trailerStatusColors[t.status],
                    )}>
                      {t.status.replace(/_/g, ' ')}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-sm font-medium text-[var(--text-secondary)]">{t.currentDockDoor ?? '—'}</td>
                  <td className="px-4 py-3 text-xs text-[var(--text-secondary)]">
                    {t.checkInTime ? formatTime(t.checkInTime) : '—'}
                  </td>
                  <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{t.palletCount ?? '—'}</td>
                  <td className="px-4 py-3">
                    <div className="flex items-center justify-end gap-1.5">
                      {t.status === 'IN_YARD' && (
                        <button
                          onClick={() => handleDockTrailer(t.id)}
                          className="px-2.5 py-1 text-xs font-medium rounded-lg bg-[var(--nexus-success-600)] text-white hover:bg-[var(--nexus-success-700)] transition-colors"
                        >
                          Dock
                        </button>
                      )}
                      {t.status === 'DOCKED' && (
                        <button
                          onClick={() => handleCheckOutTrailer(t.id)}
                          className="px-2.5 py-1 text-xs font-medium rounded-lg bg-[var(--nexus-ai-600)] text-white hover:bg-[var(--nexus-ai-700)] transition-colors"
                        >
                          Check Out
                        </button>
                      )}
                      <button
                        onClick={() => handleShowTrailerEvents(t.id)}
                        className="px-2.5 py-1 text-xs font-medium rounded-lg bg-[var(--surface-muted)] text-[var(--text-secondary)] hover:bg-[var(--surface-muted)] bg-[var(--surface-muted)] dark:text-[var(--text-tertiary)] hover:bg-[var(--interactive-hover)] transition-colors"
                      >
                        <Eye className="w-3 h-3 inline mr-1" /> History
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {filtered.length === 0 && (
                <tr>
                  <td colSpan={8} className="px-4 py-10 text-center text-[var(--text-tertiary)] dark:text-[var(--text-secondary)] text-sm">
                    No trailers found
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    )
  }

  // ── Main Render ──

  const views: { key: ViewMode; label: string; icon: React.ReactNode }[] = [
    { key: 'docks', label: 'Dock Doors', icon: <Truck className="w-4 h-4" /> },
    { key: 'yard', label: 'Yard', icon: <MapPin className="w-4 h-4" /> },
    { key: 'appointments', label: 'Appointments', icon: <Calendar className="w-4 h-4" /> },
    { key: 'calendar', label: 'Calendar', icon: <CalendarDays className="w-4 h-4" /> },
    { key: 'trailers', label: 'Trailers', icon: <Package className="w-4 h-4" /> },
  ]

  return (
    <div className="enterprise-page space-y-6">
      {/* Header */}
      <EnterpriseBreadcrumbs crumbs={[
        { label: 'Home', path: '/' },
        { label: 'Warehouse', path: '/warehouse' },
        { label: 'Yard & Dock Management' },
      ]} />

      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5">
            <Truck className="w-7 h-7 text-[var(--nexus-primary-500)]" /> Yard & Dock Management
          </h1>
          <p className="text-sm text-[var(--text-secondary)] mt-1">
            {warehouses.find((w) => w.id === selectedWarehouse)?.name} &middot; Dock utilization {stats.dockUtilization}%
          </p>
        </div>
      </div>

      {/* KPI Cards */}
      <div className="enterprise-kpi-grid grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
        <EnterpriseKPICard
          title="Dock Utilization"
          value={`${stats.dockUtilization}%`}
          icon={<BarChart3 className="w-5 h-5" />}
          color={stats.dockUtilization > 80 ? 'warning' : stats.dockUtilization > 50 ? 'primary' : 'success'}
          trend={stats.dockUtilization > 70 ? 'up' : 'down'}
          trendValue={`${stats.dockUtilization > 70 ? '+5%' : '-3%'}`}
        />
        <EnterpriseKPICard
          title="Appointments Today"
          value={stats.appointmentsToday}
          icon={<Calendar className="w-5 h-5" />}
          color="primary"
        />
        <EnterpriseKPICard
          title="In Progress Now"
          value={stats.inProgressNow}
          icon={<Clock className="w-5 h-5" />}
          color="info"
        />
        <EnterpriseKPICard
          title="Completed Today"
          value={stats.completedToday}
          icon={<Package className="w-5 h-5" />}
          color="success"
        />
        <EnterpriseKPICard
          title="No Shows"
          value={stats.noShows}
          icon={<AlertTriangle className="w-5 h-5" />}
          color={stats.noShows > 0 ? 'error' : 'success'}
          trend={stats.noShows > 0 ? 'up' : 'neutral'}
          trendValue={stats.noShows > 0 ? '+1' : '0'}
        />
      </div>

      {/* Toolbar */}
      <div className="enterprise-toolbar flex flex-wrap items-center gap-3 p-4 bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)]">
        <div className="flex items-center gap-2">
          <label className="text-xs font-medium text-[var(--text-secondary)]">Warehouse</label>
          <select
            value={selectedWarehouse}
            onChange={(e) => setSelectedWarehouse(e.target.value)}
            className="enterprise-input text-sm py-1.5"
          >
            {warehouses.map((wh) => (
              <option key={wh.id} value={wh.id}>{wh.name}</option>
            ))}
          </select>
        </div>

        <div className="h-6 w-px bg-[var(--surface-muted)] bg-[var(--surface-muted)]" />

        <div className="flex items-center gap-2">
          <label className="text-xs font-medium text-[var(--text-secondary)]">Date</label>
          <input
            type="date"
            value={selectedDate}
            onChange={(e) => setSelectedDate(e.target.value)}
            className="enterprise-input text-sm py-1.5"
          />
        </div>

        <div className="flex-1" />

        <button
          onClick={() => setShowAppointmentModal(true)}
          className="enterprise-btn enterprise-btn-primary text-sm"
        >
          <Plus className="w-4 h-4" /> New Appointment
        </button>

        <div className="h-6 w-px bg-[var(--surface-muted)] bg-[var(--surface-muted)]" />

        <div className="flex items-center rounded-lg border border-[var(--border-default)] overflow-hidden">
          {views.map((view) => (
            <button
              key={view.key}
              onClick={() => setActiveView(view.key)}
              className={clsx(
                'flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium transition-colors',
                activeView === view.key
                  ? 'bg-[var(--nexus-primary-600)] text-white'
                  : 'text-[var(--text-secondary)] hover:bg-[var(--surface-muted)] dark:hover:bg-[var(--surface-muted)]',
              )}
            >
              {view.icon}
              <span className="hidden sm:inline">{view.label}</span>
            </button>
          ))}
        </div>
      </div>

      {/* View Content */}
      <div className="enterprise-card p-5">
        {activeView === 'docks' && renderDockDoorsView()}
        {activeView === 'yard' && renderYardView()}
        {activeView === 'appointments' && renderAppointmentsView()}
        {activeView === 'calendar' && renderCalendarView()}
        {activeView === 'trailers' && renderTrailersView()}
      </div>

      {/* New Appointment Modal */}
      {showAppointmentModal && (
        <div className="enterprise-modal-overlay" onClick={() => setShowAppointmentModal(false)}>
          <div
            className="enterprise-modal max-w-2xl w-full max-h-[90vh] overflow-y-auto"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between p-6 border-b border-[var(--border-subtle)]">
              <h2 className="text-lg font-semibold text-[var(--text-primary)]">New Appointment</h2>
              <button
                onClick={() => setShowAppointmentModal(false)}
                className="p-1.5 hover:bg-[var(--surface-muted)] dark:hover:bg-[var(--surface-muted)] rounded-lg transition-colors"
              >
                <X className="w-5 h-5 text-[var(--text-secondary)]" />
              </button>
            </div>

            <div className="p-6 space-y-5">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1.5">Type *</label>
                  <select
                    value={aptForm.type}
                    onChange={(e) => setAptForm({ ...aptForm, type: e.target.value })}
                    className="enterprise-input w-full text-sm"
                  >
                    <option value="INBOUND_DELIVERY">Inbound Delivery</option>
                    <option value="OUTBOUND_PICKUP">Outbound Pickup</option>
                    <option value="RETURN">Return</option>
                    <option value="CROSS_DOCK">Cross Dock</option>
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1.5">Carrier Name *</label>
                  <input
                    value={aptForm.carrierName}
                    onChange={(e) => setAptForm({ ...aptForm, carrierName: e.target.value })}
                    className="enterprise-input w-full text-sm"
                    placeholder="FastFreight Logistics"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1.5">Carrier Code</label>
                  <input
                    value={aptForm.carrierCode}
                    onChange={(e) => setAptForm({ ...aptForm, carrierCode: e.target.value })}
                    className="enterprise-input w-full text-sm"
                    placeholder="FFL"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1.5">Trailer Number</label>
                  <input
                    value={aptForm.trailerNumber}
                    onChange={(e) => setAptForm({ ...aptForm, trailerNumber: e.target.value })}
                    className="enterprise-input w-full text-sm"
                    placeholder="TRL-9921"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1.5">License Plate</label>
                  <input
                    value={aptForm.licensePlate}
                    onChange={(e) => setAptForm({ ...aptForm, licensePlate: e.target.value })}
                    className="enterprise-input w-full text-sm"
                    placeholder="TRK-4521"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1.5">Driver Name *</label>
                  <input
                    value={aptForm.driverName}
                    onChange={(e) => setAptForm({ ...aptForm, driverName: e.target.value })}
                    className="enterprise-input w-full text-sm"
                    placeholder="John Smith"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1.5">Driver Phone</label>
                  <input
                    value={aptForm.driverPhone}
                    onChange={(e) => setAptForm({ ...aptForm, driverPhone: e.target.value })}
                    className="enterprise-input w-full text-sm"
                    placeholder="555-0101"
                  />
                </div>
                <div />
              </div>

              <div className="border-t border-[var(--border-subtle)] pt-4">
                <h3 className="text-sm font-semibold text-[var(--text-secondary)] mb-3">Schedule</h3>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1.5">Estimated Arrival *</label>
                    <input
                      type="datetime-local"
                      value={aptForm.estimatedArrival}
                      onChange={(e) => setAptForm({ ...aptForm, estimatedArrival: e.target.value })}
                      className="enterprise-input w-full text-sm"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1.5">Estimated Departure</label>
                    <input
                      type="datetime-local"
                      value={aptForm.estimatedDeparture}
                      onChange={(e) => setAptForm({ ...aptForm, estimatedDeparture: e.target.value })}
                      className="enterprise-input w-full text-sm"
                    />
                  </div>
                </div>
              </div>

              <div className="border-t border-[var(--border-subtle)] pt-4">
                <h3 className="text-sm font-semibold text-[var(--text-secondary)] mb-3">Load Details</h3>
                <div className="grid grid-cols-3 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1.5">Load Count</label>
                    <input
                      type="number"
                      min={0}
                      value={aptForm.loadCount}
                      onChange={(e) => setAptForm({ ...aptForm, loadCount: Number(e.target.value) })}
                      className="enterprise-input w-full text-sm"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1.5">Pallet Count</label>
                    <input
                      type="number"
                      min={0}
                      value={aptForm.palletCount}
                      onChange={(e) => setAptForm({ ...aptForm, palletCount: Number(e.target.value) })}
                      className="enterprise-input w-full text-sm"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1.5">Piece Count</label>
                    <input
                      type="number"
                      min={0}
                      value={aptForm.pieceCount}
                      onChange={(e) => setAptForm({ ...aptForm, pieceCount: Number(e.target.value) })}
                      className="enterprise-input w-full text-sm"
                    />
                  </div>
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1.5">Special Instructions</label>
                <textarea
                  value={aptForm.specialInstructions}
                  onChange={(e) => setAptForm({ ...aptForm, specialInstructions: e.target.value })}
                  className="enterprise-input w-full text-sm resize-none"
                  rows={3}
                  placeholder="Hazmat handling, temperature requirements, etc."
                />
              </div>
            </div>

            <div className="p-6 border-t border-[var(--border-subtle)] flex justify-end gap-3">
              <button
                onClick={() => setShowAppointmentModal(false)}
                className="enterprise-btn text-sm"
              >
                Cancel
              </button>
              <button
                onClick={handleCreateAppointment}
                disabled={saving}
                className="enterprise-btn enterprise-btn-primary text-sm"
              >
                {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Calendar className="w-4 h-4" />}
                Create Appointment
              </button>
            </div>
          </div>
        </div>
      )}

      {showCheckInModal && (
        <div className="enterprise-modal-overlay" onClick={() => setShowCheckInModal(false)}>
          <div
            className="enterprise-modal max-w-lg w-full max-h-[90vh] overflow-y-auto"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between p-6 border-b border-[var(--border-subtle)]">
              <h2 className="text-lg font-semibold text-[var(--text-primary)]">Check In Trailer</h2>
              <button
                onClick={() => setShowCheckInModal(false)}
                className="p-1.5 hover:bg-[var(--surface-muted)] dark:hover:bg-[var(--surface-muted)] rounded-lg transition-colors"
              >
                <X className="w-5 h-5 text-[var(--text-secondary)]" />
              </button>
            </div>

            <div className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1.5">Trailer Number *</label>
                <input
                  value={checkInForm.trailerNumber}
                  onChange={(e) => setCheckInForm({ ...checkInForm, trailerNumber: e.target.value })}
                  className="enterprise-input w-full text-sm"
                  placeholder="TRL-9921"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1.5">Carrier *</label>
                <input
                  value={checkInForm.carrierId}
                  onChange={(e) => setCheckInForm({ ...checkInForm, carrierId: e.target.value })}
                  className="enterprise-input w-full text-sm"
                  placeholder="FFL"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1.5">License Plate</label>
                <input
                  value={checkInForm.licensePlate}
                  onChange={(e) => setCheckInForm({ ...checkInForm, licensePlate: e.target.value })}
                  className="enterprise-input w-full text-sm"
                  placeholder="TRK-4521"
                />
              </div>
            </div>

            <div className="p-6 border-t border-[var(--border-subtle)] flex justify-end gap-3">
              <button
                onClick={() => setShowCheckInModal(false)}
                className="enterprise-btn text-sm"
              >
                Cancel
              </button>
              <button
                onClick={handleTrailerCheckIn}
                disabled={saving}
                className="enterprise-btn enterprise-btn-primary text-sm"
              >
                {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Check className="w-4 h-4" />}
                Check In
              </button>
            </div>
          </div>
        </div>
      )}

      {showEventsModal && (
        <div className="enterprise-modal-overlay" onClick={() => setShowEventsModal(false)}>
          <div
            className="enterprise-modal max-w-lg w-full max-h-[90vh] overflow-y-auto"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between p-6 border-b border-[var(--border-subtle)]">
              <h2 className="text-lg font-semibold text-[var(--text-primary)]">
                Trailer History — {trailers.find((t) => t.id === selectedTrailerId)?.trailerNumber ?? ''}
              </h2>
              <button
                onClick={() => setShowEventsModal(false)}
                className="p-1.5 hover:bg-[var(--surface-muted)] dark:hover:bg-[var(--surface-muted)] rounded-lg transition-colors"
              >
                <X className="w-5 h-5 text-[var(--text-secondary)]" />
              </button>
            </div>

            <div className="p-6">
              {trailerEvents.length === 0 ? (
                <p className="text-sm text-[var(--text-secondary)] text-center py-8">No events recorded</p>
              ) : (
                <div className="space-y-3">
                  {trailerEvents.map((ev) => (
                    <div key={ev.id} className="flex items-start gap-3 p-3 rounded-lg bg-[var(--surface-sunken)] bg-[var(--surface-base)]/50 border border-[var(--border-default)]">
                      <div className="w-8 h-8 rounded-full bg-[var(--nexus-primary-100)] dark:bg-[var(--nexus-primary-900)]/40 flex items-center justify-center flex-shrink-0 mt-0.5">
                        <Clock className="w-4 h-4 text-[var(--nexus-primary-600)] dark:text-[var(--nexus-primary-400)]" />
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2">
                          <span className="text-sm font-semibold text-[var(--text-primary)]">{ev.eventType.replace(/_/g, ' ')}</span>
                          <span className="text-xs text-[var(--text-tertiary)] dark:text-[var(--text-secondary)]">{formatTime(ev.performedAt)}</span>
                        </div>
                        {ev.performedBy && (
                          <p className="text-xs text-[var(--text-secondary)] mt-0.5">by {ev.performedBy}</p>
                        )}
                        {ev.details && (
                          <p className="text-xs text-[var(--text-secondary)] mt-1">{ev.details}</p>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
