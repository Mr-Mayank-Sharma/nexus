import client from './client'

/** T-09: Shopify → OMS fulfillment reconciliation. */

export type MatchType = 'FULFILLED' | 'PARTIAL' | 'MISMATCH' | 'UNFULFILLED' | 'EXCESS'

export type Resolution = 'FLAGGED' | 'REPAIRED' | 'ACCEPTED' | 'MANUAL'

export type NetsuiteSyncStatus = 'PENDING' | 'SYNCED' | 'FAILED' | 'SKIPPED'

/** One row of the mismatch report (ledger rows with FLAGGED / REPAIRED resolution). */
export interface ReconciliationReportRow {
  orderId: string
  orderNumber: string
  matchType: MatchType
  resolution: Resolution
  reason?: string
  netsuiteSyncStatus: NetsuiteSyncStatus
  inventoryDelta?: number
  createdAt: string
}

/** Full reconciliation ledger entry for an order (NxFulfillmentReconciliation). */
export interface NxFulfillmentReconciliation {
  id: string
  tenantId: string
  orderId: string
  orderNumber: string
  shopifyFulfillmentId?: string
  matchType: MatchType
  fulfilledLocation?: string
  routedLocation?: string
  /** JSON string of fulfilled line items. */
  fulfilledItems?: string
  /** JSON string of remaining/unfulfilled line items. */
  remainingItems?: string
  resolution: Resolution
  netsuiteSyncStatus: NetsuiteSyncStatus
  inventoryDelta?: number
  notes?: string
  createdAt: string
  updatedAt: string
}

const WRAPPER_KEYS = ['success', 'data', 'message', 'error', 'errors', 'pagination']

/** Client normalizes every body to {success, data, message, ...}; unwrap back to the payload. */
async function unwrap<T>(promise: Promise<{ data: unknown }>): Promise<{ data: T }> {
  const res = await promise
  const body = res.data
  if (body && typeof body === 'object' && !Array.isArray(body) && 'data' in body) {
    const obj = body as Record<string, unknown>
    if (Object.keys(obj).every(k => WRAPPER_KEYS.includes(k))) return { data: obj.data as T }
  }
  return { data: body as T }
}

export const reconciliationApi = {
  /** Ledger-based mismatch report (FLAGGED / REPAIRED rows). */
  getMismatches: () =>
    unwrap<ReconciliationReportRow[]>(client.get<ReconciliationReportRow[]>('/reconciliation/mismatches')),

  /** Full reconciliation history for a single order. */
  getOrderHistory: (orderId: string) =>
    unwrap<NxFulfillmentReconciliation[]>(client.get<NxFulfillmentReconciliation[]>(`/reconciliation/orders/${orderId}/history`)),

  /** On-demand cross-system repair pass. */
  triggerRepair: () =>
    unwrap<string>(client.post<string>('/reconciliation/repair')),
}

export default reconciliationApi