import client from './client'
import { ApiResponse, Shipment } from '../types'

export async function getShipments(): Promise<ApiResponse<Shipment[]>> {
  const { data } = await client.get('/shipping/shipments')
  return data
}

export async function getShipment(id: string): Promise<ApiResponse<Shipment>> {
  const { data } = await client.get(`/shipping/shipments/${id}`)
  return data
}

export async function getShipmentsByOrder(orderId: string): Promise<ApiResponse<Shipment[]>> {
  const { data } = await client.get(`/shipping/orders/${orderId}/shipments`)
  return data
}

export async function createShipment(payload: Partial<Shipment>): Promise<ApiResponse<Shipment>> {
  const { data } = await client.post('/shipping/shipments', payload)
  return data
}

export async function updateTracking(id: string, trackingNumber: string, carrierId: string, serviceLevel?: string): Promise<ApiResponse<Shipment>> {
  const { data } = await client.post(`/shipping/shipments/${id}/tracking`, null, {
    params: { trackingNumber, carrierId, serviceLevel }
  })
  return data
}

export async function markShipped(id: string): Promise<ApiResponse<Shipment>> {
  const { data } = await client.post(`/shipping/shipments/${id}/ship`)
  return data
}

export async function markDelivered(id: string): Promise<ApiResponse<Shipment>> {
  const { data } = await client.post(`/shipping/shipments/${id}/deliver`)
  return data
}

export async function voidShipment(id: string): Promise<ApiResponse<Shipment>> {
  const { data } = await client.post(`/shipping/shipments/${id}/void`)
  return data
}

export async function getShippingKPIs(): Promise<ApiResponse<Record<string, number>>> {
  const { data } = await client.get('/shipping/kpis')
  return data
}
