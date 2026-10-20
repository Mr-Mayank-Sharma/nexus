import client from '../../api/client'

export interface ReleasePolicy { b2bDays: number; b2cDays: number }
export interface HeldOrder {
  id: string
  orderNumber: string | null
  channel: string | null
  orderType: 'B2C' | 'B2B'
  requestedShipDate: string
  releaseAt: string
  status: string
  customerEmail: string | null
  total: number | null
  currency: string | null
}

export async function getPolicy(): Promise<ReleasePolicy> {
  return (await client.get('/order-release/policy')).data.data
}
export async function savePolicy(p: ReleasePolicy): Promise<ReleasePolicy> {
  return (await client.put('/order-release/policy', p)).data.data
}
export async function getHeld(): Promise<HeldOrder[]> {
  return (await client.get('/order-release/held')).data?.data ?? []
}
export async function releaseNow(orderId: string): Promise<HeldOrder> {
  return (await client.post(`/order-release/orders/${orderId}/release`)).data.data
}
export async function reschedule(orderId: string, orderType: string, requestedShipDate: string | null): Promise<HeldOrder> {
  return (await client.put(`/order-release/orders/${orderId}/schedule`, { orderType, requestedShipDate })).data.data
}
