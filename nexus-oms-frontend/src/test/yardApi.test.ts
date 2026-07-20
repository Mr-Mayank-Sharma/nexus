import { describe, it, expect, vi, beforeEach } from 'vitest'
import {
  getDockDoors,
  getDockDoor,
  createDockDoor,
  updateDockDoor,
  assignVehicleToDoor,
  releaseDoor,
  getDockUtilization,
  getYardLocations,
  getYardLocation,
  createYardLocation,
  assignToYard,
  releaseYard,
  getYardUtilization,
  getAppointments,
  getAppointment,
  requestAppointment,
  confirmAppointment,
  checkInAppointment,
  startAppointment,
  completeAppointment,
  cancelAppointment,
  markNoShow,
  getAppointmentCalendar,
  getAppointmentStats,
} from '../api/yardManagement'

const mockGet = vi.fn()
const mockPost = vi.fn()
const mockPut = vi.fn()

vi.mock('../api/client', () => ({
  default: {
    get: (...args: unknown[]) => mockGet(...args),
    post: (...args: unknown[]) => mockPost(...args),
    put: (...args: unknown[]) => mockPut(...args),
  },
}))

describe('Yard Management API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGet.mockResolvedValue({ data: { success: true } })
    mockPost.mockResolvedValue({ data: { success: true } })
    mockPut.mockResolvedValue({ data: { success: true } })
  })

  describe('getDockDoors', () => {
    it('should GET /yards/docks with warehouseId', async () => {
      await getDockDoors('wh-1')
      expect(mockGet).toHaveBeenCalledWith('/yards/docks?warehouseId=wh-1')
    })
  })

  describe('getDockDoor', () => {
    it('should GET /yards/docks/:id', async () => {
      await getDockDoor('door-1')
      expect(mockGet).toHaveBeenCalledWith('/yards/docks/door-1')
    })
  })

  describe('createDockDoor', () => {
    it('should POST /yards/docks with data', async () => {
      const data = { name: 'Door 1', type: 'inbound' }
      await createDockDoor(data)
      expect(mockPost).toHaveBeenCalledWith('/yards/docks', data)
    })
  })

  describe('updateDockDoor', () => {
    it('should PUT /yards/docks/:id with data', async () => {
      const data = { status: 'maintenance' }
      await updateDockDoor('door-1', data)
      expect(mockPut).toHaveBeenCalledWith('/yards/docks/door-1', data)
    })
  })

  describe('assignVehicleToDoor', () => {
    it('should POST /yards/docks/:doorId/assign with params', async () => {
      const data = { vehicleId: 'v-1', appointmentId: 'appt-1' }
      await assignVehicleToDoor('door-1', data)
      expect(mockPost).toHaveBeenCalledWith('/yards/docks/door-1/assign', null, { params: data })
    })
  })

  describe('releaseDoor', () => {
    it('should POST /yards/docks/:doorId/release', async () => {
      await releaseDoor('door-1')
      expect(mockPost).toHaveBeenCalledWith('/yards/docks/door-1/release')
    })
  })

  describe('getDockUtilization', () => {
    it('should GET /yards/docks/utilization with warehouseId', async () => {
      await getDockUtilization('wh-1')
      expect(mockGet).toHaveBeenCalledWith('/yards/docks/utilization?warehouseId=wh-1')
    })
  })

  describe('getYardLocations', () => {
    it('should GET /yards/locations with warehouseId', async () => {
      await getYardLocations('wh-1')
      expect(mockGet).toHaveBeenCalledWith('/yards/locations?warehouseId=wh-1')
    })
  })

  describe('getYardLocation', () => {
    it('should GET /yards/locations/:id', async () => {
      await getYardLocation('loc-1')
      expect(mockGet).toHaveBeenCalledWith('/yards/locations/loc-1')
    })
  })

  describe('createYardLocation', () => {
    it('should POST /yards/locations with data', async () => {
      const data = { name: 'Lot A', capacity: 50 }
      await createYardLocation(data)
      expect(mockPost).toHaveBeenCalledWith('/yards/locations', data)
    })
  })

  describe('assignToYard', () => {
    it('should POST /yards/locations/:locationId/assign with params', async () => {
      const data = { vehicleId: 'v-1' }
      await assignToYard('loc-1', data)
      expect(mockPost).toHaveBeenCalledWith('/yards/locations/loc-1/assign', null, { params: data })
    })
  })

  describe('releaseYard', () => {
    it('should POST /yards/locations/:locationId/release', async () => {
      await releaseYard('loc-1')
      expect(mockPost).toHaveBeenCalledWith('/yards/locations/loc-1/release')
    })
  })

  describe('getYardUtilization', () => {
    it('should GET /yards/locations/utilization with warehouseId', async () => {
      await getYardUtilization('wh-1')
      expect(mockGet).toHaveBeenCalledWith('/yards/locations/utilization?warehouseId=wh-1')
    })
  })

  describe('getAppointments', () => {
    it('should GET /yards/appointments with warehouseId only', async () => {
      await getAppointments('wh-1')
      expect(mockGet).toHaveBeenCalledWith('/yards/appointments?warehouseId=wh-1')
    })

    it('should include status param when provided', async () => {
      await getAppointments('wh-1', 'confirmed')
      expect(mockGet).toHaveBeenCalledWith('/yards/appointments?warehouseId=wh-1&status=confirmed')
    })
  })

  describe('getAppointment', () => {
    it('should GET /yards/appointments/:id', async () => {
      await getAppointment('appt-1')
      expect(mockGet).toHaveBeenCalledWith('/yards/appointments/appt-1')
    })
  })

  describe('requestAppointment', () => {
    it('should POST /yards/appointments with data', async () => {
      const data = { carrierId: 'c-1', scheduledAt: '2026-01-15T10:00:00Z' }
      await requestAppointment(data)
      expect(mockPost).toHaveBeenCalledWith('/yards/appointments', data)
    })
  })

  describe('confirmAppointment', () => {
    it('should POST /yards/appointments/:id/confirm', async () => {
      await confirmAppointment('appt-1')
      expect(mockPost).toHaveBeenCalledWith('/yards/appointments/appt-1/confirm')
    })
  })

  describe('checkInAppointment', () => {
    it('should POST /yards/appointments/:id/check-in with checkedInBy', async () => {
      await checkInAppointment('appt-1', 'user-1')
      expect(mockPost).toHaveBeenCalledWith(
        '/yards/appointments/appt-1/check-in?checkedInBy=user-1',
      )
    })
  })

  describe('startAppointment', () => {
    it('should POST /yards/appointments/:id/start', async () => {
      await startAppointment('appt-1')
      expect(mockPost).toHaveBeenCalledWith('/yards/appointments/appt-1/start')
    })
  })

  describe('completeAppointment', () => {
    it('should POST /yards/appointments/:id/complete with completedBy', async () => {
      await completeAppointment('appt-1', 'user-1')
      expect(mockPost).toHaveBeenCalledWith(
        '/yards/appointments/appt-1/complete?completedBy=user-1',
      )
    })
  })

  describe('cancelAppointment', () => {
    it('should POST /yards/appointments/:id/cancel', async () => {
      await cancelAppointment('appt-1')
      expect(mockPost).toHaveBeenCalledWith('/yards/appointments/appt-1/cancel')
    })
  })

  describe('markNoShow', () => {
    it('should POST /yards/appointments/:id/no-show', async () => {
      await markNoShow('appt-1')
      expect(mockPost).toHaveBeenCalledWith('/yards/appointments/appt-1/no-show')
    })
  })

  describe('getAppointmentCalendar', () => {
    it('should GET /yards/appointments/calendar with warehouseId and date', async () => {
      await getAppointmentCalendar('wh-1', '2026-01-15')
      expect(mockGet).toHaveBeenCalledWith(
        '/yards/appointments/calendar?warehouseId=wh-1&date=2026-01-15',
      )
    })
  })

  describe('getAppointmentStats', () => {
    it('should GET /yards/appointments/stats with warehouseId', async () => {
      await getAppointmentStats('wh-1')
      expect(mockGet).toHaveBeenCalledWith('/yards/appointments/stats?warehouseId=wh-1')
    })
  })
})
