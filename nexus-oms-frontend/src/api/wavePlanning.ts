import api from './client'

export async function getWaves(params?: { status?: string; warehouseId?: string }) {
  const searchParams = new URLSearchParams()
  if (params?.status) searchParams.set('status', params.status)
  if (params?.warehouseId) searchParams.set('warehouseId', params.warehouseId)
  const qs = searchParams.toString()
  return (await api.get(`/waves${qs ? '?' + qs : ''}`)).data
}

export async function getWave(id: string) {
  return (await api.get(`/waves/${id}`)).data
}

export async function createWave(data: Record<string, unknown>) {
  return (await api.post('/waves', data)).data
}

export async function updateWave(id: string, data: Record<string, unknown>) {
  return (await api.put(`/waves/${id}`, data)).data
}

export async function addWaveRule(waveId: string, data: Record<string, unknown>) {
  return (await api.post(`/waves/${waveId}/rules`, data)).data
}

export async function removeWaveRule(ruleId: string) {
  return (await api.delete(`/waves/rules/${ruleId}`)).data
}

export async function planWave(id: string) {
  return (await api.post(`/waves/${id}/plan`)).data
}

export async function releaseWave(id: string, releasedBy: string) {
  return (await api.post(`/waves/${id}/release?releasedBy=${releasedBy}`)).data
}

export async function pauseWave(id: string) {
  return (await api.post(`/waves/${id}/pause`)).data
}

export async function resumeWave(id: string) {
  return (await api.post(`/waves/${id}/resume`)).data
}

export async function completeWave(id: string) {
  return (await api.post(`/waves/${id}/complete`)).data
}

export async function cancelWave(id: string) {
  return (await api.post(`/waves/${id}/cancel`)).data
}

export async function optimizeWave(id: string) {
  return (await api.post(`/waves/${id}/optimize`)).data
}

export async function getWaveStats(warehouseId?: string) {
  const qs = warehouseId ? `?warehouseId=${warehouseId}` : ''
  return (await api.get(`/waves/stats${qs}`)).data
}
