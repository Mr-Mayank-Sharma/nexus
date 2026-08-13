import { useEffect, useRef, useState } from 'react'
import { X, Camera, Keyboard } from 'lucide-react'
import { useBarcodeScanner } from './useBarcodeScanner'

interface ScannerOverlayProps {
  open: boolean
  title?: string
  placeholder?: string
  onScan: (code: string) => void
  onClose: () => void
}

export default function ScannerOverlay({
  open,
  title = 'Scan barcode',
  placeholder = 'Scan or type barcode…',
  onScan,
  onClose,
}: ScannerOverlayProps) {
  const [manual, setManual] = useState('')
  const inputRef = useRef<HTMLInputElement | null>(null)
  const detectRef = useRef(onScan)
  detectRef.current = onScan

  const { start, stop, supported, error, stream, videoRef, resetDetection } = useBarcodeScanner({
    onDetect: (code) => {
      detectRef.current(code)
      setManual(code)
    },
  })

  useEffect(() => {
    if (open) {
      setManual('')
      resetDetection()
      if (supported) void start()
      setTimeout(() => inputRef.current?.focus(), 100)
    } else {
      stop()
    }
  }, [open, supported, start, stop, resetDetection])

  if (!open) return null

  const handleManual = () => {
    const value = manual.trim()
    if (!value) return
    onScan(value)
    setManual('')
  }

  return (
    <div className="fixed inset-0 z-50 bg-black flex flex-col" role="dialog" aria-modal="true">
      <div className="flex items-center justify-between px-4 py-3 bg-black text-white">
        <h2 className="text-base font-semibold">{title}</h2>
        <button
          type="button"
          onClick={() => {
            stop()
            onClose()
          }}
          className="p-2 rounded-lg text-white/80 hover:text-white hover:bg-white/10"
          aria-label="Close scanner"
        >
          <X className="w-6 h-6" />
        </button>
      </div>

      <div className="relative flex-1 overflow-hidden bg-black">
        {supported && stream && (
          <video
            ref={(el) => {
              videoRef.current = el
              if (el && stream) el.srcObject = stream
            }}
            playsInline
            muted
            autoPlay
            className="w-full h-full object-cover"
          />
        )}
        <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
          <div className="w-72 h-40 border-2 border-emerald-400 rounded-2xl shadow-[0_0_0_9999px_rgba(0,0,0,0.45)]" />
        </div>
        {!supported && (
          <div className="absolute inset-0 flex flex-col items-center justify-center text-white/80 gap-3 px-6">
            <Camera className="w-10 h-10 text-white/40" />
            <p className="text-sm text-center">
              Camera scanning isn't supported in this browser. Use the manual entry field below.
            </p>
          </div>
        )}
        {error && (
          <div className="absolute top-3 inset-x-3 rounded-lg bg-red-500/90 text-white text-xs px-3 py-2 text-center">
            {error}
          </div>
        )}
      </div>

      <div className="bg-black border-t border-white/10 px-4 py-4 pb-8 flex items-center gap-2">
        <Keyboard className="w-5 h-5 text-white/50 shrink-0" />
        <input
          ref={inputRef}
          value={manual}
          onChange={(e) => setManual(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') handleManual()
          }}
          placeholder={placeholder}
          className="flex-1 bg-white/10 text-white rounded-lg px-4 py-3 text-base placeholder:text-white/40 focus:outline-none focus:ring-2 focus:ring-emerald-400"
        />
        <button
          type="button"
          onClick={handleManual}
          className="shrink-0 px-4 py-3 rounded-lg bg-emerald-500 text-white font-semibold active:scale-95 transition-transform"
        >
          OK
        </button>
      </div>
    </div>
  )
}
