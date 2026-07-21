import client from './client'
import { ApiResponse } from '../types'

export async function getDashboardKpis(): Promise<ApiResponse<Record<string, any>>> {
  try {
    const { data } = await client.get('/dashboard')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get dashboard KPIs'
    return { success: false, error: msg } as any
  }
}

export async function getOrderVelocity(hours: number = 24): Promise<ApiResponse<Record<string, any>>> {
  try {
    const { data } = await client.get('/orders/stats', { params: { hours } })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get order velocity'
    return { success: false, error: msg } as any
  }
}

export async function getCarrierPerformance(): Promise<ApiResponse<Record<string, any>>> {
  try {
    const { data } = await client.get('/carriers/kpis')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get carrier performance'
    return { success: false, error: msg } as any
  }
}

export async function getCostBreakdown(): Promise<ApiResponse<Record<string, any>>> {
  try {
    const { data } = await client.get('/shipping/kpis')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get cost breakdown'
    return { success: false, error: msg } as any
  }
}

export async function getLanePerformance(): Promise<ApiResponse<Record<string, any>>> {
  try {
    const { data } = await client.get('/carriers/rates')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get lane performance'
    return { success: false, error: msg } as any
  }
}

export async function getReturnsAnalytics(): Promise<ApiResponse<Record<string, any>>> {
  try {
    const { data } = await client.get('/returns/analytics')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get returns analytics'
    return { success: false, error: msg } as any
  }
}

export async function getActivity(): Promise<ApiResponse<any[]>> {
  try {
    const { data } = await client.get('/dashboard/activity')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get activity'
    return { success: false, error: msg } as any
  }
}

export async function getAlerts(): Promise<ApiResponse<any[]>> {
  try {
    const { data } = await client.get('/analytics/alerts')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get alerts'
    return { success: false, error: msg } as any
  }
}

export async function getOrderStatusDistribution(): Promise<ApiResponse<any[]>> {
  try {
    const { data } = await client.get('/analytics/order-status-distribution')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get order status distribution'
    return { success: false, error: msg } as any
  }
}

export async function getTaskQueueSummary(): Promise<ApiResponse<Record<string, any>>> {
  try {
    const { data } = await client.get('/analytics/task-queue-summary')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get task queue summary'
    return { success: false, error: msg } as any
  }
}

export async function getWarehousesSummary(): Promise<ApiResponse<any[]>> {
  try {
    const { data } = await client.get('/warehouses/summary')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get warehouses summary'
    return { success: false, error: msg } as any
  }
}
