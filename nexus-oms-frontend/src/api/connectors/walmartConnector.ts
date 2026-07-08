import type { WalmartOrder, WalmartListing, WalmartInventory, WalmartConfig } from './types'
import { getOrders, getOrderDetail, getInventory, getListings, authorizeConnector, syncConnector } from './connectorClient'

export type { WalmartOrder, WalmartListing, WalmartInventory, WalmartConfig }

export async function fetchWalmartOrders(signal?: AbortSignal) {
  return getOrders<WalmartOrder>('walmart', signal)
}

export async function fetchWalmartOrderDetail(orderId: string, signal?: AbortSignal) {
  return getOrderDetail<WalmartOrder>('walmart', orderId, signal)
}

export async function fetchWalmartInventory(signal?: AbortSignal) {
  return getInventory<WalmartInventory>('walmart', signal)
}

export async function fetchWalmartListings(signal?: AbortSignal) {
  return getListings<WalmartListing>('walmart', signal)
}

export async function authorizeWalmart(config: WalmartConfig, signal?: AbortSignal) {
  return authorizeConnector('walmart', config as Record<string, unknown>, signal)
}

export async function syncWalmartOrders(signal?: AbortSignal) {
  return syncConnector('walmart', signal)
}
