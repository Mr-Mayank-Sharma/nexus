import client from './client'
import { ApiResponse, NxPackage } from '../types'

export async function getPackages(status?: string): Promise<ApiResponse<NxPackage[]>> {
  const params = status ? { status } : {}
  const { data } = await client.get('/packing/packages', { params })
  return data
}

export async function getPackage(id: string): Promise<ApiResponse<NxPackage>> {
  const { data } = await client.get(`/packing/packages/${id}`)
  return data
}

export async function createPackage(payload: Partial<NxPackage>): Promise<ApiResponse<NxPackage>> {
  const { data } = await client.post('/packing/packages', payload)
  return data
}

export async function startPacking(id: string): Promise<ApiResponse<NxPackage>> {
  const { data } = await client.post(`/packing/packages/${id}/start`)
  return data
}

export async function addPackageItem(id: string, itemJson: string): Promise<ApiResponse<NxPackage>> {
  const { data } = await client.post(`/packing/packages/${id}/items`, itemJson, {
    headers: { 'Content-Type': 'application/json' }
  })
  return data
}

export async function completePacking(id: string, packedBy: string): Promise<ApiResponse<NxPackage>> {
  const { data } = await client.post(`/packing/packages/${id}/complete`, null, { params: { packedBy } })
  return data
}

export async function generateLabel(id: string, carrierId: string, carrierName: string,
  serviceLevel: string, trackingNumber: string, labelUrl?: string): Promise<ApiResponse<NxPackage>> {
  const { data } = await client.post(`/packing/packages/${id}/label`, null, {
    params: { carrierId, carrierName, serviceLevel, trackingNumber, labelUrl }
  })
  return data
}

export async function shipPackage(id: string): Promise<ApiResponse<NxPackage>> {
  const { data } = await client.post(`/packing/packages/${id}/ship`)
  return data
}

export async function voidPackage(id: string): Promise<ApiResponse<NxPackage>> {
  const { data } = await client.post(`/packing/packages/${id}/void`)
  return data
}

export async function getPackingKPIs(): Promise<ApiResponse<Record<string, number>>> {
  const { data } = await client.get('/packing/kpis')
  return data
}
