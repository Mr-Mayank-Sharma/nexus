import { RefreshCw, X } from 'lucide-react'

interface Props {
  onUpdate: () => void
  onDismiss: () => void
}

export default function UpdateBanner({ onUpdate, onDismiss }: Props) {
  return (
    <div className="fixed top-0 left-0 right-0 z-50 bg-[var(--color-primary)] text-white px-4 py-3 flex items-center justify-between shadow-lg" data-testid="update-banner">
      <span className="text-sm font-medium">A new version is available</span>
      <div className="flex items-center gap-2">
        <button
          onClick={onUpdate}
          className="inline-flex items-center gap-1.5 px-3 py-1 rounded text-sm font-medium bg-white/20 hover:bg-white/30 transition-colors"
          data-testid="update-button"
        >
          <RefreshCw className="w-3.5 h-3.5" />
          Update
        </button>
        <button
          onClick={onDismiss}
          className="p-1 rounded hover:bg-white/20 transition-colors"
          data-testid="dismiss-update"
          aria-label="Dismiss update notification"
        >
          <X className="w-4 h-4" />
        </button>
      </div>
    </div>
  )
}
