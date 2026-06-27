import React, { useState, useMemo } from 'react'
import { ChevronUp, ChevronDown, ChevronsUpDown, ChevronLeft, ChevronRight } from 'lucide-react'
import { clsx } from 'clsx'

export interface Column<T> {
  key: keyof T | string
  header: string
  sortable?: boolean
  render?: (item: T) => React.ReactNode
  className?: string
  headerClassName?: string
  width?: string
}

interface DataTableProps<T> {
  columns: Column<T>[]
  data: T[]
  keyExtractor: (item: T) => string
  onRowClick?: (item: T) => void
  selectedIds?: string[]
  onSelectionChange?: (ids: string[]) => void
  isLoading?: boolean
  pagination?: {
    page: number
    totalPages: number
    total: number
    onPageChange: (page: number) => void
  }
  emptyMessage?: string
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
  onSort?: (key: string) => void
}

function LoadingSkeleton() {
  return (
    <div className="animate-pulse">
      {Array.from({ length: 8 }).map((_, i) => (
        <div key={i} className="flex items-center gap-4 px-6 py-4 border-b border-gray-100">
          {Array.from({ length: 5 }).map((_, j) => (
            <div key={j} className="h-4 bg-gray-200 rounded flex-1" />
          ))}
        </div>
      ))}
    </div>
  )
}

export default function DataTable<T extends Record<string, unknown>>({
  columns,
  data,
  keyExtractor,
  onRowClick,
  selectedIds = [],
  onSelectionChange,
  isLoading,
  pagination,
  emptyMessage = 'No data found',
  sortBy,
  sortOrder,
  onSort,
}: DataTableProps<T>) {
  const [localSelected, setLocalSelected] = useState<string[]>(selectedIds)

  const selectedSet = useMemo(() => new Set(selectedIds.length ? selectedIds : localSelected), [selectedIds, localSelected])

  const allSelected = data.length > 0 && data.every((item) => selectedSet.has(keyExtractor(item)))
  const someSelected = data.some((item) => selectedSet.has(keyExtractor(item))) && !allSelected

  function handleSelectAll() {
    if (allSelected) {
      const newIds = localSelected.filter((id) => !data.some((item) => keyExtractor(item) === id))
      setLocalSelected(newIds)
      onSelectionChange?.(newIds)
    } else {
      const newIds = [...localSelected, ...data.map((item) => keyExtractor(item)).filter((id) => !localSelected.includes(id))]
      setLocalSelected(newIds)
      onSelectionChange?.(newIds)
    }
  }

  function handleSelect(id: string) {
    const newIds = localSelected.includes(id) ? localSelected.filter((sid) => sid !== id) : [...localSelected, id]
    setLocalSelected(newIds)
    onSelectionChange?.(newIds)
  }

  function SortIcon({ column }: { column: Column<T> }) {
    if (!column.sortable) return null
    const isActive = sortBy === column.key
    if (!isActive) return <ChevronsUpDown className="w-3.5 h-3.5 text-gray-400" />
    return sortOrder === 'asc' ? <ChevronUp className="w-3.5 h-3.5 text-primary-600" /> : <ChevronDown className="w-3.5 h-3.5 text-primary-600" />
  }

  return (
    <div className="w-full">
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead>
            <tr className="border-b border-gray-200 bg-gray-50/50">
              {onSelectionChange && (
                <th className="px-4 py-3 text-left w-10">
                  <input
                    type="checkbox"
                    className="rounded border-gray-300 text-primary-600 focus:ring-primary-500"
                    checked={allSelected}
                    ref={(el) => el && (el.indeterminate = someSelected)}
                    onChange={handleSelectAll}
                  />
                </th>
              )}
              {columns.map((column) => (
                <th
                  key={String(column.key)}
                  className={clsx(
                    'px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider',
                    column.sortable && 'cursor-pointer select-none hover:text-gray-700',
                    column.headerClassName,
                  )}
                  style={{ width: column.width }}
                  onClick={() => column.sortable && onSort?.(String(column.key))}
                >
                  <div className="flex items-center gap-1">
                    {column.header}
                    <SortIcon column={column} />
                  </div>
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {isLoading ? (
              <tr>
                <td colSpan={columns.length + (onSelectionChange ? 1 : 0)}>
                  <LoadingSkeleton />
                </td>
              </tr>
            ) : data.length === 0 ? (
              <tr>
                <td colSpan={columns.length + (onSelectionChange ? 1 : 0)} className="px-6 py-12 text-center text-gray-500">
                  {emptyMessage}
                </td>
              </tr>
            ) : (
              data.map((item) => {
                const id = keyExtractor(item)
                const isSelected = selectedSet.has(id)
                return (
                  <tr
                    key={id}
                    className={clsx(
                      'transition-colors',
                      isSelected ? 'bg-primary-50/50' : 'hover:bg-gray-50',
                      onRowClick && 'cursor-pointer',
                    )}
                    onClick={() => onRowClick?.(item)}
                  >
                    {onSelectionChange && (
                      <td className="px-4 py-3" onClick={(e) => e.stopPropagation()}>
                        <input
                          type="checkbox"
                          className="rounded border-gray-300 text-primary-600 focus:ring-primary-500"
                          checked={isSelected}
                          onChange={() => handleSelect(id)}
                        />
                      </td>
                    )}
                    {columns.map((column) => (
                      <td key={String(column.key)} className={clsx('px-4 py-3 text-sm text-gray-700', column.className)}>
                        {column.render ? column.render(item) : String(item[column.key as keyof T] ?? '')}
                      </td>
                    ))}
                  </tr>
                )
              })
            )}
          </tbody>
        </table>
      </div>
      {pagination && (
        <div className="flex items-center justify-between px-6 py-3 border-t border-gray-200 bg-gray-50/50">
          <span className="text-sm text-gray-600">
            {pagination.total} total
          </span>
          <div className="flex items-center gap-2">
            <button
              className="btn-ghost p-1 disabled:opacity-40"
              disabled={pagination.page <= 1}
              onClick={() => pagination.onPageChange(pagination.page - 1)}
            >
              <ChevronLeft className="w-4 h-4" />
            </button>
            <span className="text-sm text-gray-700">
              Page {pagination.page} of {pagination.totalPages}
            </span>
            <button
              className="btn-ghost p-1 disabled:opacity-40"
              disabled={pagination.page >= pagination.totalPages}
              onClick={() => pagination.onPageChange(pagination.page + 1)}
            >
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
