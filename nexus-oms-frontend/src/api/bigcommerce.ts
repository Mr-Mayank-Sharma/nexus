import client from './client'
import { ApiResponse, SyncLog, PaginationParams, BigCommerceConfig, SyncResult } from '../types'
export type { BigCommerceConfig, SyncResult }

export async function getConfig(): Promise<ApiResponse<BigCommerceConfig>> {
  try {
    const { data } = await client.get('/integrations/bigcommerce/config')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get config'
    return { success: false, error: msg } as any
  }
}

export async function updateConfig(config: Partial<BigCommerceConfig>): Promise<ApiResponse<BigCommerceConfig>> {
  try {
    const { data } = await client.put('/integrations/bigcommerce/config', config)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to update config'
    return { success: false, error: msg } as any
  }
}

export async function syncOrders(): Promise<ApiResponse<SyncResult>> {
  try {
    const { data } = await client.post('/integrations/bigcommerce/sync/orders')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to sync orders'
    return { success: false, error: msg } as any
  }
}

export async function syncProducts(): Promise<ApiResponse<SyncResult>> {
  try {
    const { data } = await client.post('/integrations/bigcommerce/sync/products')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to sync products'
    return { success: false, error: msg } as any
  }
}

export async function pushInventory(): Promise<ApiResponse<SyncResult>> {
  try {
    const { data } = await client.post('/integrations/bigcommerce/sync/inventory')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to push inventory'
    return { success: false, error: msg } as any
  }
}

export async function pushShipments(): Promise<ApiResponse<SyncResult>> {
  try {
    const { data } = await client.post('/integrations/bigcommerce/sync/shipments')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to push shipments'
    return { success: false, error: msg } as any
  }
}

export async function pushRefunds(): Promise<ApiResponse<SyncResult>> {
  try {
    const { data } = await client.post('/integrations/bigcommerce/sync/refunds')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to push refunds'
    return { success: false, error: msg } as any
  }
}

export async function getSyncLogs(params?: PaginationParams): Promise<ApiResponse<SyncLog[]>> {
  try {
    const { data } = await client.get('/integrations/bigcommerce/sync-logs', { params })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get sync logs'
    return { success: false, error: msg } as any
  }
}

export async function registerWebhooks(baseUrl: string): Promise<ApiResponse<null>> {
  try {
    const { data } = await client.post('/integrations/bigcommerce/webhooks/register', null, { params: { baseUrl } })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to register webhooks'
    return { success: false, error: msg } as any
  }
}
