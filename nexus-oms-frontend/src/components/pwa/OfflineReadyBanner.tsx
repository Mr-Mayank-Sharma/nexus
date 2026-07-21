import { Wifi } from 'lucide-react'

interface Props {
  onDismiss: () => void
}

export default function OfflineReadyBanner({ onDismiss }: Props) {
  return (
    <div className="fixed bottom-4 right-4 z-50 bg-[var(--bg-elevated)] border border-[var(--border-subtle)] rounded-lg shadow-lg px-4 py-3 flex items-center gap-3 max-w-sm" data-testid="offline-ready">
      <Wifi className="w-5 h-5 text-green-500 shrink-0" />
      <div className="flex-1">
        <p className="text-sm font-medium text-[var(--text-primary)]">App ready for offline use</p>
        <p className="text-xs text-[var(--text-tertiary)]">Content is cached for offline access.</p>
      </div>
      <button
        onClick={onDismiss}
        className="text-xs text-[var(--color-primary)] font-medium hover:underline"
        data-testid="dismiss-offline"
      >
        Dismiss
      </button>
    </div>
  )
}
