import client from './client'
import { ApiResponse, IntegrationStore, IntegrationStoreSetting, SyncTypeStatus, StoreSyncStatus } from '../types'
export type { IntegrationStore, IntegrationStoreSetting, SyncTypeStatus, StoreSyncStatus }

export async function getStores(platform?: string): Promise<ApiResponse<IntegrationStore[]>> {
  try {
    const { data } = await client.get('/integration-stores', { params: { platform } })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get stores'
    return { success: false, error: msg } as any
  }
}

export async function getStore(id: string): Promise<ApiResponse<IntegrationStore>> {
  try {
    const { data } = await client.get(`/integration-stores/${id}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get store'
    return { success: false, error: msg } as any
  }
}

export async function createStore(store: Record<string, any>): Promise<ApiResponse<IntegrationStore>> {
  try {
    const { data } = await client.post('/integration-stores', store)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to create store'
    return { success: false, error: msg } as any
  }
}

export async function updateStore(id: string, store: Record<string, any>): Promise<ApiResponse<IntegrationStore>> {
  try {
    const { data } = await client.put(`/integration-stores/${id}`, store)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to update store'
    return { success: false, error: msg } as any
  }
}

export async function deleteStore(id: string): Promise<ApiResponse<null>> {
  try {
    const { data } = await client.delete(`/integration-stores/${id}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to delete store'
    return { success: false, error: msg } as any
  }
}

export async function getStoreSettings(id: string): Promise<ApiResponse<IntegrationStoreSetting[]>> {
  try {
    const { data } = await client.get(`/integration-stores/${id}/settings`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get store settings'
    return { success: false, error: msg } as any
  }
}

export async function getStoreSyncStatus(id: string): Promise<ApiResponse<StoreSyncStatus>> {
  try {
    const { data } = await client.get(`/integration-stores/${id}/sync-status`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get store sync status'
    return { success: false, error: msg } as any
  }
}

// Shopify-specific
export async function shopifySyncOrders(storeId: string): Promise<ApiResponse<any>> {
  try {
    const { data } = await client.post(`/shopify/stores/${storeId}/sync/orders`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to sync orders'
    return { success: false, error: msg } as any
  }
}

export async function shopifySyncProducts(storeId: string): Promise<ApiResponse<any>> {
  try {
    const { data } = await client.post(`/shopify/stores/${storeId}/sync/products`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to sync products'
    return { success: false, error: msg } as any
  }
}

export async function shopifyPushInventory(storeId: string): Promise<ApiResponse<any>> {
  try {
    const { data } = await client.post(`/shopify/stores/${storeId}/sync/inventory`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to push inventory'
    return { success: false, error: msg } as any
  }
}

export async function shopifyPushFulfillments(storeId: string): Promise<ApiResponse<any>> {
  try {
    const { data } = await client.post(`/shopify/stores/${storeId}/sync/fulfillments`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to push fulfillments'
    return { success: false, error: msg } as any
  }
}

export async function shopifyPushRefunds(storeId: string): Promise<ApiResponse<any>> {
  try {
    const { data } = await client.post(`/shopify/stores/${storeId}/sync/refunds`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to push refunds'
    return { success: false, error: msg } as any
  }
}

export async function shopifyRegisterWebhooks(storeId: string, baseUrl: string): Promise<ApiResponse<null>> {
  try {
    const { data } = await client.post(`/shopify/stores/${storeId}/webhooks/register`, null, { params: { baseUrl } })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to register webhooks'
    return { success: false, error: msg } as any
  }
}
