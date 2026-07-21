import { useState, useMemo, ReactNode } from 'react'
import { ChevronDown, ChevronUp, ChevronsUpDown, Search, Download, Columns } from 'lucide-react'
import { clsx } from 'clsx'

export interface Column<T = any> {
  key: string
  label: string
  sortable?: boolean
  filterable?: boolean
  width?: string
  minWidth?: string
  render?: (value: any, row: T, index: number) => ReactNode
  align?: 'left' | 'center' | 'right'
  frozen?: 'left' | 'right'
  hidden?: boolean
}

interface Props<T> {
  columns: Column<T>[]
  data: T[]
  loading?: boolean
  sortable?: boolean
  filterable?: boolean
  selectable?: boolean
  pageSize?: number
  totalElements?: number
  page?: number
  onPageChange?: (page: number) => void
  onSort?: (key: string, direction: 'asc' | 'desc') => void
  onSelectionChange?: (selected: string[]) => void
  emptyMessage?: string
  emptyIcon?: ReactNode
  onRowClick?: (row: T) => void
  rowKey?: string
  exportable?: boolean
  onExport?: () => void
}

function EnterpriseDataGrid<T extends Record<string, any>>({
  columns: rawColumns, data, loading, sortable = true, selectable = false,
  pageSize = 20, totalElements = 0, page = 0, onPageChange, onSort,
  onSelectionChange, emptyMessage = 'No data found', emptyIcon, onRowClick,
  rowKey = 'id', exportable = false, onExport,
}: Props<T>) {
  const columns = rawColumns.filter(c => !c.hidden)
  const [sortKey, setSortKey] = useState<string | null>(null)
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('asc')
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [globalFilter, setGlobalFilter] = useState('')
  const [columnChooserOpen, setColumnChooserOpen] = useState(false)
  const [visibleColumns, setVisibleColumns] = useState<Set<string>>(new Set(rawColumns.map(c => c.key)))

  const filteredData = useMemo(() => {
    if (!globalFilter) return data
    return data.filter(row =>
      columns.some(col => {
        const val = row[col.key]
        return val != null && String(val).toLowerCase().includes(globalFilter.toLowerCase())
      })
    )
  }, [data, globalFilter, columns])

  const sortedData = useMemo(() => {
    if (!sortKey) return filteredData
    return [...filteredData].sort((a, b) => {
      const aVal = a[sortKey]
      const bVal = b[sortKey]
      if (aVal == null) return 1
      if (bVal == null) return -1
      const cmp = aVal < bVal ? -1 : aVal > bVal ? 1 : 0
      return sortDir === 'asc' ? cmp : -cmp
    })
  }, [filteredData, sortKey, sortDir])

  const totalPages = Math.max(1, Math.ceil(totalElements / pageSize))
  const allSelected = selected.size === sortedData.length && sortedData.length > 0

  const handleSort = (key: string) => {
    if (sortKey === key) {
      const newDir = sortDir === 'asc' ? 'desc' : 'asc'
      setSortDir(newDir)
      onSort?.(key, newDir)
    } else {
      setSortKey(key)
      setSortDir('asc')
      onSort?.(key, 'asc')
    }
  }

  const toggleSelectAll = () => {
    if (allSelected) {
      setSelected(new Set())
      onSelectionChange?.([])
    } else {
      const all = new Set(sortedData.map(r => r[rowKey]))
      setSelected(all)
      onSelectionChange?.(Array.from(all))
    }
  }

  const toggleSelect = (id: string) => {
    const next = new Set(selected)
    next.has(id) ? next.delete(id) : next.add(id)
    setSelected(next)
    onSelectionChange?.(Array.from(next))
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between gap-4">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-[var(--text-tertiary)]" />
          <input
            className="enterprise-input w-full pl-9"
            placeholder="Search..."
            aria-label="Filter table rows"
            value={globalFilter}
            onChange={e => setGlobalFilter(e.target.value)}
          />
        </div>
        <div className="flex items-center gap-2">
          {exportable && (
            <button className="enterprise-btn enterprise-btn-secondary enterprise-btn-sm" onClick={onExport}>
              <Download className="w-4 h-4" /> Export
            </button>
          )}
          <div className="relative">
            <button className="enterprise-btn enterprise-btn-secondary enterprise-btn-sm" onClick={() => setColumnChooserOpen(!columnChooserOpen)}>
              <Columns className="w-4 h-4" /> Columns
            </button>
            {columnChooserOpen && (
              <div className="absolute right-0 top-full mt-1 bg-[var(--bg-card)] border border-[var(--border-color)] rounded-lg shadow-xl z-50 min-w-[180px] py-1">
                {rawColumns.map(col => (
                  <label key={col.key} className="flex items-center gap-2 px-3 py-1.5 text-sm hover:bg-[var(--bg-tertiary)] cursor-pointer">
                    <input type="checkbox" checked={visibleColumns.has(col.key)} onChange={() => {
                      const next = new Set(visibleColumns)
                      next.has(col.key) ? next.delete(col.key) : next.add(col.key)
                      setVisibleColumns(next)
                    }} className="rounded border-[var(--border-color)] text-[var(--color-primary-600)]" />
                    {col.label}
                  </label>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>

      <div className="enterprise-card overflow-hidden">
        <div className="overflow-x-auto">
          <table className="enterprise-table" role="grid" aria-rowcount={sortedData.length} aria-colcount={columns.length}>
            <thead>
              <tr>
                {selectable && (
                  <th className="w-10">
                    <input type="checkbox" checked={allSelected} onChange={toggleSelectAll}
                      aria-label="Select all rows"
                      className="rounded border-[var(--border-color)] text-[var(--color-primary-600)]" />
                  </th>
                )}
                {columns.filter(c => visibleColumns.has(c.key)).map(col => (
                  <th key={col.key} className={clsx(col.sortable !== false && sortable && 'cursor-pointer select-none')}
                    style={{ width: col.width, minWidth: col.minWidth }}
                    aria-sort={col.sortable !== false && sortable ? (sortKey === col.key ? (sortDir === 'asc' ? 'ascending' : 'descending') : 'none') : undefined}
                    tabIndex={col.sortable !== false && sortable ? 0 : undefined}
                    onClick={() => col.sortable !== false && sortable && handleSort(col.key)}
                    onKeyDown={(e) => { if (col.sortable !== false && sortable && (e.key === 'Enter' || e.key === ' ')) { e.preventDefault(); handleSort(col.key) } }}
                  >
                    <div className={clsx('flex items-center gap-1', col.align === 'right' && 'justify-end', col.align === 'center' && 'justify-center')}>
                      {col.label}
                      {col.sortable !== false && sortable && (
                        sortKey === col.key
                          ? (sortDir === 'asc' ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />)
                          : <ChevronsUpDown className="w-3 h-3 opacity-30" />
                      )}
                    </div>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {loading ? (
                Array.from({ length: 5 }).map((_, i) => (
                  <tr key={i}>
                    {selectable && (
                      <td className="w-10"><div className="enterprise-skeleton w-4 h-4 rounded mx-auto" /></td>
                    )}
                    {columns.filter(c => visibleColumns.has(c.key)).map((col, j) => {
                      const skeletonWidths = ['55%', '75%', '45%', '85%', '60%', '70%', '50%', '80%']
                      return (
                        <td key={col.key}>
                          <div className="enterprise-skeleton h-4" style={{ width: skeletonWidths[j % skeletonWidths.length] }} />
                        </td>
                      )
                    })}
                  </tr>
                ))
              ) : sortedData.length === 0 ? (
                <tr>
                  <td colSpan={columns.length + (selectable ? 1 : 0)}>
                    <div className="enterprise-empty-state">
                      {emptyIcon}
                      <h3>{emptyMessage}</h3>
                    </div>
                  </td>
                </tr>
              ) : (
                sortedData.map((row, i) => (
                  <tr key={row[rowKey]} className={clsx(onRowClick && 'cursor-pointer')}
                    onClick={() => onRowClick?.(row)}
                  >
                    {selectable && (
                      <td onClick={e => e.stopPropagation()}>
                        <input type="checkbox" checked={selected.has(row[rowKey])}
                          onChange={() => toggleSelect(row[rowKey])}
                          aria-label={`Select row ${i + 1}`}
                          aria-selected={selected.has(row[rowKey])}
                          className="rounded border-[var(--border-color)] text-[var(--color-primary-600)]" />
                      </td>
                    )}
                    {columns.map(col => (
                      visibleColumns.has(col.key) && (
                        <td key={col.key} className={clsx(col.align === 'right' && 'text-right', col.align === 'center' && 'text-center')}>
                          {col.render ? col.render(row[col.key], row, i) : row[col.key] ?? '-'}
                        </td>
                      )
                    ))}
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {totalPages > 1 && (
        <div className="flex items-center justify-between px-1">
          <span className="text-sm text-[var(--text-tertiary)]">
            {page * pageSize + 1}–{Math.min((page + 1) * pageSize, totalElements)} of {totalElements}
          </span>
          <div className="flex items-center gap-1">
            <button className="enterprise-btn enterprise-btn-ghost enterprise-btn-sm"
              disabled={page === 0} onClick={() => onPageChange?.(page - 1)}
              aria-label="Go to previous page">Previous</button>
            {Array.from({ length: Math.min(totalPages, 7) }, (_, i) => {
              const start = Math.max(0, Math.min(page - 3, totalPages - 7))
              const p = start + i
              if (p >= totalPages) return null
              return (
                <button key={p}
                  className={clsx('enterprise-btn enterprise-btn-sm min-w-[32px]',
                    p === page ? 'enterprise-btn-primary' : 'enterprise-btn-ghost')}
                  onClick={() => onPageChange?.(p)}
                  aria-label={`Go to page ${p + 1}`}
                  aria-current={p === page ? 'page' : undefined}
                >{p + 1}</button>
              )
            })}
            <button className="enterprise-btn enterprise-btn-ghost enterprise-btn-sm"
              disabled={page >= totalPages - 1} onClick={() => onPageChange?.(page + 1)}
              aria-label="Go to next page">Next</button>
          </div>
        </div>
      )}
    </div>
  )
}

export default EnterpriseDataGrid
export type { Column }
