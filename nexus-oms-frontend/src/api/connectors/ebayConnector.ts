import type { EbayOrder, EbayListing, EbayInventory, EbayConfig } from './types'
import { getOrders, getOrderDetail, getInventory, getListings, authorizeConnector, syncConnector } from './connectorClient'

export type { EbayOrder, EbayListing, EbayInventory, EbayConfig }

export async function fetchEbayOrders(signal?: AbortSignal) {
  return getOrders<EbayOrder>('ebay', signal)
}

export async function fetchEbayOrderDetail(orderId: string, signal?: AbortSignal) {
  return getOrderDetail<EbayOrder>('ebay', orderId, signal)
}

export async function fetchEbayInventory(signal?: AbortSignal) {
  return getInventory<EbayInventory>('ebay', signal)
}

export async function fetchEbayListings(signal?: AbortSignal) {
  return getListings<EbayListing>('ebay', signal)
}

export async function authorizeEbay(config: EbayConfig, signal?: AbortSignal) {
  return authorizeConnector('ebay', config as Record<string, unknown>, signal)
}

export async function syncEbayOrders(signal?: AbortSignal) {
  return syncConnector('ebay', signal)
}
