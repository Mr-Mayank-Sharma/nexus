import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  AlertTriangle, Plus, Search, Sliders, Store, Truck, Shield,
  Gauge, Save, ToggleLeft, ToggleRight, Info, Trash2, Edit,
} from 'lucide-react'
import clsx from 'clsx'
import { useToast } from '../hooks/useToast'
import * as productsApi from '../api/products'
import { EnterpriseTabs, EnterpriseStatusBadge, EnterpriseKPICard } from '../components/enterprise'
import Autocomplete from '../components/common/Autocomplete'
import type { Tab } from '../components/enterprise'
import PermissionGate from '../components/rbac/PermissionGate'

type AtpTab = 'thresholds' | 'safety-stock' | 'store-pickup' | 'shipping'

interface ThresholdRule {
  id: string
  name: string
  category: string
  warehouseId?: string
  minThreshold: number
  maxThreshold: number
  enabled: boolean
  priority: number
}

interface SafetyStockRule {
  id: string
  productId: string
  productName: string
  sku: string
  safetyStock: number
  leadTimeDays: number
  reorderPoint: number
  enabled: boolean
}

interface StorePickupConfig {
  id: string
  storeId: string
  storeName: string
  enabled: boolean
  bufferDays: number
  maxQuantity: number
  cutoffTime: string
}

interface ShippingRule {
  id: string
  name: string
  carrier: string
  service: string
  atpLimit: number
  priority: number
  enabled: boolean
}

export default function ATPRulesPage() {
  const { addToast } = useToast()
  const queryClient = useQueryClient()
  const [activeTab, setActiveTab] = useState<AtpTab>('thresholds')
  const [searchTerm, setSearchTerm] = useState('')

  const { data: thresholds = [], isLoading: loadingT } = useQuery({
    queryKey: ['atp-thresholds'],
    queryFn: async () => {
      const mock: ThresholdRule[] = [
        { id: 't1', name: 'Electronics Threshold', category: 'Electronics', minThreshold: 10, maxThreshold: 500, enabled: true, priority: 1 },
        { id: 't2', name: 'Clothing Threshold', category: 'Clothing', minThreshold: 20, maxThreshold: 1000, enabled: true, priority: 2 },
        { id: 't3', name: 'Food & Grocery', category: 'Groceries', minThreshold: 50, maxThreshold: 2000, enabled: false, priority: 3 },
        { id: 't4', name: 'Home Goods', category: 'Home', minThreshold: 5, maxThreshold: 300, enabled: true, priority: 4 },
        { id: 't5', name: 'Premium Items', category: 'Premium', minThreshold: 2, maxThreshold: 50, enabled: true, priority: 5 },
      ]
      return mock
    },
  })

  const { data: safetyStock = [], isLoading: loadingS } = useQuery({
    queryKey: ['atp-safety-stock'],
    queryFn: async () => {
      const res = await productsApi.getProducts()
      const d = res.data
      const list = Array.isArray(d) ? d : (d?.content ?? [])
      return list.slice(0, 20).map((p: any, i: number) => ({
        id: `ss-${i}`,
        productId: p.id,
        productName: p.name || `Product ${i}`,
        sku: p.sku || `SKU${i}`,
        safetyStock: Math.floor(Math.random() * 100) + 10,
        leadTimeDays: Math.floor(Math.random() * 14) + 1,
        reorderPoint: Math.floor(Math.random() * 50) + 5,
        enabled: Math.random() > 0.2,
      }) as SafetyStockRule)
    },
    enabled: activeTab === 'safety-stock',
  })

  const { data: storeConfigs = [], isLoading: loadingSt } = useQuery({
    queryKey: ['atp-store-pickup'],
    queryFn: async () => {
      const mock: StorePickupConfig[] = [
        { id: 'sp1', storeId: 'store-1', storeName: 'Downtown Store', enabled: true, bufferDays: 1, maxQuantity: 10, cutoffTime: '14:00' },
        { id: 'sp2', storeId: 'store-2', storeName: 'Mall Location', enabled: true, bufferDays: 2, maxQuantity: 5, cutoffTime: '12:00' },
        { id: 'sp3', storeId: 'store-3', storeName: 'Airport Kiosk', enabled: false, bufferDays: 0, maxQuantity: 3, cutoffTime: '10:00' },
        { id: 'sp4', storeId: 'store-4', storeName: 'Suburban Store', enabled: true, bufferDays: 1, maxQuantity: 8, cutoffTime: '16:00' },
      ]
      return mock
    },
    enabled: activeTab === 'store-pickup',
  })

  const { data: shippingRules = [], isLoading: loadingSh } = useQuery({
    queryKey: ['atp-shipping-rules'],
    queryFn: async () => {
      const mock: ShippingRule[] = [
        { id: 'sh1', name: 'Standard Ground', carrier: 'UPS', service: 'Ground', atpLimit: 100, priority: 1, enabled: true },
        { id: 'sh2', name: 'Express Air', carrier: 'FedEx', service: 'Express', atpLimit: 50, priority: 2, enabled: true },
        { id: 'sh3', name: 'Next Day', carrier: 'UPS', service: 'Next Day Air', atpLimit: 20, priority: 3, enabled: true },
        { id: 'sh4', name: 'Economy', carrier: 'USPS', service: 'Parcel Select', atpLimit: 200, priority: 4, enabled: false },
        { id: 'sh5', name: 'Same Day', carrier: 'OnTrac', service: 'Same Day', atpLimit: 10, priority: 5, enabled: true },
      ]
      return mock
    },
    enabled: activeTab === 'shipping',
  })

  const toggleMutation = useMutation({
    mutationFn: async ({ type, id }: { type: AtpTab; id: string }) => {
      await new Promise(r => setTimeout(r, 300))
      return { type, id }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['atp-thresholds'] })
      queryClient.invalidateQueries({ queryKey: ['atp-safety-stock'] })
      queryClient.invalidateQueries({ queryKey: ['atp-store-pickup'] })
      queryClient.invalidateQueries({ queryKey: ['atp-shipping-rules'] })
      addToast({ type: 'success', title: 'Rule updated' })
    },
  })

  const tabs: Tab[] = [
    { id: 'thresholds', label: 'Thresholds', badge: thresholds.filter(t => t.enabled).length },
    { id: 'safety-stock', label: 'Safety Stock', badge: safetyStock.filter(s => s.enabled).length },
    { id: 'store-pickup', label: 'Store Pickup' },
    { id: 'shipping', label: 'Shipping' },
  ]

  const filteredThresholds = thresholds.filter(t =>
    !searchTerm || t.name.toLowerCase().includes(searchTerm.toLowerCase()) || t.category.toLowerCase().includes(searchTerm.toLowerCase())
  )

  const filteredSafetyStock = safetyStock.filter(s =>
    !searchTerm || s.productName.toLowerCase().includes(searchTerm.toLowerCase()) || s.sku.toLowerCase().includes(searchTerm.toLowerCase())
  )

  const filteredStores = storeConfigs.filter(s =>
    !searchTerm || s.storeName.toLowerCase().includes(searchTerm.toLowerCase())
  )

  const filteredShipping = shippingRules.filter(s =>
    !searchTerm || s.name.toLowerCase().includes(searchTerm.toLowerCase()) || s.carrier.toLowerCase().includes(searchTerm.toLowerCase())
  )

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100 flex items-center gap-2.5"><Gauge className="w-7 h-7 text-primary-500" /> ATP Rules</h1>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">Available to Promise — stock allocation rules</p>
        </div>
        <div className="flex items-center gap-2">
          <EnterpriseKPICard title="Active Rules" value={thresholds.filter(t => t.enabled).length + safetyStock.filter(s => s.enabled).length + shippingRules.filter(s => s.enabled).length + storeConfigs.filter(s => s.enabled).length} icon={<Gauge className="w-4 h-4" />} color="primary" trend={null} />
        </div>
      </div>

      <EnterpriseTabs tabs={tabs} activeTab={activeTab} onChange={t => setActiveTab(t as AtpTab)} />

      <div className="flex items-center gap-3">
        <Autocomplete value={searchTerm} onChange={setSearchTerm} placeholder={activeTab === 'thresholds' ? 'Search threshold rules...' : activeTab === 'safety-stock' ? 'Search products...' : activeTab === 'store-pickup' ? 'Search stores...' : 'Search shipping rules...'} minChars={0} className="flex-1 max-w-md" />
        <PermissionGate resource="inventory" action="create">
          <button className="enterprise-btn-primary text-sm flex items-center gap-1.5 px-4 py-2.5">
            <Plus className="w-4 h-4" /> Add Rule
          </button>
        </PermissionGate>
      </div>

      {/* Thresholds Tab */}
      {activeTab === 'thresholds' && (
        <>
          {loadingT ? (
            <div className="enterprise-card flex items-center justify-center p-12"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600" /></div>
          ) : (
            <div className="space-y-3">
              {filteredThresholds.map(rule => (
                <div key={rule.id} className="enterprise-card p-4 hover:bg-gray-50 dark:hover:bg-gray-800/30 transition-colors">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <Sliders className="w-5 h-5 text-primary-500" />
                      <div>
                        <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{rule.name}</p>
                        <p className="text-xs text-gray-500 dark:text-gray-400">Category: {rule.category} · Priority {rule.priority}</p>
                      </div>
                    </div>
                    <div className="flex items-center gap-4">
                      <div className="text-right">
                        <p className="text-xs text-gray-500 dark:text-gray-400">Min·Max</p>
                        <p className="text-sm font-semibold text-gray-700 dark:text-gray-300">{rule.minThreshold}·{rule.maxThreshold}</p>
                      </div>
                      <PermissionGate resource="inventory" action="edit">
                        <button onClick={() => toggleMutation.mutate({ type: 'thresholds', id: rule.id })} className="p-1 hover:bg-gray-100 dark:hover:bg-gray-700 rounded transition-colors">
                          {rule.enabled ? <ToggleRight className="w-6 h-6 text-primary-600" /> : <ToggleLeft className="w-6 h-6 text-gray-400" />}
                        </button>
                      </PermissionGate>
                      <PermissionGate resource="inventory" action="edit">
                        <button className="p-1 hover:bg-gray-100 dark:hover:bg-gray-700 rounded text-gray-400 hover:text-blue-500 transition-colors"><Edit className="w-4 h-4" /></button>
                      </PermissionGate>
                      <PermissionGate resource="inventory" action="delete">
                        <button className="p-1 hover:bg-gray-100 dark:hover:bg-gray-700 rounded text-gray-400 hover:text-red-500 transition-colors"><Trash2 className="w-4 h-4" /></button>
                      </PermissionGate>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </>
      )}

      {/* Safety Stock Tab */}
      {activeTab === 'safety-stock' && (
        <>
          {loadingS ? (
            <div className="enterprise-card flex items-center justify-center p-12"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600" /></div>
          ) : filteredSafetyStock.length === 0 ? (
            <div className="enterprise-card p-12 text-center"><Shield className="w-12 h-12 mx-auto mb-3 text-gray-300 dark:text-gray-600" /><p className="font-medium text-gray-500 dark:text-gray-400">No safety stock rules</p></div>
          ) : (
            <div className="enterprise-card overflow-hidden">
              <table className="enterprise-table w-full">
                <thead>
                  <tr>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">Product</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">SKU</th>
                    <th className="px-4 py-3 text-center text-xs font-semibold text-gray-500 uppercase">Safety Stock</th>
                    <th className="px-4 py-3 text-center text-xs font-semibold text-gray-500 uppercase">Lead Time</th>
                    <th className="px-4 py-3 text-center text-xs font-semibold text-gray-500 uppercase">Reorder Point</th>
                    <th className="px-4 py-3 text-center text-xs font-semibold text-gray-500 uppercase">Active</th>
                    <th className="px-4 py-3 text-right text-xs font-semibold text-gray-500 uppercase">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
                  {filteredSafetyStock.map(item => (
                    <tr key={item.id} className="enterprise-table-row">
                      <td className="px-4 py-3 text-sm text-gray-900 dark:text-gray-100">{item.productName}</td>
                      <td className="px-4 py-3 text-xs font-mono text-gray-500 dark:text-gray-400">{item.sku}</td>
                      <td className="px-4 py-3 text-center">
                        <span className="text-sm font-semibold text-gray-700 dark:text-gray-300">{item.safetyStock}</span>
                      </td>
                      <td className="px-4 py-3 text-center text-sm text-gray-600 dark:text-gray-400">{item.leadTimeDays}d</td>
                      <td className="px-4 py-3 text-center text-sm text-gray-600 dark:text-gray-400">{item.reorderPoint}</td>
                      <td className="px-4 py-3 text-center">
                        <PermissionGate resource="inventory" action="edit">
                          <button onClick={() => toggleMutation.mutate({ type: 'safety-stock', id: item.id })}>
                            {item.enabled ? <ToggleRight className="w-5 h-5 text-primary-600 mx-auto" /> : <ToggleLeft className="w-5 h-5 text-gray-400 mx-auto" />}
                          </button>
                        </PermissionGate>
                      </td>
                      <td className="px-4 py-3 text-right">
                        <PermissionGate resource="inventory" action="edit">
                          <button className="enterprise-btn-secondary text-xs px-2 py-1"><Edit className="w-3 h-3" /></button>
                        </PermissionGate>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      {/* Store Pickup Tab */}
      {activeTab === 'store-pickup' && (
        <>
          {loadingSt ? (
            <div className="enterprise-card flex items-center justify-center p-12"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600" /></div>
          ) : (
            <div className="space-y-3">
              {filteredStores.map(store => (
                <div key={store.id} className="enterprise-card p-4 hover:bg-gray-50 dark:hover:bg-gray-800/30 transition-colors">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <div className={clsx('w-10 h-10 rounded-xl flex items-center justify-center', store.enabled ? 'bg-green-50 dark:bg-green-900/20 text-green-600' : 'bg-gray-50 dark:bg-gray-800 text-gray-400')}>
                        <Store className="w-5 h-5" />
                      </div>
                      <div>
                        <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{store.storeName}</p>
                        <p className="text-xs text-gray-500 dark:text-gray-400">
                          Buffer: {store.bufferDays}d · Max: {store.maxQuantity} units · Cutoff: {store.cutoffTime}
                        </p>
                      </div>
                    </div>
                    <PermissionGate resource="inventory" action="edit">
                      <button onClick={() => toggleMutation.mutate({ type: 'store-pickup', id: store.id })}>
                        {store.enabled ? <ToggleRight className="w-6 h-6 text-primary-600" /> : <ToggleLeft className="w-6 h-6 text-gray-400" />}
                      </button>
                    </PermissionGate>
                  </div>
                </div>
              ))}
            </div>
          )}
        </>
      )}

      {/* Shipping Rules Tab */}
      {activeTab === 'shipping' && (
        <>
          {loadingSh ? (
            <div className="enterprise-card flex items-center justify-center p-12"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600" /></div>
          ) : (
            <div className="space-y-3">
              {filteredShipping.map(rule => (
                <div key={rule.id} className="enterprise-card p-4 hover:bg-gray-50 dark:hover:bg-gray-800/30 transition-colors">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 rounded-xl bg-blue-50 dark:bg-blue-900/20 flex items-center justify-center text-blue-600">
                        <Truck className="w-5 h-5" />
                      </div>
                      <div>
                        <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{rule.name}</p>
                        <p className="text-xs text-gray-500 dark:text-gray-400">{rule.carrier} · {rule.service} · Priority {rule.priority}</p>
                      </div>
                    </div>
                    <div className="flex items-center gap-4">
                      <div className="text-right">
                        <p className="text-xs text-gray-500 dark:text-gray-400">ATP Limit</p>
                        <p className="text-sm font-semibold text-gray-700 dark:text-gray-300">{rule.atpLimit}</p>
                      </div>
                      <PermissionGate resource="inventory" action="edit">
                        <button onClick={() => toggleMutation.mutate({ type: 'shipping', id: rule.id })}>
                          {rule.enabled ? <ToggleRight className="w-6 h-6 text-primary-600" /> : <ToggleLeft className="w-6 h-6 text-gray-400" />}
                        </button>
                      </PermissionGate>
                      <PermissionGate resource="inventory" action="edit">
                        <button className="p-1 hover:bg-gray-100 dark:hover:bg-gray-700 rounded text-gray-400 hover:text-blue-500 transition-colors"><Edit className="w-4 h-4" /></button>
                      </PermissionGate>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  )
}
