import api from './client'

export async function clockIn(staffId: string, warehouseId: string) {
  return (await api.post(`/labor/clock-in?staffId=${staffId}&warehouseId=${warehouseId}`)).data
}

export async function clockOut(laborEntryId: string) {
  return (await api.post(`/labor/${laborEntryId}/clock-out`)).data
}

export async function startBreak(laborEntryId: string) {
  return (await api.post(`/labor/${laborEntryId}/break/start`)).data
}

export async function endBreak(laborEntryId: string) {
  return (await api.post(`/labor/${laborEntryId}/break/end`)).data
}

export async function updateProgress(laborEntryId: string, data: Record<string, unknown>) {
  return (await api.put(`/labor/${laborEntryId}/progress`, data)).data
}

export async function assignTask(laborEntryId: string, taskType: string, waveId?: string) {
  const params = `?taskType=${taskType}${waveId ? '&waveId=' + waveId : ''}`
  return (await api.post(`/labor/${laborEntryId}/assign${params}`)).data
}

export async function getActiveWorkers(warehouseId: string) {
  return (await api.get(`/labor/workers/active?warehouseId=${warehouseId}`)).data
}

export async function getLaborStats(warehouseId: string, date?: string) {
  const params = `?warehouseId=${warehouseId}${date ? '&date=' + date : ''}`
  return (await api.get(`/labor/stats${params}`)).data
}

export async function getEfficiencyByWorker(warehouseId: string, date?: string) {
  const params = `?warehouseId=${warehouseId}${date ? '&date=' + date : ''}`
  return (await api.get(`/labor/efficiency/by-worker${params}`)).data
}

export async function getEfficiencyByShift(warehouseId: string, date?: string) {
  const params = `?warehouseId=${warehouseId}${date ? '&date=' + date : ''}`
  return (await api.get(`/labor/efficiency/by-shift${params}`)).data
}

export async function getEfficiencyByTaskType(warehouseId: string, date?: string) {
  const params = `?warehouseId=${warehouseId}${date ? '&date=' + date : ''}`
  return (await api.get(`/labor/efficiency/by-task${params}`)).data
}

export async function getShiftSchedules(warehouseId: string, date?: string) {
  const params = `?warehouseId=${warehouseId}${date ? '&date=' + date : ''}`
  return (await api.get(`/labor/schedules${params}`)).data
}

export async function createShiftSchedule(data: Record<string, unknown>) {
  return (await api.post('/labor/schedules', data)).data
}

export async function getEngineeredStandards(warehouseId: string) {
  return (await api.get(`/labor/standards?warehouseId=${warehouseId}`)).data
}

export async function createEngineeredStandard(data: Record<string, unknown>) {
  return (await api.post('/labor/standards', data)).data
}

export async function calculateIncentivePay(laborEntryId: string, warehouseId: string) {
  return (await api.get(`/labor/${laborEntryId}/incentive?warehouseId=${warehouseId}`)).data
}

export async function getWorkloadRules(warehouseId: string) {
  return (await api.get(`/labor/workload-rules?warehouseId=${warehouseId}`)).data
}

export async function createWorkloadRule(data: Record<string, unknown>) {
  return (await api.post('/labor/workload-rules', data)).data
}

export async function updateWorkloadRule(id: string, data: Record<string, unknown>) {
  return (await api.put(`/labor/workload-rules/${id}`, data)).data
}

export async function getWorkloadBalance(warehouseId: string) {
  return (await api.get(`/labor/workload/balance?warehouseId=${warehouseId}`)).data
}

export async function rebalanceWorkload(warehouseId: string) {
  return (await api.get(`/labor/workload/rebalance?warehouseId=${warehouseId}`)).data
}

export async function calculatePerformanceVsStandard(warehouseId: string, date: string) {
  return (await api.get(`/labor/performance/vs-standard?warehouseId=${warehouseId}&date=${date}`)).data
}

export async function logProductivity(data: Record<string, unknown>) {
  return (await api.post('/labor/productivity', data)).data
}

export async function getProductivityLogs(warehouseId: string, daysBack: number = 7) {
  return (await api.get(`/labor/productivity?warehouseId=${warehouseId}&daysBack=${daysBack}`)).data
}

export async function getProductivityByTaskType(warehouseId: string, daysBack: number = 7) {
  return (await api.get(`/labor/productivity/by-task?warehouseId=${warehouseId}&daysBack=${daysBack}`)).data
}
