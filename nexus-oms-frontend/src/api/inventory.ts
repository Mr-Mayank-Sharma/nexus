import client from './client'
import { ApiResponse, Inventory, InventoryFilters } from '../types'

export async function getInventory(params?: InventoryFilters): Promise<ApiResponse<Inventory[]>> {
  try {
    const { data } = await client.get('/inventory', { params })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get inventory'
    return { success: false, error: msg } as any
  }
}

export async function getInventoryBySku(sku: string): Promise<ApiResponse<Inventory>> {
  try {
    const { data } = await client.get(`/inventory/${sku}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get inventory by SKU'
    return { success: false, error: msg } as any
  }
}

export async function getAtp(sku: string): Promise<ApiResponse<{ sku: string; atp: number }>> {
  try {
    const { data } = await client.get('/inventory', { params: { sku } })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get ATP'
    return { success: false, error: msg } as any
  }
}

export async function adjustInventory(sku: string, quantity: number, reason: string): Promise<ApiResponse<Inventory>> {
  try {
    const { data } = await client.post('/inventory/adjust', { sku, quantity, reason })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to adjust inventory'
    return { success: false, error: msg } as any
  }
}
