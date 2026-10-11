import client from '../../api/client'

export interface ChannelMapping {
  nodeId: string
  externalLocationId: string
  externalLocationName?: string | null
  enabled: boolean
  bufferUnits: number
  contributionCap: number | null
}

export interface ShopifyLocation { id: string; name: string; active: boolean }

export type RunStatus = 'RUNNING' | 'COMPLETED' | 'PARTIAL' | 'FAILED' | 'BLOCKED'
export type LineOutcome = 'PLANNED' | 'PUBLISHED' | 'UNCHANGED' | 'SKIPPED' | 'FAILED'

export interface PublishRun {
  id: string
  status: RunStatus
  dryRun: boolean
  triggeredBy: string | null
  startedAt: string
  completedAt: string | null
  itemsPlanned: number
  itemsPublished: number
  itemsUnchanged: number
  itemsFailed: number
  itemsSkipped: number
  message: string | null
}

export interface PublishLine {
  id: string
  sku: string
  externalLocationId: string
  calculatedQty: number
  channelQtyBefore: number | null
  publishedQty: number | null
  outcome: LineOutcome
  drift: boolean
  calculation: string | null
  error: string | null
}

/** One node's contribution to a line, as recorded by the server (parsed from `calculation`). */
export interface Contribution {
  nodeName: string | null
  onHand: number
  allocated: number
  reserved: number
  damaged: number
  safetyStock: number
  buffer: number
  expiredLotUnits: number
  sellable: number
  cappedTo: number | null
  contributed: number
  excluded: string | null
}

export function parseCalculation(json: string | null): Contribution[] {
  if (!json) return []
  try {
    const parsed = JSON.parse(json)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

async function call<T>(method: 'get' | 'post' | 'put', url: string, body?: unknown, params?: Record<string, unknown>): Promise<T> {
  try {
    const { data } = await client.request({ method, url, data: body, params })
    if (data && data.success === false) throw new Error(data.message || 'Request failed')
    return data.data as T
  } catch (err: any) {
    throw new Error(err?.response?.data?.message || err?.message || 'Request failed')
  }
}

const base = (storeId: string) => `/shopify/stores/${storeId}/inventory`

export const getMappings = (storeId: string) => call<ChannelMapping[]>('get', `${base(storeId)}/location-mappings`)
export const saveMappings = (storeId: string, mappings: ChannelMapping[]) => call<ChannelMapping[]>('put', `${base(storeId)}/location-mappings`, mappings)
export const getShopifyLocations = (storeId: string) => call<ShopifyLocation[]>('get', `${base(storeId)}/shopify-locations`)
export const publish = (storeId: string, dryRun: boolean) => call<PublishRun>('post', `${base(storeId)}/publish`, undefined, { dryRun })
export const getRuns = (storeId: string) => call<PublishRun[]>('get', `${base(storeId)}/runs`)
export const getRunLines = (storeId: string, runId: string) => call<PublishLine[]>('get', `${base(storeId)}/runs/${runId}/lines`, undefined, { limit: 2000 })
