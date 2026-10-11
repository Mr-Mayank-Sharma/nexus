import { getOrders } from '../../api/connectors/connectorClient'
import type { MarketplaceName } from './useMarketplaceStatus'

/** An order as /connectors/{marketplace}/orders returns it: a Nexus order that carries this channel. */
export interface ChannelOrder {
  orderId: string
  id: string
  status: string
  subStatus: string | null
  total: number | null
  currency: string | null
  customerEmail: string | null
  trackingNumber: string | null
  createdAt: string | null
  items: { sku: string; qty: number; productName?: string; unitPrice?: number }[]
}

export interface ChannelOrderRow {
  id: string
  orderId: string
  status: string
  customer: string
  /** Number of line items (not units). */
  items: number
  amount: string
  placedAt: string
}

export function formatMoney(total: number | null | undefined, currency: string | null | undefined): string {
  if (total == null) return '—'
  try {
    return new Intl.NumberFormat(undefined, { style: 'currency', currency: currency || 'USD' }).format(total)
  } catch {
    return `${total.toFixed(2)} ${currency ?? ''}`.trim()
  }
}

/** Only what the API sends; a missing value stays missing rather than becoming "now" or "1 item". */
export function toRow(o: ChannelOrder): ChannelOrderRow {
  const placed = o.createdAt ? new Date(o.createdAt.endsWith('Z') ? o.createdAt : o.createdAt + 'Z') : null
  return {
    id: o.id,
    orderId: o.orderId,
    status: o.status,
    customer: o.customerEmail || '—',
    items: o.items?.length ?? 0,
    amount: formatMoney(o.total, o.currency),
    placedAt: placed && !Number.isNaN(placed.getTime()) ? placed.toLocaleString() : '—',
  }
}

export async function loadChannelOrders(marketplace: MarketplaceName, signal?: AbortSignal): Promise<ChannelOrderRow[]> {
  const res = (await getOrders<ChannelOrder>(marketplace, signal)) as unknown as { orders?: ChannelOrder[] }
  return (res.orders ?? []).map(toRow)
}
