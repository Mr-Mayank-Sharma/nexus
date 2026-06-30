import client from './client'
import { ApiResponse, Picklist, PicklistItem } from '../types'

export async function getPicklists(status?: string): Promise<ApiResponse<Picklist[]>> {
  try {
    const params = status ? { status } : {}
    const { data } = await client.get('/picking/picklists', { params })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get picklists'
    return { success: false, error: msg } as any
  }
}

export async function getPicklist(id: string): Promise<ApiResponse<Picklist>> {
  try {
    const { data } = await client.get(`/picking/picklists/${id}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get picklist'
    return { success: false, error: msg } as any
  }
}

export async function getPicklistItems(id: string): Promise<ApiResponse<PicklistItem[]>> {
  try {
    const { data } = await client.get(`/picking/picklists/${id}/items`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get picklist items'
    return { success: false, error: msg } as any
  }
}

export async function createPicklist(payload: Partial<Picklist>): Promise<ApiResponse<Picklist>> {
  try {
    const { data } = await client.post('/picking/picklists', payload)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to create picklist'
    return { success: false, error: msg } as any
  }
}

export async function assignPicker(picklistId: string, staffId: string): Promise<ApiResponse<Picklist>> {
  try {
    const { data } = await client.post(`/picking/picklists/${picklistId}/assign`, null, { params: { staffId } })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to assign picker'
    return { success: false, error: msg } as any
  }
}

export async function pickItem(itemId: string, staffId: string): Promise<ApiResponse<PicklistItem>> {
  try {
    const { data } = await client.post(`/picking/items/${itemId}/pick`, null, { params: { staffId } })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to pick item'
    return { success: false, error: msg } as any
  }
}

export async function completePicklist(id: string): Promise<ApiResponse<Picklist>> {
  try {
    const { data } = await client.post(`/picking/picklists/${id}/complete`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to complete picklist'
    return { success: false, error: msg } as any
  }
}

export async function cancelPicklist(id: string): Promise<ApiResponse<Picklist>> {
  try {
    const { data } = await client.post(`/picking/picklists/${id}/cancel`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to cancel picklist'
    return { success: false, error: msg } as any
  }
}

export async function getPickingKPIs(): Promise<ApiResponse<Record<string, number>>> {
  try {
    const { data } = await client.get('/picking/kpis')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get picking KPIs'
    return { success: false, error: msg } as any
  }
}
