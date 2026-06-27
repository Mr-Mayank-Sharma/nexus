import { useEffect } from 'react'

interface Options {
  ctrl?: boolean
  meta?: boolean
  alt?: boolean
  shift?: boolean
}

export default function useKeyboardShortcut(
  key: string,
  handler: () => void,
  options: Options = {}
) {
  useEffect(() => {
    const listener = (e: KeyboardEvent) => {
      const { ctrl, meta, alt, shift } = options
      if (e.key.toLowerCase() !== key.toLowerCase()) return
      if (ctrl && !e.ctrlKey) return
      if (meta && !e.metaKey) return
      if (alt && !e.altKey) return
      if (shift && !e.shiftKey) return
      if (!ctrl && !meta && (e.ctrlKey || e.metaKey)) return

      e.preventDefault()
      handler()
    }

    window.addEventListener('keydown', listener)
    return () => window.removeEventListener('keydown', listener)
  }, [key, handler, options])
}

export { useKeyboardShortcut }
