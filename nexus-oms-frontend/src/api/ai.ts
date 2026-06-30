import client from './client'
import { ApiResponse, AiModel } from '../types'

export async function getModelInfo(): Promise<ApiResponse<AiModel[]>> {
  const { data } = await client.get('/api/ai/models')
  return data
}

export async function predictCarrier(payload: Record<string, any>): Promise<ApiResponse<Record<string, any>>> {
  const { data } = await client.post('/ai/predict/carrier', payload)
  return data
}

export async function predictDemand(payload: Record<string, any>): Promise<ApiResponse<Record<string, any>>> {
  const { data } = await client.post('/ai/predict/demand', payload)
  return data
}

export async function predictInventory(payload: Record<string, any>): Promise<ApiResponse<Record<string, any>>> {
  const { data } = await client.post('/ai/predict/inventory', payload)
  return data
}
