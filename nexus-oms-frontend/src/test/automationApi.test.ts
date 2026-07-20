import { describe, it, expect, vi, beforeEach } from 'vitest'
import {
  getAutomationSystems,
  getAutomationSystem,
  createAutomationSystem,
  updateAutomationSystem,
  toggleAutomationSystem,
  getSystemHealth,
  sendCommand,
  getCommands,
  getCommand,
  cancelCommand,
  retryCommand,
  getCommandStats,
  getLogs,
  getRecentLogs,
  getAlerts,
  acknowledgeAlert,
  resolveAlert,
  getAlertStats,
} from '../api/automation'

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

describe('Automation API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGet.mockResolvedValue({ data: { success: true } })
    mockPost.mockResolvedValue({ data: { success: true } })
    mockPut.mockResolvedValue({ data: { success: true } })
  })

  describe('getAutomationSystems', () => {
    it('should GET /automation/systems with warehouseId', async () => {
      await getAutomationSystems('wh-1')
      expect(mockGet).toHaveBeenCalledWith('/automation/systems?warehouseId=wh-1')
    })
  })

  describe('getAutomationSystem', () => {
    it('should GET /automation/systems/:id', async () => {
      await getAutomationSystem('sys-1')
      expect(mockGet).toHaveBeenCalledWith('/automation/systems/sys-1')
    })
  })

  describe('createAutomationSystem', () => {
    it('should POST /automation/systems with data', async () => {
      const data = { name: 'Conveyor A', type: 'conveyor' }
      await createAutomationSystem(data)
      expect(mockPost).toHaveBeenCalledWith('/automation/systems', data)
    })
  })

  describe('updateAutomationSystem', () => {
    it('should PUT /automation/systems/:id with data', async () => {
      const data = { throughput: 500 }
      await updateAutomationSystem('sys-1', data)
      expect(mockPut).toHaveBeenCalledWith('/automation/systems/sys-1', data)
    })
  })

  describe('toggleAutomationSystem', () => {
    it('should PUT /automation/systems/:id/toggle with isActive=true', async () => {
      await toggleAutomationSystem('sys-1', true)
      expect(mockPut).toHaveBeenCalledWith('/automation/systems/sys-1/toggle?isActive=true')
    })

    it('should PUT with isActive=false', async () => {
      await toggleAutomationSystem('sys-1', false)
      expect(mockPut).toHaveBeenCalledWith('/automation/systems/sys-1/toggle?isActive=false')
    })
  })

  describe('getSystemHealth', () => {
    it('should GET /automation/systems/health with warehouseId', async () => {
      await getSystemHealth('wh-1')
      expect(mockGet).toHaveBeenCalledWith('/automation/systems/health?warehouseId=wh-1')
    })
  })

  describe('sendCommand', () => {
    it('should POST /automation/commands with data', async () => {
      const data = { systemId: 'sys-1', type: 'pick', params: {} }
      await sendCommand(data)
      expect(mockPost).toHaveBeenCalledWith('/automation/commands', data)
    })
  })

  describe('getCommands', () => {
    it('should GET /automation/commands with no params', async () => {
      await getCommands({})
      expect(mockGet).toHaveBeenCalledWith('/automation/commands')
    })

    it('should include systemId and status when provided', async () => {
      await getCommands({ systemId: 'sys-1', status: 'pending' })
      expect(mockGet).toHaveBeenCalledWith('/automation/commands?systemId=sys-1&status=pending')
    })

    it('should include only systemId when status is absent', async () => {
      await getCommands({ systemId: 'sys-1' })
      expect(mockGet).toHaveBeenCalledWith('/automation/commands?systemId=sys-1')
    })
  })

  describe('getCommand', () => {
    it('should GET /automation/commands/:id', async () => {
      await getCommand('cmd-1')
      expect(mockGet).toHaveBeenCalledWith('/automation/commands/cmd-1')
    })
  })

  describe('cancelCommand', () => {
    it('should POST /automation/commands/:id/cancel', async () => {
      await cancelCommand('cmd-1')
      expect(mockPost).toHaveBeenCalledWith('/automation/commands/cmd-1/cancel')
    })
  })

  describe('retryCommand', () => {
    it('should POST /automation/commands/:id/retry', async () => {
      await retryCommand('cmd-1')
      expect(mockPost).toHaveBeenCalledWith('/automation/commands/cmd-1/retry')
    })
  })

  describe('getCommandStats', () => {
    it('should GET /automation/commands/stats with warehouseId', async () => {
      await getCommandStats('wh-1')
      expect(mockGet).toHaveBeenCalledWith('/automation/commands/stats?warehouseId=wh-1')
    })
  })

  describe('getLogs', () => {
    it('should GET /automation/logs with no params', async () => {
      await getLogs({})
      expect(mockGet).toHaveBeenCalledWith('/automation/logs')
    })

    it('should build query string from provided params', async () => {
      await getLogs({ systemId: 'sys-1', level: 'error', from: '2026-01-01', to: '2026-01-31' })
      expect(mockGet).toHaveBeenCalledWith(
        '/automation/logs?systemId=sys-1&level=error&from=2026-01-01&to=2026-01-31',
      )
    })
  })

  describe('getRecentLogs', () => {
    it('should GET /automation/logs/recent with warehouseId', async () => {
      await getRecentLogs('wh-1')
      expect(mockGet).toHaveBeenCalledWith('/automation/logs/recent?warehouseId=wh-1')
    })
  })

  describe('getAlerts', () => {
    it('should GET /automation/alerts with warehouseId only', async () => {
      await getAlerts('wh-1')
      expect(mockGet).toHaveBeenCalledWith('/automation/alerts?warehouseId=wh-1')
    })

    it('should include status when provided', async () => {
      await getAlerts('wh-1', 'firing')
      expect(mockGet).toHaveBeenCalledWith('/automation/alerts?warehouseId=wh-1&status=firing')
    })
  })

  describe('acknowledgeAlert', () => {
    it('should PUT /automation/alerts/:id/acknowledge with acknowledgedBy', async () => {
      await acknowledgeAlert('alert-1', 'user-1')
      expect(mockPut).toHaveBeenCalledWith(
        '/automation/alerts/alert-1/acknowledge?acknowledgedBy=user-1',
      )
    })
  })

  describe('resolveAlert', () => {
    it('should PUT /automation/alerts/:id/resolve with encoded resolutionNotes', async () => {
      await resolveAlert('alert-1', 'Fixed by restarting')
      expect(mockPut).toHaveBeenCalledWith(
        '/automation/alerts/alert-1/resolve?resolutionNotes=Fixed%20by%20restarting',
      )
    })
  })

  describe('getAlertStats', () => {
    it('should GET /automation/alerts/stats with warehouseId', async () => {
      await getAlertStats('wh-1')
      expect(mockGet).toHaveBeenCalledWith('/automation/alerts/stats?warehouseId=wh-1')
    })
  })
})
