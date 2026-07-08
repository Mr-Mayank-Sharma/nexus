import client from './client'
import type { ApiResponse, RateShoppingResult } from '../types'

export async function shopRates(payload: {
  fromZip: string
  toZip: string
  toCountry?: string
  totalWeightKg: number
  declaredValue?: number
  numPackages?: number
  residential?: boolean
  serviceLevels?: string[]
  orderId?: string
}): Promise<ApiResponse<RateShoppingResult>> {
  try {
    const { data } = await client.post('/rate-shopping/shop', payload)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to shop rates'
    return { success: false, error: msg } as any
  }
}

export async function getBestRate(payload: {
  fromZip: string
  toZip: string
  toCountry?: string
  totalWeightKg: number
  declaredValue?: number
  numPackages?: number
  residential?: boolean
}): Promise<ApiResponse<RateShoppingResult>> {
  try {
    const { data } = await client.post('/rate-shopping/best', payload)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get best rate'
    return { success: false, error: msg } as any
  }
}
