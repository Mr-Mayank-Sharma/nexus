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

export function getModulesForRole(role: string): WorkspaceModule[] {
  const groups: WorkspaceModule[] = [
    // ──────────────────────────────────────────────
    // 1. HOME & DASHBOARDS
    // ──────────────────────────────────────────────
    {
      id: 'home', label: 'Home & Dashboards', path: '/', icon: 'LayoutDashboard',
      children: [
        { id: 'launch-pad', label: 'Launch Pad', path: '/', icon: 'LayoutDashboard' },
        { id: 'analytics-dashboard', label: 'Analytics Dashboard', path: '/analytics-dashboard', icon: 'BarChart3' },
        { id: 'dashboard', label: 'Dashboard', path: '/dashboard', icon: 'Activity' },
        { id: 'notifications', label: 'Notifications', path: '/notifications', icon: 'Bell' },
      ]
    },
    // ──────────────────────────────────────────────
    // 2. ORDER MANAGEMENT
    // ──────────────────────────────────────────────
    {
      id: 'order-management', label: 'Order Management', path: '/orders', icon: 'Package',
      children: [
        { id: 'orders', label: 'All Orders', path: '/orders', icon: 'Package' },
        { id: 'find-order', label: 'Find Orders', path: '/orders/search', icon: 'Search' },
        { id: 'create-order', label: 'Create Order', path: '/orders/new', icon: 'Plus' },
        { id: 'order-approvals', label: 'Order Approvals', path: '/order-approvals', icon: 'ClipboardCheck' },
        { id: 'order-routing', label: 'Order Routing', path: '/order-routing', icon: 'GitBranch' },
        { id: 'brokering', label: 'Brokering Queue', path: '/brokering', icon: 'Activity' },
        { id: 'rejections', label: 'Rejections', path: '/rejections', icon: 'AlertTriangle' },
        { id: 'transfers', label: 'Transfer Orders', path: '/transfers', icon: 'ArrowRightLeft' },
        { id: 'fulfillment-limits', label: 'Fulfillment Limits', path: '/fulfillment-limits', icon: 'Gauge' },
      ]
    },
    // ──────────────────────────────────────────────
    // 3. FULFILLMENT OPERATIONS
    // ──────────────────────────────────────────────
    {
      id: 'fulfillment-group', label: 'Fulfillment Operations', path: '/fulfillment', icon: 'Truck',
      children: [
        { id: 'fulfillment', label: 'Fulfillment Overview', path: '/fulfillment', icon: 'Truck' },
        { id: 'picking', label: 'Picking', path: '/picking', icon: 'ClipboardList' },
        { id: 'packing', label: 'Packing', path: '/packing', icon: 'Package' },
        { id: 'shipping', label: 'Shipping', path: '/shipping', icon: 'Ship' },
        { id: 'wave-planning', label: 'Wave Planning', path: '/wave-planning', icon: 'Layers' },
        { id: 'labor-management', label: 'Labor Management', path: '/labor-management', icon: 'Users' },
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
      id: 'inventory-management', label: 'Inventory Management', path: '/inventory', icon: 'Warehouse',
      children: [
        { id: 'inventory', label: 'Inventory', path: '/inventory', icon: 'Warehouse' },
        { id: 'inventory-enhanced', label: 'Multi-Node Inventory', path: '/inventory/enhanced', icon: 'Warehouse' },
        { id: 'receiving', label: 'Receiving', path: '/inventory/receiving', icon: 'PackagePlus' },
        { id: 'cycle-counts', label: 'Cycle Counts', path: '/inventory/cycle-counts', icon: 'ClipboardCheck' },
        { id: 'replenishment', label: 'Replenishment', path: '/replenishment', icon: 'RefreshCw' },
      ]
    },
    // ──────────────────────────────────────────────
    // 5. RETURNS & EXCEPTIONS
    // ──────────────────────────────────────────────
    {
      id: 'returns-management', label: 'Returns & Exceptions', path: '/returns', icon: 'RotateCcw',
      children: [
        { id: 'returns', label: 'Returns', path: '/returns', icon: 'RotateCcw' },
        { id: 'returns-enhanced', label: 'Returns Command Center', path: '/returns-enhanced', icon: 'RotateCcw' },
        { id: 'freight-audit', label: 'Freight Audit', path: '/freight-audit', icon: 'Receipt' },
      ]
    },
    // ──────────────────────────────────────────────
    // 6. STORE OPERATIONS
    // ──────────────────────────────────────────────
    {
      id: 'store-group', label: 'Store Operations', path: '/store-dashboard', icon: 'Store',
      children: [
        { id: 'store-dashboard', label: 'Store Dashboard', path: '/store-dashboard', icon: 'Store' },
        { id: 'stores', label: 'Stores', path: '/stores', icon: 'Store' },
        { id: 'bopis', label: 'BOPIS', path: '/bopis', icon: 'ShoppingBag' },
        { id: 'bopis-owner', label: 'BOPIS Owner', path: '/bopis-owner', icon: 'ShoppingBag' },
        { id: 'bopis-app', label: 'BOPIS App', path: '/bopis-app', icon: 'ShoppingBag' },
        { id: 'pre-orders', label: 'Pre-Orders', path: '/pre-orders', icon: 'Calendar' },
        { id: 'atp-rules', label: 'ATP Rules', path: '/atp-rules', icon: 'Gauge' },
        { id: 'endless-aisle', label: 'Endless Aisle', path: '/endless-aisle', icon: 'ShoppingCart' },
      ]
    },
    // ──────────────────────────────────────────────
    // 7. WAREHOUSE OPERATIONS
    // ──────────────────────────────────────────────
    {
      id: 'warehouse-group', label: 'Warehouse Operations', path: '/warehouse-dashboard', icon: 'Building2',
      children: [
        { id: 'warehouse-dashboard', label: 'WH Dashboard', path: '/warehouse-dashboard', icon: 'BarChart3' },
        { id: 'warehouse', label: 'Warehouse Mgmt', path: '/warehouse', icon: 'Building2' },
        { id: 'slotting-optimization', label: 'Slotting Optimization', path: '/slotting-optimization', icon: 'Target' },
        { id: 'yard-dock', label: 'Yard & Dock', path: '/yard-dock', icon: 'Truck' },
        { id: 'automation-systems', label: 'Automation Systems', path: '/automation-systems', icon: 'Bot' },
      ]
    },
    // ──────────────────────────────────────────────
    // 8. PRODUCT MANAGEMENT
    // ──────────────────────────────────────────────
    {
      id: 'product-management', label: 'Product Management', path: '/products', icon: 'Tags',
      children: [
        { id: 'products', label: 'Products', path: '/products', icon: 'Tags' },
      ]
    },
    // ──────────────────────────────────────────────
    // 9. CUSTOMER MANAGEMENT
    // ──────────────────────────────────────────────
    {
      id: 'customer-management', label: 'Customer Management', path: '/customers', icon: 'Users',
      children: [
        { id: 'customers', label: 'Customers', path: '/customers', icon: 'Users' },
      ]
    },
    // ──────────────────────────────────────────────
    // 10. PROCUREMENT
    // ──────────────────────────────────────────────
    {
      id: 'procurement-group', label: 'Procurement', path: '/procurement', icon: 'ShoppingCart',
      children: [
        { id: 'procurement', label: 'Procurement', path: '/procurement', icon: 'ShoppingCart' },
        { id: 'purchase-requests', label: 'Purchase Requests', path: '/procurement/requests', icon: 'FileText' },
        { id: 'suppliers', label: 'Suppliers', path: '/procurement/suppliers', icon: 'Building' },
      ]
    },
    // ──────────────────────────────────────────────
    // 11. SUPPLY CHAIN & LOGISTICS
    // ──────────────────────────────────────────────
    {
      id: 'supply-chain-group', label: 'Supply Chain & Logistics', path: '/carriers', icon: 'Ship',
      children: [
        { id: 'carriers', label: 'Carriers', path: '/carriers', icon: 'Ship' },
        { id: 'rate-shopping', label: 'Rate Shopping', path: '/rate-shopping', icon: 'TrendingDown' },
        { id: 'routing', label: 'Routing Rules', path: '/routing-rules', icon: 'GitBranch' },
      ]
    },
    // ──────────────────────────────────────────────
    // 12. FINANCE
    // ──────────────────────────────────────────────
    {
      id: 'finance-group', label: 'Finance', path: '/invoices', icon: 'Receipt',
      children: [
        { id: 'invoices', label: 'Invoices', path: '/invoices', icon: 'Receipt' },
        { id: 'payments', label: 'Payments', path: '/payments', icon: 'CreditCard' },
        { id: 'manifest', label: 'Manifests', path: '/manifest', icon: 'ClipboardList' },
      ]
    },
    // ──────────────────────────────────────────────
    // 13. INTELLIGENCE & AI
    // ──────────────────────────────────────────────
    {
      id: 'ai-group', label: 'Intelligence & AI', path: '/ai', icon: 'Brain',
      children: [
        { id: 'ai', label: 'AI Control Center', path: '/ai', icon: 'Brain' },
        { id: 'ai-platform', label: 'AI Platform', path: '/ai-platform', icon: 'Cpu' },
        { id: 'ai-briefing', label: 'AI Briefing', path: '/ai-briefing', icon: 'Brain' },
        { id: 'ai-routing', label: 'AI Routing', path: '/ai-routing', icon: 'GitBranch' },
        { id: 'ai-packing', label: 'AI Packing', path: '/ai-packing', icon: 'PackagePlus' },
        { id: 'ai-loading', label: 'AI Loading', path: '/ai-loading', icon: 'Truck' },
        { id: 'ai-audit', label: 'AI Audit Trail', path: '/ai-audit', icon: 'Shield' },
        { id: 'ai-forecasting', label: 'AI Forecasting', path: '/ai-forecasting', icon: 'BarChart3' },
        { id: 'experiments', label: 'AI Experiments', path: '/experiments', icon: 'FlaskConical' },
        { id: 'analytics', label: 'Analytics', path: '/analytics', icon: 'BarChart3' },
      ]
    },
    // ──────────────────────────────────────────────
    // 14. INTEGRATIONS
    // ──────────────────────────────────────────────
    {
      id: 'integrations-group', label: 'Integrations', path: '/integration-hub', icon: 'Zap',
      children: [
        { id: 'integration-hub', label: 'Integration Hub', path: '/integration-hub', icon: 'Zap' },
        { id: 'edi', label: 'EDI Automation', path: '/edi', icon: 'Upload' },
        { id: 'import-export', label: 'Import/Export', path: '/import-export', icon: 'Upload' },
        { id: 'b2b-portal', label: 'B2B Portal', path: '/b2b-portal', icon: 'ShoppingCart' },
        { id: 'email-parser', label: 'Email Order Parser', path: '/email-parser', icon: 'Mail' },
        { id: 'documents', label: 'Documents', path: '/documents', icon: 'FileText' },
        { id: 'bigcommerce', label: 'BigCommerce', path: '/integrations/bigcommerce', icon: 'Store' },
        { id: 'amazon', label: 'Amazon', path: '/integrations/amazon', icon: 'ShoppingCart' },
        { id: 'ebay', label: 'eBay', path: '/integrations/ebay', icon: 'ShoppingBag' },
        { id: 'walmart', label: 'Walmart', path: '/integrations/walmart', icon: 'Globe' },
        { id: 'marketplace-hub', label: 'Marketplace Hub', path: '/integrations/marketplace', icon: 'Globe' },
      ]
    },
    // ──────────────────────────────────────────────
    // 15. ADMINISTRATION
    // ──────────────────────────────────────────────
    {
      id: 'admin-group', label: 'Administration', path: '/settings', icon: 'Settings',
      children: [
        { id: 'users', label: 'Users & Roles', path: '/users', icon: 'UserCog' },
        { id: 'settings', label: 'Settings', path: '/settings', icon: 'Settings' },
        { id: 'audit', label: 'Audit & Compliance', path: '/audit', icon: 'Shield' },
        { id: 'workflows', label: 'Workflows', path: '/workflows', icon: 'GitBranch' },
        { id: 'promotions', label: 'Promotions', path: '/promotions', icon: 'Tags' },
        { id: 'task-queues', label: 'Task Queues', path: '/task-queues', icon: 'AlertTriangle' },
        { id: 'report-builder', label: 'Report Builder', path: '/report-builder', icon: 'BarChart3' },
      ]
    },
  ]

  const roleModules: Record<string, string[]> = {
    ADMIN: groups.flatMap(g => [g.id, ...(g.children?.map(c => c.id) || [])]),
    CEO: [
      'launch-pad', 'analytics-dashboard', 'dashboard', 'notifications',
      'ai', 'ai-briefing', 'ai-routing', 'ai-packing', 'ai-loading', 'ai-audit', 'ai-forecasting',
      'experiments', 'analytics',
      'audit', 'settings', 'promotions', 'documents', 'report-builder',
      'endless-aisle', 'products',
    ],
    OPS_MANAGER: [
      'launch-pad', 'analytics-dashboard', 'dashboard', 'notifications',
      'orders', 'find-order', 'create-order', 'order-approvals', 'order-routing',
      'brokering', 'rejections', 'transfers', 'fulfillment-limits',
      'fulfillment', 'picking', 'packing', 'shipping',
      'wave-planning', 'labor-management', 'pickers', 'label-printing',
      'inventory', 'inventory-enhanced', 'receiving', 'cycle-counts', 'replenishment',
      'returns', 'returns-enhanced', 'freight-audit',
      'warehouse-dashboard', 'warehouse', 'slotting-optimization', 'yard-dock', 'automation-systems',
      'products', 'customers',
      'procurement', 'purchase-requests', 'suppliers',
      'carriers', 'rate-shopping', 'routing',
      'invoices', 'payments', 'manifest',
      'integration-hub', 'edi', 'import-export', 'b2b-portal',
      'email-parser', 'documents',
      'bigcommerce', 'amazon', 'ebay', 'walmart', 'marketplace-hub',
      'users', 'settings', 'audit', 'workflows', 'promotions',
      'task-queues', 'report-builder',
      'ai-briefing', 'ai-routing', 'ai-audit', 'ai-forecasting', 'analytics',
      'endless-aisle',
    ],
    WAREHOUSE_MANAGER: [
      'launch-pad', 'analytics-dashboard', 'dashboard', 'notifications',
      'warehouse-dashboard', 'warehouse',
      'inventory', 'inventory-enhanced', 'receiving', 'cycle-counts', 'replenishment',
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
      'store-dashboard', 'stores', 'bopis', 'bopis-owner', 'bopis-app',
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
      'orders', 'find-order', 'order-approvals',
      'customers',
      'returns', 'returns-enhanced',
      'products',
      'brokering', 'rejections',
      'promotions',
    ],
    PROCUREMENT_MANAGER: [
      'launch-pad', 'analytics-dashboard', 'dashboard', 'notifications',
      'procurement', 'purchase-requests', 'suppliers',
      'invoices',
      'analytics',
    ],
    FINANCE: [
      'launch-pad', 'analytics-dashboard', 'dashboard', 'notifications',
      'invoices', 'payments', 'manifest',
      'returns', 'freight-audit',
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

  return groups
    .filter(g => g.children?.some(c => allowedIds.includes(c.id)))
    .map(g => ({
      ...g,
      children: g.children?.filter(c => allowedIds.includes(c.id)),
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
