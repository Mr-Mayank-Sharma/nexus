import api from './client'

export async function getInvoices(status?: string) {
  const params = status ? `?status=${status}` : ''
  return api.get(`/freight/invoices${params}`)
}

export async function getInvoice(id: string) {
  return api.get(`/freight/invoices/${id}`)
}

export async function getInvoiceLines(id: string) {
  return api.get(`/freight/invoices/${id}/lines`)
}

export async function createInvoice(data: Record<string, unknown>) {
  return api.post('/freight/invoices', data)
}

export async function addInvoiceLine(invoiceId: string, data: Record<string, unknown>) {
  return api.post(`/freight/invoices/${invoiceId}/lines`, data)
}

export async function performAuditMatch(invoiceId: string) {
  return api.post(`/freight/invoices/${invoiceId}/audit`)
}

export async function getAuditLogs(invoiceId: string) {
  return api.get(`/freight/invoices/${invoiceId}/audit-log`)
}

export async function approveInvoice(id: string, approvedBy: string) {
  return api.post(`/freight/invoices/${id}/approve?approvedBy=${approvedBy}`)
}

export async function disputeInvoice(id: string, reason: string, performedBy?: string) {
  const params = new URLSearchParams({ reason })
  if (performedBy) params.set('performedBy', performedBy)
  return api.post(`/freight/invoices/${id}/dispute?${params.toString()}`)
}

export async function markPaid(id: string, performedBy?: string) {
  const params = performedBy ? `?performedBy=${performedBy}` : ''
  return api.post(`/freight/invoices/${id}/pay${params}`)
}

export async function getStats() {
  return api.get('/freight/stats')
}
