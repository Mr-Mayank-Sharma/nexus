import axios from 'axios'
import { ApiResponse, AiSuggestion, AiActionHistory, AiExecuteRequest } from '../types'
export type { AiSuggestion, AiActionHistory, AiExecuteRequest }

const aiClient = axios.create({ baseURL: 'http://localhost:8081/api/v1', timeout: 30000 })

export async function getAiSuggestions(orderId: string): Promise<ApiResponse<AiSuggestion[]>> {
  const { data } = await aiClient.get(`/orders/${orderId}/ai-suggestions`)
  return data
}

export async function getAiHistory(orderId: string): Promise<ApiResponse<AiActionHistory[]>> {
  const { data } = await aiClient.get(`/orders/${orderId}/ai-history`)
  return data
}

export async function executeAiAction(orderId: string, payload: AiExecuteRequest): Promise<ApiResponse<AiActionHistory>> {
  const { data } = await aiClient.post(`/orders/${orderId}/ai-execute`, payload)
  return data
}
