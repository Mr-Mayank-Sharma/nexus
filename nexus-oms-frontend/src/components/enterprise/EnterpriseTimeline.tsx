import { ReactNode } from 'react'
import { clsx } from 'clsx'
import { Check, Clock, X, AlertCircle, Loader2 } from 'lucide-react'

export interface TimelineEvent {
  id: string
  title: string
  description?: string
  timestamp: string
  status: 'completed' | 'current' | 'pending' | 'error' | 'skipped'
  icon?: ReactNode
}

interface Props {
  events: TimelineEvent[]
  direction?: 'vertical' | 'horizontal'
}

const statusIcon = (status: TimelineEvent['status']) => {
  switch (status) {
    case 'completed': return <Check className="w-4 h-4" />
    case 'current': return <Loader2 className="w-4 h-4 animate-spin" />
    case 'error': return <X className="w-4 h-4" />
    case 'skipped': return <AlertCircle className="w-4 h-4" />
    default: return <Clock className="w-4 h-4" />
  }
}

const statusColors: Record<TimelineEvent['status'], string> = {
  completed: 'bg-[var(--color-success)] text-white',
  current: 'bg-[var(--color-primary-500)] text-white',
  pending: 'bg-[var(--bg-tertiary)] text-[var(--text-tertiary)]',
  error: 'bg-[var(--color-error)] text-white',
  skipped: 'bg-[var(--color-warning)] text-white',
}

const lineColors: Record<TimelineEvent['status'], string> = {
  completed: 'bg-[var(--color-success)]',
  current: 'bg-[var(--color-primary-200)]',
  pending: 'bg-[var(--border-color)]',
  error: 'bg-[var(--color-error)]',
  skipped: 'bg-[var(--color-warning)]',
}

export default function EnterpriseTimeline({ events, direction = 'vertical' }: Props) {
  if (direction === 'horizontal') {
    return (
      <div className="flex items-start gap-0 overflow-x-auto pb-4">
        {events.map((event, i) => (
          <div key={event.id} className="flex items-center min-w-0">
            <div className="flex flex-col items-center">
              <div className={clsx('w-8 h-8 rounded-full flex items-center justify-center shrink-0', statusColors[event.status])}>
                {event.icon || statusIcon(event.status)}
              </div>
              <div className="mt-2 text-center px-2">
                <p className="text-xs font-medium text-[var(--text-primary)] whitespace-nowrap">{event.title}</p>
                {event.description && <p className="text-[10px] text-[var(--text-tertiary)] mt-0.5">{event.description}</p>}
                <p className="text-[10px] text-[var(--text-tertiary)] mt-0.5">{event.timestamp}</p>
              </div>
            </div>
            {i < events.length - 1 && (
              <div className={clsx('h-0.5 w-12 mx-1 mt-4 shrink-0', lineColors[event.status])} />
            )}
          </div>
        ))}
      </div>
    )
  }

  return (
    <div className="relative">
      {events.map((event, i) => (
        <div key={event.id} className="flex gap-4 pb-6 relative last:pb-0">
          {i < events.length - 1 && (
            <div className={clsx('absolute left-[15px] top-8 w-0.5 h-full -translate-x-1/2', lineColors[event.status])} />
          )}
          <div className={clsx('w-8 h-8 rounded-full flex items-center justify-center shrink-0 z-10', statusColors[event.status])}>
            {event.icon || statusIcon(event.status)}
          </div>
          <div className="flex-1 min-w-0 pt-1">
            <p className="text-sm font-medium text-[var(--text-primary)]">{event.title}</p>
            {event.description && <p className="text-xs text-[var(--text-secondary)] mt-0.5">{event.description}</p>}
            <p className="text-xs text-[var(--text-tertiary)] mt-1">{event.timestamp}</p>
          </div>
        </div>
      ))}
    </div>
  )
}
