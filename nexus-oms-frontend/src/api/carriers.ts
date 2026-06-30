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
  const { data } = await client.get('/carriers')
  return data
}

export async function getCarrier(id: string): Promise<ApiResponse<Carrier>> {
  const { data } = await client.get(`/carriers/${id}`)
  return data
}

export async function createCarrier(payload: CarrierFormData): Promise<ApiResponse<Carrier>> {
  const { data } = await client.post('/carriers', payload)
  return data
}

export async function updateCarrier(id: string, payload: Partial<CarrierFormData>): Promise<ApiResponse<Carrier>> {
  const { data } = await client.put(`/carriers/${id}`, payload)
  return data
}

export async function deleteCarrier(id: string): Promise<ApiResponse<null>> {
  const { data } = await client.delete(`/carriers/${id}`)
  return data
}

export async function getCarrierKPIs(): Promise<ApiResponse<Record<string, number>>> {
  const { data } = await client.get('/carriers/kpis')
  return data
}
