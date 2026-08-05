import api from './client'

// Dock Doors
export async function getDockDoors(warehouseId: string) {
  return (await api.get(`/yards/docks?warehouseId=${warehouseId}`)).data
}

export async function getDockDoor(id: string) {
  return (await api.get(`/yards/docks/${id}`)).data
}

export async function createDockDoor(data: Record<string, unknown>) {
  return (await api.post('/yards/docks', data)).data
}

export async function updateDockDoor(id: string, data: Record<string, unknown>) {
  return (await api.put(`/yards/docks/${id}`, data)).data
}

export async function assignVehicleToDoor(doorId: string, data: Record<string, unknown>) {
  return (await api.post(`/yards/docks/${doorId}/assign`, null, { params: data })).data
}

export async function releaseDoor(doorId: string) {
  return (await api.post(`/yards/docks/${doorId}/release`)).data
}

export async function getDockUtilization(warehouseId: string) {
  return (await api.get(`/yards/docks/utilization?warehouseId=${warehouseId}`)).data
}

// Yard Locations
export async function getYardLocations(warehouseId: string) {
  return (await api.get(`/yards/locations?warehouseId=${warehouseId}`)).data
}

export async function getYardLocation(id: string) {
  return (await api.get(`/yards/locations/${id}`)).data
}

export async function createYardLocation(data: Record<string, unknown>) {
  return (await api.post('/yards/locations', data)).data
}

export async function assignToYard(locationId: string, data: Record<string, unknown>) {
  return (await api.post(`/yards/locations/${locationId}/assign`, null, { params: data })).data
}

export async function releaseYard(locationId: string) {
  return (await api.post(`/yards/locations/${locationId}/release`)).data
}

export async function getYardUtilization(warehouseId: string) {
  return (await api.get(`/yards/locations/utilization?warehouseId=${warehouseId}`)).data
}

// Appointments
export async function getAppointments(warehouseId: string, status?: string) {
  const params = new URLSearchParams({ warehouseId })
  if (status) params.set('status', status)
  return (await api.get(`/yards/appointments?${params.toString()}`)).data
}

export async function getAppointment(id: string) {
  return (await api.get(`/yards/appointments/${id}`)).data
}

export async function requestAppointment(data: Record<string, unknown>) {
  return (await api.post('/yards/appointments', data)).data
}

export async function confirmAppointment(id: string) {
  return (await api.post(`/yards/appointments/${id}/confirm`)).data
}

export async function checkInAppointment(id: string, checkedInBy: string) {
  return (await api.post(`/yards/appointments/${id}/check-in?checkedInBy=${checkedInBy}`)).data
}

export async function startAppointment(id: string) {
  return (await api.post(`/yards/appointments/${id}/start`)).data
}

export async function completeAppointment(id: string, completedBy: string) {
  return (await api.post(`/yards/appointments/${id}/complete?completedBy=${completedBy}`)).data
}

export async function cancelAppointment(id: string) {
  return (await api.post(`/yards/appointments/${id}/cancel`)).data
}

export async function markNoShow(id: string) {
  return (await api.post(`/yards/appointments/${id}/no-show`)).data
}

export async function getAppointmentCalendar(warehouseId: string, date: string) {
  return (await api.get(`/yards/appointments/calendar?warehouseId=${warehouseId}&date=${date}`)).data
}

export async function getAppointmentStats(warehouseId: string) {
  return (await api.get(`/yards/appointments/stats?warehouseId=${warehouseId}`)).data
}

export async function getTrailers(warehouseId: string, status?: string) {
  const params = new URLSearchParams({ warehouseId })
  if (status) params.set('status', status)
  return (await api.get(`/trailers?${params.toString()}`)).data
}

export async function getTrailer(id: string) {
  return (await api.get(`/trailers/${id}`)).data
}

export async function getTrailerEvents(id: string) {
  return (await api.get(`/trailers/${id}/events`)).data
}

export async function checkInTrailer(data: Record<string, unknown>) {
  const params = new URLSearchParams()
  Object.entries(data).forEach(([k, v]) => { if (v !== undefined && v !== null) params.set(k, String(v)) })
  return (await api.post(`/trailers/check-in?${params.toString()}`)).data
}

export async function dockTrailer(id: string, dockDoorId: string, performedBy?: string) {
  const params = new URLSearchParams({ dockDoorId })
  if (performedBy) params.set('performedBy', performedBy)
  return (await api.post(`/trailers/${id}/dock?${params.toString()}`)).data
}

export async function checkOutTrailer(id: string, loaded: boolean, palletCount: number, sealNumber?: string, performedBy?: string) {
  const params = new URLSearchParams({ loaded: String(loaded), palletCount: String(palletCount) })
  if (sealNumber) params.set('sealNumber', sealNumber)
  if (performedBy) params.set('performedBy', performedBy)
  return (await api.post(`/trailers/${id}/check-out?${params.toString()}`)).data
}

export async function getTrailerStats(warehouseId: string) {
  return (await api.get(`/trailers/stats?warehouseId=${warehouseId}`)).data
}
