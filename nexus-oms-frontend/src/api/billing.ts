import client from './client'

export interface RateCard {
  id: string
  tenantId: string
  clientId?: string
  clientName?: string
  currency: string
  perOrderFee: number
  perLineFee: number
  pickingFeePerLine: number
  storageFeePerUnit: number
  description?: string
  effectiveFrom?: string
  effectiveTo?: string
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface BillingStatementLine {
  id: string
  statementId: string
  rateType: string
  description: string
  quantity: number
  unitPrice: number
  amount: number
  createdAt: string
}

export interface BillingStatement {
  id: string
  tenantId: string
  clientId: string
  clientName: string
  currency: string
  periodStart: string
  periodEnd: string
  status: 'DRAFT' | 'ISSUED' | 'PAID'
  orderCount: number
  lineCount: number
  pickedLines: number
  unitsHandled: number
  subtotal: number
  total: number
  notes?: string
  createdAt: string
  updatedAt: string
}

export interface ClientPortalOverview {
  client: { id: string; name: string; email: string }
  ordersLast30d: number
  openOrders: number
  fulfilledOrders: number
  unitsOrdered: number
  ordersByStatus: Record<string, number>
  outstandingInvoices: number
  outstandingBalance: number
  recentOrders: Array<{
    id: string
    orderNumber: string
    status: string
    totalAmount: number
    createdAt: string
  }>
}

// ── Rate Cards ──────────────────────────────────────────────────────────────

export async function getRateCards(clientId?: string) {
  const qs = clientId ? `?clientId=${clientId}` : ''
  return (await client.get(`/rate-cards${qs}`)).data
}

export async function getRateCard(id: string) {
  return (await client.get(`/rate-cards/${id}`)).data
}

export async function createRateCard(data: Partial<RateCard>) {
  return (await client.post('/rate-cards', data)).data
}

export async function updateRateCard(id: string, data: Partial<RateCard>) {
  return (await client.put(`/rate-cards/${id}`, data)).data
}

export async function deleteRateCard(id: string) {
  return (await client.delete(`/rate-cards/${id}`)).data
}

// ── Billing Statements ──────────────────────────────────────────────────────

export async function getStatements(clientId?: string) {
  const qs = clientId ? `?clientId=${clientId}` : ''
  return (await client.get(`/billing/statements${qs}`)).data
}

export async function getStatement(id: string) {
  return (await client.get(`/billing/statements/${id}`)).data
}

export async function generateStatement(clientId: string, periodStart: string, periodEnd: string) {
  return (await client.post(`/billing/statements/generate?clientId=${clientId}&periodStart=${periodStart}&periodEnd=${periodEnd}`)).data
}

export async function updateStatementStatus(id: string, status: string) {
  return (await client.patch(`/billing/statements/${id}/status?status=${status}`)).data
}

// ── Client Portal ───────────────────────────────────────────────────────────

export async function getClientOverview(clientId: string) {
  return (await client.get(`/client-portal/overview?clientId=${clientId}`)).data
}
