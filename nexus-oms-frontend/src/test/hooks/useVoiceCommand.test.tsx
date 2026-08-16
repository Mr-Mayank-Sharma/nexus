import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useVoiceCommand } from '../../hooks/useVoiceCommand'

class FakeSpeechRecognition {
  lang = ''
  continuous = false
  interimResults = false
  maxAlternatives = 0
  onresult: any = null
  onerror: any = null
  onend: any = null
  started = false
  start() {
    this.started = true
  }
  stop() {
    this.started = false
    if (this.onend) this.onend()
  }
}

describe('useVoiceCommand Hook', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('webkitSpeechRecognition', FakeSpeechRecognition)
  })

  it('reports supported when a recognition API is present', () => {
    const { result } = renderHook(() => useVoiceCommand(vi.fn()))
    expect(result.current.supported).toBe(true)
    expect(result.current.listening).toBe(false)
  })

  it('reports unsupported and surfaces an error when no API exists', () => {
    vi.unstubAllGlobals()
    vi.stubGlobal('SpeechRecognition', undefined)
    vi.stubGlobal('webkitSpeechRecognition', undefined)
    const { result } = renderHook(() => useVoiceCommand(vi.fn()))
    expect(result.current.supported).toBe(false)
    act(() => {
      result.current.start()
    })
    expect(result.current.error).toBe('Speech recognition is not supported in this browser')
  })

  it('starts listening on start()', () => {
    const { result } = renderHook(() => useVoiceCommand(vi.fn()))
    act(() => {
      result.current.start()
    })
    expect(result.current.listening).toBe(true)
  })

  it('toggles listening state', () => {
    const { result } = renderHook(() => useVoiceCommand(vi.fn()))
    act(() => {
      result.current.toggle()
    })
    expect(result.current.listening).toBe(true)
    act(() => {
      result.current.toggle()
    })
    expect(result.current.listening).toBe(false)
  })
})
