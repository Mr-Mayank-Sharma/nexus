import { useAuth } from '../context/AuthContext'
import type { UserRole } from '../types'

export type { UserRole }

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
  icon: string
}

export const ROLE_WORKSPACES: Record<UserRole, RoleWorkspace> = {
  ADMIN: { role: 'ADMIN', label: 'System Administrator', description: 'Full system control & configuration', color: 'bg-red-600', homePath: '/', icon: 'Shield' },
  CEO: { role: 'CEO', label: 'Executive Dashboard', description: 'Company-wide overview & strategy', color: 'bg-purple-600', homePath: '/', icon: 'Award' },
  OPS_MANAGER: { role: 'OPS_MANAGER', label: 'Operations Manager', description: 'Order lifecycle & fulfillment oversight', color: 'bg-blue-600', homePath: '/dashboard', icon: 'BarChart3' },
  WAREHOUSE_MANAGER: { role: 'WAREHOUSE_MANAGER', label: 'Warehouse Manager', description: 'Warehouse operations & labor management', color: 'bg-amber-600', homePath: '/warehouse-dashboard', icon: 'Building2' },
  PICKER: { role: 'PICKER', label: 'Picker', description: 'Pick items from shelves', color: 'bg-cyan-600', homePath: '/picking', icon: 'ClipboardCheck' },
  PACKER: { role: 'PACKER', label: 'Packer', description: 'Pack orders for shipment', color: 'bg-emerald-600', homePath: '/packer', icon: 'PackagePlus' },
  LOADER: { role: 'LOADER', label: 'Loader', description: 'Load & dispatch shipments', color: 'bg-orange-600', homePath: '/loader', icon: 'Truck' },
  STORE_MANAGER: { role: 'STORE_MANAGER', label: 'Store Manager', description: 'Store operations & inventory', color: 'bg-green-600', homePath: '/store-dashboard', icon: 'Store' },
  BOPIS_OWNER: { role: 'BOPIS_OWNER', label: 'BOPIS Owner', description: 'Buy Online Pickup In Store operations', color: 'bg-teal-600', homePath: '/bopis', icon: 'ShoppingBag' },
  CUSTOMER_SUPPORT: { role: 'CUSTOMER_SUPPORT', label: 'Customer Support', description: 'Orders, returns, & customer inquiries', color: 'bg-indigo-600', homePath: '/orders', icon: 'Users' },
  PROCUREMENT_MANAGER: { role: 'PROCUREMENT_MANAGER', label: 'Procurement Manager', description: 'Suppliers, RFQs, & purchase orders', color: 'bg-cyan-700', homePath: '/procurement', icon: 'ShoppingCart' },
  FINANCE: { role: 'FINANCE', label: 'Finance', description: 'Invoices, payments & billing', color: 'bg-emerald-700', homePath: '/invoices', icon: 'Receipt' },
  LOGISTICS_MANAGER: { role: 'LOGISTICS_MANAGER', label: 'Logistics Manager', description: 'Carriers, rates & shipping operations', color: 'bg-indigo-700', homePath: '/shipping', icon: 'Ship' },
  VIEWER: { role: 'VIEWER', label: 'Read Only', description: 'View dashboards & reports', color: 'bg-gray-600', homePath: '/', icon: 'Eye' },
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
    { id: 'warehouse-dashboard', label: 'WH Dashboard', path: '/warehouse-dashboard', icon: 'BarChart3' },
    { id: 'packer', label: 'Packer View', path: '/packer', icon: 'PackagePlus' },
    { id: 'loader', label: 'Loader View', path: '/loader', icon: 'Truck' },
    { id: 'store-dashboard', label: 'Store Dashboard', path: '/store-dashboard', icon: 'Store' },
    { id: 'bopis', label: 'BOPIS', path: '/bopis', icon: 'ShoppingBag' },
    { id: 'bopis-owner', label: 'BOPIS Owner', path: '/bopis-owner', icon: 'ShoppingBag' },
    { id: 'pre-orders', label: 'Pre-Orders', path: '/pre-orders', icon: 'Calendar' },
    { id: 'atp-rules', label: 'ATP Rules', path: '/atp-rules', icon: 'Gauge' },
    { id: 'task-queues', label: 'Task Queues', path: '/task-queues', icon: 'AlertTriangle' },
    { id: 'procurement', label: 'Procurement', path: '/procurement', icon: 'ShoppingCart', children: [
      { id: 'purchase-requests', label: 'Requests', path: '/procurement', icon: 'FileText' },
      { id: 'suppliers', label: 'Suppliers', path: '/procurement', icon: 'Building' },
    ]},
    { id: 'invoices', label: 'Invoices', path: '/invoices', icon: 'Receipt' },
    { id: 'payments', label: 'Payments', path: '/payments', icon: 'CreditCard' },
    { id: 'inventory-enhanced', label: 'Multi-Node Inventory', path: '/inventory/enhanced', icon: 'Warehouse' },
    { id: 'wave-planning', label: 'Wave Planning', path: '/wave-planning', icon: 'Layers' },
    { id: 'labor-management', label: 'Labor Management', path: '/labor-management', icon: 'Users' },
    { id: 'slotting-optimization', label: 'Slotting Optimization', path: '/slotting-optimization', icon: 'Target' },
    { id: 'yard-dock', label: 'Yard & Dock', path: '/yard-dock', icon: 'Truck' },
    { id: 'automation-systems', label: 'Automation Systems', path: '/automation-systems', icon: 'Bot' },
    { id: 'label-printing', label: 'Label Printing', path: '/label-printing', icon: 'Printer' },
    { id: 'manifest', label: 'Manifests', path: '/manifest', icon: 'ClipboardList' },
    { id: 'report-builder', label: 'Report Builder', path: '/report-builder', icon: 'BarChart3' },
    { id: 'returns-enhanced', label: 'Returns Command Center', path: '/returns-enhanced', icon: 'RotateCcw' },
    { id: 'carriers', label: 'Carriers', path: '/carriers', icon: 'Ship' },
    { id: 'rate-shopping', label: 'Rate Shopping', path: '/rate-shopping', icon: 'TrendingDown' },
    { id: 'routing', label: 'Routing Rules', path: '/routing-rules', icon: 'GitBranch' },
    { id: 'order-routing', label: 'Order Routing', path: '/order-routing', icon: 'Route' },
    { id: 'edi', label: 'EDI Automation', path: '/edi', icon: 'Upload' },
    { id: 'email-parser', label: 'Email Order Parser', path: '/email-parser', icon: 'Mail' },
    { id: 'import-export', label: 'Import/Export', path: '/import-export', icon: 'Upload' },
    { id: 'b2b-portal', label: 'B2B Portal', path: '/b2b-portal', icon: 'ShoppingCart' },
    { id: 'stores', label: 'Stores', path: '/stores', icon: 'Store' },
    { id: 'experiments', label: 'AI Experiments', path: '/experiments', icon: 'FlaskConical' },
    { id: 'analytics', label: 'Analytics', path: '/analytics', icon: 'BarChart3' },
    { id: 'ai', label: 'AI Control Center', path: '/ai', icon: 'Brain' },
    { id: 'ai-platform', label: 'AI Platform', path: '/ai-platform', icon: 'Cpu' },
    { id: 'ai-briefing', label: 'AI Briefing', path: '/ai-briefing', icon: 'Brain' },
    { id: 'ai-routing', label: 'AI Routing', path: '/ai-routing', icon: 'GitBranch' },
    { id: 'ai-packing', label: 'AI Packing', path: '/ai-packing', icon: 'PackagePlus' },
    { id: 'ai-loading', label: 'AI Loading', path: '/ai-loading', icon: 'Truck' },
    { id: 'ai-audit', label: 'AI Audit Trail', path: '/ai-audit', icon: 'Shield' },
    { id: 'ai-forecasting', label: 'AI Forecasting', path: '/ai-forecasting', icon: 'BarChart3' },
    { id: 'notifications', label: 'Notifications', path: '/notifications', icon: 'Bell' },
    { id: 'workflows', label: 'Workflows', path: '/workflows', icon: 'GitBranch' },
    { id: 'integration-hub', label: 'Integration Hub', path: '/integration-hub', icon: 'Zap' },
    { id: 'integrations', label: 'Sales Channels', path: '/integrations', icon: 'Store', children: [
      { id: 'bigcommerce', label: 'BigCommerce', path: '/integrations/bigcommerce', icon: 'Store' },
      { id: 'amazon', label: 'Amazon', path: '/integrations/amazon', icon: 'ShoppingCart' },
      { id: 'ebay', label: 'eBay', path: '/integrations/ebay', icon: 'ShoppingBag' },
      { id: 'walmart', label: 'Walmart', path: '/integrations/walmart', icon: 'Globe' },
      { id: 'marketplace-hub', label: 'Marketplace Hub', path: '/integrations/marketplace', icon: 'Globe' },
    ]},
    { id: 'documents', label: 'Documents', path: '/documents', icon: 'FileText' },
    { id: 'users', label: 'Users & Roles', path: '/users', icon: 'UserCog' },
    { id: 'settings', label: 'Settings', path: '/settings', icon: 'Settings' },
    { id: 'audit', label: 'Audit & Compliance', path: '/audit', icon: 'Shield' },
  ]

  const roleModules: Record<string, string[]> = {
    ADMIN: [...all.map(m => m.id), ...all.flatMap(m => m.children?.map(c => c.id) || [])],
    CEO: ['dashboard', 'analytics', 'ai', 'ai-briefing', 'ai-routing', 'ai-packing', 'ai-loading', 'ai-audit', 'ai-forecasting', 'notifications', 'audit', 'documents', 'settings'],
    OPS_MANAGER: ['dashboard', 'orders', 'customers', 'products', 'inventory', 'fulfillment', 'returns', 'picking', 'packing', 'shipping', 'warehouse', 'routing', 'task-queues', 'ai-briefing', 'ai-routing', 'ai-audit', 'ai-forecasting', 'notifications', 'analytics', 'slotting-optimization', 'yard-dock', 'automation-systems'],
    WAREHOUSE_MANAGER: ['dashboard', 'warehouse-dashboard', 'warehouse', 'inventory', 'fulfillment', 'picking', 'packing', 'shipping', 'receiving', 'cycle-counts', 'task-queues', 'analytics', 'notifications', 'wave-planning', 'labor-management', 'slotting-optimization', 'yard-dock', 'automation-systems'],
    PICKER: ['picking', 'inventory'],
    PACKER: ['packer', 'packing', 'inventory'],
    LOADER: ['loader', 'shipping'],
    STORE_MANAGER: ['dashboard', 'store-dashboard', 'orders', 'inventory', 'customers', 'products', 'bopis', 'returns', 'notifications'],
    BOPIS_OWNER: ['bopis', 'bopis-owner', 'orders', 'inventory', 'customers', 'notifications'],
    CUSTOMER_SUPPORT: ['dashboard', 'orders', 'customers', 'returns', 'products', 'notifications'],
    PROCUREMENT_MANAGER: ['dashboard', 'procurement', 'invoices', 'notifications', 'analytics'],
    FINANCE: ['dashboard', 'invoices', 'payments', 'returns', 'customers', 'analytics', 'notifications'],
    LOGISTICS_MANAGER: ['dashboard', 'shipping', 'carriers', 'routing', 'fulfillment', 'orders', 'notifications', 'analytics', 'warehouse', 'yard-dock'],
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

export function getRoleLabel(role: UserRole): string {
  return ROLE_WORKSPACES[role]?.label || role.replace(/_/g, ' ')
}
