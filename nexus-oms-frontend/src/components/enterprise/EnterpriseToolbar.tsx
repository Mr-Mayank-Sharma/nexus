import { ReactNode, useState, useRef } from 'react'
import { Search, X, Loader2 } from 'lucide-react'
import { clsx } from 'clsx'

interface Action {
  label: string
  icon?: ReactNode
  onClick: () => void
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost'
  disabled?: boolean
  loading?: boolean
}

interface AutocompleteConfig<T = any> {
  fetchSuggestions: (query: string) => Promise<T[]>
  onSelect: (item: T) => void
  getOptionLabel?: (item: T) => string
  getOptionValue?: (item: T) => string
  renderOption?: (item: T, highlighted: boolean) => ReactNode
  debounceMs?: number
  minChars?: number
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
  autocomplete?: AutocompleteConfig
}

export default function EnterpriseToolbar({ title, subtitle, searchValue, onSearch, searchPlaceholder = 'Search...', actions, filters, children, autocomplete }: Props) {
  const [open, setOpen] = useState(false)
  const [highlightedIndex, setHighlightedIndex] = useState(-1)
  const [options, setOptions] = useState<any[]>([])
  const [loading, setLoading] = useState(false)
  const [fetched, setFetched] = useState(false)
  const [search, setSearch] = useState(searchValue || '')
  const debounceRef = useRef<ReturnType<typeof setTimeout>>()

  const currentValue = searchValue !== undefined ? searchValue : search
  const updateValue = onSearch || ((v: string) => setSearch(v))

  const handleChange = (value: string) => {
    updateValue(value)
    if (!autocomplete) return
    if (debounceRef.current) clearTimeout(debounceRef.current)
    const min = autocomplete.minChars ?? 2
    if (value.length < min) { setOptions([]); setFetched(false); return }
    debounceRef.current = setTimeout(async () => {
      setLoading(true)
      try {
        const result = await autocomplete.fetchSuggestions(value)
        const items = Array.isArray(result) ? result : result?.data ?? result?.content ?? result?.items ?? result?.results ?? []
        setOptions(items)
        setFetched(true)
        setOpen(true)
      } catch { setOptions([]); setFetched(true) }
      finally { setLoading(false) }
    }, autocomplete.debounceMs ?? 300)
  }

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

      {(onSearch || autocomplete || filters) && (
        <div className="flex items-center gap-3 flex-wrap">
          {(onSearch || autocomplete) && (
            <div className="relative flex-1 max-w-sm">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-[var(--text-tertiary)] pointer-events-none" />
              <input className="enterprise-input w-full pl-9" placeholder={searchPlaceholder}
                value={currentValue}
                onChange={e => handleChange(e.target.value)}
                onFocus={() => autocomplete && options.length > 0 && setOpen(true)}
                onKeyDown={e => {
                  if (!autocomplete || !open) return
                  if (e.key === 'ArrowDown') { e.preventDefault(); setHighlightedIndex(p => p < options.length - 1 ? p + 1 : 0) }
                  if (e.key === 'ArrowUp') { e.preventDefault(); setHighlightedIndex(p => p > 0 ? p - 1 : options.length - 1) }
                  if (e.key === 'Enter' && highlightedIndex >= 0) {
                    e.preventDefault()
                    const item = options[highlightedIndex]
                    const label = autocomplete.getOptionLabel?.(item) ?? item?.label ?? item?.name ?? item?.id ?? String(item)
                    updateValue(label)
                    autocomplete.onSelect(item)
                    setOpen(false); setHighlightedIndex(-1)
                  }
                  if (e.key === 'Escape') { setOpen(false); setHighlightedIndex(-1) }
                }}
                autoComplete="off"
              />
              {loading && <Loader2 className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-[var(--text-tertiary)] animate-spin" />}
              {autocomplete && open && options.length > 0 && (
                <div className="absolute z-50 top-full mt-1 w-full bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg shadow-xl overflow-auto max-h-[300px]">
                  {options.map((item, i) => {
                    const label = autocomplete.getOptionLabel?.(item) ?? item?.label ?? item?.name ?? item?.id ?? String(item)
                    const highlighted = i === highlightedIndex
                    return (
                      <div key={autocomplete.getOptionValue?.(item) ?? label}
                        className={clsx('px-3 py-2 text-sm cursor-pointer',
                          highlighted ? 'bg-primary-50 dark:bg-primary-900/20 text-primary-700 dark:text-primary-300' : 'text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-800'
                        )}
                        onMouseDown={() => { updateValue(label); autocomplete.onSelect(item); setOpen(false); setHighlightedIndex(-1) }}
                        onMouseEnter={() => setHighlightedIndex(i)}
                      >
                        {autocomplete.renderOption ? autocomplete.renderOption(item, highlighted) : label}
                      </div>
                    )
                  })}
                </div>
              )}
              {autocomplete && open && fetched && options.length === 0 && !loading && currentValue.length >= (autocomplete.minChars ?? 2) && (
                <div className="absolute z-50 top-full mt-1 w-full bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg shadow-xl p-3 text-center text-sm text-gray-500">
                  No results found
                </div>
              )}
            </div>
          )}
          {filters}
        </div>
      )}

      {children}
    </div>
  )
}
