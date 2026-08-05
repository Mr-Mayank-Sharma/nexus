import api from './client'

// Systems
export async function getAutomationSystems(warehouseId: string) {
  return (await api.get(`/automation/systems?warehouseId=${warehouseId}`)).data
}

export async function getAutomationSystem(id: string) {
  return (await api.get(`/automation/systems/${id}`)).data
}

export async function createAutomationSystem(data: Record<string, unknown>) {
  return (await api.post('/automation/systems', data)).data
}

export async function updateAutomationSystem(id: string, data: Record<string, unknown>) {
  return (await api.put(`/automation/systems/${id}`, data)).data
}

export async function toggleAutomationSystem(id: string, isActive: boolean) {
  return (await api.put(`/automation/systems/${id}/toggle?isActive=${isActive}`)).data
}

export async function getSystemHealth(warehouseId: string) {
  return (await api.get(`/automation/systems/health?warehouseId=${warehouseId}`)).data
}

// Commands
export async function sendCommand(data: Record<string, unknown>) {
  return (await api.post('/automation/commands', data)).data
}

export async function getCommands(params: { systemId?: string; status?: string }) {
  const searchParams = new URLSearchParams()
  if (params.systemId) searchParams.set('systemId', params.systemId)
  if (params.status) searchParams.set('status', params.status)
  const qs = searchParams.toString()
  return (await api.get(`/automation/commands${qs ? '?' + qs : ''}`)).data
}

export async function getCommand(id: string) {
  return (await api.get(`/automation/commands/${id}`)).data
}

export async function cancelCommand(id: string) {
  return (await api.post(`/automation/commands/${id}/cancel`)).data
}

export async function retryCommand(id: string) {
  return (await api.post(`/automation/commands/${id}/retry`)).data
}

export async function getCommandStats(warehouseId: string) {
  return (await api.get(`/automation/commands/stats?warehouseId=${warehouseId}`)).data
}

// Logs
export async function getLogs(params: { systemId?: string; level?: string; from?: string; to?: string }) {
  const searchParams = new URLSearchParams()
  if (params.systemId) searchParams.set('systemId', params.systemId)
  if (params.level) searchParams.set('level', params.level)
  if (params.from) searchParams.set('from', params.from)
  if (params.to) searchParams.set('to', params.to)
  const qs = searchParams.toString()
  return (await api.get(`/automation/logs${qs ? '?' + qs : ''}`)).data
}

export async function getRecentLogs(warehouseId: string) {
  return (await api.get(`/automation/logs/recent?warehouseId=${warehouseId}`)).data
}

// Alerts
export async function getAlerts(warehouseId: string, status?: string) {
  const params = new URLSearchParams({ warehouseId })
  if (status) params.set('status', status)
  return (await api.get(`/automation/alerts?${params.toString()}`)).data
}

export async function acknowledgeAlert(id: string, acknowledgedBy: string) {
  return (await api.put(`/automation/alerts/${id}/acknowledge?acknowledgedBy=${acknowledgedBy}`)).data
}

export async function resolveAlert(id: string, resolutionNotes: string) {
  return (await api.put(`/automation/alerts/${id}/resolve?resolutionNotes=${encodeURIComponent(resolutionNotes)}`)).data
}

export async function getAlertStats(warehouseId: string) {
  return (await api.get(`/automation/alerts/stats?warehouseId=${warehouseId}`)).data
}

// Integration helpers
export async function executePick(systemId: string, binLocation: string, quantity: number, destination: string) {
  return (await api.post(`/automation/integration/pick?systemId=${systemId}&binLocation=${binLocation}&quantity=${quantity}&destination=${destination}`)).data
}

export async function executeSort(systemId: string, packageId: string, destinationChute: string) {
  return (await api.post(`/automation/integration/sort?systemId=${systemId}&packageId=${packageId}&destinationChute=${destinationChute}`)).data
}

export async function executeConvey(systemId: string, packageId: string, destinationZone: string) {
  return (await api.post(`/automation/integration/convey?systemId=${systemId}&packageId=${packageId}&destinationZone=${destinationZone}`)).data
}

export async function getAutomationStatus(systemId: string) {
  return (await api.get(`/automation/integration/status?systemId=${systemId}`)).data
}
