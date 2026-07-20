import api from './client'

// Systems
export async function getAutomationSystems(warehouseId: string) {
  return api.get(`/automation/systems?warehouseId=${warehouseId}`)
}

export async function getAutomationSystem(id: string) {
  return api.get(`/automation/systems/${id}`)
}

export async function createAutomationSystem(data: Record<string, unknown>) {
  return api.post('/automation/systems', data)
}

export async function updateAutomationSystem(id: string, data: Record<string, unknown>) {
  return api.put(`/automation/systems/${id}`, data)
}

export async function toggleAutomationSystem(id: string, isActive: boolean) {
  return api.put(`/automation/systems/${id}/toggle?isActive=${isActive}`)
}

export async function getSystemHealth(warehouseId: string) {
  return api.get(`/automation/systems/health?warehouseId=${warehouseId}`)
}

// Commands
export async function sendCommand(data: Record<string, unknown>) {
  return api.post('/automation/commands', data)
}

export async function getCommands(params: { systemId?: string; status?: string }) {
  const searchParams = new URLSearchParams()
  if (params.systemId) searchParams.set('systemId', params.systemId)
  if (params.status) searchParams.set('status', params.status)
  const qs = searchParams.toString()
  return api.get(`/automation/commands${qs ? '?' + qs : ''}`)
}

export async function getCommand(id: string) {
  return api.get(`/automation/commands/${id}`)
}

export async function cancelCommand(id: string) {
  return api.post(`/automation/commands/${id}/cancel`)
}

export async function retryCommand(id: string) {
  return api.post(`/automation/commands/${id}/retry`)
}

export async function getCommandStats(warehouseId: string) {
  return api.get(`/automation/commands/stats?warehouseId=${warehouseId}`)
}

// Logs
export async function getLogs(params: { systemId?: string; level?: string; from?: string; to?: string }) {
  const searchParams = new URLSearchParams()
  if (params.systemId) searchParams.set('systemId', params.systemId)
  if (params.level) searchParams.set('level', params.level)
  if (params.from) searchParams.set('from', params.from)
  if (params.to) searchParams.set('to', params.to)
  const qs = searchParams.toString()
  return api.get(`/automation/logs${qs ? '?' + qs : ''}`)
}

export async function getRecentLogs(warehouseId: string) {
  return api.get(`/automation/logs/recent?warehouseId=${warehouseId}`)
}

// Alerts
export async function getAlerts(warehouseId: string, status?: string) {
  const params = new URLSearchParams({ warehouseId })
  if (status) params.set('status', status)
  return api.get(`/automation/alerts?${params.toString()}`)
}

export async function acknowledgeAlert(id: string, acknowledgedBy: string) {
  return api.put(`/automation/alerts/${id}/acknowledge?acknowledgedBy=${acknowledgedBy}`)
}

export async function resolveAlert(id: string, resolutionNotes: string) {
  return api.put(`/automation/alerts/${id}/resolve?resolutionNotes=${encodeURIComponent(resolutionNotes)}`)
}

export async function getAlertStats(warehouseId: string) {
  return api.get(`/automation/alerts/stats?warehouseId=${warehouseId}`)
}

// Integration helpers
export async function executePick(systemId: string, binLocation: string, quantity: number, destination: string) {
  return api.post(`/automation/integration/pick?systemId=${systemId}&binLocation=${binLocation}&quantity=${quantity}&destination=${destination}`)
}

export async function executeSort(systemId: string, packageId: string, destinationChute: string) {
  return api.post(`/automation/integration/sort?systemId=${systemId}&packageId=${packageId}&destinationChute=${destinationChute}`)
}

export async function executeConvey(systemId: string, packageId: string, destinationZone: string) {
  return api.post(`/automation/integration/convey?systemId=${systemId}&packageId=${packageId}&destinationZone=${destinationZone}`)
}

export async function getAutomationStatus(systemId: string) {
  return api.get(`/automation/integration/status?systemId=${systemId}`)
}
