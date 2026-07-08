import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  PackagePlus, Package, Box, Printer, CheckCircle, Search, Eye,
  Clock, AlertTriangle, ArrowRight, QrCode, Scan,
} from 'lucide-react'
import clsx from 'clsx'
import { useToast } from '../hooks/useToast'
import * as packingApi from '../api/packing'
import Autocomplete from '../components/common/Autocomplete'
import { EnterpriseKPICard, EnterpriseStatusBadge } from '../components/enterprise'

export default function PackerScreen() {
  const navigate = useNavigate()
  const { addToast } = useToast()
  const queryClient = useQueryClient()
  const [searchTerm, setSearchTerm] = useState('')
  const [scanInput, setScanInput] = useState('')

  const { data: packages = [], isLoading } = useQuery({
    queryKey: ['packer-packages'],
    queryFn: async () => {
      const res = await packingApi.getPackages({})
      const d = res.data
      return (Array.isArray(d) ? d : (d?.content ?? [])).slice(0, 20)
    },
  })

  const startPacking = useMutation({
    mutationFn: async (id: string) => { await new Promise(r => setTimeout(r, 300)); return id },
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['packer-packages'] }); addToast({ type: 'success', title: 'Packing started' }) },
  })

  const completePacking = useMutation({
    mutationFn: async (id: string) => { await new Promise(r => setTimeout(r, 400)); return id },
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['packer-packages'] }); addToast({ type: 'success', title: 'Package completed' }) },
  })

  const kpis = [
    { title: 'To Pack', value: '18', icon: <Package className="w-5 h-5" />, color: 'warning' as const, trend: null },
    { title: 'Packed Today', value: '47', icon: <PackagePlus className="w-5 h-5" />, color: 'success' as const, trend: { value: 12, isUp: true } },
    { title: 'My Station', value: 'Stn #4', icon: <Box className="w-5 h-5" />, color: 'info' as const, trend: null },
    { title: 'Avg Pack Time', value: '3.2 min', icon: <Clock className="w-5 h-5" />, color: 'primary' as const, trend: { value: 8, isUp: true } },
  ]

  const pendingPacks = packages.filter((p: any) => p.status === 'PENDING' || p.status === 'ALLOCATED')
  const inProgress = packages.filter((p: any) => p.status === 'IN_PROGRESS')

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100 flex items-center gap-2.5">
            <PackagePlus className="w-7 h-7 text-emerald-500" />
            Packing Station
          </h1>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">Scan, pack, and label orders for shipment</p>
        </div>
        <button onClick={() => navigate('/packing')} className="enterprise-btn-secondary text-sm flex items-center gap-1.5 px-4 py-2">
          <Box className="w-4 h-4" /> Full Packing View
        </button>
      </div>

      <div className="grid grid-cols-4 gap-4">
        {kpis.map(k => (
          <EnterpriseKPICard key={k.title} title={k.title} value={k.value} icon={k.icon} color={k.color} trend={k.trend} />
        ))}
      </div>

      {/* Scan Bar */}
      <div className="enterprise-card p-4">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-emerald-50 dark:bg-emerald-900/20 flex items-center justify-center">
            <Scan className="w-5 h-5 text-emerald-600" />
          </div>
          <div className="relative flex-1">
            <Autocomplete
              value={scanInput}
              onChange={setScanInput}
              placeholder="Scan or enter order / package ID..."
              minChars={0}
              inputClassName="w-full pl-10 pr-4 py-3 text-sm border-2 border-emerald-200 dark:border-emerald-800 rounded-xl bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500"
            />
            <QrCode className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-emerald-400" />
          </div>
          <button className="enterprise-btn-primary text-sm px-6 py-3 bg-emerald-600 hover:bg-emerald-700">
            <Search className="w-4 h-4" /> Find
          </button>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-6">
        {/* Pending Queue */}
        <div className="enterprise-card p-5">
          <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-4 flex items-center gap-2">
            <Package className="w-4 h-4 text-amber-500" /> Packing Queue
            <span className="ml-auto text-xs text-gray-400">{pendingPacks.length} items</span>
          </h3>
          {pendingPacks.length === 0 ? (
            <div className="text-center py-8 text-gray-400">
              <CheckCircle className="w-10 h-10 mx-auto mb-2 text-green-300" />
              <p className="text-sm">All caught up!</p>
            </div>
          ) : (
            <div className="space-y-2">
              {pendingPacks.slice(0, 8).map((pkg: any, i: number) => (
                <div key={pkg.id || i} className="flex items-center justify-between p-3 rounded-lg bg-gray-50 dark:bg-gray-800/50 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors">
                  <div className="flex items-center gap-3">
                    <Package className="w-4 h-4 text-gray-400" />
                    <div>
                      <p className="text-sm font-medium text-gray-900 dark:text-gray-100">Package #{pkg.id?.slice(0, 8) || `PKG-${i + 1}`}</p>
                      <p className="text-xs text-gray-500">{pkg.orderNumber || `ORD-${i + 1}`}</p>
                    </div>
                  </div>
                  <button onClick={() => startPacking.mutate(pkg.id)} className="enterprise-btn-primary text-xs px-3 py-1.5 bg-emerald-600 hover:bg-emerald-700">
                    Start Packing
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* In Progress */}
        <div className="enterprise-card p-5">
          <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-4 flex items-center gap-2">
            <Box className="w-4 h-4 text-blue-500" /> In Progress
          </h3>
          {inProgress.length === 0 ? (
            <div className="text-center py-8 text-gray-400">
              <PackagePlus className="w-10 h-10 mx-auto mb-2 text-gray-300" />
              <p className="text-sm">No active packing</p>
            </div>
          ) : (
            <div className="space-y-2">
              {inProgress.map((pkg: any, i: number) => (
                <div key={pkg.id || i} className="flex items-center justify-between p-3 rounded-lg bg-blue-50 dark:bg-blue-900/10 border border-blue-200 dark:border-blue-800">
                  <div className="flex items-center gap-3">
                    <Box className="w-4 h-4 text-blue-500" />
                    <div>
                      <p className="text-sm font-medium text-gray-900 dark:text-gray-100">Package #{pkg.id?.slice(0, 8)}</p>
                      <p className="text-xs text-gray-500">3 items · Box size: M</p>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    <button className="enterprise-btn-secondary text-xs px-2 py-1"><Printer className="w-3 h-3" /></button>
                    <button onClick={() => completePacking.mutate(pkg.id)} className="enterprise-btn-primary text-xs px-3 py-1.5 bg-green-600 hover:bg-green-700">
                      <CheckCircle className="w-3 h-3" /> Complete
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
