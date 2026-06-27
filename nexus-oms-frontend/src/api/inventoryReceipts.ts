import client from './client'
import { ApiResponse, InventoryReceipt, PaginationParams } from '../types'

export async function getReceipts(params?: PaginationParams & { status?: string }): Promise<ApiResponse<InventoryReceipt[]>> {
  const { data } = await client.get('/inventory-receipts', { params })
  return data
}

export async function getReceipt(id: string): Promise<ApiResponse<InventoryReceipt>> {
  const { data } = await client.get(`/inventory-receipts/${id}`)
  return data
}

export async function createReceipt(receipt: Partial<InventoryReceipt>): Promise<ApiResponse<InventoryReceipt>> {
  const { data } = await client.post('/inventory-receipts', receipt)
  return data
}

export async function receiveInventory(id: string, receivedBy?: string): Promise<ApiResponse<InventoryReceipt>> {
  const { data } = await client.post(`/inventory-receipts/${id}/receive`, null, { params: { receivedBy } })
  return data
}
