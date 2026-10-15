import client from '../../api/client'

export type Evidence = 'TESTED' | 'CODE_ONLY' | 'NOT_IMPLEMENTED'
export type Tier = 'NATIVE' | 'CONNECTOR' | 'GENERIC'

export interface Capability { name: string; evidence: Evidence; liveVerified: boolean; note: string }
export interface CatalogEntry {
  key: string; name: string; group: string; tier: Tier; route: string; summary: string
  capabilities: Capability[]
}
export interface Catalog {
  groupOrder: string[]
  entries: CatalogEntry[]
  counts: { native: number; connector: number; generic: number; total: number }
  evidenceLegend: Record<Evidence, string>
  liveVerifiedNote: string
}

export interface ConnectionHealth {
  connections: number; verified: number; failing: number; untested: number
  connectors: { id: string; name: string; platform: string; state: 'VERIFIED' | 'FAILING' | 'UNTESTED'; lastTestAt?: string; lastTestMessage?: string }[]
}

export async function getCatalog(): Promise<Catalog> {
  return (await client.get('/integration-hub/catalog')).data
}

export async function getConnectionHealth(): Promise<ConnectionHealth> {
  return (await client.get('/integration-hub/health')).data
}
