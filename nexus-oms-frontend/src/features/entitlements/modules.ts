/**
 * Sellable modules, mirroring com.nexus.oms.entitlement.ProductModule. The backend enforces the
 * plan on every API call; this map only decides what the navigation shows.
 */
export type ProductModule = 'PLATFORM' | 'OMS' | 'WMS' | 'THREE_PL' | 'EDI' | 'AI'

export const MODULE_LABELS: Record<ProductModule, string> = {
  PLATFORM: 'Platform',
  OMS: 'Order Management',
  WMS: 'Warehouse Management',
  THREE_PL: '3PL Billing & Client Portal',
  EDI: 'EDI',
  AI: 'AI & Intelligence',
}

/** UI route prefix → module. Longest prefix wins; unlisted routes are PLATFORM. */
const ROUTE_PREFIXES: Record<string, ProductModule> = {
  // OMS
  '/orders': 'OMS', '/order-twin': 'OMS', '/order-approvals': 'OMS', '/order-routing': 'OMS',
  '/routing-rules': 'OMS', '/brokering': 'OMS', '/rejections': 'OMS', '/fulfillment-limits': 'OMS',
  '/reconciliation': 'OMS', '/store-operations': 'OMS', '/order-replay': 'OMS', '/returns': 'OMS', '/returns-enhanced': 'OMS', '/returns-finance': 'OMS',
  '/store-dashboard': 'OMS', '/bopis': 'OMS', '/bopis-owner': 'OMS', '/bopis-app': 'OMS',
  '/pre-orders': 'OMS', '/atp-rules': 'OMS', '/endless-aisle': 'OMS', '/promotions': 'OMS',
  '/email-parser': 'OMS', '/b2b-portal': 'OMS',
  // WMS
  '/fulfillment': 'WMS', '/picking': 'WMS', '/packing': 'WMS', '/shipping': 'WMS', '/wave-planning': 'WMS',
  '/pickers': 'WMS', '/packer': 'WMS', '/loader': 'WMS', '/label-printing': 'WMS',
  '/inventory/receiving': 'WMS', '/inventory/cycle-counts': 'WMS', '/inventory/rfid': 'WMS',
  '/warehouse-twin': 'WMS', '/warehouse-dashboard': 'WMS', '/warehouse': 'WMS',
  '/slotting-optimization': 'WMS', '/yard-dock': 'WMS', '/automation-systems': 'WMS',
  '/labor-management': 'WMS', '/task-queues': 'WMS', '/carriers': 'WMS', '/rate-shopping': 'WMS',
  '/freight-audit': 'WMS', '/manifest': 'WMS',
  '/rf': 'WMS', '/pick': 'WMS', '/pack': 'WMS', '/receive': 'WMS', '/ship': 'WMS', '/count': 'WMS', '/scan': 'WMS',
  // Add-ons
  '/rate-cards': 'THREE_PL', '/billing-statements': 'THREE_PL', '/client-portal': 'THREE_PL',
  '/edi': 'EDI',
  '/ai': 'AI', '/ai-platform': 'AI', '/ai-briefing': 'AI', '/ai-routing': 'AI', '/ai-packing': 'AI',
  '/ai-loading': 'AI', '/ai-audit': 'AI', '/ai-forecasting': 'AI', '/mcp-server': 'AI', '/experiments': 'AI',
}

const BY_LENGTH = Object.entries(ROUTE_PREFIXES).sort((a, b) => b[0].length - a[0].length)

export function moduleForRoute(path: string): ProductModule {
  for (const [prefix, module] of BY_LENGTH) {
    if (path === prefix || path.startsWith(prefix + '/')) return module
  }
  return 'PLATFORM'
}
