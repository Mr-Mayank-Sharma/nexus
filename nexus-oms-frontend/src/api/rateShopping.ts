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
  const { data } = await client.post('/rate-shopping/shop', payload)
  return data
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
  const { data } = await client.post('/rate-shopping/best', payload)
  return data
}
