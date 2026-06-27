import client from './client'
import { ApiResponse, SyncLog, PaginationParams } from '../types'

export interface BigCommerceConfig {
  id: string
  tenantId: string
  storeHash: string
  accessToken: string
  clientId?: string
  apiPath: string
  isActive: boolean
  autoSyncOrders: boolean
  autoSyncInventory: boolean
  syncIntervalMinutes: number
  lastOrderSyncAt?: string
  lastProductSyncAt?: string
  lastInventorySyncAt?: string
}

export interface SyncResult {
  syncLogId: string
  syncType: string
  status: string
  itemsProcessed: number
  itemsSucceeded: number
  itemsFailed: number
  message?: string
}

export async function getConfig(): Promise<ApiResponse<BigCommerceConfig>> {
  const { data } = await client.get('/integrations/bigcommerce/config')
  return data
}

export async function updateConfig(config: Partial<BigCommerceConfig>): Promise<ApiResponse<BigCommerceConfig>> {
  const { data } = await client.put('/integrations/bigcommerce/config', config)
  return data
}

export async function syncOrders(): Promise<ApiResponse<SyncResult>> {
  const { data } = await client.post('/integrations/bigcommerce/sync/orders')
  return data
}

export async function syncProducts(): Promise<ApiResponse<SyncResult>> {
  const { data } = await client.post('/integrations/bigcommerce/sync/products')
  return data
}

export async function pushInventory(): Promise<ApiResponse<SyncResult>> {
  const { data } = await client.post('/integrations/bigcommerce/sync/inventory')
  return data
}

export async function pushShipments(): Promise<ApiResponse<SyncResult>> {
  const { data } = await client.post('/integrations/bigcommerce/sync/shipments')
  return data
}

export async function pushRefunds(): Promise<ApiResponse<SyncResult>> {
  const { data } = await client.post('/integrations/bigcommerce/sync/refunds')
  return data
}

export async function getSyncLogs(params?: PaginationParams): Promise<ApiResponse<SyncLog[]>> {
  const { data } = await client.get('/integrations/bigcommerce/sync-logs', { params })
  return data
}

export async function registerWebhooks(baseUrl: string): Promise<ApiResponse<null>> {
  const { data } = await client.post('/integrations/bigcommerce/webhooks/register', null, { params: { baseUrl } })
  return data
}
