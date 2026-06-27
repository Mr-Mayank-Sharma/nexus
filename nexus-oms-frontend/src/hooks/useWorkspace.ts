import { useAuth } from '../context/AuthContext'

export type UserRole = 'ADMIN' | 'OPS' | 'WAREHOUSE' | 'VIEWER' | 'SALES_EXEC' | 'CUSTOMER_SUPPORT' | 'WAREHOUSE_OPERATOR' | 'WAREHOUSE_MANAGER' | 'PROCUREMENT_MANAGER' | 'FINANCE' | 'LOGISTICS_MANAGER' | 'CEO'

export interface WorkspaceModule {
  id: string
  label: string
  path: string
  icon: string
  badge?: number
  children?: WorkspaceModule[]
}

export interface RoleWorkspace {
  role: UserRole
  label: string
  description: string
  color: string
  homePath: string
}

export const ROLE_WORKSPACES: Record<string, RoleWorkspace> = {
  ADMIN: { role: 'ADMIN', label: 'Admin Workspace', description: 'Full system control', color: 'bg-red-600', homePath: '/admin' },
  CEO: { role: 'CEO', label: 'Executive Dashboard', description: 'Company-wide overview', color: 'bg-purple-600', homePath: '/' },
  OPS: { role: 'OPS', label: 'Operations', description: 'Order lifecycle management', color: 'bg-blue-600', homePath: '/orders' },
  WAREHOUSE: { role: 'WAREHOUSE', label: 'Warehouse', description: 'Inventory & fulfillment', color: 'bg-amber-600', homePath: '/inventory' },
  SALES_EXEC: { role: 'SALES_EXEC', label: 'Sales', description: 'Customers & orders', color: 'bg-green-600', homePath: '/customers' },
  CUSTOMER_SUPPORT: { role: 'CUSTOMER_SUPPORT', label: 'Customer Support', description: 'Orders, returns, tickets', color: 'bg-teal-600', homePath: '/orders' },
  WAREHOUSE_OPERATOR: { role: 'WAREHOUSE_OPERATOR', label: 'Warehouse Ops', description: 'Pick, pack, ship', color: 'bg-amber-600', homePath: '/picking' },
  WAREHOUSE_MANAGER: { role: 'WAREHOUSE_MANAGER', label: 'Warehouse Mgmt', description: 'Warehouse operations', color: 'bg-orange-600', homePath: '/warehouse' },
  PROCUREMENT_MANAGER: { role: 'PROCUREMENT_MANAGER', label: 'Procurement', description: 'Suppliers & purchasing', color: 'bg-cyan-600', homePath: '/procurement' },
  FINANCE: { role: 'FINANCE', label: 'Finance', description: 'Invoices & payments', color: 'bg-emerald-600', homePath: '/invoices' },
  LOGISTICS_MANAGER: { role: 'LOGISTICS_MANAGER', label: 'Logistics', description: 'Carriers & shipments', color: 'bg-indigo-600', homePath: '/shipping' },
  VIEWER: { role: 'VIEWER', label: 'Read Only', description: 'View reports & data', color: 'bg-gray-600', homePath: '/' },
}

export function getModulesForRole(role: string): WorkspaceModule[] {
  const all: WorkspaceModule[] = [
    { id: 'dashboard', label: 'Dashboard', path: '/', icon: 'LayoutDashboard' },
    { id: 'orders', label: 'Orders', path: '/orders', icon: 'Package', children: [
      { id: 'order-search', label: 'Order Search', path: '/orders', icon: 'Search' },
      { id: 'create-order', label: 'Create Order', path: '/orders/new', icon: 'Plus' },
    ]},
    { id: 'customers', label: 'Customers', path: '/customers', icon: 'Users' },
    { id: 'products', label: 'Product Catalog', path: '/products', icon: 'Tags' },
    { id: 'inventory', label: 'Inventory', path: '/inventory', icon: 'Warehouse', children: [
      { id: 'inv-overview', label: 'Overview', path: '/inventory', icon: 'Warehouse' },
      { id: 'receiving', label: 'Receiving', path: '/inventory/receiving', icon: 'PackagePlus' },
      { id: 'cycle-counts', label: 'Cycle Counts', path: '/inventory/cycle-counts', icon: 'ClipboardCheck' },
    ]},
    { id: 'fulfillment', label: 'Fulfillment', path: '/fulfillment', icon: 'Truck' },
    { id: 'picking', label: 'Picking', path: '/picking', icon: 'ClipboardList' },
    { id: 'packing', label: 'Packing', path: '/packing', icon: 'Package' },
    { id: 'shipping', label: 'Shipping', path: '/shipping', icon: 'Truck' },
    { id: 'returns', label: 'Returns', path: '/returns', icon: 'RotateCcw' },
    { id: 'warehouse', label: 'Warehouse Mgmt', path: '/warehouse', icon: 'Building2' },
    { id: 'procurement', label: 'Procurement', path: '/procurement', icon: 'ShoppingCart', children: [
      { id: 'purchase-requests', label: 'Requests', path: '/procurement/requests', icon: 'FileText' },
      { id: 'purchase-orders', label: 'Purchase Orders', path: '/procurement/orders', icon: 'ShoppingCart' },
      { id: 'suppliers', label: 'Suppliers', path: '/procurement/suppliers', icon: 'Building' },
    ]},
    { id: 'invoices', label: 'Invoices', path: '/invoices', icon: 'Receipt' },
    { id: 'payments', label: 'Payments', path: '/payments', icon: 'CreditCard' },
    { id: 'carriers', label: 'Carriers', path: '/carriers', icon: 'Ship' },
    { id: 'routing', label: 'Routing Rules', path: '/routing-rules', icon: 'GitBranch' },
    { id: 'analytics', label: 'Analytics', path: '/analytics', icon: 'BarChart3', children: [
      { id: 'sales-reports', label: 'Sales Reports', path: '/analytics/sales', icon: 'BarChart3' },
      { id: 'inventory-reports', label: 'Inventory Reports', path: '/analytics/inventory', icon: 'Warehouse' },
      { id: 'carrier-reports', label: 'Carrier Reports', path: '/analytics/carriers', icon: 'Ship' },
    ]},
    { id: 'ai', label: 'AI Control Center', path: '/ai', icon: 'Brain', children: [
      { id: 'ai-forecast', label: 'Forecasting', path: '/ai/forecast', icon: 'Brain' },
      { id: 'ai-chat', label: 'Chat Assistant', path: '/ai/chat', icon: 'MessageSquare' },
    ]},
    { id: 'ai-platform', label: 'AI Platform', path: '/ai-platform', icon: 'Cpu' },
    { id: 'notifications', label: 'Notifications', path: '/notifications', icon: 'Bell' },
    { id: 'workflows', label: 'Workflows', path: '/workflows', icon: 'GitBranch' },
    { id: 'integration-hub', label: 'Integration Hub', path: '/integration-hub', icon: 'Zap' },
    { id: 'integrations', label: 'Sales Channels', path: '/integrations', icon: 'Store' },
    { id: 'documents', label: 'Documents', path: '/documents', icon: 'FileText' },
    { id: 'users', label: 'Users & Roles', path: '/users', icon: 'UserCog' },
    { id: 'settings', label: 'Settings', path: '/settings', icon: 'Settings', children: [
      { id: 'company-settings', label: 'Company', path: '/settings/company', icon: 'Building' },
      { id: 'system-admin', label: 'System Admin', path: '/settings/system', icon: 'Settings' },
    ]},
    { id: 'audit', label: 'Audit & Compliance', path: '/audit', icon: 'Shield' },
  ]

  const roleModules: Record<string, string[]> = {
    ADMIN: all.map(m => m.id),
    CEO: ['dashboard', 'analytics', 'ai', 'notifications', 'audit', 'documents'],
    OPS: ['dashboard', 'orders', 'customers', 'products', 'inventory', 'fulfillment', 'returns', 'routing', 'shipping', 'notifications'],
    WAREHOUSE: ['inventory', 'fulfillment', 'picking', 'packing', 'shipping', 'warehouse'],
    SALES_EXEC: ['dashboard', 'orders', 'customers', 'products', 'notifications'],
    CUSTOMER_SUPPORT: ['orders', 'customers', 'returns', 'notifications', 'products'],
    WAREHOUSE_OPERATOR: ['picking', 'packing', 'shipping', 'inventory'],
    WAREHOUSE_MANAGER: ['warehouse', 'inventory', 'fulfillment', 'picking', 'packing', 'shipping', 'analytics'],
    PROCUREMENT_MANAGER: ['procurement', 'suppliers', 'invoices', 'notifications', 'dashboard'],
    FINANCE: ['invoices', 'payments', 'returns', 'customers', 'analytics', 'dashboard'],
    LOGISTICS_MANAGER: ['shipping', 'carriers', 'routing', 'fulfillment', 'orders', 'notifications', 'analytics'],
    VIEWER: ['dashboard', 'analytics', 'orders', 'inventory'],
  }

  const allowedIds = roleModules[role] || roleModules.VIEWER
  return all.filter(m => allowedIds.includes(m.id)).map(m => ({
    ...m,
    children: m.children?.filter(c => allowedIds.includes(c.id)),
  }))
}

export function useWorkspace() {
  const { user } = useAuth()
  const role = (user?.role || 'VIEWER') as UserRole
  const workspace = ROLE_WORKSPACES[role] || ROLE_WORKSPACES.VIEWER
  const modules = getModulesForRole(role)

  return { role, workspace, modules, isAdmin: role === 'ADMIN' }
}
