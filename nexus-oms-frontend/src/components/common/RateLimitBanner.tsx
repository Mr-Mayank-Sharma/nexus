import { AlertTriangle } from 'lucide-react'

interface Props {
  remainingMs: number
}

function formatTime(ms: number): string {
  const seconds = Math.ceil(ms / 1000)
  return seconds < 60 ? `${seconds}s` : `${Math.ceil(seconds / 60)}m`
}

export default function RateLimitBanner({ remainingMs }: Props) {
  return (
    <div className="flex items-center gap-2 p-3 rounded-lg bg-red-50 border border-red-200 text-red-700 text-sm" role="alert" data-testid="rate-limit-banner">
      <AlertTriangle className="w-4 h-4 shrink-0" />
      <span>Too many attempts. Try again in {formatTime(remainingMs)}.</span>
    </div>
  )
}
