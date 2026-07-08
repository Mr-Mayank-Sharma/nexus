import { useState, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Package, Users, ClipboardCheck, PackagePlus, Ship, Warehouse, Store, CreditCard,
  GitBranch, Route, BarChart3, Brain, Cpu, FlaskConical, Settings, RotateCcw,
  Globe, ShoppingCart, Receipt, Bell, FileText, Shield, UserCog, Activity,
  Upload, Download, Zap, Mail, TrendingDown, Truck, Building2, Search, AlertTriangle,
  LayoutDashboard, ChevronRight, Star, Sparkles, Menu, ShoppingBag, Calendar, Gauge,
  Plus, Layers, Printer,
} from 'lucide-react'
import { clsx } from 'clsx'
import Autocomplete from '../components/common/Autocomplete'
import { useAuth } from '../context/AuthContext'

interface AppCard {
  name: string
  path: string
  icon: React.ReactNode
  description: string
  category: string
  color: string
  isNew?: boolean
}

const apps: AppCard[] = [
  // Orders & Customers
  { name: 'Dashboard', path: '/dashboard', icon: <LayoutDashboard className="w-6 h-6" />, description: 'KPIs, charts & metrics', category: 'Overview', color: 'from-blue-500 to-blue-600' },
  { name: 'Orders', path: '/orders', icon: <Package className="w-6 h-6" />, description: 'Manage sales orders', category: 'Orders & Customers', color: 'from-indigo-500 to-indigo-600' },
  { name: 'Fulfillment', path: '/fulfillment', icon: <Truck className="w-6 h-6" />, description: 'Track order fulfillment', category: 'Orders & Customers', color: 'from-emerald-500 to-emerald-600' },
  { name: 'BOPIS', path: '/bopis', icon: <ShoppingBag className="w-6 h-6" />, description: 'Buy Online Pickup In Store', category: 'Orders & Customers', color: 'from-teal-500 to-teal-600' },
  { name: 'BOPIS Owner', path: '/bopis-owner', icon: <ShoppingBag className="w-6 h-6" />, description: 'BOPIS customer experience', category: 'Orders & Customers', color: 'from-cyan-500 to-cyan-600' },
  { name: 'Store Dashboard', path: '/store-dashboard', icon: <Store className="w-6 h-6" />, description: 'Retail store operations', category: 'Orders & Customers', color: 'from-green-500 to-green-600' },
  { name: 'Pre-Orders', path: '/pre-orders', icon: <Calendar className="w-6 h-6" />, description: 'Pre-order product management', category: 'Orders & Customers', color: 'from-violet-500 to-violet-600' },
  { name: 'ATP Rules', path: '/atp-rules', icon: <Gauge className="w-6 h-6" />, description: 'Available to Promise rules', category: 'Orders & Customers', color: 'from-sky-500 to-sky-600' },
  { name: 'Returns', path: '/returns', icon: <RotateCcw className="w-6 h-6" />, description: 'Process returns & RMA', category: 'Orders & Customers', color: 'from-amber-500 to-amber-600' },
  { name: 'Returns Command Center', path: '/returns-enhanced', icon: <RotateCcw className="w-6 h-6" />, description: 'RMA lifecycle & disposition AI', category: 'Orders & Customers', color: 'from-red-500 to-red-600', isNew: true },
  { name: 'Customers', path: '/customers', icon: <Users className="w-6 h-6" />, description: 'Customer directory & 360', category: 'Orders & Customers', color: 'from-purple-500 to-purple-600' },
  { name: 'Create Order', path: '/orders/new', icon: <Plus className="w-6 h-6" />, description: '4-step order creation wizard', category: 'Orders & Customers', color: 'from-green-500 to-green-600', isNew: true },
  { name: 'B2B Portal', path: '/b2b-portal', icon: <Globe className="w-6 h-6" />, description: 'Business ordering portal', category: 'Orders & Customers', color: 'from-cyan-500 to-cyan-600' },

  // Fulfillment & Warehouse
  { name: 'WH Dashboard', path: '/warehouse-dashboard', icon: <Building2 className="w-6 h-6" />, description: 'Warehouse operations & labor', category: 'Fulfillment & Warehouse', color: 'from-amber-500 to-amber-600' },
  { name: 'Picking', path: '/picking', icon: <ClipboardCheck className="w-6 h-6" />, description: 'Picklist management', category: 'Fulfillment & Warehouse', color: 'from-orange-500 to-orange-600' },
  { name: 'Packer View', path: '/packer', icon: <PackagePlus className="w-6 h-6" />, description: 'Packing station experience', category: 'Fulfillment & Warehouse', color: 'from-emerald-500 to-emerald-600' },
  { name: 'Packing', path: '/packing', icon: <PackagePlus className="w-6 h-6" />, description: 'Package orders for shipment', category: 'Fulfillment & Warehouse', color: 'from-rose-500 to-rose-600' },
  { name: 'Loader View', path: '/loader', icon: <Truck className="w-6 h-6" />, description: 'Loading dock management', category: 'Fulfillment & Warehouse', color: 'from-orange-500 to-orange-600' },
  { name: 'Shipping', path: '/shipping', icon: <Ship className="w-6 h-6" />, description: 'Manage outbound shipments', category: 'Fulfillment & Warehouse', color: 'from-teal-500 to-teal-600' },
  { name: 'Carriers', path: '/carriers', icon: <Truck className="w-6 h-6" />, description: 'Carrier management', category: 'Fulfillment & Warehouse', color: 'from-yellow-500 to-yellow-600' },
  { name: 'Rate Shopping', path: '/rate-shopping', icon: <TrendingDown className="w-6 h-6" />, description: 'Compare carrier rates', category: 'Fulfillment & Warehouse', color: 'from-pink-500 to-pink-600' },
  { name: 'Warehouse', path: '/warehouse', icon: <Building2 className="w-6 h-6" />, description: 'Facilities, zones & bins', category: 'Fulfillment & Warehouse', color: 'from-violet-500 to-violet-600' },
  { name: 'Wave Planning', path: '/wave-planning', icon: <Layers className="w-6 h-6" />, description: 'Order wave creation & management', category: 'Fulfillment & Warehouse', color: 'from-cyan-500 to-cyan-600', isNew: true },
  { name: 'Labor Management', path: '/labor-management', icon: <Users className="w-6 h-6" />, description: 'Employee productivity & scheduling', category: 'Fulfillment & Warehouse', color: 'from-blue-500 to-blue-600', isNew: true },
  { name: 'Label Printing', path: '/label-printing', icon: <Printer className="w-6 h-6" />, description: 'Shipping labels & bulk printing', category: 'Fulfillment & Warehouse', color: 'from-yellow-600 to-yellow-700', isNew: true },
  { name: 'Manifests', path: '/manifest', icon: <ClipboardCheck className="w-6 h-6" />, description: 'Carrier manifest close-out', category: 'Fulfillment & Warehouse', color: 'from-indigo-500 to-indigo-600', isNew: true },

  // Inventory
  { name: 'Inventory', path: '/inventory', icon: <Warehouse className="w-6 h-6" />, description: 'Stock levels & adjustments', category: 'Inventory', color: 'from-green-500 to-green-600' },
  { name: 'Multi-Node Inventory', path: '/inventory/enhanced', icon: <Warehouse className="w-6 h-6" />, description: 'Real-time ATP across all nodes', category: 'Inventory', color: 'from-teal-500 to-teal-600', isNew: true },
  { name: 'Receiving', path: '/inventory/receiving', icon: <Download className="w-6 h-6" />, description: 'Receive inbound stock', category: 'Inventory', color: 'from-lime-500 to-lime-600' },
  { name: 'Cycle Counts', path: '/inventory/cycle-counts', icon: <ClipboardCheck className="w-6 h-6" />, description: 'Inventory counting', category: 'Inventory', color: 'from-emerald-500 to-emerald-600' },

  // Products & Channels
  { name: 'Products', path: '/products', icon: <PackagePlus className="w-6 h-6" />, description: 'Product catalog & SKUs', category: 'Products & Channels', color: 'from-sky-500 to-sky-600' },
  { name: 'Stores', path: '/stores', icon: <Store className="w-6 h-6" />, description: 'Sales channel stores', category: 'Products & Channels', color: 'from-blue-500 to-blue-600' },
  { name: 'Integrations', path: '/integrations', icon: <Zap className="w-6 h-6" />, description: 'Shopify, BigCommerce & more', category: 'Products & Channels', color: 'from-indigo-500 to-indigo-600' },
  { name: 'Marketplace Hub', path: '/integrations/marketplace', icon: <Globe className="w-6 h-6" />, description: 'All marketplace connectors', category: 'Products & Channels', color: 'from-purple-500 to-purple-600', isNew: true },
  { name: 'Amazon', path: '/integrations/amazon', icon: <ShoppingCart className="w-6 h-6" />, description: 'Amazon Seller Central', category: 'Products & Channels', color: 'from-amber-500 to-amber-600', isNew: true },
  { name: 'eBay', path: '/integrations/ebay', icon: <ShoppingBag className="w-6 h-6" />, description: 'eBay marketplace', category: 'Products & Channels', color: 'from-blue-500 to-blue-600', isNew: true },
  { name: 'Walmart', path: '/integrations/walmart', icon: <Globe className="w-6 h-6" />, description: 'Walmart Marketplace', category: 'Products & Channels', color: 'from-sky-500 to-sky-600', isNew: true },
  { name: 'Integration Hub', path: '/integration-hub', icon: <Globe className="w-6 h-6" />, description: 'Integration platform', category: 'Products & Channels', color: 'from-purple-500 to-purple-600' },

  // Operations
  { name: 'Procurement', path: '/procurement', icon: <ShoppingCart className="w-6 h-6" />, description: 'Suppliers, RFQs & POs', category: 'Operations', color: 'from-orange-500 to-orange-600' },
  { name: 'Invoices', path: '/invoices', icon: <Receipt className="w-6 h-6" />, description: 'Invoicing & payments', category: 'Operations', color: 'from-rose-500 to-rose-600' },
  { name: 'Payments & Invoicing', path: '/payments', icon: <CreditCard className="w-6 h-6" />, description: 'Payments, invoices & reconciliation', category: 'Operations', color: 'from-green-600 to-green-700', isNew: true },
  { name: 'Report Builder', path: '/report-builder', icon: <BarChart3 className="w-6 h-6" />, description: 'Custom dashboards & scheduled reports', category: 'Operations', color: 'from-violet-500 to-violet-600', isNew: true },
  { name: 'Import/Export', path: '/import-export', icon: <Activity className="w-6 h-6" />, description: 'Data import & export', category: 'Operations', color: 'from-amber-500 to-amber-600' },
  { name: 'EDI Automation', path: '/edi', icon: <FileText className="w-6 h-6" />, description: 'EDI document management', category: 'Operations', color: 'from-teal-500 to-teal-600' },
  { name: 'Email Orders', path: '/email-parser', icon: <Mail className="w-6 h-6" />, description: 'Parse orders from email', category: 'Operations', color: 'from-cyan-500 to-cyan-600' },
  { name: 'Task Queues', path: '/task-queues', icon: <AlertTriangle className="w-6 h-6" />, description: 'Swap, bad address, fraud & hold', category: 'Operations', color: 'from-red-500 to-red-600' },
  { name: 'Workflows', path: '/workflows', icon: <GitBranch className="w-6 h-6" />, description: 'Automation workflows', category: 'Operations', color: 'from-violet-500 to-violet-600' },
  { name: 'Documents', path: '/documents', icon: <FileText className="w-6 h-6" />, description: 'Document management', category: 'Operations', color: 'from-slate-500 to-slate-600' },

  // Routing & Intelligence
  { name: 'Routing Rules', path: '/routing-rules', icon: <GitBranch className="w-6 h-6" />, description: 'Order routing rules', category: 'Routing & Intelligence', color: 'from-blue-500 to-blue-600' },
  { name: 'Order Routing', path: '/order-routing', icon: <Route className="w-6 h-6" />, description: 'Intelligent allocation', category: 'Routing & Intelligence', color: 'from-indigo-500 to-indigo-600' },
  { name: 'Analytics', path: '/analytics', icon: <BarChart3 className="w-6 h-6" />, description: 'Reports & dashboards', category: 'Routing & Intelligence', color: 'from-purple-500 to-purple-600' },
  { name: 'AI Models', path: '/ai', icon: <Brain className="w-6 h-6" />, description: 'AI predictions & insights', category: 'Routing & Intelligence', color: 'from-fuchsia-500 to-fuchsia-600' },
  { name: 'AI Platform', path: '/ai-platform', icon: <Cpu className="w-6 h-6" />, description: 'Model management & training', category: 'Routing & Intelligence', color: 'from-pink-500 to-pink-600', isNew: true },
  { name: 'AI Briefing', path: '/ai-briefing', icon: <Brain className="w-6 h-6" />, description: 'CEO executive briefing & insights', category: 'Routing & Intelligence', color: 'from-purple-500 to-purple-600', isNew: true },
  { name: 'AI Routing', path: '/ai-routing', icon: <GitBranch className="w-6 h-6" />, description: 'Intelligent order brokering', category: 'Routing & Intelligence', color: 'from-blue-500 to-blue-600', isNew: true },
  { name: 'AI Packing', path: '/ai-packing', icon: <PackagePlus className="w-6 h-6" />, description: 'Optimized packaging AI', category: 'Routing & Intelligence', color: 'from-emerald-500 to-emerald-600', isNew: true },
  { name: 'AI Loading', path: '/ai-loading', icon: <Truck className="w-6 h-6" />, description: 'Truck loading optimization', category: 'Routing & Intelligence', color: 'from-orange-500 to-orange-600', isNew: true },
  { name: 'AI Audit', path: '/ai-audit', icon: <Shield className="w-6 h-6" />, description: 'AI decision audit trail', category: 'Routing & Intelligence', color: 'from-gray-500 to-gray-600', isNew: true },
  { name: 'AI Forecasting', path: '/ai-forecasting', icon: <BarChart3 className="w-6 h-6" />, description: 'Demand & supplier forecasting', category: 'Routing & Intelligence', color: 'from-cyan-500 to-cyan-600', isNew: true },
  { name: 'Experiments', path: '/experiments', icon: <FlaskConical className="w-6 h-6" />, description: 'A/B testing & experiments', category: 'Routing & Intelligence', color: 'from-rose-500 to-rose-600' },

  // Administration
  { name: 'Users & Roles', path: '/users', icon: <UserCog className="w-6 h-6" />, description: 'User & permission management', category: 'Administration', color: 'from-sky-500 to-sky-600' },
  { name: 'Audit', path: '/audit', icon: <Shield className="w-6 h-6" />, description: 'Audit logs & history', category: 'Administration', color: 'from-gray-500 to-gray-600' },
  { name: 'Notifications', path: '/notifications', icon: <Bell className="w-6 h-6" />, description: 'Alerts & notifications', category: 'Administration', color: 'from-amber-500 to-amber-600' },
  { name: 'Settings', path: '/settings', icon: <Settings className="w-6 h-6" />, description: 'Company configuration', category: 'Administration', color: 'from-slate-500 to-slate-600' },
]

const CATEGORY_ICONS: Record<string, React.ReactNode> = {
  'Overview': <LayoutDashboard className="w-5 h-5" />,
  'Orders & Customers': <Package className="w-5 h-5" />,
  'Fulfillment & Warehouse': <Truck className="w-5 h-5" />,
  'Inventory': <Warehouse className="w-5 h-5" />,
  'Products & Channels': <Store className="w-5 h-5" />,
  'Operations': <Activity className="w-5 h-5" />,
  'Routing & Intelligence': <Brain className="w-5 h-5" />,
  'Administration': <Settings className="w-5 h-5" />,
}

const CATEGORY_COLORS: Record<string, string> = {
  'Overview': 'border-l-blue-500',
  'Orders & Customers': 'border-l-indigo-500',
  'Fulfillment & Warehouse': 'border-l-emerald-500',
  'Inventory': 'border-l-green-500',
  'Products & Channels': 'border-l-sky-500',
  'Operations': 'border-l-amber-500',
  'Routing & Intelligence': 'border-l-purple-500',
  'Administration': 'border-l-gray-500',
}

const CATEGORY_BG: Record<string, string> = {
  'Overview': 'bg-blue-50 dark:bg-blue-950/20',
  'Orders & Customers': 'bg-indigo-50 dark:bg-indigo-950/20',
  'Fulfillment & Warehouse': 'bg-emerald-50 dark:bg-emerald-950/20',
  'Inventory': 'bg-green-50 dark:bg-green-950/20',
  'Products & Channels': 'bg-sky-50 dark:bg-sky-950/20',
  'Operations': 'bg-amber-50 dark:bg-amber-950/20',
  'Routing & Intelligence': 'bg-purple-50 dark:bg-purple-950/20',
  'Administration': 'bg-gray-50 dark:bg-gray-950/20',
}

function getInitials(name: string): string {
  return name.split(' ').map(n => n[0]).join('').toUpperCase().slice(0, 2)
}

export default function LaunchPadPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const [searchQuery, setSearchQuery] = useState('')
  const [favoriteApps, setFavoriteApps] = useState<string[]>([
    '/orders', '/picking', '/packing', '/shipping', '/inventory', '/dashboard'
  ])

  const filtered = useMemo(() => {
    if (!searchQuery.trim()) return apps
    const q = searchQuery.toLowerCase()
    return apps.filter(a => a.name.toLowerCase().includes(q) || a.description.toLowerCase().includes(q) || a.category.toLowerCase().includes(q))
  }, [searchQuery])

  const grouped = useMemo(() => {
    const map = new Map<string, AppCard[]>()
    for (const app of filtered) {
      const list = map.get(app.category) || []
      list.push(app)
      map.set(app.category, list)
    }
    return Array.from(map.entries())
  }, [filtered])

  const favorites = useMemo(() => apps.filter(a => favoriteApps.includes(a.path)), [favoriteApps])

  const toggleFavorite = (path: string) => {
    setFavoriteApps(prev =>
      prev.includes(path) ? prev.filter(p => p !== path) : [...prev, path]
    )
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100 dark:from-gray-950 dark:to-gray-900">
      {/* Hero Header */}
      <div className="relative overflow-hidden bg-gradient-to-r from-primary-600 via-primary-700 to-indigo-800 dark:from-gray-900 dark:via-gray-800 dark:to-gray-900">
        <div className="absolute inset-0 bg-[url('data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNjAiIGhlaWdodD0iNjAiIHZpZXdCb3g9IjAgMCA2MCA2MCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48ZyBmaWxsPSJub25lIiBmaWxsLXJ1bGU9ImV2ZW5vZGQiPjxnIGZpbGw9IiNmZmYiIGZpbGwtb3BhY2l0eT0iMC4wMyI+PHBhdGggZD0iTTM2IDM0djItSDI0di0yaDEyek0zNiAyNHYySDI0di0yaDEyeiIvPjwvZz48L2c+PC9zdmc+')] opacity-40" />
        <div className="relative max-w-7xl mx-auto px-6 py-10 sm:px-8">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-4">
              <div className="w-14 h-14 rounded-2xl bg-white/15 backdrop-blur-sm flex items-center justify-center border border-white/20 shadow-lg">
                <Menu className="w-7 h-7 text-white" />
              </div>
              <div>
                <h1 className="text-2xl sm:text-3xl font-bold text-white tracking-tight">
                  Welcome back{user?.username ? `, ${user.username}` : ''}
                </h1>
                <p className="text-sm text-white/70 mt-0.5">Nexus OMS — Launch Pad</p>
              </div>
            </div>
            <div className="hidden sm:flex items-center gap-3">
              {user && (
                <div className="flex items-center gap-2.5 px-3 py-2 rounded-xl bg-white/10 backdrop-blur-sm border border-white/10">
                  <div className="w-8 h-8 rounded-full bg-white/20 flex items-center justify-center text-xs font-bold text-white">
                    {getInitials(user.username || user.email || 'U')}
                  </div>
                  <div className="text-right">
                    <p className="text-xs font-medium text-white">{user.email || user.username}</p>
                    <p className="text-[10px] text-white/60">{user.role || 'Administrator'}</p>
                  </div>
                </div>
              )}
            </div>
          </div>

          {/* Search */}
          <Autocomplete value={searchQuery} onChange={setSearchQuery} placeholder="Search apps, features, or descriptions..." minChars={0} className="mt-6 max-w-xl" inputClassName="rounded-xl bg-white/10 backdrop-blur-sm border border-white/15 text-white placeholder-white/40 text-sm focus:outline-none focus:ring-2 focus:ring-white/30 focus:border-transparent transition-all" />
        </div>
      </div>

      <div className="max-w-7xl mx-auto px-6 sm:px-8 py-8 space-y-10">
        {/* Favorites Row */}
        {!searchQuery && (
          <section>
            <div className="flex items-center gap-2 mb-4">
              <Star className="w-4.5 h-4.5 text-amber-500 fill-amber-500" />
              <h2 className="text-sm font-semibold text-gray-700 dark:text-gray-300 uppercase tracking-wider">Favorites</h2>
            </div>
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3">
              {favorites.map(app => (
                <button
                  key={app.path}
                  onClick={() => navigate(app.path)}
                  className="group relative flex flex-col items-center gap-2 p-4 rounded-xl bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 shadow-sm hover:shadow-md hover:border-primary-300 dark:hover:border-primary-600 transition-all duration-200"
                >
                  <div className={clsx(
                    'w-10 h-10 rounded-xl flex items-center justify-center text-white shadow-sm bg-gradient-to-br',
                    app.color
                  )}>
                    {app.icon}
                  </div>
                  <span className="text-xs font-medium text-gray-700 dark:text-gray-300 text-center leading-tight">{app.name}</span>
                  <button
                    onClick={e => { e.stopPropagation(); toggleFavorite(app.path) }}
                    className="absolute top-1.5 right-1.5 opacity-0 group-hover:opacity-100 transition-opacity"
                  >
                    <Star className="w-3.5 h-3.5 text-amber-400 fill-amber-400" />
                  </button>
                </button>
              ))}
            </div>
          </section>
        )}

        {/* Category Sections */}
        {grouped.length === 0 ? (
          <div className="text-center py-20">
            <Search className="w-12 h-12 mx-auto mb-4 text-gray-300 dark:text-gray-600" />
            <p className="text-lg font-medium text-gray-500 dark:text-gray-400">No apps match "{searchQuery}"</p>
            <button onClick={() => setSearchQuery('')} className="mt-3 text-sm text-primary-600 hover:text-primary-700 dark:text-primary-400">
              Clear search
            </button>
          </div>
        ) : (
          grouped.map(([category, categoryApps]) => (
            <section key={category}>
              <div className={clsx(
                'flex items-center gap-2 mb-4 px-3 py-2 rounded-lg border-l-4',
                CATEGORY_BG[category],
                CATEGORY_COLORS[category],
                'border-l-4'
              )}>
                <span className="text-gray-500 dark:text-gray-400">{CATEGORY_ICONS[category]}</span>
                <h2 className="text-sm font-semibold text-gray-700 dark:text-gray-300 uppercase tracking-wider">{category}</h2>
                <span className="text-[10px] text-gray-400 dark:text-gray-500 ml-auto font-medium">{categoryApps.length} apps</span>
              </div>
              <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3">
                {categoryApps.map(app => (
                  <button
                    key={app.path}
                    onClick={() => navigate(app.path)}
                    className="group relative flex flex-col items-center gap-2.5 p-4 rounded-xl bg-white dark:bg-gray-800/80 border border-gray-200 dark:border-gray-700/50 shadow-sm hover:shadow-md hover:border-primary-300 dark:hover:border-primary-600 hover:-translate-y-0.5 transition-all duration-200 text-left"
                  >
                    {app.isNew && (
                      <span className="absolute -top-1.5 -right-1.5 px-1.5 py-0.5 rounded-full bg-gradient-to-r from-purple-500 to-pink-500 text-[9px] font-bold text-white uppercase shadow-sm">
                        New
                      </span>
                    )}
                    <div className={clsx(
                      'w-10 h-10 rounded-xl flex items-center justify-center text-white shadow-sm bg-gradient-to-br',
                      app.color
                    )}>
                      {app.icon}
                    </div>
                    <span className="text-xs font-semibold text-gray-800 dark:text-gray-200 text-center leading-tight">{app.name}</span>
                    <span className="text-[10px] text-gray-400 dark:text-gray-500 text-center leading-tight line-clamp-2">{app.description}</span>
                    <button
                      onClick={e => { e.stopPropagation(); toggleFavorite(app.path) }}
                      className={clsx(
                        'absolute top-1.5 right-1.5 transition-all',
                        favoriteApps.includes(app.path) ? 'opacity-100' : 'opacity-0 group-hover:opacity-100'
                      )}
                    >
                      <Star className={clsx(
                        'w-3.5 h-3.5',
                        favoriteApps.includes(app.path) ? 'text-amber-400 fill-amber-400' : 'text-gray-300 dark:text-gray-600'
                      )} />
                    </button>
                  </button>
                ))}
              </div>
            </section>
          ))
        )}
      </div>
    </div>
  )
}
