import client from './client'
import { ApiResponse, InventoryReceipt, PaginationParams } from '../types'

export async function getReceipts(params?: PaginationParams & { status?: string }): Promise<ApiResponse<InventoryReceipt[]>> {
  try {
    const { data } = await client.get('/inventory-receipts', { params })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get receipts'
    return { success: false, error: msg } as any
  }
}

export async function getReceipt(id: string): Promise<ApiResponse<InventoryReceipt>> {
  try {
    const { data } = await client.get(`/inventory-receipts/${id}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get receipt'
    return { success: false, error: msg } as any
  }
}

export async function createReceipt(receipt: Partial<InventoryReceipt>): Promise<ApiResponse<InventoryReceipt>> {
  try {
    const { data } = await client.post('/inventory-receipts', receipt)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to create receipt'
    return { success: false, error: msg } as any
  }
}

export async function receiveInventory(id: string, receivedBy?: string): Promise<ApiResponse<InventoryReceipt>> {
  try {
    const { data } = await client.post(`/inventory-receipts/${id}/receive`, null, { params: { receivedBy } })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to receive inventory'
    return { success: false, error: msg } as any
  }
}
