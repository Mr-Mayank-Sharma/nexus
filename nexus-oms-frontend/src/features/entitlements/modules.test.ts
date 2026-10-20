import { readFileSync } from 'fs'
import { join } from 'path'
import { describe, expect, it } from 'vitest'
import { moduleForRoute } from './modules'

describe('moduleForRoute', () => {
  it('maps routes to modules with the longest prefix', () => {
    expect(moduleForRoute('/orders/123')).toBe('OMS')
    expect(moduleForRoute('/inventory')).toBe('PLATFORM')
    expect(moduleForRoute('/inventory/receiving')).toBe('WMS')
    expect(moduleForRoute('/returns-finance')).toBe('OMS')
    expect(moduleForRoute('/ai-platform/models')).toBe('AI')
    expect(moduleForRoute('/packer')).toBe('WMS')
    expect(moduleForRoute('/pack')).toBe('WMS')
    expect(moduleForRoute('/products')).toBe('PLATFORM')
  })

  it('classifies every route the app declares', () => {
    // Shared pages: everything else must belong to a module so it hides with the plan.
    const platform = new Set(['/analytics-dashboard', '/dashboard', '/account/security', '/inventory',
      '/integrations/bigcommerce', '/integrations/stores', '/integrations/directory', '/integrations', '/integration-hub', '/sync', '/import-export', '/jobs', '/generic-connections',
      '/notifications', '/procurement', '/invoices', '/workflows', '/documents', '/users', '/customers',
      '/products', '/stores', '/audit', '/analytics', '/settings', '/inventory/enhanced', '/integrations/amazon',
      '/integrations/shopify', '/integrations/ebay', '/integrations/walmart', '/integrations/marketplace',
      '/report-builder', '/payments', '/transfers', '/replenishment'])
    const app = readFileSync(join(__dirname, '../../App.tsx'), 'utf8')
    const routes = [...app.matchAll(/<Route\s+path="([^"]+)"/g)].map(m => '/' + m[1].replace(/^\//, '').replace(/\/(\*|:id)$/, ''))
      .filter(p => p !== '/login' && p !== '/*' && p !== '/')
    const unclassified = [...new Set(routes)].filter(p => moduleForRoute(p) === 'PLATFORM' && !platform.has(p))
    expect(unclassified).toEqual([])
  })
})
