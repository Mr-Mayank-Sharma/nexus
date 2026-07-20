import api from './client'

export async function getWaves(params?: { status?: string; warehouseId?: string }) {
  const searchParams = new URLSearchParams()
  if (params?.status) searchParams.set('status', params.status)
  if (params?.warehouseId) searchParams.set('warehouseId', params.warehouseId)
  const qs = searchParams.toString()
  return api.get(`/waves${qs ? '?' + qs : ''}`)
}

export async function getWave(id: string) {
  return api.get(`/waves/${id}`)
}

export async function createWave(data: Record<string, unknown>) {
  return api.post('/waves', data)
}

export async function updateWave(id: string, data: Record<string, unknown>) {
  return api.put(`/waves/${id}`, data)
}

export async function addWaveRule(waveId: string, data: Record<string, unknown>) {
  return api.post(`/waves/${waveId}/rules`, data)
}

export async function removeWaveRule(ruleId: string) {
  return api.delete(`/waves/rules/${ruleId}`)
}

export async function planWave(id: string) {
  return api.post(`/waves/${id}/plan`)
}

export async function releaseWave(id: string, releasedBy: string) {
  return api.post(`/waves/${id}/release?releasedBy=${releasedBy}`)
}

export async function pauseWave(id: string) {
  return api.post(`/waves/${id}/pause`)
}

export async function resumeWave(id: string) {
  return api.post(`/waves/${id}/resume`)
}

export async function completeWave(id: string) {
  return api.post(`/waves/${id}/complete`)
}

export async function cancelWave(id: string) {
  return api.post(`/waves/${id}/cancel`)
}

export async function optimizeWave(id: string) {
  return api.post(`/waves/${id}/optimize`)
}

export async function getWaveStats(warehouseId?: string) {
  const qs = warehouseId ? `?warehouseId=${warehouseId}` : ''
  return api.get(`/waves/stats${qs}`)
}
