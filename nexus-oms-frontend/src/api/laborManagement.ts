import api from './client'

export async function clockIn(staffId: string, warehouseId: string) {
  return api.post(`/labor/clock-in?staffId=${staffId}&warehouseId=${warehouseId}`)
}

export async function clockOut(laborEntryId: string) {
  return api.post(`/labor/${laborEntryId}/clock-out`)
}

export async function startBreak(laborEntryId: string) {
  return api.post(`/labor/${laborEntryId}/break/start`)
}

export async function endBreak(laborEntryId: string) {
  return api.post(`/labor/${laborEntryId}/break/end`)
}

export async function updateProgress(laborEntryId: string, data: Record<string, unknown>) {
  return api.put(`/labor/${laborEntryId}/progress`, data)
}

export async function assignTask(laborEntryId: string, taskType: string, waveId?: string) {
  const params = `?taskType=${taskType}${waveId ? '&waveId=' + waveId : ''}`
  return api.post(`/labor/${laborEntryId}/assign${params}`)
}

export async function getActiveWorkers(warehouseId: string) {
  return api.get(`/labor/workers/active?warehouseId=${warehouseId}`)
}

export async function getLaborStats(warehouseId: string, date?: string) {
  const params = `?warehouseId=${warehouseId}${date ? '&date=' + date : ''}`
  return api.get(`/labor/stats${params}`)
}

export async function getEfficiencyByWorker(warehouseId: string, date?: string) {
  const params = `?warehouseId=${warehouseId}${date ? '&date=' + date : ''}`
  return api.get(`/labor/efficiency/by-worker${params}`)
}

export async function getEfficiencyByShift(warehouseId: string, date?: string) {
  const params = `?warehouseId=${warehouseId}${date ? '&date=' + date : ''}`
  return api.get(`/labor/efficiency/by-shift${params}`)
}

export async function getEfficiencyByTaskType(warehouseId: string, date?: string) {
  const params = `?warehouseId=${warehouseId}${date ? '&date=' + date : ''}`
  return api.get(`/labor/efficiency/by-task${params}`)
}

export async function getShiftSchedules(warehouseId: string, date?: string) {
  const params = `?warehouseId=${warehouseId}${date ? '&date=' + date : ''}`
  return api.get(`/labor/schedules${params}`)
}

export async function createShiftSchedule(data: Record<string, unknown>) {
  return api.post('/labor/schedules', data)
}

export async function getEngineeredStandards(warehouseId: string) {
  return api.get(`/labor/standards?warehouseId=${warehouseId}`)
}

export async function createEngineeredStandard(data: Record<string, unknown>) {
  return api.post('/labor/standards', data)
}

export async function calculateIncentivePay(laborEntryId: string, warehouseId: string) {
  return api.get(`/labor/${laborEntryId}/incentive?warehouseId=${warehouseId}`)
}

export async function getWorkloadRules(warehouseId: string) {
  return api.get(`/labor/workload-rules?warehouseId=${warehouseId}`)
}

export async function createWorkloadRule(data: Record<string, unknown>) {
  return api.post('/labor/workload-rules', data)
}

export async function updateWorkloadRule(id: string, data: Record<string, unknown>) {
  return api.put(`/labor/workload-rules/${id}`, data)
}

export async function getWorkloadBalance(warehouseId: string) {
  return api.get(`/labor/workload/balance?warehouseId=${warehouseId}`)
}

export async function rebalanceWorkload(warehouseId: string) {
  return api.get(`/labor/workload/rebalance?warehouseId=${warehouseId}`)
}

export async function calculatePerformanceVsStandard(warehouseId: string, date: string) {
  return api.get(`/labor/performance/vs-standard?warehouseId=${warehouseId}&date=${date}`)
}

export async function logProductivity(data: Record<string, unknown>) {
  return api.post('/labor/productivity', data)
}

export async function getProductivityLogs(warehouseId: string, daysBack: number = 7) {
  return api.get(`/labor/productivity?warehouseId=${warehouseId}&daysBack=${daysBack}`)
}

export async function getProductivityByTaskType(warehouseId: string, daysBack: number = 7) {
  return api.get(`/labor/productivity/by-task?warehouseId=${warehouseId}&daysBack=${daysBack}`)
}
