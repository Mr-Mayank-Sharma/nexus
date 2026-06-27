import client from './client'
import { ApiResponse } from '../types'

export interface IntegrationStore {
  id: string
  tenantId: string
  storeCode: string
  storeName: string
  platform: string
  platformType?: string
  status: string
  currency: string
  defaultLocale: string
  timezone: string
  externalStoreId?: string
  externalDomain?: string
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface IntegrationStoreSetting {
  id: string
  storeId: string
  settingType: string
  settingValue?: string
  description?: string
  isEncrypted: boolean
}

export interface SyncTypeStatus {
  syncType: string
  enabled: boolean
  intervalMinutes: number
  lastSyncAt?: string
  lastSyncStatus?: string
  lastSyncMessage?: string
}

export interface StoreSyncStatus {
  storeId: string
  storeCode: string
  storeName: string
  platform: string
  connected: boolean
  syncTypes: SyncTypeStatus[]
  lastError?: string
}

export async function getStores(platform?: string): Promise<ApiResponse<IntegrationStore[]>> {
  const { data } = await client.get('/integration-stores', { params: { platform } })
  return data
}

export async function getStore(id: string): Promise<ApiResponse<IntegrationStore>> {
  const { data } = await client.get(`/integration-stores/${id}`)
  return data
}

export async function createStore(store: Record<string, any>): Promise<ApiResponse<IntegrationStore>> {
  const { data } = await client.post('/integration-stores', store)
  return data
}

export async function updateStore(id: string, store: Record<string, any>): Promise<ApiResponse<IntegrationStore>> {
  const { data } = await client.put(`/integration-stores/${id}`, store)
  return data
}

export async function deleteStore(id: string): Promise<ApiResponse<null>> {
  const { data } = await client.delete(`/integration-stores/${id}`)
  return data
}

export async function getStoreSettings(id: string): Promise<ApiResponse<IntegrationStoreSetting[]>> {
  const { data } = await client.get(`/integration-stores/${id}/settings`)
  return data
}

export async function getStoreSyncStatus(id: string): Promise<ApiResponse<StoreSyncStatus>> {
  const { data } = await client.get(`/integration-stores/${id}/sync-status`)
  return data
}

// Shopify-specific
export async function shopifySyncOrders(storeId: string): Promise<ApiResponse<any>> {
  const { data } = await client.post(`/shopify/stores/${storeId}/sync/orders`)
  return data
}

export async function shopifySyncProducts(storeId: string): Promise<ApiResponse<any>> {
  const { data } = await client.post(`/shopify/stores/${storeId}/sync/products`)
  return data
}

export async function shopifyPushInventory(storeId: string): Promise<ApiResponse<any>> {
  const { data } = await client.post(`/shopify/stores/${storeId}/sync/inventory`)
  return data
}

export async function shopifyPushFulfillments(storeId: string): Promise<ApiResponse<any>> {
  const { data } = await client.post(`/shopify/stores/${storeId}/sync/fulfillments`)
  return data
}

export async function shopifyPushRefunds(storeId: string): Promise<ApiResponse<any>> {
  const { data } = await client.post(`/shopify/stores/${storeId}/sync/refunds`)
  return data
}

export async function shopifyRegisterWebhooks(storeId: string, baseUrl: string): Promise<ApiResponse<null>> {
  const { data } = await client.post(`/shopify/stores/${storeId}/webhooks/register`, null, { params: { baseUrl } })
  return data
}
