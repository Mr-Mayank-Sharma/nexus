import { useEffect, useState } from 'react'
import { statusTone } from './format'

/** Shared visual atoms for the twin pages (glass panels, status chips, live indicator). */


const TONES = {
  green: 'bg-emerald-50 text-emerald-700 ring-emerald-600/15',
  blue: 'bg-blue-50 text-blue-700 ring-blue-600/15',
  amber: 'bg-amber-50 text-amber-700 ring-amber-600/20',
  red: 'bg-rose-50 text-rose-700 ring-rose-600/15',
  slate: 'bg-slate-100 text-slate-600 ring-slate-500/10',
} as const

export function Chip({ status, label }: { status: string | null | undefined; label?: string }) {
  const t = statusTone(status)
  return (
    <span className={`inline-flex items-center rounded-md px-2 py-0.5 text-[11px] font-medium ring-1 ring-inset ${TONES[t.tone]}`}>
      {label ?? t.label}
    </span>
  )
}

export function Glass({ className = '', children }: { className?: string; children: React.ReactNode }) {
  return (
    <div className={`rounded-2xl border border-white/70 bg-white/85 shadow-[0_8px_30px_rgba(15,23,42,0.08)] backdrop-blur-md ${className}`}>
      {children}
    </div>
  )
}

export function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-4 py-1.5 text-[13px]">
      <span className="text-slate-500">{label}</span>
      <span className="truncate text-right font-medium text-slate-800">{value ?? '—'}</span>
    </div>
  )
}

export function Progress({ value, tone = 'green' }: { value: number; tone?: 'green' | 'blue' }) {
  return (
    <div className="h-1.5 w-full overflow-hidden rounded-full bg-slate-100">
      <div className={`h-full rounded-full ${tone === 'green' ? 'bg-emerald-500' : 'bg-blue-600'}`}
           style={{ width: `${Math.max(0, Math.min(100, value))}%` }} />
    </div>
  )
}

export function LivePill({ updatedAt, failing, refreshMs = 10_000 }: { updatedAt: number; failing: boolean; refreshMs?: number }) {
  const [now, setNow] = useState(Date.now())
  useEffect(() => { const t = setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(t) }, [])
  const stale = now - updatedAt > refreshMs * 4
  const tone = failing ? 'bg-rose-50 text-rose-700' : stale ? 'bg-amber-50 text-amber-700' : 'bg-emerald-50 text-emerald-700'
  const dot = failing ? 'bg-rose-500' : stale ? 'bg-amber-500' : 'bg-emerald-500 animate-pulse'
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[12px] font-semibold ${tone}`}
          title={updatedAt ? `Last update ${new Date(updatedAt).toLocaleTimeString()}` : ''}>
      <span className={`h-2 w-2 rounded-full ${dot}`} />
      {failing ? 'Offline' : stale ? 'Stale' : 'Live'}
      <span className="font-medium text-slate-500">{new Date(now).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
    </span>
  )
}

