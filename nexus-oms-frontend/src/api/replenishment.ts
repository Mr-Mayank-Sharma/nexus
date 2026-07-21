import api from './client'

export async function getRules(warehouseId: string) {
  return api.get(`/replenishment/rules?warehouseId=${warehouseId}`)
}

export async function getRule(id: string) {
  return api.get(`/replenishment/rules/${id}`)
}

export async function createRule(data: Record<string, unknown>) {
  return api.post('/replenishment/rules', data)
}

export async function updateRule(id: string, data: Record<string, unknown>) {
  return api.put(`/replenishment/rules/${id}`, data)
}

export async function deleteRule(id: string) {
  return api.delete(`/replenishment/rules/${id}`)
}

export async function getSuggestions(warehouseId: string, status?: string) {
  const params = new URLSearchParams({ warehouseId })
  if (status) params.set('status', status)
  return api.get(`/replenishment/suggestions?${params.toString()}`)
}

export async function getSuggestion(id: string) {
  return api.get(`/replenishment/suggestions/${id}`)
}

export async function approveSuggestion(id: string, approvedBy: string) {
  return api.post(`/replenishment/suggestions/${id}/approve?approvedBy=${approvedBy}`)
}

export async function rejectSuggestion(id: string, reason?: string) {
  const params = reason ? `?reason=${encodeURIComponent(reason)}` : ''
  return api.post(`/replenishment/suggestions/${id}/reject${params}`)
}

export async function generateSuggestions(warehouseId: string) {
  return api.post(`/replenishment/generate?warehouseId=${warehouseId}`)
}

export async function getStats(warehouseId: string) {
  return api.get(`/replenishment/stats?warehouseId=${warehouseId}`)
}
