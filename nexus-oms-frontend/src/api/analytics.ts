import client from './client'
import { ApiResponse } from '../types'

export async function getDashboardKpis(): Promise<ApiResponse<Record<string, any>>> {
  const { data } = await client.get('/analytics/dashboard')
  return data
}

export async function getOrderVelocity(hours: number = 24): Promise<ApiResponse<Record<string, any>>> {
  const { data } = await client.get('/analytics/orders/velocity', { params: { hours } })
  return data
}

export async function getCarrierPerformance(): Promise<ApiResponse<Record<string, any>>> {
  const { data } = await client.get('/analytics/carrier-performance')
  return data
}

export async function getCostBreakdown(): Promise<ApiResponse<Record<string, any>>> {
  const { data } = await client.get('/analytics/cost-breakdown')
  return data
}

export async function getLanePerformance(): Promise<ApiResponse<Record<string, any>>> {
  const { data } = await client.get('/analytics/lanes')
  return data
}

export async function getReturnsAnalytics(): Promise<ApiResponse<Record<string, any>>> {
  const { data } = await client.get('/analytics/returns')
  return data
}

export async function getActivity(): Promise<ApiResponse<any[]>> {
  const { data } = await client.get('/analytics/activity')
  return data
}
