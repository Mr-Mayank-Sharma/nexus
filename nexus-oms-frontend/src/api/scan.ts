import client from './client'
import { ApiResponse } from '../types'

export interface ScanResult {
  type: string
  id: string
  label: string
  sub: string
  href: string
}

export async function searchScan(q: string): Promise<ApiResponse<ScanResult[]>> {
  try {
    const { data } = await client.get('/rf/search', { params: { q } })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to search'
    return { success: false, error: msg } as any
  }
}
