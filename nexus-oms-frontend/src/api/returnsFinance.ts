import client from './client'
import type { ApiResponse } from '../types'

// ── T-10: Returns / Exchange Financial Reconciliation ────────────────────────

export interface CreditMemo {
  id: string
  tenantId?: string
  memoNumber?: string
  invoiceId?: string
  salesReturnId?: string
  returnId?: string
  outcome?: string
  taxDelta?: number
  sourceLocationId?: string
  targetLocationId?: string
  customerDepositApplied?: number
  orderId?: string
  customerId?: string
  supplierId?: string
  memoType?: string
  reason?: string
  amount: number
  currency?: string
  status: string
  notes?: string
  createdAt?: string
  updatedAt?: string
}

export interface InvoiceApplication {
  invoiceId: string
  amount: number
  taxAmount: number
}

export interface CreditMemoDetail {
  id: string
  memoNumber?: string
  returnId?: string
  outcome?: string
  amount: number
  taxDelta?: number
  status?: string
  invoiceApplications?: InvoiceApplication[]
}

export interface ImpactReport {
  totalMemos: number
  multiInvoiceMemos: number
}

export interface TransactionSplit {
  invoiceId: string
  amount: number
  taxAmount?: number
  outcome?: string // REFUND | STORE_CREDIT | LIKE_FOR_LIKE_EXCHANGE | LESSER_VALUE_EXCHANGE
}

export async function splitCreditMemos(params: {
  returnId: string
  transactions: TransactionSplit[]
}): Promise<ApiResponse<CreditMemo[]>> {
  try {
    const { data } = await client.post('/returns-finance/credit-memos/split', params)
    return data
  } catch (err: any) {
    return { success: false, error: err?.response?.data?.message || err?.message || 'Failed to split credit memos' } as any
  }
}

export async function lesserValueExchange(params: {
  returnId: string
  exchangeInvoiceId: string
  exchangeValue: number
  sourceLocationId?: string
  targetLocationId?: string
}): Promise<ApiResponse<CreditMemo>> {
  try {
    const { data } = await client.post('/returns-finance/exchange/lesser-value', params)
    return data
  } catch (err: any) {
    return { success: false, error: err?.response?.data?.message || err?.message || 'Failed to process exchange' } as any
  }
}

export async function getCreditMemosForReturn(returnId: string): Promise<ApiResponse<CreditMemoDetail[]>> {
  try {
    const { data } = await client.get(`/returns-finance/returns/${returnId}/credit-memos`)
    return data
  } catch (err: any) {
    return { success: false, error: err?.response?.data?.message || err?.message || 'Failed to get credit memos' } as any
  }
}

export async function applyCustomerDeposit(creditMemoId: string, deposit: number): Promise<ApiResponse<CreditMemo>> {
  try {
    const { data } = await client.post(`/returns-finance/credit-memos/${creditMemoId}/deposit`, null, { params: { deposit } })
    return data
  } catch (err: any) {
    return { success: false, error: err?.response?.data?.message || err?.message || 'Failed to apply deposit' } as any
  }
}

export async function getReturnsFinanceImpact(): Promise<ApiResponse<ImpactReport>> {
  try {
    const { data } = await client.get('/returns-finance/impact')
    return data
  } catch (err: any) {
    return { success: false, error: err?.response?.data?.message || err?.message || 'Failed to get impact' } as any
  }
}
