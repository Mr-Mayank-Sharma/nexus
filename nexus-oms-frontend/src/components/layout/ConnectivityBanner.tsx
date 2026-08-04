import { useState, useEffect } from 'react'
import { WifiOff, ServerCrash } from 'lucide-react'

export default function ConnectivityBanner() {
  const [online, setOnline] = useState(navigator.onLine)
  const [backendDown, setBackendDown] = useState(false)

  useEffect(() => {
    const onOnline = () => { setOnline(true); setBackendDown(false) }
    const onOffline = () => { setOnline(false); setBackendDown(false) }
    const onBackendDown = () => setBackendDown(true)
    const onBackendUp = () => setBackendDown(false)

    window.addEventListener('online', onOnline)
    window.addEventListener('offline', onOffline)
    window.addEventListener('nexus:backend-unreachable', onBackendDown)
    window.addEventListener('nexus:backend-reachable', onBackendUp)

    return () => {
      window.removeEventListener('online', onOnline)
      window.removeEventListener('offline', onOffline)
      window.removeEventListener('nexus:backend-unreachable', onBackendDown)
      window.removeEventListener('nexus:backend-reachable', onBackendUp)
    }
  }, [])

  if (online && !backendDown) return null

  return (
    <div className="fixed bottom-0 inset-x-0 z-50 flex items-center justify-center gap-2 bg-red-600 px-4 py-2 text-sm font-medium text-white shadow-lg">
      {!online ? (
        <>
          <WifiOff className="w-4 h-4" />
          You are offline — some features may be unavailable
        </>
      ) : (
        <>
          <ServerCrash className="w-4 h-4" />
          Backend unreachable — retrying automatically
        </>
      )}
    </div>
  )
}
