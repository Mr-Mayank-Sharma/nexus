import { useState, useEffect, useCallback } from 'react'

interface SWState {
  needRefresh: boolean
  offlineReady: boolean
}

export function useServiceWorker() {
  const [state, setState] = useState<SWState>({ needRefresh: false, offlineReady: false })

  const handleUpdate = useCallback(() => {
    setState(prev => ({ ...prev, needRefresh: true }))
  }, [])

  const handleOfflineReady = useCallback(() => {
    setState(prev => ({ ...prev, offlineReady: true }))
  }, [])

  const updateSW = useCallback(async () => {
    if ('serviceWorker' in navigator) {
      const reg = await navigator.serviceWorker.getRegistration()
      if (reg?.waiting) {
        reg.waiting.postMessage({ type: 'SKIP_WAITING' })
        window.location.reload()
      }
    }
  }, [])

  const dismissUpdate = useCallback(() => {
    setState(prev => ({ ...prev, needRefresh: false }))
  }, [])

  const dismissOffline = useCallback(() => {
    setState(prev => ({ ...prev, offlineReady: false }))
  }, [])

  useEffect(() => {
    if (!('serviceWorker' in navigator)) return

    const onLoad = async () => {
      const reg = await navigator.serviceWorker.register('/sw.js')

      reg.addEventListener('updatefound', () => {
        const newWorker = reg.installing
        if (!newWorker) return

        newWorker.addEventListener('statechange', () => {
          if (newWorker.state === 'installed') {
            if (navigator.serviceWorker.controller) {
              handleUpdate()
            } else {
              handleOfflineReady()
            }
          }
        })
      })
    }

    window.addEventListener('load', onLoad)
    return () => window.removeEventListener('load', onLoad)
  }, [handleUpdate, handleOfflineReady])

  return { ...state, updateSW, dismissUpdate, dismissOffline }
}
