import client from './client'
import { ApiResponse, Picklist, PicklistItem } from '../types'

export async function getPicklists(status?: string): Promise<ApiResponse<Picklist[]>> {
  const params = status ? { status } : {}
  const { data } = await client.get('/picking/picklists', { params })
  return data
}

export async function getPicklist(id: string): Promise<ApiResponse<Picklist>> {
  const { data } = await client.get(`/picking/picklists/${id}`)
  return data
}

export async function getPicklistItems(id: string): Promise<ApiResponse<PicklistItem[]>> {
  const { data } = await client.get(`/picking/picklists/${id}/items`)
  return data
}

export async function createPicklist(payload: Partial<Picklist>): Promise<ApiResponse<Picklist>> {
  const { data } = await client.post('/picking/picklists', payload)
  return data
}

export async function assignPicker(picklistId: string, staffId: string): Promise<ApiResponse<Picklist>> {
  const { data } = await client.post(`/picking/picklists/${picklistId}/assign`, null, { params: { staffId } })
  return data
}

export async function pickItem(itemId: string, staffId: string): Promise<ApiResponse<PicklistItem>> {
  const { data } = await client.post(`/picking/items/${itemId}/pick`, null, { params: { staffId } })
  return data
}

export async function completePicklist(id: string): Promise<ApiResponse<Picklist>> {
  const { data } = await client.post(`/picking/picklists/${id}/complete`)
  return data
}

export async function cancelPicklist(id: string): Promise<ApiResponse<Picklist>> {
  const { data } = await client.post(`/picking/picklists/${id}/cancel`)
  return data
}

export async function getPickingKPIs(): Promise<ApiResponse<Record<string, number>>> {
  const { data } = await client.get('/picking/kpis')
  return data
}
