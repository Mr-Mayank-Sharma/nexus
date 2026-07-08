import type { AmazonOrder, AmazonListing, AmazonInventory, AmazonConfig } from './types'
import { getOrders, getOrderDetail, getInventory, getListings, authorizeConnector, syncConnector } from './connectorClient'

export type { AmazonOrder, AmazonListing, AmazonInventory, AmazonConfig }

export async function fetchAmazonOrders(signal?: AbortSignal) {
  return getOrders<AmazonOrder>('amazon', signal)
}

export async function fetchAmazonOrderDetail(orderId: string, signal?: AbortSignal) {
  return getOrderDetail<AmazonOrder>('amazon', orderId, signal)
}

export async function fetchAmazonInventory(signal?: AbortSignal) {
  return getInventory<AmazonInventory>('amazon', signal)
}

export async function fetchAmazonListings(signal?: AbortSignal) {
  return getListings<AmazonListing>('amazon', signal)
}

export async function authorizeAmazon(config: AmazonConfig, signal?: AbortSignal) {
  return authorizeConnector('amazon', config as Record<string, unknown>, signal)
}

export async function syncAmazonOrders(signal?: AbortSignal) {
  return syncConnector('amazon', signal)
}
