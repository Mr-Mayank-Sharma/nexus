import api from './client'

export async function getSlottingAssignments(warehouseId: string) {
  return api.get(`/slotting/assignments?warehouseId=${warehouseId}`)
}

export async function getSlottingAssignment(id: string) {
  return api.get(`/slotting/assignments/${id}`)
}

export async function getSlottingRules(warehouseId: string) {
  return api.get(`/slotting/rules?warehouseId=${warehouseId}`)
}

export async function createSlottingRule(data: Record<string, unknown>) {
  return api.post('/slotting/rules', data)
}

export async function updateSlottingRule(id: string, data: Record<string, unknown>) {
  return api.put(`/slotting/rules/${id}`, data)
}

export async function toggleSlottingRule(id: string, isActive: boolean) {
  return api.put(`/slotting/rules/${id}/toggle?isActive=${isActive}`)
}

export async function analyzeSlotting(warehouseId: string) {
  return api.get(`/slotting/analyze?warehouseId=${warehouseId}`)
}

export async function optimizeSlotting(warehouseId: string) {
  return api.post(`/slotting/optimize?warehouseId=${warehouseId}`)
}

export async function reassignSku(data: Record<string, unknown>) {
  return api.post('/slotting/reassign', data)
}

export async function getVelocityAnalysis(warehouseId: string) {
  return api.get(`/slotting/velocity?warehouseId=${warehouseId}`)
}

export async function getSpaceUtilization(warehouseId: string) {
  return api.get(`/slotting/space?warehouseId=${warehouseId}`)
}

export async function getSlottingAuditLog(warehouseId: string, from?: string, to?: string) {
  const params = new URLSearchParams({ warehouseId })
  if (from) params.set('from', from)
  if (to) params.set('to', to)
  return api.get(`/slotting/audit?${params.toString()}`)
}
