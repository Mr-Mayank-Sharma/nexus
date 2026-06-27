import client from './client'
import { ApiResponse, RoutingRule } from '../types'

export async function getRules(): Promise<ApiResponse<RoutingRule[]>> {
  const { data } = await client.get('/routing-rules')
  return data
}

export async function getRule(id: string): Promise<ApiResponse<RoutingRule>> {
  const { data } = await client.get(`/routing-rules/${id}`)
  return data
}

export async function createRule(rule: Partial<RoutingRule>): Promise<ApiResponse<RoutingRule>> {
  const { data } = await client.post('/routing-rules', rule)
  return data
}

export async function updateRule(id: string, rule: Partial<RoutingRule>): Promise<ApiResponse<RoutingRule>> {
  const { data } = await client.put(`/routing-rules/${id}`, rule)
  return data
}

export async function deleteRule(id: string): Promise<ApiResponse<null>> {
  const { data } = await client.delete(`/routing-rules/${id}`)
  return data
}

export async function reorderRules(ruleIds: string[]): Promise<ApiResponse<null>> {
  const { data } = await client.put('/routing-rules/reorder', ruleIds)
  return data
}
