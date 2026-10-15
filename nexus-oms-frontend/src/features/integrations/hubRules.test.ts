import { describe, expect, it } from 'vitest'
import { groupPlatforms, isSecretField, jobView, requiredFields, verificationView } from './hubRules'
import type { Connection, HubJob, Platform } from './hubApi'

const conn = (v: Connection['verification'], extra: Partial<Connection> = {}): Connection =>
  ({ id: '1', platform: 'STRIPE', name: 'Stripe', category: 'Payments', supportedSyncTypes: [], verification: v, ...extra })
const job = (status: string, extra: Partial<HubJob> = {}): HubJob =>
  ({ jobId: 'j', connectorId: '1', syncType: 'ORDER_IMPORT', status, itemsSucceeded: 0, itemsFailed: 0, startedAt: '', ...extra })

describe('hubRules', () => {
  it('a saved but untested connection is never shown as working', () => {
    expect(verificationView(conn('UNTESTED')).tone).toBe('muted')
    expect(verificationView(conn('UNTESTED')).label).toBe('Never tested')
  })

  it('a failing connection shows the platform\'s reason', () => {
    const v = verificationView(conn('FAILING', { lastTestMessage: '401 from the platform' }))
    expect(v).toMatchObject({ label: 'Failing', tone: 'bad', detail: '401 from the platform' })
  })

  it('only a passed test is verified', () => {
    expect(verificationView(conn('VERIFIED', { lastTestAt: '2026-10-15T10:00:00' })).tone).toBe('good')
  })

  it('a preview is reported as nothing imported, with its explanation', () => {
    const v = jobView(job('NOT_SUPPORTED', { message: 'Magento returned 3 orders, but nothing was imported into Nexus' }))
    expect(v.label).toBe('Nothing imported')
    expect(v.tone).toBe('warn')
    expect(v.detail).toContain('3 orders')
  })

  it('a failure carries its reason; a done run reports counts', () => {
    expect(jobView(job('FAILED', { error: 'charge_id required' })).detail).toBe('charge_id required')
    expect(jobView(job('COMPLETED', { itemsSucceeded: 4, itemsFailed: 1 })).detail).toBe('4 succeeded, 1 failed')
  })

  it('asks only for what the connector uses, addresses first and secrets last', () => {
    expect(requiredFields({ requiredSettings: ['secret_key'] } as Platform)).toEqual(['secret_key'])          // Stripe has no address field
    expect(requiredFields({ requiredSettings: ['access_token', 'shop_domain'] } as Platform)).toEqual(['shop_domain', 'access_token'])
    expect(requiredFields({ requiredSettings: [] } as unknown as Platform)).toEqual(['base_url', 'api_key'])  // generic platform
    expect(requiredFields({} as Platform)).toEqual(['base_url', 'api_key'])
    expect(isSecretField('api_key')).toBe(true)
    expect(isSecretField('client_secret')).toBe(true)
    expect(isSecretField('base_url')).toBe(false)
  })

  it('groups platforms by category, alphabetically', () => {
    const g = groupPlatforms([{ name: 'B', category: 'Shipping' }, { name: 'A', category: 'Shipping' }, { name: 'Z', category: 'CRM' }] as Platform[])
    expect(g.map(x => x.category)).toEqual(['CRM', 'Shipping'])
    expect(g[1].platforms.map(p => p.name)).toEqual(['A', 'B'])
  })
})
