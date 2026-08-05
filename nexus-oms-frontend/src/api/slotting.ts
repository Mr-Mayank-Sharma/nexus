import api from './client'

export async function getSlottingAssignments(warehouseId: string) {
  return (await api.get(`/slotting/assignments?warehouseId=${warehouseId}`)).data
}

export async function getSlottingAssignment(id: string) {
  return (await api.get(`/slotting/assignments/${id}`)).data
}

export async function getSlottingRules(warehouseId: string) {
  return (await api.get(`/slotting/rules?warehouseId=${warehouseId}`)).data
}

export async function createSlottingRule(data: Record<string, unknown>) {
  return (await api.post('/slotting/rules', data)).data
}

export async function updateSlottingRule(id: string, data: Record<string, unknown>) {
  return (await api.put(`/slotting/rules/${id}`, data)).data
}

export async function toggleSlottingRule(id: string, isActive: boolean) {
  return (await api.put(`/slotting/rules/${id}/toggle?isActive=${isActive}`)).data
}

export async function analyzeSlotting(warehouseId: string) {
  return (await api.get(`/slotting/analyze?warehouseId=${warehouseId}`)).data
}

export async function optimizeSlotting(warehouseId: string) {
  return (await api.post(`/slotting/optimize?warehouseId=${warehouseId}`)).data
}

export async function reassignSku(data: Record<string, unknown>) {
  return (await api.post('/slotting/reassign', data)).data
}

export async function getVelocityAnalysis(warehouseId: string) {
  return (await api.get(`/slotting/velocity?warehouseId=${warehouseId}`)).data
}

export async function getSpaceUtilization(warehouseId: string) {
  return (await api.get(`/slotting/space?warehouseId=${warehouseId}`)).data
}

export async function getSlottingAuditLog(warehouseId: string, from?: string, to?: string) {
  const params = new URLSearchParams({ warehouseId })
  if (from) params.set('from', from)
  if (to) params.set('to', to)
  return (await api.get(`/slotting/audit?${params.toString()}`)).data
}
