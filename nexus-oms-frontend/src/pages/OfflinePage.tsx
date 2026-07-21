import { WifiOff, RefreshCw } from 'lucide-react'

export default function OfflinePage() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-[var(--bg-primary)] p-4">
      <div className="text-center max-w-md">
        <WifiOff className="w-16 h-16 mx-auto mb-4 text-[var(--text-tertiary)]" />
        <h1 className="text-2xl font-bold text-[var(--text-primary)] mb-2">You&apos;re Offline</h1>
        <p className="text-[var(--text-secondary)] mb-6">
          No internet connection detected. Some features may be unavailable.
          Cached data will be shown where possible.
        </p>
        <button
          onClick={() => window.location.reload()}
          className="enterprise-btn enterprise-btn-primary"
          data-testid="retry-offline"
        >
          <RefreshCw className="w-4 h-4" />
          Try Again
        </button>
      </div>
    </div>
  )
}
