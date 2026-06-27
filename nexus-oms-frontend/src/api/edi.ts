import client from './client'
import type { ApiResponse, EdiDocument, EdiPartner } from '../types'

export async function getEdiDocuments(params?: {
  docType?: string
  status?: string
  page?: number
  size?: number
}): Promise<ApiResponse<{
  content: EdiDocument[]
  totalPages: number
  totalElements: number
  number: number
}>> {
  const { data } = await client.get('/edi', { params })
  return data
}

export async function getEdiDocument(id: string): Promise<ApiResponse<EdiDocument>> {
  const { data } = await client.get(`/edi/${id}`)
  return data
}

export async function uploadEdiDocument(
  file: File,
  docType: string
): Promise<ApiResponse<EdiDocument>> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('docType', docType)
  const { data } = await client.post('/edi/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return data
}

export async function parseEdiContent(
  content: string,
  docType: string
): Promise<ApiResponse<EdiDocument>> {
  const params = new URLSearchParams()
  params.append('content', content)
  params.append('docType', docType)
  const { data } = await client.post('/edi/parse', params, {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  })
  return data
}

export async function reprocessEdiDocument(id: string): Promise<ApiResponse<EdiDocument>> {
  const { data } = await client.post(`/edi/${id}/reprocess`)
  return data
}

export async function getEdiKPIs(): Promise<ApiResponse<Record<string, number>>> {
  const { data } = await client.get('/edi/kpis')
  return data
}

export async function getEdiPartners(): Promise<ApiResponse<EdiPartner[]>> {
  const { data } = await client.get('/edi/partners')
  return data
}

export async function createEdiPartner(
  partner: Partial<EdiPartner>
): Promise<ApiResponse<EdiPartner>> {
  const { data } = await client.post('/edi/partners', partner)
  return data
}
