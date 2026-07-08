import client from './client'
import { ApiResponse, Shipment } from '../types'

export async function getShipments(): Promise<ApiResponse<Shipment[]>> {
  try {
    const { data } = await client.get('/shipments')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get shipments'
    return { success: false, error: msg } as any
  }
}

export async function getShipment(id: string): Promise<ApiResponse<Shipment>> {
  try {
    const { data } = await client.get(`/shipments/${id}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get shipment'
    return { success: false, error: msg } as any
  }
}

export async function getShipmentsByOrder(orderId: string): Promise<ApiResponse<Shipment[]>> {
  try {
    const { data } = await client.get('/shipments', { params: { orderId } })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get shipments by order'
    return { success: false, error: msg } as any
  }
}

export async function createShipment(payload: Partial<Shipment>): Promise<ApiResponse<Shipment>> {
  try {
    const { data } = await client.post('/shipments', payload)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to create shipment'
    return { success: false, error: msg } as any
  }
}

export async function updateTracking(id: string, trackingNumber: string, carrierId: string, serviceLevel?: string): Promise<ApiResponse<Shipment>> {
  try {
    const { data } = await client.put(`/shipments/${id}/tracking`, { trackingNumber, carrierId, serviceLevel })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to update tracking'
    return { success: false, error: msg } as any
  }
}

export async function markShipped(id: string): Promise<ApiResponse<Shipment>> {
  try {
    const { data } = await client.post(`/shipments/${id}/ship`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to mark shipped'
    return { success: false, error: msg } as any
  }
}

export async function markDelivered(id: string): Promise<ApiResponse<Shipment>> {
  try {
    const { data } = await client.post(`/shipments/${id}/deliver`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to mark delivered'
    return { success: false, error: msg } as any
  }
}

export async function voidShipment(id: string): Promise<ApiResponse<Shipment>> {
  try {
    const { data } = await client.post(`/shipments/${id}/void`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to void shipment'
    return { success: false, error: msg } as any
  }
}

export async function getShippingKPIs(): Promise<ApiResponse<Record<string, number>>> {
  try {
    const { data } = await client.get('/shipping/kpis')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get shipping KPIs'
    return { success: false, error: msg } as any
  }
}
