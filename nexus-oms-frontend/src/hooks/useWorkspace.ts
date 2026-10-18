import { useAuth } from '../context/AuthContext'
import type { UserRole } from '../types'

export type { UserRole }

export interface WorkspaceModule {
  id: string
  label: string
  path: string
  icon: string
  badge?: number
  /** Heading the group sits under in the sidebar. Groups are shown in SECTION_ORDER. */
  section?: string
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
  OPS_MANAGER: { role: 'OPS_MANAGER', label: 'Operations Manager', description: 'Order lifecycle & fulfillment oversight', color: 'bg-blue-600', homePath: '/', icon: 'BarChart3' },
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

/** Sidebar headings, in the order a person works: see it, sell it, fulfil it, source and ship it, learn from it, connect it, administer it. */
export const SECTION_ORDER = ['Overview', 'Commerce', 'Fulfillment', 'Supply chain', 'Intelligence', 'Connect', 'Administration']

export function getModulesForRole(role: string): WorkspaceModule[] {
  const groups: WorkspaceModule[] = [
    // ──────────────────────────────────────────────
    // 1. DASHBOARDS & ANALYTICS
    // ──────────────────────────────────────────────
    {
      section: 'Overview', id: 'home', label: 'Dashboards & Analytics', path: '/', icon: 'LayoutDashboard',
      children: [
        { id: 'launch-pad', label: 'Launch Pad', path: '/', icon: 'LayoutDashboard' },
        { id: 'analytics-dashboard', label: 'Analytics Dashboard', path: '/analytics-dashboard', icon: 'BarChart3' },
        { id: 'dashboard', label: 'Dashboard', path: '/dashboard', icon: 'Activity' },
        { id: 'analytics', label: 'Analytics', path: '/analytics', icon: 'BarChart3' },
        { id: 'report-builder', label: 'Report Builder', path: '/report-builder', icon: 'BarChart3' },
        { id: 'notifications', label: 'Notifications', path: '/notifications', icon: 'Bell' },
      ]
    },
    // ──────────────────────────────────────────────
    // 2. ORDER MANAGEMENT
    // ──────────────────────────────────────────────
    {
      section: 'Commerce', id: 'order-management', label: 'Order Management', path: '/orders', icon: 'Package',
      children: [
        { id: 'order-twin', label: 'Order Flow Twin', path: '/order-twin', icon: 'Route' },
        { id: 'orders', label: 'All Orders', path: '/orders', icon: 'Package' },
        { id: 'find-order', label: 'Find Orders', path: '/orders/search', icon: 'Search' },
        { id: 'create-order', label: 'Create Order', path: '/orders/new', icon: 'Plus' },
        { id: 'b2b-portal', label: 'B2B Portal', path: '/b2b-portal', icon: 'ShoppingCart' },
        { id: 'order-approvals', label: 'Order Approvals', path: '/order-approvals', icon: 'ClipboardCheck' },
        { id: 'order-routing', label: 'Order Routing', path: '/order-routing', icon: 'GitBranch' },
        { id: 'order-replay', label: 'Routing What-If', path: '/order-replay', icon: 'FlaskConical' },
        { id: 'brokering', label: 'Brokering Queue', path: '/brokering', icon: 'Activity' },
        { id: 'rejections', label: 'Rejections', path: '/rejections', icon: 'AlertTriangle' },
        { id: 'fulfillment-limits', label: 'Fulfillment Limits', path: '/fulfillment-limits', icon: 'Gauge' },
        { id: 'reconciliation', label: 'Reconciliation', path: '/reconciliation', icon: 'RefreshCcw' },
        { id: 'customers', label: 'Customers', path: '/customers', icon: 'Users' },
      ]
    },
    // ──────────────────────────────────────────────
    // 3. FULFILLMENT OPERATIONS
    // ──────────────────────────────────────────────
    {
      section: 'Fulfillment', id: 'fulfillment-group', label: 'Fulfillment Operations', path: '/fulfillment', icon: 'Truck',
      children: [
        { id: 'fulfillment', label: 'Fulfillment Overview', path: '/fulfillment', icon: 'Truck' },
        { id: 'picking', label: 'Picking', path: '/picking', icon: 'ClipboardList' },
        { id: 'packing', label: 'Packing', path: '/packing', icon: 'Package' },
        { id: 'shipping', label: 'Shipping', path: '/shipping', icon: 'Ship' },
        { id: 'wave-planning', label: 'Wave Planning', path: '/wave-planning', icon: 'Layers' },
        { id: 'pickers', label: 'Pickers', path: '/pickers', icon: 'ClipboardCheck' },
        { id: 'packer', label: 'Packer View', path: '/packer', icon: 'PackagePlus' },
        { id: 'loader', label: 'Loader View', path: '/loader', icon: 'Truck' },
        { id: 'label-printing', label: 'Label Printing', path: '/label-printing', icon: 'Printer' },
      ]
    },
    // ──────────────────────────────────────────────
    // 4. INVENTORY MANAGEMENT
    // ──────────────────────────────────────────────
    {
      section: 'Commerce', id: 'inventory-management', label: 'Inventory & Catalog', path: '/inventory', icon: 'Warehouse',
      children: [
        { id: 'inventory', label: 'Inventory', path: '/inventory', icon: 'Warehouse' },
        { id: 'inventory-enhanced', label: 'Multi-Node Inventory', path: '/inventory/enhanced', icon: 'Warehouse' },
        { id: 'receiving', label: 'Receiving', path: '/inventory/receiving', icon: 'PackagePlus' },
        { id: 'cycle-counts', label: 'Cycle Counts', path: '/inventory/cycle-counts', icon: 'ClipboardCheck' },
        { id: 'rfid', label: 'RFID & Serialized', path: '/inventory/rfid', icon: 'Radio' },
        { id: 'replenishment', label: 'Replenishment', path: '/replenishment', icon: 'RefreshCw' },
        { id: 'transfers', label: 'Transfer Orders', path: '/transfers', icon: 'ArrowRightLeft' },
        { id: 'products', label: 'Products', path: '/products', icon: 'Tags' },
      ]
    },
    // ──────────────────────────────────────────────
    // 5. RETURNS & EXCEPTIONS
    // ──────────────────────────────────────────────
    {
      section: 'Commerce', id: 'returns-management', label: 'Returns & Exceptions', path: '/returns', icon: 'RotateCcw',
      children: [
        { id: 'returns', label: 'Returns', path: '/returns', icon: 'RotateCcw' },
        { id: 'returns-enhanced', label: 'Returns Command Center', path: '/returns-enhanced', icon: 'RotateCcw' },
        { id: 'returns-finance', label: 'Returns Finance', path: '/returns-finance', icon: 'DollarSign' },
      ]
    },
    // ──────────────────────────────────────────────
    // 6. STORE OPERATIONS
    // ──────────────────────────────────────────────
    {
      section: 'Fulfillment', id: 'store-group', label: 'Store Operations', path: '/store-dashboard', icon: 'Store',
      children: [
        { id: 'store-dashboard', label: 'Store Dashboard', path: '/store-dashboard', icon: 'Store' },
        { id: 'store-operations', label: 'POS Stock Check', path: '/store-operations', icon: 'ClipboardCheck' },
        { id: 'bopis', label: 'BOPIS', path: '/bopis', icon: 'ShoppingBag' },
        { id: 'bopis-owner', label: 'BOPIS Owner', path: '/bopis-owner', icon: 'ShoppingBag' },
        { id: 'bopis-app', label: 'BOPIS App', path: '/bopis-app', icon: 'ShoppingBag' },
        { id: 'pre-orders', label: 'Pre-Orders', path: '/pre-orders', icon: 'Calendar' },
        { id: 'atp-rules', label: 'ATP Rules', path: '/atp-rules', icon: 'Gauge' },
        { id: 'endless-aisle', label: 'Endless Aisle', path: '/endless-aisle', icon: 'ShoppingCart' },
        { id: 'promotions', label: 'Promotions', path: '/promotions', icon: 'Tags' },
      ]
    },
    // ──────────────────────────────────────────────
    // 7. WAREHOUSE OPERATIONS
    // ──────────────────────────────────────────────
    {
      section: 'Fulfillment', id: 'warehouse-group', label: 'Warehouse Operations', path: '/warehouse-dashboard', icon: 'Building2',
      children: [
        { id: 'warehouse-twin', label: 'Warehouse Twin', path: '/warehouse-twin', icon: 'Layers' },
        { id: 'warehouse-dashboard', label: 'WH Dashboard', path: '/warehouse-dashboard', icon: 'BarChart3' },
        { id: 'warehouse', label: 'Warehouse Mgmt', path: '/warehouse', icon: 'Building2' },
        { id: 'slotting-optimization', label: 'Slotting Optimization', path: '/slotting-optimization', icon: 'Target' },
        { id: 'yard-dock', label: 'Yard & Dock', path: '/yard-dock', icon: 'Truck' },
        { id: 'automation-systems', label: 'Automation Systems', path: '/automation-systems', icon: 'Bot' },
        { id: 'labor-management', label: 'Labor Management', path: '/labor-management', icon: 'Users' },
        { id: 'task-queues', label: 'Task Queues', path: '/task-queues', icon: 'AlertTriangle' },
      ]
    },
    // ──────────────────────────────────────────────
    // 8. PROCUREMENT
    // ──────────────────────────────────────────────
    {
      section: 'Supply chain', id: 'procurement-group', label: 'Procurement', path: '/procurement', icon: 'ShoppingCart',
      children: [
        { id: 'procurement', label: 'Procurement', path: '/procurement', icon: 'ShoppingCart' },
        { id: 'purchase-requests', label: 'Purchase Requests', path: '/procurement/requests', icon: 'FileText' },
        { id: 'suppliers', label: 'Suppliers', path: '/procurement/suppliers', icon: 'Building' },
      ]
    },
    // ──────────────────────────────────────────────
    // 9. SUPPLY CHAIN & LOGISTICS
    // ──────────────────────────────────────────────
    {
      section: 'Supply chain', id: 'supply-chain-group', label: 'Shipping & Logistics', path: '/carriers', icon: 'Ship',
      children: [
        { id: 'carriers', label: 'Carriers', path: '/carriers', icon: 'Ship' },
        { id: 'rate-shopping', label: 'Rate Shopping', path: '/rate-shopping', icon: 'TrendingDown' },
        { id: 'freight-audit', label: 'Freight Audit', path: '/freight-audit', icon: 'Receipt' },
        { id: 'routing', label: 'Routing Rules', path: '/routing-rules', icon: 'GitBranch' },
      ]
    },
    // ──────────────────────────────────────────────
    // 10. FINANCE
    // ──────────────────────────────────────────────
    {
      section: 'Supply chain', id: 'finance-group', label: 'Finance', path: '/invoices', icon: 'Receipt',
      children: [
        { id: 'invoices', label: 'Invoices', path: '/invoices', icon: 'Receipt' },
        { id: 'payments', label: 'Payments', path: '/payments', icon: 'CreditCard' },
        { id: 'manifest', label: 'Manifests', path: '/manifest', icon: 'ClipboardList' },
      ]
    },
    // ──────────────────────────────────────────────
    // 11. INTELLIGENCE & AI
    // ──────────────────────────────────────────────
    {
      section: 'Intelligence', id: 'ai-group', label: 'Intelligence & AI', path: '/ai', icon: 'Brain',
      children: [
        { id: 'ai', label: 'AI Control Center', path: '/ai', icon: 'Brain' },
        { id: 'ai-platform', label: 'AI Platform', path: '/ai-platform', icon: 'Cpu' },
        { id: 'ai-briefing', label: 'AI Briefing', path: '/ai-briefing', icon: 'Brain' },
        { id: 'ai-routing', label: 'AI Routing', path: '/ai-routing', icon: 'GitBranch' },
        { id: 'ai-packing', label: 'AI Packing', path: '/ai-packing', icon: 'PackagePlus' },
        { id: 'ai-loading', label: 'AI Loading', path: '/ai-loading', icon: 'Truck' },
        { id: 'ai-audit', label: 'AI Audit Trail', path: '/ai-audit', icon: 'Shield' },
        { id: 'ai-forecasting', label: 'AI Forecasting', path: '/ai-forecasting', icon: 'BarChart3' },
        { id: 'mcp-server', label: 'MCP Server', path: '/mcp-server', icon: 'Bot' },
        { id: 'experiments', label: 'AI Experiments', path: '/experiments', icon: 'FlaskConical' },
      ]
    },
    // ──────────────────────────────────────────────
    // 12. MARKETPLACES
    // ──────────────────────────────────────────────
    {
      section: 'Connect', id: 'integrations-group', label: 'Integrations', path: '/integration-hub', icon: 'Zap',
      children: [
        { id: 'integrations-directory', label: 'Directory', path: '/integrations/directory', icon: 'Layers' },
        { id: 'integration-hub', label: 'Connections', path: '/integration-hub', icon: 'Zap' },
        { id: 'stores', label: 'Store Connections', path: '/integrations/stores', icon: 'Store' },
        { id: 'sync', label: 'Sync Monitor', path: '/sync', icon: 'RefreshCw' },
      ]
    },
    {
      section: 'Connect', id: 'marketplaces-group', label: 'Sales Channels', path: '/integrations/marketplace', icon: 'Globe',
      children: [
        { id: 'shopify', label: 'Shopify', path: '/integrations/shopify', icon: 'ShoppingBag' },
        { id: 'bigcommerce', label: 'BigCommerce', path: '/integrations/bigcommerce', icon: 'Store' },
        { id: 'amazon', label: 'Amazon', path: '/integrations/amazon', icon: 'ShoppingCart' },
        { id: 'ebay', label: 'eBay', path: '/integrations/ebay', icon: 'ShoppingBag' },
        { id: 'walmart', label: 'Walmart', path: '/integrations/walmart', icon: 'Globe' },
        { id: 'marketplace-hub', label: 'All Channels', path: '/integrations/marketplace', icon: 'Globe' },
      ]
    },
    {
      section: 'Connect', id: 'data-exchange-group', label: 'Data Exchange', path: '/import-export', icon: 'Upload',
      children: [
        { id: 'edi', label: 'EDI', path: '/edi', icon: 'Upload' },
        { id: 'email-parser', label: 'Email Order Parser', path: '/email-parser', icon: 'Mail' },
        { id: 'import-export', label: 'Import / Export', path: '/import-export', icon: 'Download' },
        { id: 'jobs', label: 'Scheduled Jobs', path: '/jobs', icon: 'CalendarClock' },
        { id: 'documents', label: 'Documents', path: '/documents', icon: 'FileText' },
      ]
    },
    // ──────────────────────────────────────────────
    // 14. ADMINISTRATION
    // ──────────────────────────────────────────────
    {
      section: 'Administration', id: 'admin-group', label: 'Administration', path: '/settings', icon: 'Settings',
      children: [
        { id: 'users', label: 'Users & Roles', path: '/users', icon: 'UserCog' },
        { id: 'settings', label: 'Settings', path: '/settings', icon: 'Settings' },
        { id: 'audit', label: 'Audit & Compliance', path: '/audit', icon: 'Shield' },
        { id: 'workflows', label: 'Workflows', path: '/workflows', icon: 'GitBranch' },
      ]
    },
  ]

  const roleModules: Record<string, string[]> = {
    ADMIN: groups.flatMap(g => [g.id, ...(g.children?.map(c => c.id) || [])]),
    CEO: [
      'launch-pad', 'analytics-dashboard', 'dashboard', 'notifications',
      'ai', 'ai-briefing', 'ai-routing', 'ai-packing', 'ai-loading', 'ai-audit', 'ai-forecasting',
      'mcp-server', 'experiments', 'analytics',
      'audit', 'settings', 'promotions', 'documents', 'report-builder',
      'endless-aisle', 'products',
    ],
    OPS_MANAGER: [
      'launch-pad', 'analytics-dashboard', 'dashboard', 'notifications',
      'order-twin', 'orders', 'find-order', 'create-order', 'order-approvals', 'order-routing', 'order-replay',
      'brokering', 'rejections', 'transfers', 'fulfillment-limits', 'reconciliation',
      'fulfillment', 'picking', 'packing', 'shipping',
      'wave-planning', 'labor-management', 'pickers', 'label-printing',
      'inventory', 'inventory-enhanced', 'receiving', 'cycle-counts', 'rfid', 'replenishment',
      'returns', 'returns-enhanced', 'returns-finance', 'freight-audit',
      'warehouse-twin', 'warehouse-dashboard', 'warehouse', 'slotting-optimization', 'yard-dock', 'automation-systems',
      'products', 'customers',
      'procurement', 'purchase-requests', 'suppliers',
      'carriers', 'rate-shopping', 'routing',
      'invoices', 'payments', 'manifest',
      'integrations-directory', 'integration-hub', 'sync', 'edi', 'import-export', 'jobs', 'b2b-portal',
      'email-parser', 'documents', 'store-operations',
      'bigcommerce', 'amazon', 'ebay', 'walmart', 'marketplace-hub',
      'users', 'settings', 'audit', 'workflows', 'promotions',
      'task-queues', 'report-builder',
      'ai-briefing', 'ai-routing', 'ai-audit', 'ai-forecasting', 'mcp-server', 'analytics',
      'endless-aisle',
    ],
    WAREHOUSE_MANAGER: [
      'launch-pad', 'analytics-dashboard', 'dashboard', 'notifications',
      'warehouse-twin', 'warehouse-dashboard', 'warehouse',
      'inventory', 'inventory-enhanced', 'receiving', 'cycle-counts', 'rfid', 'replenishment',
      'fulfillment', 'picking', 'packing', 'shipping',
      'wave-planning', 'labor-management', 'pickers', 'packer', 'loader',
      'label-printing',
      'slotting-optimization', 'yard-dock', 'automation-systems',
      'products',
      'task-queues', 'analytics',
      'transfers', 'endless-aisle', 'notifications',
    ],
    PICKER: ['picking', 'inventory', 'packer', 'loader'],
    PACKER: ['packer', 'packing', 'inventory'],
    LOADER: ['loader', 'shipping'],
    STORE_MANAGER: [
      'launch-pad', 'analytics-dashboard', 'dashboard', 'notifications',
      'store-dashboard', 'store-operations', 'bopis', 'bopis-owner', 'bopis-app',
      'pre-orders', 'atp-rules', 'endless-aisle',
      'orders', 'inventory', 'customers', 'products',
      'returns', 'replenishment',
      'promotions',
    ],
    BOPIS_OWNER: [
      'bopis', 'bopis-owner', 'bopis-app',
      'orders', 'inventory', 'customers',
      'notifications', 'endless-aisle',
    ],
    CUSTOMER_SUPPORT: [
      'launch-pad', 'notifications',
      'order-twin', 'orders', 'find-order', 'order-approvals',
      'customers',
      'returns', 'returns-enhanced', 'returns-finance',
      'products',
      'brokering', 'rejections',
      'promotions',
    ],
    PROCUREMENT_MANAGER: [      'launch-pad', 'analytics-dashboard', 'dashboard', 'notifications',
      'procurement', 'purchase-requests', 'suppliers',
      'invoices',
      'analytics',
    ],
    FINANCE: [
      'launch-pad', 'analytics-dashboard', 'dashboard', 'notifications',
      'invoices', 'payments', 'manifest',
      'returns', 'returns-finance', 'freight-audit',
      'customers',
      'analytics',
    ],
    LOGISTICS_MANAGER: [
      'launch-pad', 'analytics-dashboard', 'dashboard', 'notifications',
      'shipping', 'carriers', 'rate-shopping', 'routing',
      'fulfillment', 'orders',
      'warehouse', 'yard-dock',
      'transfers',
      'analytics',
    ],
    VIEWER: [
      'launch-pad', 'analytics-dashboard', 'dashboard',
      'analytics', 'orders', 'inventory',
    ],
  }

  const allowedIds = roleModules[role] || roleModules.VIEWER

  const rank = (g: WorkspaceModule) => { const i = SECTION_ORDER.indexOf(g.section ?? ''); return i < 0 ? SECTION_ORDER.length : i }
  return groups
    .filter(g => g.children?.some(c => allowedIds.includes(c.id)))
    .map(g => ({
      ...g,
      children: g.children?.filter(c => allowedIds.includes(c.id)),
    }))
    .sort((a, b) => rank(a) - rank(b))   // Array.prototype.sort is stable: groups keep their order inside a section
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
