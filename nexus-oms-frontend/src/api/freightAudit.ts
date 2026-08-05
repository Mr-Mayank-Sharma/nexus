import api from './client'

export async function getInvoices(status?: string) {
  const params = status ? `?status=${status}` : ''
  return (await api.get(`/freight/invoices${params}`)).data
}

export async function getInvoice(id: string) {
  return (await api.get(`/freight/invoices/${id}`)).data
}

export async function getInvoiceLines(id: string) {
  return (await api.get(`/freight/invoices/${id}/lines`)).data
}

export async function createInvoice(data: Record<string, unknown>) {
  return (await api.post('/freight/invoices', data)).data
}

export async function addInvoiceLine(invoiceId: string, data: Record<string, unknown>) {
  return (await api.post(`/freight/invoices/${invoiceId}/lines`, data)).data
}

export async function performAuditMatch(invoiceId: string) {
  return (await api.post(`/freight/invoices/${invoiceId}/audit`)).data
}

export async function getAuditLogs(invoiceId: string) {
  return (await api.get(`/freight/invoices/${invoiceId}/audit-log`)).data
}

export async function approveInvoice(id: string, approvedBy: string) {
  return (await api.post(`/freight/invoices/${id}/approve?approvedBy=${approvedBy}`)).data
}

export async function disputeInvoice(id: string, reason: string, performedBy?: string) {
  const params = new URLSearchParams({ reason })
  if (performedBy) params.set('performedBy', performedBy)
  return (await api.post(`/freight/invoices/${id}/dispute?${params.toString()}`)).data
}

export async function markPaid(id: string, performedBy?: string) {
  const params = performedBy ? `?performedBy=${performedBy}` : ''
  return (await api.post(`/freight/invoices/${id}/pay${params}`)).data
}

export async function getStats() {
  return (await api.get('/freight/stats')).data
}
