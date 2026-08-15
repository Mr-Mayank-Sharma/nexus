import { useCallback, useEffect, useRef, useState } from 'react'
import {
  OfflineAction,
  clearQueue,
  enqueueAction,
  flushQueue,
  pendingCount,
  subscribeQueue,
} from '../rf/offlineQueue'

interface Options {
  executor: (action: OfflineAction) => Promise<void>
}

export function useOfflineQueue({ executor }: Options) {
  const [count, setCount] = useState(pendingCount())
  const [isOnline, setIsOnline] = useState(typeof navigator === 'undefined' ? true : navigator.onLine)
  const [isFlushing, setIsFlushing] = useState(false)
  const executorRef = useRef(executor)
  executorRef.current = executor

  const sync = useCallback(() => {
    setIsFlushing(true)
    flushQueue((action) => executorRef.current(action))
      .finally(() => setIsFlushing(false))
  }, [])

  const enqueue = useCallback((type: string, payload: Record<string, unknown>) => {
    enqueueAction(type, payload)
  }, [])

  const flush = useCallback(async () => {
    setIsFlushing(true)
    try {
      const result = await flushQueue((action) => executorRef.current(action))
      return result
    } finally {
      setIsFlushing(false)
    }
  }, [])

  const clear = useCallback(() => {
    clearQueue()
  }, [])

  useEffect(() => {
    const unsubscribe = subscribeQueue(() => setCount(pendingCount()))

    const handleOnline = () => {
      setIsOnline(true)
      void sync()
    }
    const handleOffline = () => setIsOnline(false)
    const handleReachable = () => {
      setIsOnline(true)
      void sync()
    }
    const handleUnreachable = () => setIsOnline(false)

    window.addEventListener('online', handleOnline)
    window.addEventListener('offline', handleOffline)
    window.addEventListener('nexus:backend-reachable', handleReachable)
    window.addEventListener('nexus:backend-unreachable', handleUnreachable)

    return () => {
      unsubscribe()
      window.removeEventListener('online', handleOnline)
      window.removeEventListener('offline', handleOffline)
      window.removeEventListener('nexus:backend-reachable', handleReachable)
      window.removeEventListener('nexus:backend-unreachable', handleUnreachable)
    }
  }, [sync])

  return { pendingCount: count, isOnline, isFlushing, enqueue, flush, clear }
}
