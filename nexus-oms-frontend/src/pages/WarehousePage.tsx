import { useState, useEffect, useMemo } from 'react'
import { clsx } from 'clsx'
import {
  Building2, MapPin, Boxes, Users, Wrench, Plus, Search, X, Check,
  Eye, EyeOff, Loader2, ChevronDown, ChevronRight, Trash2, Edit3, Thermometer,
} from 'lucide-react'
import * as warehouseApi from '../api/warehouse'
import type {
  Warehouse, WarehouseZone, WarehouseBin, WarehouseStaff, WarehouseEquipment,
} from '../api/warehouse'
import { useToast } from '../hooks/useToast'
import Autocomplete from '../components/common/Autocomplete'
import StatusBadge from '../components/common/StatusBadge'

interface WarehouseFormData {
  name: string
  code: string
  street: string
  city: string
  state: string
  zip: string
  country: string
  capacity: number
  status: string
  managerName: string
  contactEmail: string
  contactPhone: string
  timezone: string
}

interface ZoneFormData {
  name: string
  code: string
  type: string
  capacity: number
}

interface BinFormData {
  code: string
  zoneId: string
  aisle: string
  rack: string
  shelf: string
  position: string
  type: string
  width: number
  height: number
  depth: number
  maxWeight: number
}

interface StaffFormData {
  name: string
  email: string
  role: string
  shift: string
}

interface EquipmentFormData {
  name: string
  type: string
  model: string
  serialNumber: string
}

const defaultWhForm = (): WarehouseFormData => ({
  name: '', code: '', street: '', city: '', state: '', zip: '', country: 'US',
  capacity: 10000, status: 'ACTIVE', managerName: '', contactEmail: '',
  contactPhone: '', timezone: 'America/New_York',
})

const defaultZoneForm = (): ZoneFormData => ({
  name: '', code: '', type: 'STORAGE', capacity: 1000,
})

const defaultBinForm = (): BinFormData => ({
  code: '', zoneId: '', aisle: '', rack: '', shelf: '', position: '',
  type: 'STANDARD', width: 48, height: 48, depth: 48, maxWeight: 500,
})

const defaultStaffForm = (): StaffFormData => ({
  name: '', email: '', role: 'PICKER', shift: 'MORNING',
})

const defaultEquipForm = (): EquipmentFormData => ({
  name: '', type: 'FORKLIFT', model: '', serialNumber: '',
})

const capacityUtil = (wh: Warehouse) => {
  const cap = wh.capacity ?? 0
  return cap > 0 ? Math.round(((wh.utilizedCapacity ?? 0) / cap) * 100) : 0
}

function StatCard({ icon, label, value, sub }: { icon: React.ReactNode; label: string; value: string; sub?: string }) {
  return (
    <div className="card p-4 flex items-center gap-4">
      <div className="w-10 h-10 rounded-lg bg-primary-50 flex items-center justify-center text-primary-600 flex-shrink-0">
        {icon}
      </div>
      <div className="min-w-0">
        <p className="text-xs text-gray-500 font-medium">{label}</p>
        <p className="text-xl font-bold text-gray-900 mt-0.5">{value}</p>
        {sub && <p className="text-xs text-gray-400 mt-0.5">{sub}</p>}
      </div>
    </div>
  )
}

export default function WarehousePage() {
  const { addToast } = useToast()

  const [warehouses, setWarehouses] = useState<Warehouse[]>([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [expandedId, setExpandedId] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const [showWhModal, setShowWhModal] = useState(false)
  const [whForm, setWhForm] = useState<WarehouseFormData>(defaultWhForm())

  const [detailTab, setDetailTab] = useState<'zones' | 'bins' | 'staff' | 'equipment'>('zones')
  const [zones, setZones] = useState<WarehouseZone[]>([])
  const [bins, setBins] = useState<WarehouseBin[]>([])
  const [staff, setStaff] = useState<WarehouseStaff[]>([])
  const [equipment, setEquipment] = useState<WarehouseEquipment[]>([])
  const [detailLoading, setDetailLoading] = useState(false)

  const [showZoneModal, setShowZoneModal] = useState(false)
  const [zoneForm, setZoneForm] = useState<ZoneFormData>(defaultZoneForm())
  const [showBinModal, setShowBinModal] = useState(false)
  const [binForm, setBinForm] = useState<BinFormData>(defaultBinForm())
  const [showStaffModal, setShowStaffModal] = useState(false)
  const [staffForm, setStaffForm] = useState<StaffFormData>(defaultStaffForm())
  const [showEquipModal, setShowEquipModal] = useState(false)
  const [equipForm, setEquipForm] = useState<EquipmentFormData>(defaultEquipForm())

  useEffect(() => { fetchWarehouses() }, [])

  useEffect(() => {
    if (expandedId) loadDetailData(expandedId)
    else { setZones([]); setBins([]); setStaff([]); setEquipment([]) }
  }, [expandedId])

  async function fetchWarehouses() {
    try {
      setLoading(true)
      const res = await warehouseApi.getWarehouses(0, 100)
      setWarehouses(res.data?.content || [])
    } catch {
      addToast({ type: 'error', title: 'Failed to load warehouses' })
      setWarehouses([])
    } finally {
      setLoading(false)
    }
  }

  async function loadDetailData(whId: string) {
    setDetailLoading(true)
    setDetailTab('zones')
    try {
      const [zRes, bRes, sRes, eRes] = await Promise.all([
        warehouseApi.getZones(whId),
        warehouseApi.getBins(whId),
        warehouseApi.getStaff(whId),
        warehouseApi.getEquipment(whId),
      ])
      setZones(Array.isArray(zRes.data) ? zRes.data : [])
      setBins(Array.isArray(bRes.data) ? bRes.data : [])
      setStaff(Array.isArray(sRes.data) ? sRes.data : [])
      setEquipment(Array.isArray(eRes.data) ? eRes.data : [])
    } catch {
      addToast({ type: 'error', title: 'Failed to load warehouse details' })
      setZones([]); setBins([]); setStaff([]); setEquipment([])
    } finally {
      setDetailLoading(false)
    }
  }

  const filtered = useMemo(() => {
    if (!search) return warehouses
    const q = search.toLowerCase()
    return warehouses.filter(
      (w) => (w.name ?? '').toLowerCase().includes(q) || (w.code ?? '').toLowerCase().includes(q),
    )
  }, [warehouses, search])

  // ── Warehouse CRUD ──

  function openAddWarehouse() {
    setWhForm(defaultWhForm())
    setShowWhModal(true)
  }

  async function handleSaveWarehouse() {
    if (!whForm.name.trim() || !whForm.code.trim()) {
      addToast({ type: 'warning', title: 'Name and code are required' })
      return
    }
    setSaving(true)
    try {
      await warehouseApi.createWarehouse({
        name: whForm.name,
        code: whForm.code,
        address: {
          street: whForm.street,
          city: whForm.city,
          state: whForm.state,
          zip: whForm.zip,
          country: whForm.country,
        },
        capacity: whForm.capacity,
        status: whForm.status,
        managerName: whForm.managerName,
        contactEmail: whForm.contactEmail,
        contactPhone: whForm.contactPhone,
        timezone: whForm.timezone,
      })
      addToast({ type: 'success', title: 'Warehouse created' })
      setShowWhModal(false)
      await fetchWarehouses()
    } catch {
      addToast({ type: 'error', title: 'Failed to create warehouse' })
    } finally {
      setSaving(false)
    }
  }

  async function handleDeleteWarehouse(id: string) {
    try {
      await warehouseApi.deleteWarehouse(id)
      addToast({ type: 'success', title: 'Warehouse deleted' })
      if (expandedId === id) setExpandedId(null)
      await fetchWarehouses()
    } catch {
      addToast({ type: 'error', title: 'Failed to delete warehouse' })
    }
  }

  // ── Zone CRUD ──

  function openAddZone() {
    setZoneForm(defaultZoneForm())
    setShowZoneModal(true)
  }

  async function handleSaveZone() {
    if (!zoneForm.name.trim() || !zoneForm.code.trim()) {
      addToast({ type: 'warning', title: 'Name and code are required' })
      return
    }
    setSaving(true)
    try {
      await warehouseApi.createZone({
        warehouseId: expandedId,
        name: zoneForm.name,
        code: zoneForm.code,
        type: zoneForm.type,
        capacity: zoneForm.capacity,
      })
      addToast({ type: 'success', title: 'Zone created' })
      setShowZoneModal(false)
      if (expandedId) {
        const res = await warehouseApi.getZones(expandedId)
        setZones(Array.isArray(res.data) ? res.data : [])
      }
    } catch {
      addToast({ type: 'error', title: 'Failed to create zone' })
    } finally {
      setSaving(false)
    }
  }

  // ── Bin CRUD ──

  function openAddBin() {
    setBinForm(defaultBinForm())
    setShowBinModal(true)
  }

  async function handleSaveBin() {
    if (!binForm.code.trim()) {
      addToast({ type: 'warning', title: 'Bin code is required' })
      return
    }
    setSaving(true)
    try {
      await warehouseApi.createBin({
        warehouseId: expandedId,
        code: binForm.code,
        zoneId: binForm.zoneId || undefined,
        aisle: binForm.aisle,
        rack: binForm.rack,
        shelf: binForm.shelf,
        position: binForm.position,
        type: binForm.type,
        width: binForm.width,
        height: binForm.height,
        depth: binForm.depth,
        maxWeight: binForm.maxWeight,
      })
      addToast({ type: 'success', title: 'Bin created' })
      setShowBinModal(false)
      if (expandedId) {
        const res = await warehouseApi.getBins(expandedId)
        setBins(Array.isArray(res.data) ? res.data : [])
      }
    } catch {
      addToast({ type: 'error', title: 'Failed to create bin' })
    } finally {
      setSaving(false)
    }
  }

  async function handleReserveBin(id: string) {
    try {
      await warehouseApi.reserveBin(id)
      addToast({ type: 'success', title: 'Bin reserved' })
      if (expandedId) {
        const res = await warehouseApi.getBins(expandedId)
        setBins(Array.isArray(res.data) ? res.data : [])
      }
    } catch {
      addToast({ type: 'error', title: 'Failed to reserve bin' })
    }
  }

  async function handleReleaseBin(id: string) {
    try {
      await warehouseApi.releaseBin(id)
      addToast({ type: 'success', title: 'Bin released' })
      if (expandedId) {
        const res = await warehouseApi.getBins(expandedId)
        setBins(Array.isArray(res.data) ? res.data : [])
      }
    } catch {
      addToast({ type: 'error', title: 'Failed to release bin' })
    }
  }

  // ── Staff CRUD ──

  function openAddStaff() {
    setStaffForm(defaultStaffForm())
    setShowStaffModal(true)
  }

  async function handleSaveStaff() {
    if (!staffForm.name.trim() || !staffForm.email.trim()) {
      addToast({ type: 'warning', title: 'Name and email are required' })
      return
    }
    setSaving(true)
    try {
      await warehouseApi.createStaff({
        warehouseId: expandedId,
        name: staffForm.name,
        email: staffForm.email,
        role: staffForm.role,
        shift: staffForm.shift,
      })
      addToast({ type: 'success', title: 'Staff added' })
      setShowStaffModal(false)
      if (expandedId) {
        const res = await warehouseApi.getStaff(expandedId)
        setStaff(Array.isArray(res.data) ? res.data : [])
      }
    } catch {
      addToast({ type: 'error', title: 'Failed to add staff' })
    } finally {
      setSaving(false)
    }
  }

  // ── Equipment CRUD ──

  function openAddEquipment() {
    setEquipForm(defaultEquipForm())
    setShowEquipModal(true)
  }

  async function handleSaveEquipment() {
    if (!equipForm.name.trim() || !equipForm.model.trim()) {
      addToast({ type: 'warning', title: 'Name and model are required' })
      return
    }
    setSaving(true)
    try {
      await warehouseApi.createEquipment({
        warehouseId: expandedId,
        name: equipForm.name,
        type: equipForm.type,
        model: equipForm.model,
        serialNumber: equipForm.serialNumber,
      })
      addToast({ type: 'success', title: 'Equipment added' })
      setShowEquipModal(false)
      if (expandedId) {
        const res = await warehouseApi.getEquipment(expandedId)
        setEquipment(Array.isArray(res.data) ? res.data : [])
      }
    } catch {
      addToast({ type: 'error', title: 'Failed to add equipment' })
    } finally {
      setSaving(false)
    }
  }

  async function handleEquipStatus(id: string, status: string) {
    try {
      await warehouseApi.updateEquipmentStatus(id, status)
      addToast({ type: 'success', title: 'Equipment status updated' })
      if (expandedId) {
        const res = await warehouseApi.getEquipment(expandedId)
        setEquipment(Array.isArray(res.data) ? res.data : [])
      }
    } catch {
      addToast({ type: 'error', title: 'Failed to update equipment status' })
    }
  }

  const totalCapacity = warehouses.reduce((s, w) => s + (w.capacity ?? 0), 0)
  const totalUtilized = warehouses.reduce((s, w) => s + (w.utilizedCapacity ?? 0), 0)
  const avgUtil = totalCapacity > 0 ? Math.round((totalUtilized / totalCapacity) * 100) : 0

  const binStatusColor: Record<string, string> = {
    EMPTY: 'bg-green-100 text-green-700',
    OCCUPIED: 'bg-blue-100 text-blue-700',
    RESERVED: 'bg-yellow-100 text-yellow-700',
    BLOCKED: 'bg-red-100 text-red-700',
  }

  const equipStatusColor: Record<string, string> = {
    AVAILABLE: 'bg-green-100 text-green-700',
    IN_USE: 'bg-blue-100 text-blue-700',
    MAINTENANCE: 'bg-yellow-100 text-yellow-700',
    OUT_OF_SERVICE: 'bg-red-100 text-red-700',
  }

  function renderDetailPanel(wh: Warehouse) {
    const emptyBins = bins.filter((b) => b.status === 'EMPTY').length
    const occupiedBins = bins.filter((b) => b.status === 'OCCUPIED').length

    if (detailLoading) {
      return (
        <div className="flex items-center justify-center p-12">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600" />
        </div>
      )
    }

    return (
      <div className="space-y-4">
        <div className="flex items-center gap-3 border-b border-gray-200">
          {(['zones', 'bins', 'staff', 'equipment'] as const).map((tab) => (
            <button
              key={tab}
              onClick={() => setDetailTab(tab)}
              className={clsx(
                'px-3 py-2.5 text-sm font-medium border-b-2 transition-colors capitalize',
                detailTab === tab
                  ? 'border-primary-600 text-primary-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700',
              )}
            >
              {tab}
            </button>
          ))}
        </div>

        {detailTab === 'zones' && (
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <p className="text-sm text-gray-500">{zones.length} zones</p>
              <button onClick={openAddZone} className="btn-primary text-xs">
                <Plus className="w-3.5 h-3.5" /> Add Zone
              </button>
            </div>
            {zones.length === 0 ? (
              <p className="text-sm text-gray-400 py-6 text-center">No zones configured</p>
            ) : (
              <div className="overflow-x-auto rounded-lg border border-gray-200">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="bg-gray-50 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">
                      <th className="px-4 py-2.5">Code</th>
                      <th className="px-4 py-2.5">Name</th>
                      <th className="px-4 py-2.5">Type</th>
                      <th className="px-4 py-2.5">Capacity</th>
                      <th className="px-4 py-2.5">Utilized</th>
                      <th className="px-4 py-2.5">Status</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {zones.map((z) => (
                      <tr key={z.id} className="hover:bg-gray-50">
                        <td className="px-4 py-2.5 font-medium text-gray-900">{z.code}</td>
                        <td className="px-4 py-2.5 text-gray-700">{z.name}</td>
                        <td className="px-4 py-2.5">
                          <StatusBadge status={z.type} size="sm" />
                        </td>
                        <td className="px-4 py-2.5 text-gray-700">{(z.capacity || 0).toLocaleString()}</td>
                        <td className="px-4 py-2.5">
                          <div className="flex items-center gap-2">
                            <div className="w-20 h-1.5 bg-gray-200 rounded-full overflow-hidden">
                              <div
                                className="h-full bg-primary-500 rounded-full"
                                style={{
                                  width: `${(z.capacity || 0) > 0 ? Math.min(Math.round(((z.utilizedCapacity || 0) / (z.capacity || 0)) * 100), 100) : 0}%`,
                                }}
                              />
                            </div>
                            <span className="text-xs text-gray-500">
                              {(z.capacity || 0) > 0 ? Math.round(((z.utilizedCapacity || 0) / (z.capacity || 0)) * 100) : 0}%
                            </span>
                          </div>
                        </td>
                        <td className="px-4 py-2.5">
                          <StatusBadge status={z.isActive ? 'ACTIVE' : 'INACTIVE'} size="sm" />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}

        {detailTab === 'bins' && (
          <div className="space-y-3">
            <div className="flex items-center gap-4">
              <p className="text-sm text-gray-500">{bins.length} bins</p>
              <span className="text-xs text-green-600 font-medium">{emptyBins} empty</span>
              <span className="text-xs text-blue-600 font-medium">{occupiedBins} occupied</span>
              <div className="flex-1" />
              <button onClick={openAddBin} className="btn-primary text-xs">
                <Plus className="w-3.5 h-3.5" /> Add Bin
              </button>
            </div>
            {bins.length === 0 ? (
              <p className="text-sm text-gray-400 py-6 text-center">No bins configured</p>
            ) : (
              <div className="overflow-x-auto rounded-lg border border-gray-200">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="bg-gray-50 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">
                      <th className="px-4 py-2.5">Code</th>
                      <th className="px-4 py-2.5">Location</th>
                      <th className="px-4 py-2.5">Type</th>
                      <th className="px-4 py-2.5">Max Weight</th>
                      <th className="px-4 py-2.5">Status</th>
                      <th className="px-4 py-2.5">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {bins.map((b) => (
                      <tr key={b.id} className="hover:bg-gray-50">
                        <td className="px-4 py-2.5 font-medium text-gray-900">{b.code}</td>
                        <td className="px-4 py-2.5 text-gray-600 text-xs">
                          {[b.aisle, b.rack, b.shelf, b.position].filter(Boolean).join(' / ') || '-'}
                        </td>
                        <td className="px-4 py-2.5 text-gray-700">{b.type}</td>
                        <td className="px-4 py-2.5 text-gray-700">{b.maxWeight} lbs</td>
                        <td className="px-4 py-2.5">
                          <span
                            className={clsx(
                              'inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium',
                              binStatusColor[b.status],
                            )}
                          >
                            {b.status}
                          </span>
                        </td>
                        <td className="px-4 py-2.5">
                          <div className="flex items-center gap-1">
                            {b.status === 'EMPTY' && (
                              <button
                                onClick={() => handleReserveBin(b.id)}
                                className="p-1 hover:bg-yellow-50 rounded text-gray-500 hover:text-yellow-600"
                                title="Reserve"
                              >
                                <Check className="w-3.5 h-3.5" />
                              </button>
                            )}
                            {(b.status === 'RESERVED' || b.status === 'OCCUPIED') && (
                              <button
                                onClick={() => handleReleaseBin(b.id)}
                                className="p-1 hover:bg-blue-50 rounded text-gray-500 hover:text-blue-600"
                                title="Release"
                              >
                                <X className="w-3.5 h-3.5" />
                              </button>
                            )}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}

        {detailTab === 'staff' && (
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <p className="text-sm text-gray-500">{staff.length} staff</p>
              <button onClick={openAddStaff} className="btn-primary text-xs">
                <Plus className="w-3.5 h-3.5" /> Add Staff
              </button>
            </div>
            {staff.length === 0 ? (
              <p className="text-sm text-gray-400 py-6 text-center">No staff assigned</p>
            ) : (
              <div className="overflow-x-auto rounded-lg border border-gray-200">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="bg-gray-50 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">
                      <th className="px-4 py-2.5">Name</th>
                      <th className="px-4 py-2.5">Email</th>
                      <th className="px-4 py-2.5">Role</th>
                      <th className="px-4 py-2.5">Shift</th>
                      <th className="px-4 py-2.5">Picks Today</th>
                      <th className="px-4 py-2.5">Status</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {staff.map((s) => (
                      <tr key={s.id} className="hover:bg-gray-50">
                        <td className="px-4 py-2.5 font-medium text-gray-900">{s.name}</td>
                        <td className="px-4 py-2.5 text-gray-500 text-xs">{s.email}</td>
                        <td className="px-4 py-2.5">
                          <StatusBadge status={s.role} size="sm" />
                        </td>
                        <td className="px-4 py-2.5 text-gray-700">{s.shift}</td>
                        <td className="px-4 py-2.5">
                          <span className="font-medium text-gray-900">{s.pickCount}</span>
                        </td>
                        <td className="px-4 py-2.5">
                          <StatusBadge status={s.isActive ? 'ACTIVE' : 'INACTIVE'} size="sm" />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}

        {detailTab === 'equipment' && (
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <p className="text-sm text-gray-500">{equipment.length} items</p>
              <button onClick={openAddEquipment} className="btn-primary text-xs">
                <Plus className="w-3.5 h-3.5" /> Add Equipment
              </button>
            </div>
            {equipment.length === 0 ? (
              <p className="text-sm text-gray-400 py-6 text-center">No equipment registered</p>
            ) : (
              <div className="overflow-x-auto rounded-lg border border-gray-200">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="bg-gray-50 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">
                      <th className="px-4 py-2.5">Name</th>
                      <th className="px-4 py-2.5">Type</th>
                      <th className="px-4 py-2.5">Model</th>
                      <th className="px-4 py-2.5">Serial</th>
                      <th className="px-4 py-2.5">Status</th>
                      <th className="px-4 py-2.5">Next Maint.</th>
                      <th className="px-4 py-2.5">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {equipment.map((e) => (
                      <tr key={e.id} className="hover:bg-gray-50">
                        <td className="px-4 py-2.5 font-medium text-gray-900">{e.name}</td>
                        <td className="px-4 py-2.5 text-gray-700">{e.type.replace(/_/g, ' ')}</td>
                        <td className="px-4 py-2.5 text-gray-600 text-xs">{e.model}</td>
                        <td className="px-4 py-2.5 text-gray-500 text-xs font-mono">{e.serialNumber}</td>
                        <td className="px-4 py-2.5">
                          <span
                            className={clsx(
                              'inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium',
                              equipStatusColor[e.status],
                            )}
                          >
                            {e.status.replace(/_/g, ' ')}
                          </span>
                        </td>
                        <td className="px-4 py-2.5 text-xs text-gray-500">
                          {e.nextMaintenanceDate
                            ? new Date(e.nextMaintenanceDate).toLocaleDateString()
                            : '-'}
                        </td>
                        <td className="px-4 py-2.5">
                          <select
                            value={e.status}
                            onChange={(ev) => handleEquipStatus(e.id, ev.target.value)}
                            className="input text-xs py-1 w-28"
                          >
                            <option value="AVAILABLE">Available</option>
                            <option value="IN_USE">In Use</option>
                            <option value="MAINTENANCE">Maintenance</option>
                            <option value="OUT_OF_SERVICE">Out of Service</option>
                          </select>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2.5"><Building2 className="w-7 h-7 text-primary-500" /> Warehouses</h1>
          <p className="text-sm text-gray-500 mt-1">
            {warehouses.length} facilities &middot; {avgUtil}% avg. capacity
          </p>
        </div>
        <button onClick={openAddWarehouse} className="btn-primary text-sm">
          <Plus className="w-4 h-4" /> Add Warehouse
        </button>
      </div>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <StatCard icon={<Building2 className="w-5 h-5" />} label="Total Warehouses" value={String(warehouses.length)} />
        <StatCard
          icon={<Boxes className="w-5 h-5" />}
          label="Total Capacity"
          value={totalCapacity.toLocaleString()}
          sub={`${totalUtilized.toLocaleString()} utilized`}
        />
        <StatCard
          icon={<MapPin className="w-5 h-5" />}
          label="Active Facilities"
          value={String(warehouses.filter((w) => w.isActive).length)}
          sub={`${warehouses.filter((w) => !w.isActive).length} inactive`}
        />
        <StatCard
          icon={<Thermometer className="w-5 h-5" />}
          label="Avg. Utilization"
          value={`${avgUtil}%`}
        />
      </div>

      <Autocomplete value={search} onChange={setSearch} placeholder="Search by name or code..." minChars={0} className="max-w-md" />

      {loading ? (
        <div className="flex items-center justify-center p-12">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600" />
        </div>
      ) : filtered.length === 0 ? (
        <div className="text-center py-16 card">
          <Building2 className="w-10 h-10 text-gray-300 mx-auto mb-3" />
          <p className="text-gray-500 text-sm">
            {search ? 'No warehouses match your search' : 'No warehouses configured. Add your first warehouse to get started.'}
          </p>
        </div>
      ) : (
        <div className="card overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-200 bg-gray-50/50 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">
                <th className="px-4 py-3 w-8" />
                <th className="px-4 py-3">Code</th>
                <th className="px-4 py-3">Name</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Capacity</th>
                <th className="px-4 py-3">Utilization</th>
                <th className="px-4 py-3">Manager</th>
                <th className="px-4 py-3">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {filtered.map((wh) => {
                const isExpanded = expandedId === wh.id
                const util = capacityUtil(wh)
                return (
                  <tr key={wh.id} className={clsx('group', isExpanded && 'bg-primary-50/30')}>
                    <td className="px-4 py-3">
                      <button
                        onClick={() => setExpandedId(isExpanded ? null : wh.id)}
                        className="p-0.5 hover:bg-gray-100 rounded"
                      >
                        {isExpanded ? (
                          <ChevronDown className="w-4 h-4 text-gray-500" />
                        ) : (
                          <ChevronRight className="w-4 h-4 text-gray-400" />
                        )}
                      </button>
                    </td>
                    <td className="px-4 py-3 font-medium text-gray-900">{wh.code}</td>
                    <td className="px-4 py-3 text-gray-700">{wh.name}</td>
                    <td className="px-4 py-3">
                      <StatusBadge status={wh.isActive ? 'ACTIVE' : 'INACTIVE'} size="sm" />
                    </td>
                    <td className="px-4 py-3 text-gray-700">{(wh.capacity ?? 0).toLocaleString()}</td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        <div className="w-16 h-1.5 bg-gray-200 rounded-full overflow-hidden">
                          <div
                            className={clsx('h-full rounded-full', util > 80 ? 'bg-yellow-500' : 'bg-green-500')}
                            style={{ width: `${Math.min(util, 100)}%` }}
                          />
                        </div>
                        <span className="text-xs text-gray-500">{util}%</span>
                      </div>
                    </td>
                    <td className="px-4 py-3 text-gray-600 text-xs">{wh.managerName || '-'}</td>
                    <td className="px-4 py-3">
                      <button
                        onClick={() => handleDeleteWarehouse(wh.id)}
                        className="p-1.5 hover:bg-red-50 rounded text-gray-400 hover:text-red-600 opacity-0 group-hover:opacity-100 transition-opacity"
                        title="Delete"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {expandedId && filtered.find((w) => w.id === expandedId) && (
        <div className="card p-5 border-l-4 border-l-primary-500">
          {renderDetailPanel(filtered.find((w) => w.id === expandedId)! as Warehouse)}
        </div>
      )}

      {showWhModal && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-lg w-full max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between p-6 border-b border-gray-100">
              <h2 className="text-lg font-semibold text-gray-900">Add Warehouse</h2>
              <button onClick={() => setShowWhModal(false)} className="p-1 hover:bg-gray-100 rounded">
                <X className="w-5 h-5" />
              </button>
            </div>
            <div className="p-6 space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Name</label>
                  <input value={whForm.name} onChange={(e) => setWhForm({ ...whForm, name: e.target.value })} className="input w-full" placeholder="Main Warehouse" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Code</label>
                  <input value={whForm.code} onChange={(e) => setWhForm({ ...whForm, code: e.target.value })} className="input w-full" placeholder="WH-001" />
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Address</label>
                <input value={whForm.street} onChange={(e) => setWhForm({ ...whForm, street: e.target.value })} className="input w-full mb-2" placeholder="Street" />
                <div className="grid grid-cols-3 gap-2">
                  <input value={whForm.city} onChange={(e) => setWhForm({ ...whForm, city: e.target.value })} className="input" placeholder="City" />
                  <input value={whForm.state} onChange={(e) => setWhForm({ ...whForm, state: e.target.value })} className="input" placeholder="State" />
                  <input value={whForm.zip} onChange={(e) => setWhForm({ ...whForm, zip: e.target.value })} className="input" placeholder="ZIP" />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Capacity (sq ft)</label>
                  <input type="number" value={whForm.capacity} onChange={(e) => setWhForm({ ...whForm, capacity: Number(e.target.value) })} className="input w-full" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Status</label>
                  <select value={whForm.status} onChange={(e) => setWhForm({ ...whForm, status: e.target.value })} className="input w-full">
                    <option value="ACTIVE">Active</option>
                    <option value="INACTIVE">Inactive</option>
                    <option value="MAINTENANCE">Maintenance</option>
                  </select>
                </div>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Manager</label>
                  <input value={whForm.managerName} onChange={(e) => setWhForm({ ...whForm, managerName: e.target.value })} className="input w-full" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Email</label>
                  <input value={whForm.contactEmail} onChange={(e) => setWhForm({ ...whForm, contactEmail: e.target.value })} className="input w-full" />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Phone</label>
                  <input value={whForm.contactPhone} onChange={(e) => setWhForm({ ...whForm, contactPhone: e.target.value })} className="input w-full" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Timezone</label>
                  <input value={whForm.timezone} onChange={(e) => setWhForm({ ...whForm, timezone: e.target.value })} className="input w-full" />
                </div>
              </div>
            </div>
            <div className="p-6 border-t border-gray-100 flex justify-end gap-3">
              <button onClick={() => setShowWhModal(false)} className="btn-secondary text-sm">Cancel</button>
              <button onClick={handleSaveWarehouse} disabled={saving} className="btn-primary text-sm">
                {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Building2 className="w-4 h-4" />}
                Create Warehouse
              </button>
            </div>
          </div>
        </div>
      )}

      {showZoneModal && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-md w-full">
            <div className="flex items-center justify-between p-6 border-b border-gray-100">
              <h2 className="text-lg font-semibold text-gray-900">Add Zone</h2>
              <button onClick={() => setShowZoneModal(false)} className="p-1 hover:bg-gray-100 rounded">
                <X className="w-5 h-5" />
              </button>
            </div>
            <div className="p-6 space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Name</label>
                  <input value={zoneForm.name} onChange={(e) => setZoneForm({ ...zoneForm, name: e.target.value })} className="input w-full" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Code</label>
                  <input value={zoneForm.code} onChange={(e) => setZoneForm({ ...zoneForm, code: e.target.value })} className="input w-full" />
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Type</label>
                <select value={zoneForm.type} onChange={(e) => setZoneForm({ ...zoneForm, type: e.target.value })} className="input w-full">
                  <option value="RECEIVING">Receiving</option>
                  <option value="PICKING">Picking</option>
                  <option value="PACKING">Packing</option>
                  <option value="STORAGE">Storage</option>
                  <option value="SHIPPING">Shipping</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Capacity</label>
                <input type="number" value={zoneForm.capacity} onChange={(e) => setZoneForm({ ...zoneForm, capacity: Number(e.target.value) })} className="input w-full" />
              </div>
            </div>
            <div className="p-6 border-t border-gray-100 flex justify-end gap-3">
              <button onClick={() => setShowZoneModal(false)} className="btn-secondary text-sm">Cancel</button>
              <button onClick={handleSaveZone} disabled={saving} className="btn-primary text-sm">
                {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />}
                Create Zone
              </button>
            </div>
          </div>
        </div>
      )}

      {showBinModal && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-md w-full max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between p-6 border-b border-gray-100">
              <h2 className="text-lg font-semibold text-gray-900">Add Bin</h2>
              <button onClick={() => setShowBinModal(false)} className="p-1 hover:bg-gray-100 rounded">
                <X className="w-5 h-5" />
              </button>
            </div>
            <div className="p-6 space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Code</label>
                  <input value={binForm.code} onChange={(e) => setBinForm({ ...binForm, code: e.target.value })} className="input w-full" placeholder="A-01-01" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Type</label>
                  <select value={binForm.type} onChange={(e) => setBinForm({ ...binForm, type: e.target.value })} className="input w-full">
                    <option value="STANDARD">Standard</option>
                    <option value="BULK">Bulk</option>
                    <option value="HAZMAT">Hazmat</option>
                    <option value="CLIMATE">Climate Controlled</option>
                  </select>
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Zone</label>
                <select value={binForm.zoneId} onChange={(e) => setBinForm({ ...binForm, zoneId: e.target.value })} className="input w-full">
                  <option value="">No zone</option>
                  {zones.map((z) => (
                    <option key={z.id} value={z.id}>{z.name} ({z.code})</option>
                  ))}
                </select>
              </div>
              <div className="grid grid-cols-3 gap-2">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Aisle</label>
                  <input value={binForm.aisle} onChange={(e) => setBinForm({ ...binForm, aisle: e.target.value })} className="input w-full" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Rack</label>
                  <input value={binForm.rack} onChange={(e) => setBinForm({ ...binForm, rack: e.target.value })} className="input w-full" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Shelf</label>
                  <input value={binForm.shelf} onChange={(e) => setBinForm({ ...binForm, shelf: e.target.value })} className="input w-full" />
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Position</label>
                <input value={binForm.position} onChange={(e) => setBinForm({ ...binForm, position: e.target.value })} className="input w-full" />
              </div>
              <div className="grid grid-cols-4 gap-2">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Width</label>
                  <input type="number" value={binForm.width} onChange={(e) => setBinForm({ ...binForm, width: Number(e.target.value) })} className="input w-full" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Height</label>
                  <input type="number" value={binForm.height} onChange={(e) => setBinForm({ ...binForm, height: Number(e.target.value) })} className="input w-full" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Depth</label>
                  <input type="number" value={binForm.depth} onChange={(e) => setBinForm({ ...binForm, depth: Number(e.target.value) })} className="input w-full" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Max Wt</label>
                  <input type="number" value={binForm.maxWeight} onChange={(e) => setBinForm({ ...binForm, maxWeight: Number(e.target.value) })} className="input w-full" />
                </div>
              </div>
            </div>
            <div className="p-6 border-t border-gray-100 flex justify-end gap-3">
              <button onClick={() => setShowBinModal(false)} className="btn-secondary text-sm">Cancel</button>
              <button onClick={handleSaveBin} disabled={saving} className="btn-primary text-sm">
                {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />}
                Create Bin
              </button>
            </div>
          </div>
        </div>
      )}

      {showStaffModal && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-md w-full">
            <div className="flex items-center justify-between p-6 border-b border-gray-100">
              <h2 className="text-lg font-semibold text-gray-900">Add Staff</h2>
              <button onClick={() => setShowStaffModal(false)} className="p-1 hover:bg-gray-100 rounded">
                <X className="w-5 h-5" />
              </button>
            </div>
            <div className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Name</label>
                <input value={staffForm.name} onChange={(e) => setStaffForm({ ...staffForm, name: e.target.value })} className="input w-full" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Email</label>
                <input value={staffForm.email} onChange={(e) => setStaffForm({ ...staffForm, email: e.target.value })} className="input w-full" type="email" />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Role</label>
                  <select value={staffForm.role} onChange={(e) => setStaffForm({ ...staffForm, role: e.target.value })} className="input w-full">
                    <option value="PICKER">Picker</option>
                    <option value="PACKER">Packer</option>
                    <option value="RECEIVER">Receiver</option>
                    <option value="SUPERVISOR">Supervisor</option>
                    <option value="MANAGER">Manager</option>
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Shift</label>
                  <select value={staffForm.shift} onChange={(e) => setStaffForm({ ...staffForm, shift: e.target.value })} className="input w-full">
                    <option value="MORNING">Morning</option>
                    <option value="AFTERNOON">Afternoon</option>
                    <option value="NIGHT">Night</option>
                  </select>
                </div>
              </div>
            </div>
            <div className="p-6 border-t border-gray-100 flex justify-end gap-3">
              <button onClick={() => setShowStaffModal(false)} className="btn-secondary text-sm">Cancel</button>
              <button onClick={handleSaveStaff} disabled={saving} className="btn-primary text-sm">
                {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />}
                Add Staff
              </button>
            </div>
          </div>
        </div>
      )}

      {showEquipModal && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-md w-full">
            <div className="flex items-center justify-between p-6 border-b border-gray-100">
              <h2 className="text-lg font-semibold text-gray-900">Add Equipment</h2>
              <button onClick={() => setShowEquipModal(false)} className="p-1 hover:bg-gray-100 rounded">
                <X className="w-5 h-5" />
              </button>
            </div>
            <div className="p-6 space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Name</label>
                  <input value={equipForm.name} onChange={(e) => setEquipForm({ ...equipForm, name: e.target.value })} className="input w-full" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Type</label>
                  <select value={equipForm.type} onChange={(e) => setEquipForm({ ...equipForm, type: e.target.value })} className="input w-full">
                    <option value="FORKLIFT">Forklift</option>
                    <option value="PALLET_JACK">Pallet Jack</option>
                    <option value="CONVEYOR">Conveyor</option>
                    <option value="SCANNER">Scanner</option>
                    <option value="DOLLY">Dolly</option>
                    <option value="OTHER">Other</option>
                  </select>
                </div>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Model</label>
                  <input value={equipForm.model} onChange={(e) => setEquipForm({ ...equipForm, model: e.target.value })} className="input w-full" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Serial Number</label>
                  <input value={equipForm.serialNumber} onChange={(e) => setEquipForm({ ...equipForm, serialNumber: e.target.value })} className="input w-full" />
                </div>
              </div>
            </div>
            <div className="p-6 border-t border-gray-100 flex justify-end gap-3">
              <button onClick={() => setShowEquipModal(false)} className="btn-secondary text-sm">Cancel</button>
              <button onClick={handleSaveEquipment} disabled={saving} className="btn-primary text-sm">
                {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Wrench className="w-4 h-4" />}
                Add Equipment
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
