import client from './client'
import { ApiResponse } from '../types'

export interface Carrier {
  id: string
  name: string
  code: string
  status: string
  type: string
  accountNumber?: string
  apiKeyEncrypted?: string
  apiSecretEncrypted?: string
  otdRate: number
  avgCost: number
  totalShipments: number
  damageRate: number
  isActive: boolean
  metadata?: string
  createdAt: string
  updatedAt: string
}

export interface CarrierFormData {
  name: string
  code: string
  type: string
  accountNumber?: string
  apiKeyEncrypted?: string
  apiSecretEncrypted?: string
  isActive: boolean
}

export async function getCarriers(): Promise<ApiResponse<Carrier[]>> {
  try {
    const { data } = await client.get('/carriers')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get carriers'
    return { success: false, error: msg } as any
  }
}

export async function getCarrier(id: string): Promise<ApiResponse<Carrier>> {
  try {
    const { data } = await client.get(`/carriers/${id}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get carrier'
    return { success: false, error: msg } as any
  }
}

export async function createCarrier(payload: CarrierFormData): Promise<ApiResponse<Carrier>> {
  try {
    const { data } = await client.post('/carriers', payload)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to create carrier'
    return { success: false, error: msg } as any
  }
}

export async function updateCarrier(id: string, payload: Partial<CarrierFormData>): Promise<ApiResponse<Carrier>> {
  try {
    const { data } = await client.put(`/carriers/${id}`, payload)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to update carrier'
    return { success: false, error: msg } as any
  }
}

export async function deleteCarrier(id: string): Promise<ApiResponse<null>> {
  try {
    const { data } = await client.delete(`/carriers/${id}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to delete carrier'
    return { success: false, error: msg } as any
  }
}

export async function getCarrierKPIs(): Promise<ApiResponse<Record<string, number>>> {
  try {
    const { data } = await client.get('/carriers/kpis')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get carrier KPIs'
    return { success: false, error: msg } as any
  }
}
