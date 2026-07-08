import { useState } from 'react'
import { NavLink, useLocation, useNavigate } from 'react-router-dom'
import {
  LayoutDashboard, Package, Warehouse, Truck, RotateCcw, Ship, BarChart3,
  Brain, Settings, ChevronLeft, ChevronRight, GitBranch, ClipboardCheck,
  PackagePlus, Store, Moon, Sun, Zap, Building2, ShoppingCart, Receipt,
  Bell, FileText, UserCog, Upload, Download, Activity, Cpu, Users, Shield,
  Globe, Route, TrendingDown, Mail, FlaskConical, ShoppingBag, Calendar,
  Gauge, AlertTriangle, Eye, CreditCard, Tags, Award, ClipboardList,
  Layers, Printer, ChevronDown,
} from 'lucide-react'
import { clsx } from 'clsx'
import { useTheme } from '../../context/ThemeContext'
import { useAuth } from '../../context/AuthContext'
import { ROLE_WORKSPACES } from '../../hooks/useWorkspace'
import type { UserRole } from '../../types'

const ICON_MAP: Record<string, React.ReactNode> = {
  LayoutDashboard: <LayoutDashboard className="w-5 h-5" />,
  Package: <Package className="w-5 h-5" />,
  Warehouse: <Warehouse className="w-5 h-5" />,
  Truck: <Truck className="w-5 h-5" />,
  RotateCcw: <RotateCcw className="w-5 h-5" />,
  Ship: <Ship className="w-5 h-5" />,
  BarChart3: <BarChart3 className="w-5 h-5" />,
  Brain: <Brain className="w-5 h-5" />,
  Settings: <Settings className="w-5 h-5" />,
  GitBranch: <GitBranch className="w-5 h-5" />,
  ClipboardCheck: <ClipboardCheck className="w-5 h-5" />,
  PackagePlus: <PackagePlus className="w-5 h-5" />,
  Store: <Store className="w-5 h-5" />,
  Building2: <Building2 className="w-5 h-5" />,
  Building: <Building2 className="w-5 h-5" />,
  ShoppingCart: <ShoppingCart className="w-5 h-5" />,
  Receipt: <Receipt className="w-5 h-5" />,
  Bell: <Bell className="w-5 h-5" />,
  FileText: <FileText className="w-5 h-5" />,
  UserCog: <UserCog className="w-5 h-5" />,
  Upload: <Upload className="w-5 h-5" />,
  Download: <Download className="w-5 h-5" />,
  Activity: <Activity className="w-5 h-5" />,
  Cpu: <Cpu className="w-5 h-5" />,
  Users: <Users className="w-5 h-5" />,
  Shield: <Shield className="w-5 h-5" />,
  Globe: <Globe className="w-5 h-5" />,
  Route: <Route className="w-5 h-5" />,
  TrendingDown: <TrendingDown className="w-5 h-5" />,
  Mail: <Mail className="w-5 h-5" />,
  FlaskConical: <FlaskConical className="w-5 h-5" />,
  ShoppingBag: <ShoppingBag className="w-5 h-5" />,
  Calendar: <Calendar className="w-5 h-5" />,
  Gauge: <Gauge className="w-5 h-5" />,
  AlertTriangle: <AlertTriangle className="w-5 h-5" />,
  Eye: <Eye className="w-5 h-5" />,
  CreditCard: <CreditCard className="w-5 h-5" />,
  Tags: <Tags className="w-5 h-5" />,
  Award: <Award className="w-5 h-5" />,
  ClipboardList: <ClipboardList className="w-5 h-5" />,
  Search: <Package className="w-5 h-5" />,
  Plus: <Package className="w-5 h-5" />,
  Layers: <Layers className="w-5 h-5" />,
  Printer: <Printer className="w-5 h-5" />,
}

const STATIC_LINKS = [
  { label: 'Launch Pad', path: '/', icon: <LayoutDashboard className="w-5 h-5" /> },
]

import { getModulesForRole } from '../../hooks/useWorkspace'

function iconForModule(id: string): React.ReactNode {
  return ICON_MAP[id] || <LayoutDashboard className="w-5 h-5" />
}

function SidebarGroup({ item, location }: { item: { label: string; path: string; icon: React.ReactNode; children?: { label: string; path: string; icon: React.ReactNode }[] }; location: any; collapsed: boolean }) {
  const [open, setOpen] = useState(() => location.pathname.startsWith(item.path))
  const parentActive = item.path === '/' ? location.pathname === '/' : location.pathname.startsWith(item.path)
  const hasActiveChild = item.children?.some(c => location.pathname.startsWith(c.path)) || false

  return (
    <div>
      <button
        onClick={() => setOpen(!open)}
        className={clsx(
          'flex items-center gap-3 w-full px-3 py-2 rounded-lg text-sm font-medium transition-all duration-150 group',
          parentActive || hasActiveChild
            ? 'bg-primary-50 text-primary-700 dark:bg-primary-900/20 dark:text-primary-300'
            : 'text-gray-500 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-800',
        )}
      >
        <span className={clsx('flex-shrink-0', parentActive || hasActiveChild ? 'text-primary-600 dark:text-primary-400' : '')}>
          {item.icon}
        </span>
        <span className="flex-1 text-left">{item.label}</span>
        <ChevronDown className={clsx('w-4 h-4 transition-transform', open && 'rotate-180')} />
      </button>
      {open && item.children && (
        <div className="ml-3 mt-0.5 space-y-0.5 border-l border-gray-200 dark:border-gray-700 pl-2">
          {item.children.map((child) => {
            const childActive = child.path === '/' ? location.pathname === '/' : location.pathname.startsWith(child.path)
            return (
              <NavLink
                key={child.path}
                to={child.path}
                className={clsx(
                  'flex items-center gap-3 px-3 py-1.5 rounded-lg text-sm font-medium transition-all duration-150 group',
                  childActive
                    ? 'bg-primary-50 text-primary-700 dark:bg-primary-900/20 dark:text-primary-300'
                    : 'text-gray-500 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-800',
                )}
              >
                <span className={clsx('flex-shrink-0 w-4 h-4', childActive ? 'text-primary-600 dark:text-primary-400' : '')}>
                  {child.icon}
                </span>
                <span>{child.label}</span>
              </NavLink>
            )
          })}
        </div>
      )}
    </div>
  )
}

export default function Sidebar() {
  const [collapsed, setCollapsed] = useState(false)
  const location = useLocation()
  const navigate = useNavigate()
  const { theme, toggleTheme } = useTheme()
  const { user } = useAuth()

  const modules = getModulesForRole(user?.role || 'VIEWER')

  const moduleLinks = modules.map(m => ({
    label: m.label,
    path: m.path,
    icon: iconForModule(m.icon),
    children: m.children?.filter(c => c.path !== m.path).map(c => ({
      label: c.label,
      path: c.path,
      icon: iconForModule(c.icon),
    })),
  }))

  const allLinks = [...STATIC_LINKS, ...moduleLinks]

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

      {/* Role Badge */}
      {!collapsed && user && (
        <div className="px-3 py-2 border-b border-gray-100 dark:border-gray-800">
          <div className="flex items-center gap-2 px-2 py-1.5 rounded-lg bg-gray-50 dark:bg-gray-800/50">
            <div className={clsx(
              'w-2 h-2 rounded-full',
              user.role === 'ADMIN' ? 'bg-red-500' :
              user.role === 'CEO' ? 'bg-purple-500' :
              user.role === 'OPS_MANAGER' ? 'bg-blue-500' :
              user.role === 'WAREHOUSE_MANAGER' ? 'bg-amber-500' :
              user.role === 'STORE_MANAGER' ? 'bg-green-500' :
              user.role === 'PICKER' ? 'bg-cyan-500' :
              user.role === 'PACKER' ? 'bg-emerald-500' :
              user.role === 'LOADER' ? 'bg-orange-500' :
              user.role === 'BOPIS_OWNER' ? 'bg-teal-500' :
              'bg-gray-400'
            )} />
            <span className="text-xs font-medium text-gray-600 dark:text-gray-400 capitalize">
              {user.role.replace(/_/g, ' ')}
            </span>
          </div>
        </div>
      )}

      {/* Navigation */}
      <nav className="flex-1 px-2.5 py-3 space-y-0.5 overflow-y-auto scrollbar-hide">
        {allLinks.map((item) => {
          const isParent = item.children && item.children.length > 0
          const parentActive = item.path === '/' ? location.pathname === '/' : location.pathname.startsWith(item.path)
          const hasActiveChild = isParent && item.children!.some(c => location.pathname.startsWith(c.path))

          if (collapsed || !isParent) {
            return (
              <NavLink
                key={item.path}
                to={item.path}
                className={clsx(
                  'flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-all duration-150 group',
                  parentActive
                    ? 'bg-primary-50 text-primary-700 dark:bg-primary-900/20 dark:text-primary-300'
                    : 'text-gray-500 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-800',
                )}
                title={collapsed ? item.label : undefined}
              >
                <span className={clsx('flex-shrink-0', parentActive ? 'text-primary-600 dark:text-primary-400' : '')}>
                  {item.icon}
                </span>
                {!collapsed && <span>{item.label}</span>}
              </NavLink>
            )
          }

          return <SidebarGroup key={item.path} item={item} location={location} collapsed={collapsed} />
        })}
      </nav>

      {/* Footer */}
      <div className="p-2.5 border-t border-gray-200 dark:border-gray-800 space-y-1">
        {!collapsed && user && user.role === 'ADMIN' && (
          <div className="px-2 py-1.5">
            <select
              value={user.role}
              onChange={e => {
                const newRole = e.target.value as UserRole
                const updated = { ...user, role: newRole }
                localStorage.setItem('nexus_user', JSON.stringify(updated))
                window.location.reload()
              }}
              className="w-full text-xs bg-gray-100 dark:bg-gray-800 border-0 rounded-lg px-2 py-1.5 text-gray-700 dark:text-gray-300 cursor-pointer focus:ring-1 focus:ring-primary-500"
            >
              {Object.entries(ROLE_WORKSPACES).map(([key, ws]) => (
                <option key={key} value={key}>{ws.label}</option>
              ))}
            </select>
          </div>
        )}
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
