import client from './client'
import { ApiResponse, AiSuggestion, AiActionHistory, AiExecuteRequest } from '../types'
export type { AiSuggestion, AiActionHistory, AiExecuteRequest }

export async function getAiSuggestions(orderId: string): Promise<ApiResponse<AiSuggestion[]>> {
  const { data } = await client.get(`/orders/${orderId}/ai-suggestions`)
  return data
}

export async function getAiHistory(orderId: string): Promise<ApiResponse<AiActionHistory[]>> {
  const { data } = await client.get(`/orders/${orderId}/ai-history`)
  return data
}

export async function executeAiAction(orderId: string, payload: AiExecuteRequest): Promise<ApiResponse<AiActionHistory>> {
  const { data } = await client.post(`/orders/${orderId}/ai-execute`, payload)
  return data
}
