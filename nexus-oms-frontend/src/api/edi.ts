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
  try {
    const { data } = await client.get('/edi', { params })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get EDI documents'
    return { success: false, error: msg } as any
  }
}

export async function getEdiDocument(id: string): Promise<ApiResponse<EdiDocument>> {
  try {
    const { data } = await client.get(`/edi/${id}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get EDI document'
    return { success: false, error: msg } as any
  }
}

export async function uploadEdiDocument(
  file: File,
  docType: string
): Promise<ApiResponse<EdiDocument>> {
  try {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('docType', docType)
    const { data } = await client.post('/edi/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to upload EDI document'
    return { success: false, error: msg } as any
  }
}

export async function parseEdiContent(
  content: string,
  docType: string
): Promise<ApiResponse<EdiDocument>> {
  try {
    const params = new URLSearchParams()
    params.append('content', content)
    params.append('docType', docType)
    const { data } = await client.post('/edi/parse', params, {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to parse EDI content'
    return { success: false, error: msg } as any
  }
}

export async function reprocessEdiDocument(id: string): Promise<ApiResponse<EdiDocument>> {
  try {
    const { data } = await client.post(`/edi/${id}/reprocess`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to reprocess EDI document'
    return { success: false, error: msg } as any
  }
}

export async function getEdiKPIs(): Promise<ApiResponse<Record<string, number>>> {
  try {
    const { data } = await client.get('/edi/kpis')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get EDI KPIs'
    return { success: false, error: msg } as any
  }
}

export async function getEdiPartners(): Promise<ApiResponse<EdiPartner[]>> {
  try {
    const { data } = await client.get('/edi/partners')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get EDI partners'
    return { success: false, error: msg } as any
  }
}

export async function createEdiPartner(
  partner: Partial<EdiPartner>
): Promise<ApiResponse<EdiPartner>> {
  try {
    const { data } = await client.post('/edi/partners', partner)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to create EDI partner'
    return { success: false, error: msg } as any
  }
}
