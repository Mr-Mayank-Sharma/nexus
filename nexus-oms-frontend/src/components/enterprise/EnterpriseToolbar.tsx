import { ReactNode } from 'react'
import { Search } from 'lucide-react'
import { clsx } from 'clsx'

interface Action {
  label: string
  icon?: ReactNode
  onClick: () => void
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost'
  disabled?: boolean
  loading?: boolean
}

interface Props {
  title?: string
  subtitle?: string
  searchValue?: string
  onSearch?: (value: string) => void
  searchPlaceholder?: string
  actions?: Action[]
  filters?: ReactNode
  children?: ReactNode
}

export default function EnterpriseToolbar({ title, subtitle, searchValue, onSearch, searchPlaceholder = 'Search...', actions, filters, children }: Props) {
  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between flex-wrap gap-3">
        {title && (
          <div>
            <h1 className="text-2xl font-bold text-[var(--text-primary)]">{title}</h1>
            {subtitle && <p className="text-sm text-[var(--text-secondary)] mt-0.5">{subtitle}</p>}
          </div>
        )}
        {actions && actions.length > 0 && (
          <div className="flex items-center gap-2">
            {actions.map((action, i) => (
              <button key={i}
                className={clsx('enterprise-btn',
                  action.variant === 'primary' || !action.variant ? 'enterprise-btn-primary' : '',
                  action.variant === 'secondary' ? 'enterprise-btn-secondary' : '',
                  action.variant === 'danger' ? 'enterprise-btn-danger' : '',
                  action.variant === 'ghost' ? 'enterprise-btn-ghost' : '',
                  action.loading && 'opacity-60'
                )}
                disabled={action.disabled || action.loading}
                onClick={action.onClick}
              >
                {action.loading ? <div className="enterprise-spinner" /> : action.icon}
                {action.label}
              </button>
            ))}
          </div>
        )}
      </div>

      {(onSearch || filters) && (
        <div className="flex items-center gap-3 flex-wrap">
          {onSearch && (
            <div className="relative flex-1 max-w-sm">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-[var(--text-tertiary)]" />
              <input className="enterprise-input w-full pl-9" placeholder={searchPlaceholder}
                value={searchValue || ''} onChange={e => onSearch(e.target.value)} />
            </div>
          )}
          {filters}
        </div>
      )}

      {children}
    </div>
  )
}
