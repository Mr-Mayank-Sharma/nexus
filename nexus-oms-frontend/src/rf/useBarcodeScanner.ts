import { useCallback, useEffect, useRef, useState } from 'react'
import { BrowserMultiFormatReader } from '@zxing/browser'
import type { IScannerControls } from '@zxing/browser'

interface DetectedBarcode {
  rawValue: string
}

interface BarcodeDetectorLike {
  detect: (source: CanvasImageSource | ImageBitmap) => Promise<DetectedBarcode[]>
}

interface BarcodeDetectorCtor {
  new (options?: { formats?: string[] }): BarcodeDetectorLike
}

interface BarcodeScannerOptions {
  onDetect: (code: string) => void
}

const SCAN_COOLDOWN_MS = 1500

export function useBarcodeScanner({ onDetect }: BarcodeScannerOptions) {
  const [supported, setSupported] = useState(false)
  const [stream, setStream] = useState<MediaStream | null>(null)
  const [error, setError] = useState<string | null>(null)

  const videoRef = useRef<HTMLVideoElement | null>(null)
  const onDetectRef = useRef(onDetect)
  const runningRef = useRef(false)
  const nativeRef = useRef(false)
  const lastCodeRef = useRef<string | null>(null)
  const lastDetectedAtRef = useRef(0)
  const rafRef = useRef(0)
  const zxingControlsRef = useRef<IScannerControls | null>(null)

  onDetectRef.current = onDetect

  useEffect(() => {
    setSupported(typeof window !== 'undefined' && !!navigator?.mediaDevices?.getUserMedia)
  }, [])

  const handleValue = useCallback((raw: string) => {
    const value = raw.trim()
    const now = Date.now()
    if (value && value !== lastCodeRef.current && now - lastDetectedAtRef.current > SCAN_COOLDOWN_MS) {
      lastCodeRef.current = value
      lastDetectedAtRef.current = now
      onDetectRef.current(value)
    }
  }, [])

  const loop = useCallback(async () => {
    if (!runningRef.current) return
    const video = videoRef.current
    if (video && video.readyState >= video.HAVE_ENOUGH_DATA) {
      const Ctor = (window as unknown as { BarcodeDetector?: BarcodeDetectorCtor }).BarcodeDetector
      if (Ctor) {
        try {
          const detector = new Ctor()
          const codes = await detector.detect(video)
          for (const code of codes) {
            handleValue(code.rawValue ?? '')
          }
        } catch {
          // detection errors are transient — keep looping
        }
      }
    }
    rafRef.current = requestAnimationFrame(loop)
  }, [handleValue])

  const start = useCallback(async () => {
    if (runningRef.current) return
    setError(null)
    try {
      const mediaStream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'environment' },
        audio: false,
      })
      setStream(mediaStream)
      runningRef.current = true
      if (videoRef.current) videoRef.current.srcObject = mediaStream

      nativeRef.current = typeof window !== 'undefined' && 'BarcodeDetector' in window
      if (nativeRef.current) {
        rafRef.current = requestAnimationFrame(loop)
      } else {
        const reader = new BrowserMultiFormatReader()
        const video = videoRef.current
        if (!video) throw new Error('Video element unavailable')
        zxingControlsRef.current = await reader.decodeFromVideoElement(video, (result) => {
          if (result) handleValue(result.getText() ?? '')
        })
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Camera unavailable')
    }
  }, [loop, handleValue])

  const stop = useCallback(() => {
    runningRef.current = false
    cancelAnimationFrame(rafRef.current)
    zxingControlsRef.current?.stop()
    zxingControlsRef.current = null
    setStream((current) => {
      current?.getTracks().forEach((t) => t.stop())
      return null
    })
  }, [])

  const resetDetection = useCallback(() => {
    lastCodeRef.current = null
  }, [])

  useEffect(() => {
    return () => {
      runningRef.current = false
      cancelAnimationFrame(rafRef.current)
      zxingControlsRef.current?.stop()
    }
  }, [])

  return { start, stop, supported, error, stream, videoRef, resetDetection }
}
