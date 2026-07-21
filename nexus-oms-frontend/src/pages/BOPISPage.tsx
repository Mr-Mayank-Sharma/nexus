import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  ShoppingBag, Package, Store, Search, CheckCircle, Clock, XCircle, Truck,
  ChevronRight, Plus, Minus, MapPin, Phone, Mail, User, ArrowRight, Eye,
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

type Bopistab = 'orders' | 'catalog' | 'ship-to-store'

interface Product {
  id: string
  sku: string
  name: string
  price: number
  imageUrl?: string
  stock?: number
}

export default function BOPISPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { addToast } = useToast()
  const [activeTab, setActiveTab] = useState<Bopistab>('orders')
  const [searchTerm, setSearchTerm] = useState('')
  const [selectedOrderId, setSelectedOrderId] = useState<string | null>(null)

  const { data: bopisOrders = [], isLoading: loadingOrders } = useQuery({
    queryKey: ['bopis-orders'],
    queryFn: async () => {
      const res = await ordersApi.getOrders({ status: 'ALLOCATED' })
      const d = res.data
      if (Array.isArray(d)) return (d as Order[]).filter(o => o.channel === 'BOPIS' || o.channel === 'PICKUP')
      if (d && typeof d === 'object' && 'content' in d)
        return ((d as { content: Order[] }).content).filter(o => o.channel === 'BOPIS' || o.channel === 'PICKUP')
      return []
    },
  })

  const { data: products = [], isLoading: loadingProducts } = useQuery({
    queryKey: ['bopis-catalog'],
    queryFn: async () => {
      const res = await productsApi.getProducts()
      const d = res.data
      if (Array.isArray(d)) return d as Product[]
      if (d && typeof d === 'object' && 'content' in d) return (d as { content: Product[] }).content
      return []
    },
    enabled: activeTab === 'catalog',
  })

  const { data: shipToStoreOrders = [], isLoading: loadingShipToStore } = useQuery({
    queryKey: ['bopis-ship-to-store'],
    queryFn: async () => {
      const res = await ordersApi.getOrders({ status: 'SHIPPED' })
      const d = res.data
      if (Array.isArray(d)) return (d as Order[]).filter(o => o.channel === 'BOPIS' || o.channel === 'PICKUP')
      if (d && typeof d === 'object' && 'content' in d)
        return ((d as { content: Order[] }).content).filter(o => o.channel === 'BOPIS' || o.channel === 'PICKUP')
      return []
    },
    enabled: activeTab === 'ship-to-store',
  })

  const confirmPickupMutation = useMutation({
    mutationFn: (id: string) => ordersApi.confirmOrder(id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['bopis-orders'] }); addToast({ type: 'success', title: 'Pickup confirmed' }) },
    onError: () => addToast({ type: 'error', title: 'Confirmation failed' }),
  })

  const filteredProducts = products.filter(p =>
    !searchTerm || p.name.toLowerCase().includes(searchTerm.toLowerCase()) || p.sku.toLowerCase().includes(searchTerm.toLowerCase())
  )

  const tabs: Tab[] = [
    { id: 'orders', label: 'Orders', badge: bopisOrders.length },
    { id: 'catalog', label: 'Catalog', badge: products.length },
    { id: 'ship-to-store', label: 'Ship to Store', badge: shipToStoreOrders.length },
  ]

  const isLoading = activeTab === 'orders' ? loadingOrders : activeTab === 'catalog' ? loadingProducts : loadingShipToStore
  const items = activeTab === 'orders' ? bopisOrders : activeTab === 'catalog' ? filteredProducts : shipToStoreOrders

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5"><ShoppingBag className="w-7 h-7 text-[var(--nexus-primary-500)]" /> BOPIS</h1>
          <p className="text-sm text-[var(--text-secondary)] mt-1">Buy Online, Pick Up In Store</p>
        </div>
        <div className="flex items-center gap-2">
          <EnterpriseKPICard title="Ready for Pickup" value={bopisOrders.length} icon={<ShoppingBag className="w-4 h-4" />} color="primary" trend={null} />
          <EnterpriseKPICard title="In Transit" value={shipToStoreOrders.length} icon={<Truck className="w-4 h-4" />} color="info" trend={null} />
        </div>
      </div>

      <EnterpriseTabs tabs={tabs} activeTab={activeTab} onChange={t => setActiveTab(t as Bopistab)} />

      {isLoading ? (
        <div className="enterprise-card flex items-center justify-center p-12">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[var(--nexus-primary-600)]" />
        </div>
      ) : items.length === 0 ? (
        <div className="enterprise-card p-12 text-center">
          <ShoppingBag className="w-12 h-12 mx-auto mb-3 text-[var(--text-tertiary)] dark:text-[var(--text-secondary)]" />
          <p className="font-medium text-[var(--text-secondary)]">No {activeTab} items</p>
        </div>
      ) : (
        <div className="enterprise-card overflow-hidden">
          {activeTab === 'orders' && (
            <div className="divide-y divide-gray-100 dark:divide-gray-800">
              {bopisOrders.map(order => (
                <div key={order.id} className="p-4 hover:bg-[var(--surface-sunken)] hover:bg-[var(--surface-base)]/50 transition-colors">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 rounded-xl bg-[var(--interactive-selected)] flex items-center justify-center text-[var(--text-brand)]">
                        <Package className="w-5 h-5" />
                      </div>
                      <div>
                        <p className="text-sm font-medium text-[var(--text-primary)]">{order.customerName || 'Customer'}</p>
                        <p className="text-xs text-[var(--text-secondary)]">Order #{order.orderNumber || order.id.slice(0, 8)}</p>
                      </div>
                    </div>
                    <div className="flex items-center gap-3">
                      <span className="text-xs text-[var(--text-tertiary)] dark:text-[var(--text-secondary)]">{order.items?.length || 0} items</span>
                      <EnterpriseStatusBadge status="warning" label="Ready" />
                      <PermissionGate resource="orders" action="edit">
                        <button className="enterprise-btn-primary text-xs px-3 py-1.5" onClick={() => confirmPickupMutation.mutate(order.id)}>
                          <CheckCircle className="w-3.5 h-3.5" /> Confirm Pickup
                        </button>
                      </PermissionGate>
                      <button className="enterprise-btn-secondary text-xs px-2 py-1.5" onClick={() => navigate(`/orders/${order.id}`)}>
                        <Eye className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </div>
                  {order.items && order.items.length > 0 && (
                    <div className="mt-3 ml-13 pl-13 flex gap-3">
                      {order.items.slice(0, 3).map((item: any, i: number) => (
                        <div key={i} className="flex items-center gap-2 px-2.5 py-1.5 rounded-lg bg-[var(--surface-muted)] text-xs text-[var(--text-secondary)]">
                          {item.sku || item.productName || `Item ${i + 1}`} x{item.quantity || 1}
                        </div>
                      ))}
                      {order.items.length > 3 && <span className="text-xs text-[var(--text-tertiary)] self-center">+{order.items.length - 3} more</span>}
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}

          {activeTab === 'catalog' && (
            <>
              <div className="p-4 border-b border-[var(--border-subtle)] dark:border-[var(--border-strong)]">
                <Autocomplete value={searchTerm} onChange={setSearchTerm} placeholder="Search products by name or SKU..." minChars={0} className="max-w-md" />
              </div>
              <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4 p-4">
                {filteredProducts.map(product => (
                  <button key={product.id} onClick={() => navigate(`/products/${product.id}`)}
                    className="group text-left p-3 rounded-xl border border-[var(--border-default)] hover:border-[var(--nexus-primary-300)] dark:hover:border-[var(--nexus-primary-600)] hover:shadow-sm transition-all">
                    <div className="w-full aspect-square rounded-lg bg-[var(--surface-muted)] mb-2 flex items-center justify-center text-[var(--text-tertiary)] dark:text-[var(--text-secondary)]">
                      <Store className="w-8 h-8" />
                    </div>
                    <p className="text-xs font-medium text-[var(--text-primary)] truncate">{product.name}</p>
                    <p className="text-[10px] text-[var(--text-tertiary)] dark:text-[var(--text-secondary)] truncate">{product.sku}</p>
                    <div className="flex items-center justify-between mt-1.5">
                      <span className="text-sm font-bold text-[var(--text-brand)]">${product.price?.toFixed(2) || '0.00'}</span>
                      <span className={clsx('text-[10px] font-medium',
                        (product.stock || 0) > 0 ? 'text-[var(--nexus-success-600)] dark:text-[var(--nexus-success-400)]' : 'text-[var(--nexus-error-500)]'
                      )}>
                        {(product.stock || 0) > 0 ? 'In Stock' : 'Out of Stock'}
                      </span>
                    </div>
                  </button>
                ))}
              </div>
            </>
          )}

          {activeTab === 'ship-to-store' && (
            <div className="divide-y divide-gray-100 dark:divide-gray-800">
              {shipToStoreOrders.map(order => (
                <div key={order.id} className="p-4 hover:bg-[var(--surface-sunken)] hover:bg-[var(--surface-base)]/50 transition-colors">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 rounded-xl bg-[var(--nexus-warning-50)] dark:bg-[var(--nexus-warning-900)]/20 flex items-center justify-center text-[var(--nexus-warning-600)] dark:text-[var(--nexus-warning-400)]">
                        <Truck className="w-5 h-5" />
                      </div>
                      <div>
                        <p className="text-sm font-medium text-[var(--text-primary)]">{order.customerName || 'Customer'}</p>
                        <p className="text-xs text-[var(--text-secondary)]">Order #{order.orderNumber || order.id.slice(0, 8)}</p>
                      </div>
                    </div>
                    <div className="flex items-center gap-3">
                      <span className="text-xs text-[var(--text-tertiary)] dark:text-[var(--text-secondary)]">{order.items?.length || 0} items</span>
                      <EnterpriseStatusBadge status="pending" label="In Transit" />
                      <button className="enterprise-btn-secondary text-xs px-3 py-1.5" onClick={() => navigate(`/orders/${order.id}`)}>
                        <Eye className="w-3.5 h-3.5" /> Track
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
