import { useState } from 'react'
import { NavLink, useLocation, useNavigate } from 'react-router-dom'
import {
  LayoutDashboard, Package, Warehouse, Truck, RotateCcw, Ship, BarChart3,
  Brain, Settings, ChevronLeft, ChevronRight, GitBranch, ClipboardCheck,
  PackagePlus, Store, Moon, Sun, Zap, Building2, ShoppingCart, Receipt,
  Bell, FileText, UserCog, Upload, Download, Activity, Cpu, Users, Shield,
  Globe, Route, TrendingDown, Mail, FlaskConical, ShoppingBag, Calendar,
  Gauge, AlertTriangle, Eye, CreditCard, Tags, Award, ClipboardList,
  Layers, Printer, ChevronDown, Target, Bot, Search,
} from 'lucide-react'
import { clsx } from 'clsx'
import { useTheme } from '../../context/ThemeContext'
import { useAuth } from '../../context/AuthContext'
import { ROLE_WORKSPACES } from '../../hooks/useWorkspace'
import type { UserRole } from '../../types'

const MODULE_PERMISSIONS: Record<string, { resource: string; action?: string }> = {
  'orders': { resource: 'orders', action: 'view' },
  'order-search': { resource: 'orders', action: 'view' },
  'create-order': { resource: 'orders', action: 'create' },
  'customers': { resource: 'customers', action: 'view' },
  'products': { resource: 'products', action: 'view' },
  'inventory': { resource: 'inventory', action: 'view' },
  'inv-overview': { resource: 'inventory', action: 'view' },
  'receiving': { resource: 'inventory', action: 'edit' },
  'cycle-counts': { resource: 'inventory', action: 'edit' },
  'fulfillment': { resource: 'orders', action: 'view' },
  'picking': { resource: 'orders', action: 'edit' },
  'packing': { resource: 'orders', action: 'edit' },
  'shipping': { resource: 'orders', action: 'edit' },
  'returns': { resource: 'returns', action: 'view' },
  'warehouse': { resource: 'warehouse', action: 'view' },
  'warehouse-dashboard': { resource: 'warehouse', action: 'view' },
  'packer': { resource: 'orders', action: 'edit' },
  'loader': { resource: 'orders', action: 'edit' },
  'store-dashboard': { resource: 'orders', action: 'view' },
  'bopis': { resource: 'orders', action: 'view' },
  'bopis-owner': { resource: 'orders', action: 'view' },
  'pre-orders': { resource: 'orders', action: 'view' },
  'atp-rules': { resource: 'inventory', action: 'edit' },
  'task-queues': { resource: 'orders', action: 'view' },
  'procurement': { resource: 'procurement', action: 'view' },
  'invoices': { resource: 'invoices', action: 'view' },
  'payments': { resource: 'payments', action: 'view' },
  'analytics': { resource: 'analytics', action: 'view' },
  'ai': { resource: 'ai', action: 'view' },
  'ai-briefing': { resource: 'ai', action: 'view' },
  'ai-routing': { resource: 'ai', action: 'view' },
  'ai-packing': { resource: 'ai', action: 'view' },
  'ai-loading': { resource: 'ai', action: 'view' },
  'ai-audit': { resource: 'ai', action: 'view' },
  'ai-forecasting': { resource: 'ai', action: 'view' },
  'notifications': { resource: 'notifications', action: 'view' },
  'workflows': { resource: 'workflows', action: 'view' },
  'integration-hub': { resource: 'integrations', action: 'view' },
  'bigcommerce': { resource: 'integrations', action: 'view' },
  'amazon': { resource: 'integrations', action: 'view' },
  'ebay': { resource: 'integrations', action: 'view' },
  'walmart': { resource: 'integrations', action: 'view' },
  'marketplace-hub': { resource: 'integrations', action: 'view' },
  'documents': { resource: 'documents', action: 'view' },
  'users': { resource: 'users', action: 'view' },
  'settings': { resource: 'settings', action: 'view' },
  'audit': { resource: 'audit', action: 'view' },
  'wave-planning': { resource: 'warehouse', action: 'view' },
  'labor-management': { resource: 'warehouse', action: 'view' },
  'slotting-optimization': { resource: 'warehouse', action: 'view' },
  'yard-dock': { resource: 'warehouse', action: 'view' },
  'automation-systems': { resource: 'warehouse', action: 'view' },
  'label-printing': { resource: 'orders', action: 'view' },
  'manifest': { resource: 'orders', action: 'view' },
  'report-builder': { resource: 'analytics', action: 'view' },
  'inventory-enhanced': { resource: 'inventory', action: 'view' },
  'carriers': { resource: 'shipping', action: 'view' },
  'rate-shopping': { resource: 'shipping', action: 'view' },
  'routing': { resource: 'routing', action: 'view' },
  'order-routing': { resource: 'routing', action: 'view' },
  'edi': { resource: 'integrations', action: 'view' },
  'email-parser': { resource: 'integrations', action: 'view' },
  'import-export': { resource: 'integrations', action: 'view' },
  'b2b-portal': { resource: 'orders', action: 'view' },
  'stores': { resource: 'stores', action: 'view' },
  'experiments': { resource: 'ai', action: 'view' },
  'ai-platform': { resource: 'ai', action: 'view' },
  'returns-enhanced': { resource: 'returns', action: 'view' },
}

const ICON_MAP: Record<string, React.ReactNode> = {
  LayoutDashboard: <LayoutDashboard className="w-[18px] h-[18px]" />,
  Package: <Package className="w-[18px] h-[18px]" />,
  Warehouse: <Warehouse className="w-[18px] h-[18px]" />,
  Truck: <Truck className="w-[18px] h-[18px]" />,
  RotateCcw: <RotateCcw className="w-[18px] h-[18px]" />,
  Ship: <Ship className="w-[18px] h-[18px]" />,
  BarChart3: <BarChart3 className="w-[18px] h-[18px]" />,
  Brain: <Brain className="w-[18px] h-[18px]" />,
  Settings: <Settings className="w-[18px] h-[18px]" />,
  GitBranch: <GitBranch className="w-[18px] h-[18px]" />,
  ClipboardCheck: <ClipboardCheck className="w-[18px] h-[18px]" />,
  PackagePlus: <PackagePlus className="w-[18px] h-[18px]" />,
  Store: <Store className="w-[18px] h-[18px]" />,
  Building2: <Building2 className="w-[18px] h-[18px]" />,
  Building: <Building2 className="w-[18px] h-[18px]" />,
  ShoppingCart: <ShoppingCart className="w-[18px] h-[18px]" />,
  Receipt: <Receipt className="w-[18px] h-[18px]" />,
  Bell: <Bell className="w-[18px] h-[18px]" />,
  FileText: <FileText className="w-[18px] h-[18px]" />,
  UserCog: <UserCog className="w-[18px] h-[18px]" />,
  Upload: <Upload className="w-[18px] h-[18px]" />,
  Download: <Download className="w-[18px] h-[18px]" />,
  Activity: <Activity className="w-[18px] h-[18px]" />,
  Cpu: <Cpu className="w-[18px] h-[18px]" />,
  Users: <Users className="w-[18px] h-[18px]" />,
  Shield: <Shield className="w-[18px] h-[18px]" />,
  Globe: <Globe className="w-[18px] h-[18px]" />,
  Route: <Route className="w-[18px] h-[18px]" />,
  TrendingDown: <TrendingDown className="w-[18px] h-[18px]" />,
  Mail: <Mail className="w-[18px] h-[18px]" />,
  FlaskConical: <FlaskConical className="w-[18px] h-[18px]" />,
  ShoppingBag: <ShoppingBag className="w-[18px] h-[18px]" />,
  Calendar: <Calendar className="w-[18px] h-[18px]" />,
  Gauge: <Gauge className="w-[18px] h-[18px]" />,
  AlertTriangle: <AlertTriangle className="w-[18px] h-[18px]" />,
  Eye: <Eye className="w-[18px] h-[18px]" />,
  CreditCard: <CreditCard className="w-[18px] h-[18px]" />,
  Tags: <Tags className="w-[18px] h-[18px]" />,
  Award: <Award className="w-[18px] h-[18px]" />,
  ClipboardList: <ClipboardList className="w-[18px] h-[18px]" />,
  Search: <Package className="w-[18px] h-[18px]" />,
  Plus: <Package className="w-[18px] h-[18px]" />,
  Layers: <Layers className="w-[18px] h-[18px]" />,
  Printer: <Printer className="w-[18px] h-[18px]" />,
  Target: <Target className="w-[18px] h-[18px]" />,
  Bot: <Bot className="w-[18px] h-[18px]" />,
}

const STATIC_LINKS = [
  { label: 'Launch Pad', path: '/', icon: <LayoutDashboard className="w-[18px] h-[18px]" /> },
]

import { getModulesForRole } from '../../hooks/useWorkspace'

function iconForModule(id: string): React.ReactNode {
  return ICON_MAP[id] || <LayoutDashboard className="w-[18px] h-[18px]" />
}

const ROLE_COLORS: Record<string, string> = {
  ADMIN: 'bg-rose-500',
  CEO: 'bg-violet-500',
  OPS_MANAGER: 'bg-blue-500',
  WAREHOUSE_MANAGER: 'bg-amber-500',
  STORE_MANAGER: 'bg-emerald-500',
  PICKER: 'bg-cyan-500',
  PACKER: 'bg-teal-500',
  LOADER: 'bg-orange-500',
  BOPIS_OWNER: 'bg-pink-500',
  VIEWER: 'bg-gray-400',
}

function SidebarGroup({
  item,
  location,
  collapsed,
}: {
  item: { label: string; path: string; icon: React.ReactNode; children?: { label: string; path: string; icon: React.ReactNode }[] }
  location: any
  collapsed: boolean
}) {
  const [open, setOpen] = useState(() => location.pathname.startsWith(item.path))
  const parentActive = item.path === '/' ? location.pathname === '/' : location.pathname.startsWith(item.path)
  const hasActiveChild = item.children?.some(c => location.pathname.startsWith(c.path)) || false
  const isActive = parentActive || hasActiveChild

  return (
    <div>
      <button
        onClick={() => setOpen(!open)}
        className={clsx(
          'group flex items-center w-full rounded-lg text-[13px] font-medium transition-all duration-150',
          collapsed ? 'justify-center px-0 py-2' : 'gap-2.5 px-3 py-[7px]',
          isActive
            ? 'text-[var(--text-brand)] bg-[var(--interactive-selected)]'
            : 'text-[var(--text-secondary)] hover:text-[var(--text-primary)] hover:bg-[var(--interactive-hover)]',
        )}
        title={collapsed ? item.label : undefined}
      >
        <span className={clsx(
          'flex-shrink-0 transition-colors duration-150',
          isActive ? 'text-[var(--nexus-primary-500)]' : 'text-[var(--text-tertiary)] group-hover:text-[var(--text-secondary)]',
        )}>
          {item.icon}
        </span>
        {!collapsed && (
          <>
            <span className="flex-1 text-left truncate">{item.label}</span>
            <ChevronDown className={clsx(
              'w-3.5 h-3.5 transition-transform duration-200 flex-shrink-0',
              open ? 'rotate-180' : '',
              isActive ? 'text-[var(--nexus-primary-400)]' : 'text-[var(--text-tertiary)]',
            )} />
          </>
        )}
      </button>
      {open && !collapsed && item.children && (
        <div className="ml-[22px] mt-0.5 space-y-px pl-3 border-l border-[var(--border-subtle)]">
          {item.children.map((child) => {
            const childActive = child.path === '/' ? location.pathname === '/' : location.pathname.startsWith(child.path)
            return (
              <NavLink
                key={child.path}
                to={child.path}
                className={clsx(
                  'flex items-center gap-2 px-2.5 py-[5px] rounded-md text-[13px] font-medium transition-all duration-150',
                  childActive
                    ? 'text-[var(--text-brand)] bg-[var(--interactive-selected)]'
                    : 'text-[var(--text-tertiary)] hover:text-[var(--text-primary)] hover:bg-[var(--interactive-hover)]',
                )}
              >
                <span className={clsx(
                  'flex-shrink-0 transition-colors duration-150',
                  childActive ? 'text-[var(--nexus-primary-500)]' : '',
                )}>
                  {child.icon}
                </span>
                <span className="truncate">{child.label}</span>
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
  const { user, hasPermission } = useAuth()

  const modules = getModulesForRole(user?.role || 'VIEWER')

  const hasModulePermission = (moduleId: string): boolean => {
    const perm = MODULE_PERMISSIONS[moduleId]
    if (!perm) return true
    return hasPermission(perm.resource, perm.action)
  }

  const moduleLinks = modules
    .filter(m => hasModulePermission(m.id))
    .map(m => ({
      label: m.label,
      path: m.path,
      icon: iconForModule(m.icon),
      children: m.children
        ?.filter(c => c.path !== m.path && hasModulePermission(c.id))
        .map(c => ({
          label: c.label,
          path: c.path,
          icon: iconForModule(c.icon),
        })),
    }))

  const allLinks = [...STATIC_LINKS, ...moduleLinks]

  return (
    <aside
      className={clsx(
        'bg-[var(--surface-base)] border-r border-[var(--border-default)] flex flex-col transition-all duration-300 ease-[var(--ease-default)] h-screen sticky top-0',
        collapsed ? 'w-[56px]' : 'w-[256px]',
      )}
    >
      {/* ── Logo ── */}
      <div className={clsx(
        'flex items-center h-14 border-b border-[var(--border-subtle)] transition-all duration-300',
        collapsed ? 'justify-center px-0' : 'px-5 gap-3',
      )}>
        <div className="w-8 h-8 bg-[var(--nexus-primary-600)] rounded-lg flex items-center justify-center flex-shrink-0 shadow-sm">
          <Ship className="w-4 h-4 text-white" />
        </div>
        {!collapsed && (
          <div className="flex flex-col">
            <span className="font-bold text-[15px] text-[var(--text-primary)] tracking-[-0.02em] leading-tight">NexusShip</span>
            <span className="text-[10px] font-medium text-[var(--text-tertiary)] tracking-wide uppercase">OMS Platform</span>
          </div>
        )}
      </div>

      {/* ── Role Badge ── */}
      {!collapsed && user && (
        <div className="px-3 pt-3 pb-2">
          <div className="flex items-center gap-2.5 px-3 py-2 rounded-lg bg-[var(--surface-muted)] border border-[var(--border-subtle)]">
            <div className={clsx(
              'w-2 h-2 rounded-full flex-shrink-0',
              ROLE_COLORS[user.role] || 'bg-gray-400',
            )} />
            <span className="text-xs font-medium text-[var(--text-secondary)] capitalize truncate">
              {user.role.replace(/_/g, ' ').toLowerCase()}
            </span>
          </div>
        </div>
      )}

      {/* ── Navigation ── */}
      <nav className="flex-1 px-2.5 py-2 space-y-px overflow-y-auto scrollbar-hide">
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
                  'group flex items-center rounded-lg text-[13px] font-medium transition-all duration-150',
                  collapsed ? 'justify-center px-0 py-2' : 'gap-2.5 px-3 py-[7px]',
                  parentActive
                    ? 'text-[var(--text-brand)] bg-[var(--interactive-selected)]'
                    : 'text-[var(--text-secondary)] hover:text-[var(--text-primary)] hover:bg-[var(--interactive-hover)]',
                )}
                title={collapsed ? item.label : undefined}
              >
                <span className={clsx(
                  'flex-shrink-0 transition-colors duration-150',
                  parentActive ? 'text-[var(--nexus-primary-500)]' : 'text-[var(--text-tertiary)] group-hover:text-[var(--text-secondary)]',
                )}>
                  {item.icon}
                </span>
                {!collapsed && <span className="truncate">{item.label}</span>}
              </NavLink>
            )
          }

          return <SidebarGroup key={item.path} item={item} location={location} collapsed={collapsed} />
        })}
      </nav>

      {/* ── Footer ── */}
      <div className={clsx(
        'border-t border-[var(--border-subtle)] space-y-1',
        collapsed ? 'p-1.5' : 'p-2.5',
      )}>
        {/* Admin role switcher */}
        {!collapsed && user && user.role === 'ADMIN' && (
          <div className="px-1 pb-1">
            <select
              value={user.role}
              onChange={e => {
                const newRole = e.target.value as UserRole
                const updated = { ...user, role: newRole }
                localStorage.setItem('nexus_user', JSON.stringify(updated))
                window.location.reload()
              }}
              className="w-full text-xs bg-[var(--surface-muted)] border border-[var(--border-subtle)] rounded-lg px-2.5 py-1.5 text-[var(--text-secondary)] cursor-pointer focus:ring-1 focus:ring-[var(--border-focus)] focus:border-[var(--border-focus)] appearance-none"
            >
              {Object.entries(ROLE_WORKSPACES).map(([key, ws]) => (
                <option key={key} value={key}>{ws.label}</option>
              ))}
            </select>
          </div>
        )}

        {/* Theme toggle */}
        <button
          onClick={toggleTheme}
          className={clsx(
            'w-full flex items-center rounded-lg text-[var(--text-tertiary)] hover:text-[var(--text-primary)] hover:bg-[var(--interactive-hover)] transition-all duration-150',
            collapsed ? 'justify-center px-0 py-2' : 'gap-2.5 px-3 py-2',
          )}
          title={theme === 'light' ? 'Switch to dark mode' : 'Switch to light mode'}
        >
          {theme === 'light' ? <Moon className="w-[18px] h-[18px]" /> : <Sun className="w-[18px] h-[18px]" />}
          {!collapsed && <span className="text-[13px] font-medium">{theme === 'light' ? 'Dark mode' : 'Light mode'}</span>}
        </button>

        {/* Collapse toggle */}
        <button
          onClick={() => setCollapsed(!collapsed)}
          className={clsx(
            'w-full flex items-center rounded-lg text-[var(--text-tertiary)] hover:text-[var(--text-primary)] hover:bg-[var(--interactive-hover)] transition-all duration-150',
            collapsed ? 'justify-center px-0 py-2' : 'gap-2.5 px-3 py-2',
          )}
          title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
        >
          {collapsed ? <ChevronRight className="w-[18px] h-[18px]" /> : <ChevronLeft className="w-[18px] h-[18px]" />}
          {!collapsed && <span className="text-[13px] font-medium">Collapse</span>}
        </button>
      </div>
    </aside>
  )
}
