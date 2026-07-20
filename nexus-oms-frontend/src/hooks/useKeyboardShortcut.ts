import { useEffect, useRef } from 'react'

interface Options {
  ctrl?: boolean
  meta?: boolean
  alt?: boolean
  shift?: boolean
}

export function useKeyboardShortcut(
  key: string,
  handler: () => void,
  options: Options = {}
) {
  const handlerRef = useRef(handler)
  handlerRef.current = handler

  useEffect(() => {
    const { ctrl, meta, alt, shift } = options
    const listener = (e: KeyboardEvent) => {
      if (e.key.toLowerCase() !== key.toLowerCase()) return
      if (ctrl && !e.ctrlKey) return
      if (meta && !e.metaKey) return
      if (alt && !e.altKey) return
      if (shift && !e.shiftKey) return
      if (!ctrl && !meta && (e.ctrlKey || e.metaKey)) return

      e.preventDefault()
      handlerRef.current()
    }

    window.addEventListener('keydown', listener)
    return () => window.removeEventListener('keydown', listener)
  }, [key, options.ctrl, options.meta, options.alt, options.shift])
}

