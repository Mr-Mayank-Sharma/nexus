import api from './client'

export async function getRules(warehouseId: string) {
  return (await api.get(`/replenishment/rules?warehouseId=${warehouseId}`)).data
}

export async function getRule(id: string) {
  return (await api.get(`/replenishment/rules/${id}`)).data
}

export async function createRule(data: Record<string, unknown>) {
  return (await api.post('/replenishment/rules', data)).data
}

export async function updateRule(id: string, data: Record<string, unknown>) {
  return (await api.put(`/replenishment/rules/${id}`, data)).data
}

export async function deleteRule(id: string) {
  return (await api.delete(`/replenishment/rules/${id}`)).data
}

export async function getSuggestions(warehouseId: string, status?: string) {
  const params = new URLSearchParams({ warehouseId })
  if (status) params.set('status', status)
  return (await api.get(`/replenishment/suggestions?${params.toString()}`)).data
}

export async function getSuggestion(id: string) {
  return (await api.get(`/replenishment/suggestions/${id}`)).data
}

export async function approveSuggestion(id: string, approvedBy: string) {
  return (await api.post(`/replenishment/suggestions/${id}/approve?approvedBy=${approvedBy}`)).data
}

export async function rejectSuggestion(id: string, reason?: string) {
  const params = reason ? `?reason=${encodeURIComponent(reason)}` : ''
  return (await api.post(`/replenishment/suggestions/${id}/reject${params}`)).data
}

export async function generateSuggestions(warehouseId: string) {
  return (await api.post(`/replenishment/generate?warehouseId=${warehouseId}`)).data
}

export async function getStats(warehouseId: string) {
  return (await api.get(`/replenishment/stats?warehouseId=${warehouseId}`)).data
}
