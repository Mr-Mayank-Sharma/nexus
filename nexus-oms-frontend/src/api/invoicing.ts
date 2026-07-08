import client from './client'
import { ApiResponse, Invoice, InvoiceItem, Payment, CreditMemo, CreditMemoItem } from '../types'
export type { Invoice, InvoiceItem, Payment, CreditMemo, CreditMemoItem }

export async function getInvoices(page: number, size: number): Promise<ApiResponse<{ content: Invoice[]; totalElements: number; totalPages: number }>> {
  try {
    const { data } = await client.get('/invoices', { params: { page, size } })
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch invoices')
  }
}

export async function getInvoice(id: string): Promise<ApiResponse<Invoice>> {
  try {
    const { data } = await client.get(`/invoices/${id}`)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch invoice')
  }
}

export async function createInvoice(invoiceData: Record<string, any>): Promise<ApiResponse<Invoice>> {
  try {
    const { data } = await client.post('/invoices', invoiceData)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to create invoice')
  }
}

export async function updateInvoiceStatus(id: string, status: string): Promise<ApiResponse<Invoice>> {
  try {
    const { data } = await client.patch(`/invoices/${id}`, { status })
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to update invoice status')
  }
}

export async function recordPayment(invoiceId: string, paymentData: Record<string, any>): Promise<ApiResponse<Payment>> {
  try {
    const { data } = await client.post(`/invoicing/invoices/${invoiceId}/payments`, paymentData)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to record payment')
  }
}

export async function getPayments(page: number, size: number): Promise<ApiResponse<{ content: Payment[]; totalElements: number; totalPages: number }>> {
  try {
    const { data } = await client.get('/payments', { params: { page, size } })
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch payments')
  }
}

export async function getPayment(id: string): Promise<ApiResponse<Payment>> {
  try {
    const { data } = await client.get(`/payments/${id}`)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch payment')
  }
}

export async function processRefund(paymentId: string, refundData: Record<string, any>): Promise<ApiResponse<Payment>> {
  try {
    const { data } = await client.post(`/invoicing/payments/${paymentId}/refund`, refundData)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to process refund')
  }
}

export async function getPaymentsByInvoice(invoiceId: string): Promise<ApiResponse<Payment[]>> {
  try {
    const { data } = await client.get(`/payments?invoiceId=${invoiceId}`)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch payments for invoice')
  }
}

export async function getCreditMemos(page: number, size: number): Promise<ApiResponse<{ content: CreditMemo[]; totalElements: number; totalPages: number }>> {
  try {
    const { data } = await client.get('/invoicing/credit-memos', { params: { page, size } })
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch credit memos')
  }
}

export async function getCreditMemo(id: string): Promise<ApiResponse<CreditMemo>> {
  try {
    const { data } = await client.get(`/invoicing/credit-memos/${id}`)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch credit memo')
  }
}

export async function createCreditMemo(creditMemoData: Record<string, any>): Promise<ApiResponse<CreditMemo>> {
  try {
    const { data } = await client.post('/invoicing/credit-memos', creditMemoData)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to create credit memo')
  }
}

export async function getInvoiceSummary(): Promise<ApiResponse<Record<string, any>>> {
  try {
    const now = new Date()
    const currentMonth = now.getMonth()
    const currentYear = now.getFullYear()
    const invRes = await client.get('/invoices')
    const allInvoices: any[] = invRes.data?.invoices || invRes.data?.data || invRes.data || []
    const totalOutstanding = allInvoices
      .filter((i: any) => i.status === 'SENT' || i.status === 'OVERDUE' || i.status === 'PENDING')
      .reduce((s: number, i: any) => s + parseFloat(i.total || i.amount || 0), 0)
    const overdue = allInvoices.filter((i: any) => i.status === 'OVERDUE' || (i.dueDate && new Date(i.dueDate) < now && i.status !== 'PAID'))
    const totalOverdue = overdue.reduce((s: number, i: any) => s + parseFloat(i.total || i.amount || 0), 0)
    const paidThisMonth = allInvoices
      .filter((i: any) => i.status === 'PAID' && i.paidDate && new Date(i.paidDate).getMonth() === currentMonth && new Date(i.paidDate).getFullYear() === currentYear)
      .reduce((s: number, i: any) => s + parseFloat(i.total || i.amount || 0), 0)
    const pendingCount = allInvoices.filter((i: any) => i.status === 'SENT' || i.status === 'PENDING').length
    const paidCount = allInvoices.filter((i: any) => i.status === 'PAID').length
    const overdueCount = overdue.length
    return {
      success: true,
      data: { totalOutstanding, totalOverdue, paidThisMonth, pendingCount, paidCount, overdueCount, totalInvoices: allInvoices.length }
    }
  } catch (error: any) {
    return {
      success: true,
      data: { totalOutstanding: 0, totalOverdue: 0, paidThisMonth: 0, pendingCount: 0, paidCount: 0, overdueCount: 0, totalInvoices: 0 }
    }
  }
}
