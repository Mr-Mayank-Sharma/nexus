import { ReactNode } from 'react'
import { clsx } from 'clsx'

export interface Tab {
  id: string
  label: string
  icon?: ReactNode
  badge?: number | string
  disabled?: boolean
}

interface Props {
  tabs: Tab[]
  activeTab: string
  onChange: (tabId: string) => void
  variant?: 'underline' | 'pills' | 'cards'
}

export default function EnterpriseTabs({ tabs, activeTab, onChange, variant = 'underline' }: Props) {
  if (variant === 'pills') {
    return (
      <div className="flex items-center gap-1 bg-[var(--bg-tertiary)] p-1 rounded-lg w-fit">
        {tabs.map(tab => (
          <button key={tab.id}
            className={clsx('flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium rounded-md transition-all',
              tab.disabled && 'opacity-40 cursor-not-allowed',
              activeTab === tab.id
                ? 'bg-[var(--bg-card)] text-[var(--text-primary)] shadow-sm'
                : 'text-[var(--text-secondary)] hover:text-[var(--text-primary)]'
            )}
            disabled={tab.disabled} onClick={() => onChange(tab.id)}
          >
            {tab.icon}
            {tab.label}
            {tab.badge != null && (
              <span className="ml-1 text-[10px] bg-[var(--color-primary-100)] text-[var(--color-primary-700)] px-1.5 py-0.5 rounded-full font-semibold">
                {tab.badge}
              </span>
            )}
          </button>
        ))}
      </div>
    )
  }

  if (variant === 'cards') {
    return (
      <div className="grid grid-cols-auto-fit gap-3">
        {tabs.map(tab => (
          <button key={tab.id}
            className={clsx('flex flex-col items-center gap-1 p-4 rounded-xl border transition-all text-center',
              tab.disabled && 'opacity-40 cursor-not-allowed',
              activeTab === tab.id
                ? 'border-[var(--color-primary-500)] bg-[var(--color-primary-50)] text-[var(--color-primary-700)]'
                : 'border-[var(--border-color)] hover:border-[var(--border-color-hover)] text-[var(--text-secondary)]'
            )}
            disabled={tab.disabled} onClick={() => onChange(tab.id)}
          >
            {tab.icon && <div className="text-2xl">{tab.icon}</div>}
            <span className="text-sm font-medium">{tab.label}</span>
          </button>
        ))}
      </div>
    )
  }

  return (
    <div className="border-b border-[var(--border-color)]">
      <div className="flex gap-0 -mb-px">
        {tabs.map(tab => (
          <button key={tab.id}
            className={clsx('flex items-center gap-1.5 px-4 py-3 text-sm font-medium border-b-2 transition-all whitespace-nowrap',
              tab.disabled && 'opacity-40 cursor-not-allowed',
              activeTab === tab.id
                ? 'border-[var(--color-primary-500)] text-[var(--color-primary-600)]'
                : 'border-transparent text-[var(--text-secondary)] hover:text-[var(--text-primary)] hover:border-[var(--border-color)]'
            )}
            disabled={tab.disabled} onClick={() => onChange(tab.id)}
          >
            {tab.icon}
            {tab.label}
            {tab.badge != null && (
              <span className={clsx('ml-1 text-xs px-1.5 py-0.5 rounded-full',
                activeTab === tab.id
                  ? 'bg-[var(--color-primary-100)] text-[var(--color-primary-700)]'
                  : 'bg-[var(--bg-tertiary)] text-[var(--text-tertiary)]'
              )}>
                {tab.badge}
              </span>
            )}
          </button>
        ))}
      </div>
    </div>
  )
}
