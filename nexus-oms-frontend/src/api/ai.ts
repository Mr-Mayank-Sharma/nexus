import client from './client'
import { ApiResponse, AiModel } from '../types'

export async function getModelInfo(): Promise<ApiResponse<AiModel[]>> {
  try {
    const { data } = await client.get('/ai/models')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get model info'
    return { success: false, error: msg } as any
  }
}

export async function predictCarrier(payload: Record<string, any>): Promise<ApiResponse<Record<string, any>>> {
  try {
    const { data } = await client.post('/ai/predict/carrier', payload)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to predict carrier'
    return { success: false, error: msg } as any
  }
}

export async function predictDemand(payload: Record<string, any>): Promise<ApiResponse<Record<string, any>>> {
  try {
    const { data } = await client.post('/ai/predict/demand', payload)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to predict demand'
    return { success: false, error: msg } as any
  }
}

export async function predictInventory(payload: Record<string, any>): Promise<ApiResponse<Record<string, any>>> {
  try {
    const { data } = await client.post('/ai/predict/inventory', payload)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to predict inventory'
    return { success: false, error: msg } as any
  }
}
