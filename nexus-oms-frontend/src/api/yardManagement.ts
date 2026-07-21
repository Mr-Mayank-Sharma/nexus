import api from './client'

// Dock Doors
export async function getDockDoors(warehouseId: string) {
  return api.get(`/yards/docks?warehouseId=${warehouseId}`)
}

export async function getDockDoor(id: string) {
  return api.get(`/yards/docks/${id}`)
}

export async function createDockDoor(data: Record<string, unknown>) {
  return api.post('/yards/docks', data)
}

export async function updateDockDoor(id: string, data: Record<string, unknown>) {
  return api.put(`/yards/docks/${id}`, data)
}

export async function assignVehicleToDoor(doorId: string, data: Record<string, unknown>) {
  return api.post(`/yards/docks/${doorId}/assign`, null, { params: data })
}

export async function releaseDoor(doorId: string) {
  return api.post(`/yards/docks/${doorId}/release`)
}

export async function getDockUtilization(warehouseId: string) {
  return api.get(`/yards/docks/utilization?warehouseId=${warehouseId}`)
}

// Yard Locations
export async function getYardLocations(warehouseId: string) {
  return api.get(`/yards/locations?warehouseId=${warehouseId}`)
}

export async function getYardLocation(id: string) {
  return api.get(`/yards/locations/${id}`)
}

export async function createYardLocation(data: Record<string, unknown>) {
  return api.post('/yards/locations', data)
}

export async function assignToYard(locationId: string, data: Record<string, unknown>) {
  return api.post(`/yards/locations/${locationId}/assign`, null, { params: data })
}

export async function releaseYard(locationId: string) {
  return api.post(`/yards/locations/${locationId}/release`)
}

export async function getYardUtilization(warehouseId: string) {
  return api.get(`/yards/locations/utilization?warehouseId=${warehouseId}`)
}

// Appointments
export async function getAppointments(warehouseId: string, status?: string) {
  const params = new URLSearchParams({ warehouseId })
  if (status) params.set('status', status)
  return api.get(`/yards/appointments?${params.toString()}`)
}

export async function getAppointment(id: string) {
  return api.get(`/yards/appointments/${id}`)
}

export async function requestAppointment(data: Record<string, unknown>) {
  return api.post('/yards/appointments', data)
}

export async function confirmAppointment(id: string) {
  return api.post(`/yards/appointments/${id}/confirm`)
}

export async function checkInAppointment(id: string, checkedInBy: string) {
  return api.post(`/yards/appointments/${id}/check-in?checkedInBy=${checkedInBy}`)
}

export async function startAppointment(id: string) {
  return api.post(`/yards/appointments/${id}/start`)
}

export async function completeAppointment(id: string, completedBy: string) {
  return api.post(`/yards/appointments/${id}/complete?completedBy=${completedBy}`)
}

export async function cancelAppointment(id: string) {
  return api.post(`/yards/appointments/${id}/cancel`)
}

export async function markNoShow(id: string) {
  return api.post(`/yards/appointments/${id}/no-show`)
}

export async function getAppointmentCalendar(warehouseId: string, date: string) {
  return api.get(`/yards/appointments/calendar?warehouseId=${warehouseId}&date=${date}`)
}

export async function getAppointmentStats(warehouseId: string) {
  return api.get(`/yards/appointments/stats?warehouseId=${warehouseId}`)
}

export async function getTrailers(warehouseId: string, status?: string) {
  const params = new URLSearchParams({ warehouseId })
  if (status) params.set('status', status)
  return api.get(`/trailers?${params.toString()}`)
}

export async function getTrailer(id: string) {
  return api.get(`/trailers/${id}`)
}

export async function getTrailerEvents(id: string) {
  return api.get(`/trailers/${id}/events`)
}

export async function checkInTrailer(data: Record<string, unknown>) {
  const params = new URLSearchParams()
  Object.entries(data).forEach(([k, v]) => { if (v !== undefined && v !== null) params.set(k, String(v)) })
  return api.post(`/trailers/check-in?${params.toString()}`)
}

export async function dockTrailer(id: string, dockDoorId: string, performedBy?: string) {
  const params = new URLSearchParams({ dockDoorId })
  if (performedBy) params.set('performedBy', performedBy)
  return api.post(`/trailers/${id}/dock?${params.toString()}`)
}

export async function checkOutTrailer(id: string, loaded: boolean, palletCount: number, sealNumber?: string, performedBy?: string) {
  const params = new URLSearchParams({ loaded: String(loaded), palletCount: String(palletCount) })
  if (sealNumber) params.set('sealNumber', sealNumber)
  if (performedBy) params.set('performedBy', performedBy)
  return api.post(`/trailers/${id}/check-out?${params.toString()}`)
}

export async function getTrailerStats(warehouseId: string) {
  return api.get(`/trailers/stats?warehouseId=${warehouseId}`)
}
