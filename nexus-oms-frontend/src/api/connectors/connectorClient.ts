import type { Marketplace, ConnectorResponse } from './types'

const CONNECTOR_SERVER = 'http://localhost:8083'

async function request<T>(
  marketplace: Marketplace,
  action: string,
  options?: { method?: string; body?: unknown; signal?: AbortSignal }
): Promise<ConnectorResponse<T>> {
  const url = `${CONNECTOR_SERVER}/connectors/${marketplace}/${action}`
  const method = options?.method || (options?.body ? 'POST' : 'GET')
  const res = await fetch(url, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: options?.body ? JSON.stringify(options.body) : undefined,
    signal: options?.signal,
  })
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: res.statusText }))
    throw new Error(err.error || `HTTP ${res.status}`)
  }
  const data: ConnectorResponse<T> = await res.json()
  if (!data.success) throw new Error(data.error || 'Request failed')
  return data
}

export async function getConnectorStatus(signal?: AbortSignal) {
  const url = `${CONNECTOR_SERVER}/connectors/status`
  const res = await fetch(url, { signal })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}

export async function configureConnector(
  marketplace: Marketplace,
  config: Record<string, unknown>,
  signal?: AbortSignal
) {
  const url = `${CONNECTOR_SERVER}/connectors/configure`
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ marketplace, ...config }),
    signal,
  })
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: res.statusText }))
    throw new Error(err.error || `HTTP ${res.status}`)
  }
  return res.json()
}

export async function authorizeConnector(
  marketplace: Marketplace,
  config: Record<string, unknown>,
  signal?: AbortSignal
) {
  return request(marketplace, 'authorize', { method: 'POST', body: config, signal })
}

export async function disconnectConnector(marketplace: Marketplace, signal?: AbortSignal) {
  return request(marketplace, 'disconnect', { method: 'POST', body: {}, signal })
}

export async function syncConnector(marketplace: Marketplace, signal?: AbortSignal) {
  return request(marketplace, 'sync', { method: 'POST', body: {}, signal })
}

export async function getOrders<T>(marketplace: Marketplace, signal?: AbortSignal) {
  return request<T[]>(marketplace, 'orders', { signal })
}

export async function getOrderDetail<T>(marketplace: Marketplace, orderId: string, signal?: AbortSignal) {
  return request<T>(marketplace, `orders/${orderId}`, { signal })
}

export async function getInventory<T>(marketplace: Marketplace, signal?: AbortSignal) {
  return request<T>(marketplace, 'inventory', { signal })
}

export async function getListings<T>(marketplace: Marketplace, signal?: AbortSignal) {
  return request<T[]>(marketplace, 'listings', { signal })
}
