import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  ShoppingCart, Globe, ShoppingBag, Store, Package, CreditCard, Search, Grid3X3,
  CheckCircle, XCircle, Clock, Loader2, Plug, Zap, ExternalLink, Filter, ArrowRight,
  AlertTriangle, Wifi, SlidersHorizontal,
} from 'lucide-react'
import { clsx } from 'clsx'
import { useToast } from '../hooks/useToast'
import Autocomplete from '../components/common/Autocomplete'
import PermissionGate from '../components/rbac/PermissionGate'
import { fetchAllStatus } from '../api/connectors/connectorRegistry'

interface IntegrationCard {
  id: string
  name: string
  description: string
  icon: React.ReactNode
  color: string
  status: 'connected' | 'available' | 'coming_soon'
  category: string
  docsUrl?: string
}

const ALL_INTEGRATIONS: IntegrationCard[] = [
  {
    id: 'bigcommerce',
    name: 'BigCommerce',
    description: 'Connect your BigCommerce store to sync orders, products, inventory, shipments, and refunds',
    icon: <ShoppingCart className="w-6 h-6" />,
    color: 'bg-blue-500',
    status: 'connected',
    category: 'E-Commerce',
  },
  {
    id: 'amazon',
    name: 'Amazon Seller Central',
    description: 'Integrate Amazon marketplace to import orders, manage inventory, and sync fulfillment',
    icon: <Globe className="w-6 h-6" />,
    color: 'bg-orange-500',
    status: 'available',
    category: 'Marketplace',
  },
  {
    id: 'ebay',
    name: 'eBay',
    description: 'Connect eBay stores to sync orders, map categories, and manage inventory across sites',
    icon: <ShoppingBag className="w-6 h-6" />,
    color: 'bg-purple-500',
    status: 'available',
    category: 'Marketplace',
  },
  {
    id: 'walmart',
    name: 'Walmart Marketplace',
    description: 'Integrate Walmart Marketplace for order management, WFS fulfillment, and pricing rules',
    icon: <Store className="w-6 h-6" />,
    color: 'bg-sky-500',
    status: 'available',
    category: 'Marketplace',
  },
  {
    id: 'shopify',
    name: 'Shopify',
    description: 'Sync orders, products, and inventory from your Shopify stores in real-time',
    icon: <ShoppingBag className="w-6 h-6" />,
    color: 'bg-emerald-500',
    status: 'coming_soon',
    category: 'E-Commerce',
  },
  {
    id: 'woocommerce',
    name: 'WooCommerce',
    description: 'Connect WooCommerce sites to import orders, sync inventory, and manage fulfillment',
    icon: <ShoppingCart className="w-6 h-6" />,
    color: 'bg-indigo-500',
    status: 'coming_soon',
    category: 'E-Commerce',
  },
  {
    id: 'magento',
    name: 'Magento (Adobe Commerce)',
    description: 'Integrate Adobe Commerce / Magento 2.x for order and catalog synchronization',
    icon: <Package className="w-6 h-6" />,
    color: 'bg-rose-500',
    status: 'coming_soon',
    category: 'E-Commerce',
  },
  {
    id: 'square',
    name: 'Square',
    description: 'Connect Square POS for omnichannel order management and inventory sync',
    icon: <CreditCard className="w-6 h-6" />,
    color: 'bg-teal-500',
    status: 'coming_soon',
    category: 'POS',
  },
  {
    id: 'lightspeed',
    name: 'Lightspeed',
    description: 'Integrate Lightspeed Retail for unified inventory and multi-channel selling',
    icon: <Store className="w-6 h-6" />,
    color: 'bg-cyan-500',
    status: 'coming_soon',
    category: 'POS',
  },
]

const STATUS_CONFIG = {
  connected: { label: 'Connected', icon: <CheckCircle className="w-3.5 h-3.5" />, className: 'bg-green-50 text-green-700 border-green-200' },
  available: { label: 'Available', icon: <Wifi className="w-3.5 h-3.5" />, className: 'bg-blue-50 text-blue-700 border-blue-200' },
  coming_soon: { label: 'Coming Soon', icon: <Clock className="w-3.5 h-3.5" />, className: 'bg-gray-50 text-gray-500 border-gray-200' },
}

export default function IntegrationMarketplacePage() {
  const navigate = useNavigate()
  const [searchQuery, setSearchQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState<string>('ALL')
  const [categoryFilter, setCategoryFilter] = useState<string>('ALL')
  const [connecting, setConnecting] = useState<string | null>(null)
  const [connectorStatuses, setConnectorStatuses] = useState<Record<string, boolean>>({})
  const { addToast } = useToast()

  useEffect(() => {
    fetchAllStatus().then(status => {
      setConnectorStatuses({
        bigcommerce: status.bigcommerce?.active || false,
        amazon: status.amazon?.active || false,
        ebay: status.ebay?.active || false,
        walmart: status.walmart?.active || false,
      })
    }).catch(() => {})
  }, [])

  const integrationsWithStatus = ALL_INTEGRATIONS.map(i => {
    if (i.status === 'coming_soon') return i
    const isConnected = connectorStatuses[i.id]
    return { ...i, status: isConnected ? 'connected' as const : 'available' as const }
  })

  const categories = [...new Set(integrationsWithStatus.map(i => i.category))]

  const filteredIntegrations = integrationsWithStatus.filter(i => {
    const matchesSearch = !searchQuery || i.name.toLowerCase().includes(searchQuery.toLowerCase()) || i.description.toLowerCase().includes(searchQuery.toLowerCase())
    const matchesStatus = statusFilter === 'ALL' || i.status === statusFilter
    const matchesCategory = categoryFilter === 'ALL' || i.category === categoryFilter
    return matchesSearch && matchesStatus && matchesCategory
  })

  function handleConnect(id: string) {
    const integration = integrationsWithStatus.find(i => i.id === id)
    if (!integration) return

    if (integration.status === 'coming_soon') {
      addToast({ type: 'info', title: `${integration.name} integration is coming soon` })
      return
    }

    const pathMap: Record<string, string> = {
      bigcommerce: '/integrations/bigcommerce',
      amazon: '/integrations/amazon',
      ebay: '/integrations/ebay',
      walmart: '/integrations/walmart',
    }
    const path = pathMap[id]
    if (path) {
      navigate(path)
    } else {
      setConnecting(id)
      setTimeout(() => {
        setConnecting(null)
        addToast({ type: 'success', title: `${integration.name} integration enabled` })
      }, 1000)
    }
  }

  function handleOpen(id: string) {
    const pathMap: Record<string, string> = {
      bigcommerce: '/integrations/bigcommerce',
      amazon: '/integrations/amazon',
      ebay: '/integrations/ebay',
      walmart: '/integrations/walmart',
    }
    const path = pathMap[id]
    if (path) navigate(path)
  }

  const statusCounts = {
    connected: integrationsWithStatus.filter(i => i.status === 'connected').length,
    available: integrationsWithStatus.filter(i => i.status === 'available').length,
    coming_soon: integrationsWithStatus.filter(i => i.status === 'coming_soon').length,
    total: integrationsWithStatus.length,
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2.5"><Grid3X3 className="w-7 h-7 text-primary-500" /> Integration Marketplace</h1>
          <p className="text-sm text-gray-500 mt-1">Browse and connect your sales channels, marketplaces, and commerce platforms</p>
        </div>
        <div className="flex items-center gap-4 text-xs text-gray-500">
          <div className="flex items-center gap-1.5">
            <CheckCircle className="w-3.5 h-3.5 text-green-500" />
            <span className="font-medium text-gray-700">{statusCounts.connected}</span> Connected
          </div>
          <div className="flex items-center gap-1.5">
            <Wifi className="w-3.5 h-3.5 text-blue-500" />
            <span className="font-medium text-gray-700">{statusCounts.available}</span> Available
          </div>
          <div className="flex items-center gap-1.5">
            <Clock className="w-3.5 h-3.5 text-gray-400" />
            <span className="font-medium text-gray-700">{statusCounts.coming_soon}</span> Coming Soon
          </div>
        </div>
      </div>

      {/* Filters */}
      <div className="card p-4">
        <div className="flex items-center gap-4">
          <Autocomplete value={searchQuery} onChange={setSearchQuery} placeholder="Search integrations..." minChars={0} className="flex-1 max-w-md" />
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-1.5 text-xs text-gray-500">
              <Filter className="w-3.5 h-3.5" /> Status:
            </div>
            <select value={statusFilter} onChange={e => setStatusFilter(e.target.value)} className="input text-xs w-32">
              <option value="ALL">All Statuses</option>
              <option value="connected">Connected</option>
              <option value="available">Available</option>
              <option value="coming_soon">Coming Soon</option>
            </select>
            <select value={categoryFilter} onChange={e => setCategoryFilter(e.target.value)} className="input text-xs w-36">
              <option value="ALL">All Categories</option>
              {categories.map(cat => <option key={cat} value={cat}>{cat}</option>)}
            </select>
          </div>
        </div>
      </div>

      {/* Summary Stats */}
      <div className="grid grid-cols-4 gap-4">
        <div className="card p-4 flex items-center gap-3">
          <div className="w-10 h-10 rounded-lg bg-primary-50 flex items-center justify-center">
            <Plug className="w-5 h-5 text-primary-600" />
          </div>
          <div>
            <p className="text-lg font-bold text-gray-900">{statusCounts.total}</p>
            <p className="text-xs text-gray-500">Total Integrations</p>
          </div>
        </div>
        <div className="card p-4 flex items-center gap-3">
          <div className="w-10 h-10 rounded-lg bg-green-50 flex items-center justify-center">
            <CheckCircle className="w-5 h-5 text-green-600" />
          </div>
          <div>
            <p className="text-lg font-bold text-gray-900">{statusCounts.connected}</p>
            <p className="text-xs text-gray-500">Connected</p>
          </div>
        </div>
        <div className="card p-4 flex items-center gap-3">
          <div className="w-10 h-10 rounded-lg bg-blue-50 flex items-center justify-center">
            <Zap className="w-5 h-5 text-blue-600" />
          </div>
          <div>
            <p className="text-lg font-bold text-gray-900">{statusCounts.available}</p>
            <p className="text-xs text-gray-500">Ready to Connect</p>
          </div>
        </div>
        <div className="card p-4 flex items-center gap-3">
          <div className="w-10 h-10 rounded-lg bg-amber-50 flex items-center justify-center">
            <AlertTriangle className="w-5 h-5 text-amber-600" />
          </div>
          <div>
            <p className="text-lg font-bold text-gray-900">{statusCounts.coming_soon}</p>
            <p className="text-xs text-gray-500">In Development</p>
          </div>
        </div>
      </div>

      {/* Integration Grid */}
      {filteredIntegrations.length === 0 ? (
        <div className="card p-12 text-center">
          <Search className="w-12 h-12 text-gray-200 mx-auto mb-3" />
          <p className="text-sm text-gray-500">No integrations match your search criteria</p>
          <button onClick={() => { setSearchQuery(''); setStatusFilter('ALL'); setCategoryFilter('ALL') }}
            className="btn-secondary text-xs mt-3">Clear Filters</button>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {filteredIntegrations.map(integration => {
            const statusCfg = STATUS_CONFIG[integration.status]
            return (
              <div key={integration.id} className={clsx(
                'card p-5 transition-all',
                integration.status === 'coming_soon' && 'opacity-70',
              )}>
                <div className="flex items-start justify-between mb-4">
                  <div className={clsx('w-12 h-12 rounded-xl flex items-center justify-center text-white', integration.color)}>
                    {integration.icon}
                  </div>
                  <span className={clsx('inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium border', statusCfg.className)}>
                    {statusCfg.icon}
                    {statusCfg.label}
                  </span>
                </div>

                <h3 className="text-sm font-semibold text-gray-900 mb-1">{integration.name}</h3>
                <p className="text-xs text-gray-500 mb-4 line-clamp-2">{integration.description}</p>

                <div className="flex items-center gap-1.5 mb-4">
                  <span className="text-xs bg-gray-100 text-gray-600 px-2 py-0.5 rounded">{integration.category}</span>
                </div>

                <div className="flex items-center gap-2">
                  {integration.status === 'connected' ? (
                    <>
                      <button onClick={() => handleOpen(integration.id)} className="btn-primary text-xs flex-1">
                        <ExternalLink className="w-3.5 h-3.5" /> Open
                      </button>
                      <button className="btn-ghost text-xs text-green-600">
                        <CheckCircle className="w-3.5 h-3.5" />
                      </button>
                    </>
                  ) : integration.status === 'available' ? (
                    <PermissionGate resource="integrations" action="create">
                      <button onClick={() => handleConnect(integration.id)} disabled={connecting === integration.id}
                        className="btn-primary text-xs flex-1">
                        {connecting === integration.id ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Zap className="w-3.5 h-3.5" />}
                        {connecting === integration.id ? 'Connecting...' : 'Connect'}
                      </button>
                    </PermissionGate>
                  ) : (
                    <button disabled className="btn-secondary text-xs flex-1 opacity-50 cursor-not-allowed">
                      <Clock className="w-3.5 h-3.5" /> Coming Soon
                    </button>
                  )}
                  {integration.docsUrl && (
                    <a href={integration.docsUrl} target="_blank" rel="noopener noreferrer" className="btn-ghost text-xs">
                      <ExternalLink className="w-3.5 h-3.5" />
                    </a>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* Category Sections */}
      {categories.map(cat => {
        const catIntegrations = integrationsWithStatus.filter(i => i.category === cat)
        if (catIntegrations.length === 0) return null
        return (
          <div key={cat} className="space-y-3">
            <div className="flex items-center gap-2">
              <h2 className="text-sm font-semibold text-gray-900">{cat}</h2>
              <span className="text-xs text-gray-400">{catIntegrations.length} integrations</span>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-3 lg:grid-cols-4 gap-3">
              {catIntegrations.map(integration => {
                const statusCfg = STATUS_CONFIG[integration.status]
                return (
                  <div key={integration.id} className={clsx(
                    'enterprise-card p-4 flex items-center gap-3',
                    integration.status === 'coming_soon' && 'opacity-60',
                  )}>
                    <div className={clsx('w-9 h-9 rounded-lg flex items-center justify-center text-white shrink-0', integration.color)}>
                      <div className="scale-75">{integration.icon}</div>
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-gray-900 truncate">{integration.name}</p>
                      <span className={clsx('inline-flex items-center gap-1 text-xs font-medium mt-0.5', {
                        'text-green-600': integration.status === 'connected',
                        'text-blue-600': integration.status === 'available',
                        'text-gray-400': integration.status === 'coming_soon',
                      })}>
                        {statusCfg.label}
                      </span>
                    </div>
                    <PermissionGate resource="integrations" action="create">
                      <button onClick={() => integration.status === 'connected' ? handleOpen(integration.id) : handleConnect(integration.id)}
                        disabled={connecting === integration.id || integration.status === 'coming_soon'}
                        className={clsx(
                          'btn-ghost p-1.5 rounded-lg transition-colors',
                          integration.status === 'connected' ? 'text-green-500' : 'text-gray-300 hover:text-primary-500',
                          integration.status === 'coming_soon' && 'opacity-30 cursor-not-allowed',
                        )}>
                        {connecting === integration.id ? <Loader2 className="w-4 h-4 animate-spin" /> :
                         integration.status === 'connected' ? <CheckCircle className="w-4 h-4" /> :
                         <ArrowRight className="w-4 h-4" />}
                      </button>
                    </PermissionGate>
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
