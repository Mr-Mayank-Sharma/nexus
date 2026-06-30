import { useState } from 'react'
import { NavLink, useLocation } from 'react-router-dom'
import {
  LayoutDashboard, Package, Warehouse, Truck, RotateCcw, Ship, BarChart3,
  Brain, Settings, ChevronLeft, ChevronRight, GitBranch, ClipboardCheck,
  PackagePlus, Store, Moon, Sun, Zap, Building2, ShoppingCart, Receipt,
  Bell, FileText, UserCog, Upload, Download, Activity, Cpu, Users, Shield, Globe, Route, TrendingDown, Mail, FlaskConical,
} from 'lucide-react'
import { clsx } from 'clsx'
import { useTheme } from '../../context/ThemeContext'

interface NavItem {
  label: string
  path: string
  icon: React.ReactNode
}

const navItems: NavItem[] = [
  { label: 'Dashboard', path: '/', icon: <LayoutDashboard className="w-5 h-5" /> },
  { label: 'Orders', path: '/orders', icon: <Package className="w-5 h-5" /> },
  { label: 'Fulfillment', path: '/fulfillment', icon: <Truck className="w-5 h-5" /> },
  { label: 'Customers', path: '/customers', icon: <Users className="w-5 h-5" /> },
  { label: 'Products', path: '/products', icon: <PackagePlus className="w-5 h-5" /> },
  { label: 'Stores', path: '/stores', icon: <Store className="w-5 h-5" /> },
  { label: 'Audit', path: '/audit', icon: <Shield className="w-5 h-5" /> },
  { label: 'Picking', path: '/picking', icon: <ClipboardCheck className="w-5 h-5" /> },
  { label: 'Packing', path: '/packing', icon: <PackagePlus className="w-5 h-5" /> },
  { label: 'Shipping', path: '/shipping', icon: <Ship className="w-5 h-5" /> },
  { label: 'Inventory', path: '/inventory', icon: <Warehouse className="w-5 h-5" /> },
  { label: 'Receiving', path: '/inventory/receiving', icon: <PackagePlus className="w-5 h-5" /> },
  { label: 'Cycle Counts', path: '/inventory/cycle-counts', icon: <ClipboardCheck className="w-5 h-5" /> },
  { label: 'Routing Rules', path: '/routing-rules', icon: <GitBranch className="w-5 h-5" /> },
  { label: 'Order Routing', path: '/order-routing', icon: <Route className="w-5 h-5" /> },
  { label: 'Integrations', path: '/integrations', icon: <Store className="w-5 h-5" /> },
  { label: 'Integration Hub', path: '/integration-hub', icon: <Zap className="w-5 h-5" /> },
  { label: 'Import/Export', path: '/import-export', icon: <Activity className="w-5 h-5" /> },
  { label: 'Warehouse', path: '/warehouse', icon: <Building2 className="w-5 h-5" /> },
  { label: 'Procurement', path: '/procurement', icon: <ShoppingCart className="w-5 h-5" /> },
  { label: 'Invoices', path: '/invoices', icon: <Receipt className="w-5 h-5" /> },
  { label: 'Workflows', path: '/workflows', icon: <GitBranch className="w-5 h-5" /> },
  { label: 'Notifications', path: '/notifications', icon: <Bell className="w-5 h-5" /> },
  { label: 'Documents', path: '/documents', icon: <FileText className="w-5 h-5" /> },
  { label: 'Users & Roles', path: '/users', icon: <UserCog className="w-5 h-5" /> },
  { label: 'Returns', path: '/returns', icon: <RotateCcw className="w-5 h-5" /> },
  { label: 'B2B Portal', path: '/b2b-portal', icon: <Globe className="w-5 h-5" /> },
  { label: 'Carriers', path: '/carriers', icon: <Ship className="w-5 h-5" /> },
  { label: 'EDI Automation', path: '/edi', icon: <FileText className="w-5 h-5" /> },
  { label: 'Email Orders', path: '/email-parser', icon: <Mail className="w-5 h-5" /> },
  { label: 'Rate Shopping', path: '/rate-shopping', icon: <TrendingDown className="w-5 h-5" /> },
  { label: 'Analytics', path: '/analytics', icon: <BarChart3 className="w-5 h-5" /> },
  { label: 'AI Models', path: '/ai', icon: <Brain className="w-5 h-5" /> },
  { label: 'AI Platform', path: '/ai-platform', icon: <Cpu className="w-5 h-5" /> },
  { label: 'Experiments', path: '/experiments', icon: <FlaskConical className="w-5 h-5" /> },
  { label: 'Settings', path: '/settings', icon: <Settings className="w-5 h-5" /> },
]

export default function Sidebar() {
  const [collapsed, setCollapsed] = useState(false)
  const location = useLocation()
  const { theme, toggleTheme } = useTheme()

  return (
    <aside
      className={clsx(
        'bg-white dark:bg-gray-900 border-r border-gray-200 dark:border-gray-800 flex flex-col transition-all duration-300 ease-in-out h-screen sticky top-0',
        collapsed ? 'w-14' : 'w-60',
      )}
    >
      {/* Logo */}
      <div className={clsx(
        'flex items-center h-14 px-4 border-b border-gray-200 dark:border-gray-800',
        collapsed ? 'justify-center' : 'gap-2.5'
      )}>
        <div className="w-8 h-8 bg-primary-600 rounded-lg flex items-center justify-center flex-shrink-0">
          <Ship className="w-4.5 h-4.5 text-white" />
        </div>
        {!collapsed && (
          <span className="font-bold text-base text-gray-900 dark:text-gray-100 tracking-tight">NexusShip</span>
        )}
      </div>

      {/* Navigation */}
      <nav className="flex-1 px-2.5 py-3 space-y-0.5 overflow-y-auto scrollbar-hide">
        {navItems.map((item) => {
          const isActive = item.path === '/' ? location.pathname === '/' : location.pathname.startsWith(item.path)
          return (
            <NavLink
              key={item.path}
              to={item.path}
              className={clsx(
                'flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-all duration-150 group',
                isActive
                  ? 'bg-primary-50 text-primary-700 dark:bg-primary-900/20 dark:text-primary-300'
                  : 'text-gray-500 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-800',
              )}
              title={collapsed ? item.label : undefined}
            >
              <span className={clsx('flex-shrink-0', isActive ? 'text-primary-600 dark:text-primary-400' : '')}>
                {item.icon}
              </span>
              {!collapsed && <span>{item.label}</span>}
            </NavLink>
          )
        })}
      </nav>

      {/* Footer */}
      <div className="p-2.5 border-t border-gray-200 dark:border-gray-800 space-y-1">
        <button
          onClick={toggleTheme}
          className="w-full flex items-center justify-center gap-2 p-2 rounded-lg text-gray-400 dark:text-gray-500 hover:text-gray-700 dark:hover:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
          title={theme === 'light' ? 'Dark mode' : 'Light mode'}
        >
          {theme === 'light' ? <Moon className="w-4 h-4" /> : <Sun className="w-4 h-4" />}
          {!collapsed && <span className="text-xs">{theme === 'light' ? 'Dark Mode' : 'Light Mode'}</span>}
        </button>
        <button
          onClick={() => setCollapsed(!collapsed)}
          className="w-full flex items-center justify-center p-2 rounded-lg text-gray-400 dark:text-gray-500 hover:text-gray-700 dark:hover:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
        >
          {collapsed ? <ChevronRight className="w-4 h-4" /> : <ChevronLeft className="w-4 h-4" />}
        </button>
      </div>
    </aside>
  )
}
