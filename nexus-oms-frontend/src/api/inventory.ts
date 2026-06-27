import client from './client'
import { ApiResponse, Inventory, InventoryFilters } from '../types'

export async function getInventory(params?: InventoryFilters): Promise<ApiResponse<Inventory[]>> {
  const { data } = await client.get('/inventory', { params })
  return data
}

export async function getInventoryBySku(sku: string): Promise<ApiResponse<Inventory>> {
  const { data } = await client.get(`/inventory/${sku}`)
  return data
}

export async function getAtp(sku: string): Promise<ApiResponse<{ sku: string; atp: number }>> {
  const { data } = await client.get('/inventory/atp', { params: { sku } })
  return data
}

export async function adjustInventory(id: string, quantityChange: number): Promise<ApiResponse<Inventory>> {
  const { data } = await client.put('/inventory/adjust', { id, quantityChange })
  return data
}
