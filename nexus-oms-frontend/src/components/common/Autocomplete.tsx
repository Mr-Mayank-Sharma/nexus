import { useState, useEffect, useRef, useCallback, ReactNode } from 'react'
import { Search, Loader2, X } from 'lucide-react'
import { clsx } from 'clsx'

interface AutocompleteProps<T = any> {
  value: string
  onChange: (value: string) => void
  onSelect?: (item: T) => void
  fetchSuggestions?: (query: string) => Promise<T[]>
  suggestions?: T[]
  label?: string
  placeholder?: string
  renderOption?: (item: T, highlighted: boolean) => ReactNode
  getOptionLabel?: (item: T) => string
  getOptionValue?: (item: T) => string
  debounceMs?: number
  minChars?: number
  className?: string
  inputClassName?: string
  disabled?: boolean
  error?: string
  required?: boolean
  showSearchIcon?: boolean
  clearable?: boolean
  loading?: boolean
  maxHeight?: string
}

function defaultGetOptionLabel(item: any): string {
  if (typeof item === 'string') return item
  return item.label ?? item.name ?? item.id ?? String(item)
}

function defaultGetOptionValue(item: any): string {
  if (typeof item === 'string') return item
  return item.id ?? item.code ?? item.sku ?? defaultGetOptionLabel(item)
}

export default function Autocomplete<T = any>({
  value,
  onChange,
  onSelect,
  fetchSuggestions,
  suggestions: staticSuggestions,
  label,
  placeholder = 'Search...',
  renderOption,
  getOptionLabel = defaultGetOptionLabel,
  getOptionValue = defaultGetOptionValue,
  debounceMs = 300,
  minChars = 0,
  className,
  inputClassName,
  disabled,
  error,
  required,
  showSearchIcon = true,
  clearable = true,
  loading: externalLoading,
  maxHeight = '300px',
}: AutocompleteProps<T>) {
  const [open, setOpen] = useState(false)
  const [highlightedIndex, setHighlightedIndex] = useState(-1)
  const [options, setOptions] = useState<T[]>([])
  const [internalLoading, setInternalLoading] = useState(false)
  const [fetched, setFetched] = useState(false)
  const debounceRef = useRef<ReturnType<typeof setTimeout>>()
  const inputRef = useRef<HTMLInputElement>(null)
  const dropdownRef = useRef<HTMLDivElement>(null)
  const containerRef = useRef<HTMLDivElement>(null)

  const isLoading = externalLoading ?? internalLoading
  const showDropdown = open && options.length > 0 && !disabled
  const showNoResults = open && fetched && options.length === 0 && value.length >= minChars && !isLoading

  useEffect(() => {
    if (staticSuggestions) {
      setOptions(value.length >= minChars ? staticSuggestions : [])
      return
    }
    if (debounceRef.current) clearTimeout(debounceRef.current)
    if (value.length < minChars) {
      setOptions([])
      setFetched(false)
      return
    }
    if (!fetchSuggestions) return
    debounceRef.current = setTimeout(async () => {
      setInternalLoading(true)
      try {
        const result = await fetchSuggestions(value)
        const items = Array.isArray(result) ? result : (result as any)?.data ?? (result as any)?.content ?? (result as any)?.items ?? (result as any)?.results ?? []
        setOptions(items)
        setFetched(true)
      } catch {
        setOptions([])
        setFetched(true)
      } finally {
        setInternalLoading(false)
      }
    }, debounceMs)
    return () => { if (debounceRef.current) clearTimeout(debounceRef.current) }
  }, [value, minChars, fetchSuggestions, debounceMs, staticSuggestions])

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const handleSelect = useCallback((item: T) => {
    const label = getOptionLabel(item)
    onChange(label)
    onSelect?.(item)
    setOpen(false)
    setHighlightedIndex(-1)
    inputRef.current?.blur()
  }, [onChange, onSelect, getOptionLabel])

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (!open) {
      if (e.key === 'ArrowDown' || e.key === 'Enter') {
        setOpen(true)
      }
      return
    }
    switch (e.key) {
      case 'ArrowDown':
        e.preventDefault()
        setHighlightedIndex(prev => (prev < options.length - 1 ? prev + 1 : 0))
        break
      case 'ArrowUp':
        e.preventDefault()
        setHighlightedIndex(prev => (prev > 0 ? prev - 1 : options.length - 1))
        break
      case 'Enter':
        e.preventDefault()
        if (highlightedIndex >= 0 && highlightedIndex < options.length) {
          handleSelect(options[highlightedIndex])
        }
        break
      case 'Escape':
        setOpen(false)
        setHighlightedIndex(-1)
        break
    }
  }

  const handleFocus = () => {
    if (options.length > 0 || fetched) {
      setOpen(true)
    } else if (value.length >= minChars) {
      setOpen(true)
    }
  }

  const handleClear = () => {
    onChange('')
    setOptions([])
    setOpen(false)
    setFetched(false)
    inputRef.current?.focus()
  }

  const defaultRenderOption = (item: T, highlighted: boolean) => (
    <div
      className={clsx(
        'px-3 py-2 text-sm cursor-pointer flex items-center gap-2',
        highlighted
          ? 'bg-primary-50 dark:bg-primary-900/20 text-primary-700 dark:text-primary-300'
          : 'text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-800'
      )}
      onMouseDown={() => handleSelect(item)}
      onMouseEnter={() => setHighlightedIndex(options.indexOf(item))}
    >
      <span>{getOptionLabel(item)}</span>
    </div>
  )

  return (
    <div ref={containerRef} className={clsx('relative', className)}>
      {label && (
        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
          {label}
          {required && <span className="text-red-500 ml-0.5">*</span>}
        </label>
      )}
      <div className="relative">
        {showSearchIcon && (
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400 dark:text-gray-500 pointer-events-none" />
        )}
        <input
          ref={inputRef}
          type="text"
          className={clsx(
            'enterprise-input w-full',
            showSearchIcon && 'pl-9',
            clearable && value && 'pr-8',
            inputClassName
          )}
          placeholder={placeholder}
          value={value}
          onChange={e => { onChange(e.target.value); setOpen(true) }}
          onFocus={handleFocus}
          onKeyDown={handleKeyDown}
          disabled={disabled}
          required={required}
          autoComplete="off"
        />
        {isLoading && (
          <Loader2 className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400 animate-spin" />
        )}
        {clearable && value && !isLoading && (
          <button
            type="button"
            className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
            onClick={handleClear}
            tabIndex={-1}
          >
            <X className="w-4 h-4" />
          </button>
        )}
      </div>
      {error && <p className="mt-1 text-sm text-red-500">{error}</p>}

      {showDropdown && (
        <div
          ref={dropdownRef}
          className="absolute z-50 mt-1 w-full bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg shadow-xl overflow-auto"
          style={{ maxHeight }}
        >
          {options.map((item, index) =>
            renderOption
              ? renderOption(item, index === highlightedIndex)
              : defaultRenderOption(item, index === highlightedIndex)
          )}
        </div>
      )}

      {showNoResults && (
        <div className="absolute z-50 mt-1 w-full bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg shadow-xl p-4 text-center text-sm text-gray-500 dark:text-gray-400">
          No results found
        </div>
      )}
    </div>
  )
}
