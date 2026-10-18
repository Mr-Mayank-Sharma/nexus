import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import type { AxiosAdapter, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import client from '../../api/client'
import { getConnections, getPlatforms, testConnection } from './hubApi'
import { getCatalog, getConnectionHealth } from './catalogApi'

/**
 * The Directory and Connections screens crashed in a real browser while every unit test passed, because the tests
 * mocked the API module. The API client rewraps responses for the standard {success, data} envelope, and these
 * endpoints return the payload bare. This runs the real client, interceptor included, against a fake server.
 */
const bodies: Record<string, unknown> = {
  '/integration-hub/platforms': [{ platformType: 'STRIPE', name: 'Stripe' }],
  '/integration-hub/connectors': [{ id: 'c1', platform: 'STRIPE' }],
  '/integration-hub/catalog': { groupOrder: ['sales-channels'], entries: [{ key: 'SHOPIFY', capabilities: [] }], counts: { native: 1, connector: 0, generic: 0, total: 1 }, evidenceLegend: { TESTED: 't', CODE_ONLY: 'c', NOT_IMPLEMENTED: 'n' }, liveVerifiedNote: 'n' },
  '/integration-hub/health': { connections: 1, verified: 0, failing: 1, untested: 0, connectors: [{ id: 'c1', name: 'x', platform: 'STRIPE', state: 'FAILING' }] },
  '/integration-hub/connectors/c1/test': { success: false, connectionVerified: false, message: 'Stripe answered 401' },
}

describe('endpoints that return a bare payload', () => {
  const original = client.defaults.adapter
  beforeEach(() => {
    const fake: AxiosAdapter = async (config: InternalAxiosRequestConfig): Promise<AxiosResponse> => ({
      data: bodies[(config.url ?? '').replace(/\?.*$/, '')], status: 200, statusText: 'OK', headers: {}, config,
    })
    client.defaults.adapter = fake
  })
  afterEach(() => { client.defaults.adapter = original })

  it('platforms and connections arrive as the arrays the screens map over', async () => {
    expect(await getPlatforms()).toEqual([{ platformType: 'STRIPE', name: 'Stripe' }])
    expect(await getConnections()).toEqual([{ id: 'c1', platform: 'STRIPE' }])
  })

  it('the catalog arrives as the catalog, with its entries', async () => {
    const c = await getCatalog()
    expect(Array.isArray(c.entries)).toBe(true)
    expect(c.entries[0].key).toBe('SHOPIFY')
    expect(c.groupOrder).toEqual(['sales-channels'])
  })

  it('connection health arrives whole, so the sidebar badge can count failures', async () => {
    const h = await getConnectionHealth()
    expect(h.failing).toBe(1)
    expect(h.connectors[0].state).toBe('FAILING')
  })

  it('a failed connection test keeps its message and its success flag', async () => {
    const t = await testConnection('c1')
    expect(t.success).toBe(false)
    expect(t.message).toBe('Stripe answered 401')
    expect(t.connectionVerified).toBe(false)
  })
})
