import axios from 'axios'
import { ApiResponse, AiSuggestion, AiActionHistory, AiExecuteRequest } from '../types'
export type { AiSuggestion, AiActionHistory, AiExecuteRequest }

const aiClient = axios.create({ baseURL: 'http://localhost:8081/api/v1', timeout: 30000 })

export async function getAiSuggestions(orderId: string): Promise<ApiResponse<AiSuggestion[]>> {
  try {
    const { data } = await aiClient.get(`/orders/${orderId}/ai-suggestions`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get AI suggestions'
    return { success: false, error: msg } as any
  }
}

export async function getAiHistory(orderId: string): Promise<ApiResponse<AiActionHistory[]>> {
  try {
    const { data } = await aiClient.get(`/orders/${orderId}/ai-history`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get AI history'
    return { success: false, error: msg } as any
  }
}

export async function executeAiAction(orderId: string, payload: AiExecuteRequest): Promise<ApiResponse<AiActionHistory>> {
  try {
    const { data } = await aiClient.post(`/orders/${orderId}/ai-execute`, payload)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to execute AI action'
    return { success: false, error: msg } as any
  }
}
