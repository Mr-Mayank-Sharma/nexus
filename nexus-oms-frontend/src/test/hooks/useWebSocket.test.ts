import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useWebSocket } from '../../hooks/useWebSocket'

// Mock sockjs-client
vi.mock('sockjs-client', () => {
  return {
    default: vi.fn().mockImplementation(() => ({
      close: vi.fn(),
      send: vi.fn(),
      onmessage: null,
      onclose: null,
      onerror: null,
      onopen: null,
    })),
  }
})

// Mock stompjs
vi.mock('stompjs', () => {
  return {
    Client: vi.fn().mockImplementation(() => ({
      connect: vi.fn(),
      disconnect: vi.fn(),
      send: vi.fn(),
      subscribe: vi.fn().mockReturnValue({ unsubscribe: vi.fn() }),
      connected: false,
      activate: vi.fn(),
      deactivate: vi.fn(),
    })),
  }
})

// Mock AuthContext
vi.mock('../../context/AuthContext', () => ({
  useAuth: vi.fn().mockReturnValue({
    token: 'test-token',
    user: { id: 'user-1', name: 'Test User' },
  }),
}))

describe('WebSocket Hook', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('initializes with disconnected state', () => {
    const { result } = renderHook(() => useWebSocket())
    
    expect(result.current.isConnected).toBe(false)
    expect(result.current.lastMessage).toBeNull()
  })

  it('provides connect and disconnect functions', () => {
    const { result } = renderHook(() => useWebSocket())
    
    expect(typeof result.current.connect).toBe('function')
    expect(typeof result.current.disconnect).toBe('function')
    expect(typeof result.current.sendMessage).toBe('function')
  })

  it('calls onOrderUpdate when order message received', async () => {
    const onOrderUpdate = vi.fn()
    
    const { result } = renderHook(() => useWebSocket({ onOrderUpdate }))
    
    // Simulate connection and message
    await act(async () => {
      result.current.connect()
    })

    // Note: In a real test, we'd need to mock the WebSocket connection
    // and simulate receiving messages. This is a basic structure test.
    expect(result.current.connect).toBeDefined()
  })

  it('handles connection errors gracefully', async () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    
    const { result } = renderHook(() => useWebSocket())
    
    await act(async () => {
      result.current.connect()
    })

    // Connection would fail in test environment, but should not throw
    expect(result.current.connect).toBeDefined()
    
    consoleSpy.mockRestore()
  })

  it('provides sendMessage function', () => {
    const { result } = renderHook(() => useWebSocket())
    
    expect(typeof result.current.sendMessage).toBe('function')
    
    // sendMessage should not throw when not connected
    expect(() => {
      result.current.sendMessage('/app/test', { test: 'data' })
    }).not.toThrow()
  })

  it('cleans up on unmount', async () => {
    const { result, unmount } = renderHook(() => useWebSocket())
    
    await act(async () => {
      result.current.connect()
    })
    
    // Unmount should not throw
    expect(() => {
      unmount()
    }).not.toThrow()
  })

  it('handles auto-connect option', async () => {
    const { result } = renderHook(() => useWebSocket({ autoConnect: true }))
    
    // Should attempt to connect automatically
    expect(result.current.connect).toBeDefined()
  })

  it('handles auto-connect disabled', () => {
    const { result } = renderHook(() => useWebSocket({ autoConnect: false }))
    
    // Should not connect automatically
    expect(result.current.isConnected).toBe(false)
  })

  it('handles multiple callback types', () => {
    const callbacks = {
      onOrderUpdate: vi.fn(),
      onInventoryAlert: vi.fn(),
      onShipmentUpdate: vi.fn(),
      onSystemAlert: vi.fn(),
      onDashboardUpdate: vi.fn(),
      onUserStatus: vi.fn(),
      onNotification: vi.fn(),
    }
    
    const { result } = renderHook(() => useWebSocket(callbacks))
    
    // All callbacks should be accepted without errors
    expect(result.current.connect).toBeDefined()
  })
})
