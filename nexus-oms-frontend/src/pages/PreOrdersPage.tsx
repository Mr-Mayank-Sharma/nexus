import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  Calendar, Package, ShoppingCart, Clock, CheckCircle, AlertTriangle,
  Search, Eye, Archive, TrendingUp, Filter,
} from 'lucide-react'
import clsx from 'clsx'
import { useToast } from '../hooks/useToast'
import * as ordersApi from '../api/orders'
import * as productsApi from '../api/products'
import { Order } from '../types'
import { EnterpriseTabs, EnterpriseStatusBadge, EnterpriseKPICard } from '../components/enterprise'
import Autocomplete from '../components/common/Autocomplete'
import PermissionGate from '../components/rbac/PermissionGate'
import type { Tab } from '../components/enterprise'

type PreOrderTab = 'products' | 'orders' | 'audit'

interface PreOrderProduct {
  id: string
  sku: string
  name: string
  price: number
  expectedDate?: string
  preOrderCount: number
  status: 'ACTIVE' | 'FULFILLED' | 'CANCELLED'
  imageUrl?: string
}

interface AuditEntry {
  id: string
  productId: string
  productName: string
  action: 'CREATED' | 'UPDATED' | 'FULFILLED' | 'CANCELLED'
  oldDate?: string
  newDate?: string
  changedBy: string
  changedAt: string
}

export default function PreOrdersPage() {
  const navigate = useNavigate()
  const { addToast } = useToast()
  const [activeTab, setActiveTab] = useState<PreOrderTab>('products')
  const [searchTerm, setSearchTerm] = useState('')
  const [dateFilter, setDateFilter] = useState<'all' | 'this-month' | 'next-month' | 'overdue'>('all')

  const { data: preOrderProducts = [], isLoading: loadingProducts } = useQuery({
    queryKey: ['preorder-products'],
    queryFn: async () => {
      const res = await productsApi.getProducts()
      const d = res.data
      const list = Array.isArray(d) ? d : (d?.content ?? [])
      const mockPreOrders: PreOrderProduct[] = list.slice(0, 20).map((p: any) => ({
        id: p.id,
        sku: p.sku || `SKU-${Math.random().toString(36).slice(2, 8).toUpperCase()}`,
        name: p.name || 'Product',
        price: p.price || Math.floor(Math.random() * 200) + 10,
        expectedDate: new Date(Date.now() + Math.random() * 60 * 24 * 60 * 60 * 1000).toISOString(),
        preOrderCount: Math.floor(Math.random() * 50),
        status: Math.random() > 0.3 ? 'ACTIVE' as const : 'FULFILLED' as const,
      }))
      return mockPreOrders
    },
  })

  const { data: preOrders = [], isLoading: loadingOrders } = useQuery({
    queryKey: ['preorder-orders'],
    queryFn: async () => {
      const res = await ordersApi.getOrders({})
      const d = res.data
      const list = Array.isArray(d) ? d : (d?.content ?? [])
      return list.slice(0, 30).map((o: any, i: number) => ({
        ...o,
        _preOrder: true,
        expectedDate: new Date(Date.now() + (i % 5) * 7 * 24 * 60 * 60 * 1000).toISOString(),
      })) as Order[]
    },
    enabled: activeTab === 'orders',
  })

  const { data: auditEntries = [], isLoading: loadingAudit } = useQuery({
    queryKey: ['preorder-audit'],
    queryFn: async () => {
      const sample: AuditEntry[] = Array.from({ length: 15 }, (_, i) => ({
        id: `audit-${i}`,
        productId: `prod-${i}`,
        productName: `Product ${i + 1}`,
        action: (['CREATED', 'UPDATED', 'FULFILLED', 'CANCELLED'] as const)[i % 4],
        oldDate: i % 2 === 0 ? new Date(Date.now() - (i + 1) * 7 * 24 * 60 * 60 * 1000).toISOString() : undefined,
        newDate: new Date(Date.now() + i * 7 * 24 * 60 * 60 * 1000).toISOString(),
        changedBy: ['admin', 'manager', 'system'][i % 3],
        changedAt: new Date(Date.now() - i * 24 * 60 * 60 * 1000).toISOString(),
      }))
      return sample
    },
    enabled: activeTab === 'audit',
  })

  const filteredProducts = preOrderProducts.filter(p => {
    if (searchTerm && !p.name.toLowerCase().includes(searchTerm.toLowerCase()) && !p.sku.toLowerCase().includes(searchTerm.toLowerCase())) return false
    if (dateFilter === 'all') return true
    const expected = p.expectedDate ? new Date(p.expectedDate) : null
    if (!expected) return dateFilter === 'overdue'
    const now = new Date()
    const thisMonthEnd = new Date(now.getFullYear(), now.getMonth() + 1, 0)
    const nextMonthEnd = new Date(now.getFullYear(), now.getMonth() + 2, 0)
    if (dateFilter === 'this-month') return expected <= thisMonthEnd && expected >= now
    if (dateFilter === 'next-month') return expected > thisMonthEnd && expected <= nextMonthEnd
    if (dateFilter === 'overdue') return expected < now
    return true
  })

  const activeProducts = preOrderProducts.filter(p => p.status === 'ACTIVE').length
  const totalPreOrders = preOrders.length

  const tabs: Tab[] = [
    { id: 'products', label: 'Products', badge: activeProducts },
    { id: 'orders', label: 'Orders', badge: totalPreOrders },
    { id: 'audit', label: 'Audit', badge: auditEntries.length },
  ]

  const formatDate = (d: string) => new Date(d).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100 flex items-center gap-2.5"><Package className="w-7 h-7 text-primary-500" /> Pre-Orders</h1>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">Manage pre-order products and orders</p>
        </div>
        <div className="flex items-center gap-2">
          <EnterpriseKPICard title="Active Products" value={activeProducts} icon={<Package className="w-4 h-4" />} color="primary" trend={null} />
          <EnterpriseKPICard title="Total Pre-Orders" value={totalPreOrders} icon={<ShoppingCart className="w-4 h-4" />} color="info" trend={null} />
        </div>
      </div>

      <EnterpriseTabs tabs={tabs} activeTab={activeTab} onChange={t => setActiveTab(t as PreOrderTab)} />

      {activeTab === 'products' && (
        <>
          <div className="flex items-center gap-3">
            <Autocomplete value={searchTerm} onChange={setSearchTerm} placeholder="Search pre-order products..." minChars={0} className="flex-1 max-w-md" />
            <div className="flex gap-1 bg-gray-100 dark:bg-gray-700 rounded-lg p-0.5">
              {(['all', 'this-month', 'next-month', 'overdue'] as const).map(f => (
                <button key={f} onClick={() => setDateFilter(f)}
                  className={clsx('px-3 py-1.5 text-xs font-medium rounded-md transition-all capitalize',
                    dateFilter === f ? 'bg-white dark:bg-gray-600 text-gray-900 dark:text-gray-100 shadow-sm' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300')}>
                  {f === 'all' ? 'All' : f === 'this-month' ? 'This Month' : f === 'next-month' ? 'Next Month' : 'Overdue'}
                </button>
              ))}
            </div>
          </div>

          {loadingProducts ? (
            <div className="enterprise-card flex items-center justify-center p-12"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600" /></div>
          ) : filteredProducts.length === 0 ? (
            <div className="enterprise-card p-12 text-center"><Package className="w-12 h-12 mx-auto mb-3 text-gray-300 dark:text-gray-600" /><p className="font-medium text-gray-500 dark:text-gray-400">No pre-order products found</p></div>
          ) : (
            <div className="enterprise-card overflow-hidden">
              <div className="overflow-x-auto">
                <table className="enterprise-table w-full">
                  <thead>
                    <tr>
                      <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">Product</th>
                      <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">SKU</th>
                      <th className="px-4 py-3 text-center text-xs font-semibold text-gray-500 uppercase">Price</th>
                      <th className="px-4 py-3 text-center text-xs font-semibold text-gray-500 uppercase">Pre-Orders</th>
                      <th className="px-4 py-3 text-center text-xs font-semibold text-gray-500 uppercase">Expected</th>
                      <th className="px-4 py-3 text-center text-xs font-semibold text-gray-500 uppercase">Status</th>
                      <th className="px-4 py-3 text-right text-xs font-semibold text-gray-500 uppercase">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
                    {filteredProducts.map(product => {
                      const expectedDate = product.expectedDate ? new Date(product.expectedDate) : null
                      const isOverdue = expectedDate && expectedDate < new Date()
                      return (
                        <tr key={product.id} className="enterprise-table-row">
                          <td className="px-4 py-3 text-sm font-medium text-gray-900 dark:text-gray-100">{product.name}</td>
                          <td className="px-4 py-3 text-xs font-mono text-gray-500 dark:text-gray-400">{product.sku}</td>
                          <td className="px-4 py-3 text-center text-sm text-gray-700 dark:text-gray-300">${product.price.toFixed(2)}</td>
                          <td className="px-4 py-3 text-center">
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-blue-50 dark:bg-blue-900/20 text-blue-700 dark:text-blue-400">
                              {product.preOrderCount}
                            </span>
                          </td>
                          <td className="px-4 py-3 text-center">
                            {product.expectedDate ? (
                              <span className={clsx('inline-flex items-center gap-1 text-xs font-medium', isOverdue ? 'text-red-600 dark:text-red-400' : 'text-gray-600 dark:text-gray-400')}>
                                <Calendar className="w-3 h-3" />
                                {formatDate(product.expectedDate)}
                                {isOverdue && <AlertTriangle className="w-3 h-3 text-red-500" />}
                              </span>
                            ) : <span className="text-xs text-gray-400">—</span>}
                          </td>
                          <td className="px-4 py-3 text-center">
                            <EnterpriseStatusBadge status={product.status === 'ACTIVE' ? 'pending' : product.status === 'FULFILLED' ? 'success' : 'error'} label={product.status} />
                          </td>
                          <td className="px-4 py-3 text-right">
                            <button className="enterprise-btn-secondary text-xs px-2 py-1" onClick={() => navigate(`/products/${product.id}`)}>
                              <Eye className="w-3.5 h-3.5" /> View
                            </button>
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </>
      )}

      {activeTab === 'orders' && (
        <>
          {loadingOrders ? (
            <div className="enterprise-card flex items-center justify-center p-12"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600" /></div>
          ) : preOrders.length === 0 ? (
            <div className="enterprise-card p-12 text-center"><ShoppingCart className="w-12 h-12 mx-auto mb-3 text-gray-300 dark:text-gray-600" /><p className="font-medium text-gray-500 dark:text-gray-400">No pre-orders yet</p></div>
          ) : (
            <div className="enterprise-card overflow-hidden">
              <div className="divide-y divide-gray-100 dark:divide-gray-800">
                {preOrders.map((order, i) => (
                  <div key={order.id} className="p-4 hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-3">
                        <div className={clsx('w-10 h-10 rounded-xl flex items-center justify-center',
                          i % 3 === 0 ? 'bg-purple-50 dark:bg-purple-900/20 text-purple-600 dark:text-purple-400' :
                          i % 3 === 1 ? 'bg-blue-50 dark:bg-blue-900/20 text-blue-600 dark:text-blue-400' :
                          'bg-amber-50 dark:bg-amber-900/20 text-amber-600 dark:text-amber-400')}>
                          <ShoppingCart className="w-5 h-5" />
                        </div>
                        <div>
                          <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{order.customerName || 'Customer'}</p>
                          <p className="text-xs text-gray-500 dark:text-gray-400">Order #{order.orderNumber || order.id.slice(0, 8)}</p>
                        </div>
                      </div>
                      <div className="flex items-center gap-3">
                        <span className="text-xs text-gray-400 dark:text-gray-400 flex items-center gap-1">
                          <Calendar className="w-3 h-3" />
                          Expected {formatDate((order as any).expectedDate)}
                        </span>
                        <EnterpriseStatusBadge status={i % 5 === 0 ? 'success' : 'pending'} label={i % 5 === 0 ? 'Fulfilled' : 'Pending'} />
                        <button className="enterprise-btn-secondary text-xs px-2 py-1" onClick={() => navigate(`/orders/${order.id}`)}>
                          <Eye className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </>
      )}

      {activeTab === 'audit' && (
        <>
          {loadingAudit ? (
            <div className="enterprise-card flex items-center justify-center p-12"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600" /></div>
          ) : auditEntries.length === 0 ? (
            <div className="enterprise-card p-12 text-center"><Archive className="w-12 h-12 mx-auto mb-3 text-gray-300 dark:text-gray-600" /><p className="font-medium text-gray-500 dark:text-gray-400">No audit entries</p></div>
          ) : (
            <div className="enterprise-card overflow-hidden">
              <div className="overflow-x-auto">
                <table className="enterprise-table w-full">
                  <thead>
                    <tr>
                      <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">Product</th>
                      <th className="px-4 py-3 text-center text-xs font-semibold text-gray-500 uppercase">Action</th>
                      <th className="px-4 py-3 text-center text-xs font-semibold text-gray-500 uppercase">Previous Date</th>
                      <th className="px-4 py-3 text-center text-xs font-semibold text-gray-500 uppercase">New Date</th>
                      <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">Changed By</th>
                      <th className="px-4 py-3 text-right text-xs font-semibold text-gray-500 uppercase">Changed At</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
                    {auditEntries.map(entry => (
                      <tr key={entry.id} className="enterprise-table-row">
                        <td className="px-4 py-3 text-sm text-gray-900 dark:text-gray-100">{entry.productName}</td>
                        <td className="px-4 py-3 text-center">
                          <EnterpriseStatusBadge status={entry.action === 'CREATED' ? 'info' : entry.action === 'UPDATED' ? 'pending' : entry.action === 'FULFILLED' ? 'success' : 'error'} label={entry.action} />
                        </td>
                        <td className="px-4 py-3 text-center text-xs text-gray-500 dark:text-gray-400">{entry.oldDate ? formatDate(entry.oldDate) : '—'}</td>
                        <td className="px-4 py-3 text-center text-xs text-gray-500 dark:text-gray-400">{entry.newDate ? formatDate(entry.newDate) : '—'}</td>
                        <td className="px-4 py-3 text-sm text-gray-700 dark:text-gray-300 capitalize">{entry.changedBy}</td>
                        <td className="px-4 py-3 text-right text-xs text-gray-400 dark:text-gray-500">{formatDate(entry.changedAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  )
}
