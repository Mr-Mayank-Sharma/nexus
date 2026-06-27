import { useState, useEffect, useRef, useCallback } from 'react'
import { Search, Command, FileText, Package, Users, Building2, ShoppingCart, Receipt, BarChart3, Settings, ArrowRight, X, Bell, GitBranch, Upload } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { clsx } from 'clsx'

interface SearchItem {
  id: string
  label: string
  description?: string
  path: string
  icon: React.ReactNode
  category: string
}

const searchItems: SearchItem[] = [
  { id: 'dashboard', label: 'Dashboard', path: '/', icon: <BarChart3 className="w-4 h-4" />, category: 'Navigation' },
  { id: 'orders', label: 'Orders', description: 'View all orders', path: '/orders', icon: <Package className="w-4 h-4" />, category: 'Orders' },
  { id: 'order-new', label: 'Create Order', description: 'Create a new sales order', path: '/orders/new', icon: <Package className="w-4 h-4" />, category: 'Orders' },
  { id: 'customers', label: 'Customers', description: 'Manage customers', path: '/customers', icon: <Users className="w-4 h-4" />, category: 'Customers' },
  { id: 'inventory', label: 'Inventory', description: 'View inventory levels', path: '/inventory', icon: <Building2 className="w-4 h-4" />, category: 'Inventory' },
  { id: 'warehouse', label: 'Warehouse Management', path: '/warehouse', icon: <Building2 className="w-4 h-4" />, category: 'Warehouse' },
  { id: 'procurement', label: 'Procurement', path: '/procurement', icon: <ShoppingCart className="w-4 h-4" />, category: 'Procurement' },
  { id: 'invoices', label: 'Invoices', path: '/invoices', icon: <Receipt className="w-4 h-4" />, category: 'Finance' },
  { id: 'integration-hub', label: 'Integration Hub', path: '/integration-hub', icon: <Command className="w-4 h-4" />, category: 'Integrations' },
  { id: 'analytics', label: 'Analytics & Reports', path: '/analytics', icon: <BarChart3 className="w-4 h-4" />, category: 'Analytics' },
  { id: 'settings', label: 'Settings', path: '/settings', icon: <Settings className="w-4 h-4" />, category: 'System' },
  { id: 'users', label: 'Users & Roles', path: '/users', icon: <Users className="w-4 h-4" />, category: 'System' },
  { id: 'documents', label: 'Documents', path: '/documents', icon: <FileText className="w-4 h-4" />, category: 'Documents' },
  { id: 'notifications', label: 'Notifications', path: '/notifications', icon: <Bell className="w-4 h-4" />, category: 'System' },
  { id: 'workflows', label: 'Workflows', path: '/workflows', icon: <GitBranch className="w-4 h-4" />, category: 'Automation' },
  { id: 'import-export', label: 'Import/Export Center', path: '/import-export', icon: <Upload className="w-4 h-4" />, category: 'Integrations' },
]

interface Props {
  open: boolean
  onClose: () => void
}

export default function GlobalSearch({ open, onClose }: Props) {
  const [query, setQuery] = useState('')
  const [selectedIndex, setSelectedIndex] = useState(0)
  const inputRef = useRef<HTMLInputElement>(null)
  const navigate = useNavigate()

  const filtered = query.trim()
    ? searchItems.filter(item =>
        item.label.toLowerCase().includes(query.toLowerCase()) ||
        item.description?.toLowerCase().includes(query.toLowerCase()) ||
        item.category.toLowerCase().includes(query.toLowerCase())
      )
    : searchItems

  useEffect(() => {
    if (open) {
      setQuery('')
      setSelectedIndex(0)
      setTimeout(() => inputRef.current?.focus(), 50)
    }
  }, [open])

  useEffect(() => {
    setSelectedIndex(0)
  }, [query])

  const execute = useCallback((item: SearchItem) => {
    navigate(item.path)
    onClose()
  }, [navigate, onClose])

  const handleKeyDown = (e: React.KeyboardEvent) => {
    switch (e.key) {
      case 'ArrowDown':
        e.preventDefault()
        setSelectedIndex(i => Math.min(i + 1, filtered.length - 1))
        break
      case 'ArrowUp':
        e.preventDefault()
        setSelectedIndex(i => Math.max(i - 1, 0))
        break
      case 'Enter':
        e.preventDefault()
        if (filtered[selectedIndex]) execute(filtered[selectedIndex])
        break
      case 'Escape':
        e.preventDefault()
        onClose()
        break
    }
  }

  if (!open) return null

  const grouped = filtered.reduce<Record<string, SearchItem[]>>((acc, item) => {
    (acc[item.category] = acc[item.category] || []).push(item)
    return acc
  }, {})

  let globalIdx = 0

  return (
    <>
      <div className="fixed inset-0 bg-black/40 z-[100]" onClick={onClose} />
      <div className="fixed top-[15%] left-1/2 -translate-x-1/2 w-full max-w-2xl z-[101] animate-[slideUp_200ms_ease-out]">
        <div className="bg-[var(--bg-card)] rounded-xl shadow-2xl border border-[var(--border-color)] overflow-hidden">
          <div className="flex items-center gap-3 px-4 py-3 border-b border-[var(--border-color)]">
            <Search className="w-5 h-5 text-[var(--text-tertiary)] shrink-0" />
            <input ref={inputRef}
              className="flex-1 bg-transparent border-none outline-none text-base text-[var(--text-primary)] placeholder:text-[var(--text-tertiary)]"
              placeholder="Search pages, orders, customers..."
              value={query}
              onChange={e => setQuery(e.target.value)}
              onKeyDown={handleKeyDown}
            />
            <button onClick={onClose} className="text-[var(--text-tertiary)] hover:text-[var(--text-primary)]">
              <X className="w-4 h-4" />
            </button>
          </div>

          <div className="max-h-[400px] overflow-y-auto p-2">
            {Object.entries(grouped).map(([category, items]) => (
              <div key={category}>
                <p className="px-2 py-1.5 text-xs font-semibold text-[var(--text-tertiary)] uppercase tracking-wider">{category}</p>
                {items.map(item => {
                  const idx = globalIdx++
                  const isSelected = idx === selectedIndex
                  return (
                    <button key={item.id}
                      className={clsx('w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm transition-colors',
                        isSelected ? 'bg-[var(--color-primary-50)] text-[var(--color-primary-700)]' : 'text-[var(--text-primary)] hover:bg-[var(--bg-tertiary)]'
                      )}
                      onClick={() => execute(item)}
                      onMouseEnter={() => setSelectedIndex(idx)}
                    >
                      <span className={clsx('p-1.5 rounded-md', isSelected ? 'bg-[var(--color-primary-100)]' : 'bg-[var(--bg-tertiary)]')}>
                        {item.icon}
                      </span>
                      <div className="flex-1 text-left">
                        <p className="font-medium">{item.label}</p>
                        {item.description && <p className="text-xs text-[var(--text-tertiary)]">{item.description}</p>}
                      </div>
                      <ArrowRight className={clsx('w-4 h-4', isSelected ? 'opacity-100' : 'opacity-0')} />
                    </button>
                  )
                })}
              </div>
            ))}
            {filtered.length === 0 && (
              <div className="enterprise-empty-state py-8">
                <Search className="w-8 h-8" />
                <h3>No results found</h3>
                <p>Try a different search term</p>
              </div>
            )}
          </div>

          <div className="flex items-center gap-4 px-4 py-2.5 border-t border-[var(--border-color)] bg-[var(--bg-secondary)]">
            <div className="flex items-center gap-1 text-xs text-[var(--text-tertiary)]">
              <kbd className="px-1.5 py-0.5 bg-[var(--bg-card)] border border-[var(--border-color)] rounded text-[10px] font-mono">↑↓</kbd>
              <span>Navigate</span>
            </div>
            <div className="flex items-center gap-1 text-xs text-[var(--text-tertiary)]">
              <kbd className="px-1.5 py-0.5 bg-[var(--bg-card)] border border-[var(--border-color)] rounded text-[10px] font-mono">↵</kbd>
              <span>Open</span>
            </div>
            <div className="flex items-center gap-1 text-xs text-[var(--text-tertiary)]">
              <kbd className="px-1.5 py-0.5 bg-[var(--bg-card)] border border-[var(--border-color)] rounded text-[10px] font-mono">Esc</kbd>
              <span>Close</span>
            </div>
          </div>
        </div>
      </div>
    </>
  )
}
