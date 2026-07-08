import client from './client'
import { ApiResponse, NxPackage } from '../types'

export async function getPackages(status?: string): Promise<ApiResponse<NxPackage[]>> {
  try {
    const params = status ? { status } : {}
    const { data } = await client.get('/packing/queues', { params })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get packages'
    return { success: false, error: msg } as any
  }
}

export async function getPackage(id: string): Promise<ApiResponse<NxPackage>> {
  try {
    const { data } = await client.get(`/packing/queues/${id}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get package'
    return { success: false, error: msg } as any
  }
}

export async function createPackage(payload: Partial<NxPackage>): Promise<ApiResponse<NxPackage>> {
  try {
    const { data } = await client.post('/packing/complete', payload)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to create package'
    return { success: false, error: msg } as any
  }
}

export async function startPacking(id: string): Promise<ApiResponse<NxPackage>> {
  try {
    const { data } = await client.post(`/packing/packages/${id}/start`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to start packing'
    return { success: false, error: msg } as any
  }
}

export async function addPackageItem(id: string, itemJson: string): Promise<ApiResponse<NxPackage>> {
  try {
    const { data } = await client.post(`/packing/packages/${id}/items`, itemJson, {
      headers: { 'Content-Type': 'application/json' }
    })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to add package item'
    return { success: false, error: msg } as any
  }
}

export async function completePacking(id: string, packedBy: string): Promise<ApiResponse<NxPackage>> {
  try {
    const { data } = await client.post(`/packing/packages/${id}/complete`, null, { params: { packedBy } })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to complete packing'
    return { success: false, error: msg } as any
  }
}

export async function generateLabel(id: string, carrierId: string, carrierName: string,
  serviceLevel: string, trackingNumber: string, labelUrl?: string): Promise<ApiResponse<NxPackage>> {
  try {
    const { data } = await client.post(`/labels/generate`, { orderId: id, carrier: "UPS" })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to generate label'
    return { success: false, error: msg } as any
  }
}

export async function shipPackage(id: string): Promise<ApiResponse<NxPackage>> {
  try {
    const { data } = await client.post(`/packing/packages/${id}/ship`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to ship package'
    return { success: false, error: msg } as any
  }
}

export async function voidPackage(id: string): Promise<ApiResponse<NxPackage>> {
  try {
    const { data } = await client.post(`/packing/packages/${id}/void`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to void package'
    return { success: false, error: msg } as any
  }
}

export async function getPackingKPIs(): Promise<ApiResponse<Record<string, number>>> {
  try {
    const { data } = await client.get('/orders/stats')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get packing KPIs'
    return { success: false, error: msg } as any
  }
}
