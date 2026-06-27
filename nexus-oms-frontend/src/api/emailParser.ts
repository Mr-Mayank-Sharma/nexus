import client from './client'
import type { ApiResponse, EmailParsedOrder } from '../types'

export async function getParsedOrders(params?: {
  status?: string
  page?: number
  size?: number
}): Promise<ApiResponse<{
  content: EmailParsedOrder[]
  totalPages: number
  totalElements: number
  number: number
}>> {
  const { data } = await client.get('/email-parser', { params })
  return data
}

export async function getParsedOrder(id: string): Promise<ApiResponse<EmailParsedOrder>> {
  const { data } = await client.get(`/email-parser/${id}`)
  return data
}

export async function parseEmailContent(payload: {
  subject: string
  from: string
  body: string
}): Promise<ApiResponse<EmailParsedOrder>> {
  const params = new URLSearchParams()
  params.append('subject', payload.subject)
  params.append('from', payload.from)
  params.append('body', payload.body)
  const { data } = await client.post('/email-parser/parse', params, {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  })
  return data
}

export async function parseCsvAttachment(
  file: File,
  subject?: string,
  from?: string
): Promise<ApiResponse<EmailParsedOrder>> {
  const formData = new FormData()
  formData.append('file', file)
  if (subject) formData.append('subject', subject)
  if (from) formData.append('from', from)
  const { data } = await client.post('/email-parser/parse-csv', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return data
}

export async function approveParsedOrder(id: string): Promise<ApiResponse<EmailParsedOrder>> {
  const { data } = await client.post(`/email-parser/${id}/approve`)
  return data
}

export async function rejectParsedOrder(id: string, reason: string): Promise<ApiResponse<EmailParsedOrder>> {
  const { data } = await client.post(`/email-parser/${id}/reject`, null, {
    params: { reason },
  })
  return data
}

export async function getEmailParserKPIs(): Promise<ApiResponse<Record<string, number>>> {
  const { data } = await client.get('/email-parser/kpis')
  return data
}
