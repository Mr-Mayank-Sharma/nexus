import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Tags, Plus, Search, Loader2, Trash2, Edit3, DollarSign, Package, Weight, Image,
} from 'lucide-react'
import EnterpriseBreadcrumbs from '../components/enterprise/EnterpriseBreadcrumbs'
import EnterpriseToolbar from '../components/enterprise/EnterpriseToolbar'
import EnterpriseKPICard from '../components/enterprise/EnterpriseKPICard'
import EnterpriseStatusBadge from '../components/enterprise/EnterpriseStatusBadge'
import { useToast } from '../hooks/useToast'
import * as productsApi from '../api/products'
import type { Product } from '../types'

const categories = ['All', 'Apparel', 'Accessories', 'Home', 'Stationery']
const categoryColors: Record<string, string> = {
  Apparel: 'info', Accessories: 'primary', Home: 'success', Stationery: 'warning',
}

export default function ProductsPage() {
  const queryClient = useQueryClient()
  const { addToast } = useToast()
  const [searchTerm, setSearchTerm] = useState('')
  const [activeCategory, setActiveCategory] = useState('All')
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [editingProduct, setEditingProduct] = useState<Product | null>(null)
  const [form, setForm] = useState({ sku: '', productName: '', description: '', category: 'Apparel', unitPrice: 0, costPrice: 0, weight: 0 })

  const { data, isLoading } = useQuery({
    queryKey: ['products'],
    queryFn: async () => {
      try {
        const res = await productsApi.getProducts()
        if (!res || typeof res !== 'object') return []
        return (Array.isArray(res.data) ? res.data : []) as Product[]
      } catch {
        return [] as Product[]
      }
    },
  })
  const products: Product[] = Array.isArray(data) ? data : []

  const filtered = products.filter(p => {
    if (activeCategory !== 'All' && p.category !== activeCategory) return false
    if (searchTerm) {
      const term = searchTerm.toLowerCase()
      return p.productName.toLowerCase().includes(term) || p.sku.toLowerCase().includes(term)
    }
    return true
  })

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ['products'] })
  }

  function openCreate() {
    setForm({ sku: '', productName: '', description: '', category: 'Apparel', unitPrice: 0, costPrice: 0, weight: 0 })
    setEditingProduct(null)
    setShowCreateModal(true)
  }

  function openEdit(p: Product) {
    setForm({ sku: p.sku, productName: p.productName, description: p.description || '', category: p.category || 'Apparel', unitPrice: p.unitPrice, costPrice: p.costPrice || 0, weight: p.weight || 0 })
    setEditingProduct(p)
    setShowCreateModal(true)
  }

  const saveMutation = useMutation({
    mutationFn: () => editingProduct
      ? productsApi.updateProduct(editingProduct.id, form)
      : productsApi.createProduct(form),
    onSuccess: () => { invalidate(); setShowCreateModal(false); addToast({ type: 'success', title: editingProduct ? 'Product updated' : 'Product created' }) },
    onError: () => addToast({ type: 'error', title: 'Failed to save product' }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => productsApi.deleteProduct(id),
    onSuccess: () => { invalidate(); addToast({ type: 'success', title: 'Product deleted' }) },
    onError: () => addToast({ type: 'error', title: 'Failed to delete product' }),
  })

  const totalValue = products.reduce((sum, p) => sum + p.unitPrice, 0)
  const avgPrice = products.length > 0 ? totalValue / products.length : 0

  return (
    <div className="space-y-6">
      <EnterpriseBreadcrumbs crumbs={[{ label: 'Catalog' }, { label: 'Products' }]} />
      <EnterpriseToolbar
        title="Product Catalog"
        searchPlaceholder="Search by name or SKU..."
        searchValue={searchTerm}
        onSearch={setSearchTerm}
      />

      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <EnterpriseKPICard title="Total Products" value={products.length} icon={<Tags />} color="primary" />
        <EnterpriseKPICard title="Categories" value={new Set(products.map(p => p.category)).size} icon={<Package />} color="info" />
        <EnterpriseKPICard title="Avg Price" value={`$${avgPrice.toFixed(2)}`} icon={<DollarSign />} color="success" />
        <EnterpriseKPICard title="Total Value" value={`$${totalValue.toFixed(2)}`} icon={<DollarSign />} color="warning" />
      </div>

      <div className="flex gap-2 flex-wrap">
        {categories.map(cat => (
          <button key={cat} onClick={() => setActiveCategory(cat)}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-all ${
              activeCategory === cat
                ? 'bg-[var(--color-primary)] text-white'
                : 'bg-[var(--bg-tertiary)] text-[var(--text-secondary)] hover:bg-[var(--border-subtle)]'
            }`}>
            {cat} {cat !== 'All' && `(${products.filter(p => p.category === cat).length})`}
          </button>
        ))}
      </div>

      {isLoading ? (
        <div className="space-y-3">{[1,2,3].map(i => <div key={i} className="enterprise-skeleton h-20 w-full" />)}</div>
      ) : filtered.length === 0 ? (
        <div className="enterprise-card p-12 text-center">
          <Tags className="w-12 h-12 mx-auto mb-3 text-[var(--text-tertiary)]" />
          <p className="font-medium text-[var(--text-secondary)]">No products found</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {filtered.map(p => (
            <div key={p.id} className="enterprise-card p-5 hover:shadow-md transition-shadow">
              <div className="flex items-start justify-between">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <div className="w-10 h-10 rounded-lg bg-[var(--bg-tertiary)] flex items-center justify-center text-[var(--text-tertiary)]">
                      <Image className="w-5 h-5" />
                    </div>
                    <div className="min-w-0">
                      <p className="text-sm font-medium text-[var(--text-primary)] truncate">{p.productName}</p>
                      <p className="text-xs font-mono text-[var(--text-tertiary)]">{p.sku}</p>
                    </div>
                  </div>
                </div>
                <div className="flex gap-1">
                  <button className="enterprise-btn-ghost p-1.5" onClick={() => openEdit(p)}><Edit3 className="w-3.5 h-3.5" /></button>
                  <button className="enterprise-btn-ghost p-1.5 text-red-500"
                    onClick={() => { if (confirm('Delete?')) deleteMutation.mutate(p.id); }}>
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
              <div className="mt-3 flex items-center gap-2">
                {p.category && <EnterpriseStatusBadge status={categoryColors[p.category] || 'neutral'} label={p.category} size="sm" />}
                <EnterpriseStatusBadge status={p.isActive ? 'success' : 'neutral'} label={p.isActive ? 'Active' : 'Inactive'} size="sm" />
              </div>
              {p.description && <p className="mt-2 text-xs text-[var(--text-tertiary)] line-clamp-2">{p.description}</p>}
              <div className="mt-3 pt-3 border-t border-[var(--border-subtle)] flex items-center justify-between text-xs text-[var(--text-secondary)]">
                <span className="font-semibold text-[var(--text-primary)]">${p.unitPrice.toFixed(2)}</span>
                <div className="flex items-center gap-3">
                  {p.weight && <span className="flex items-center gap-1"><Weight className="w-3 h-3" />{p.weight} lbs</span>}
                  <span>Cost: ${p.costPrice?.toFixed(2)}</span>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {showCreateModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50" onClick={() => setShowCreateModal(false)}>
          <div className="enterprise-card p-6 w-full max-w-lg mx-4" onClick={e => e.stopPropagation()}>
            <h2 className="text-lg font-semibold text-[var(--text-primary)] mb-4">{editingProduct ? 'Edit Product' : 'Add Product'}</h2>
            <div className="grid grid-cols-2 gap-4">
              <div className="col-span-2">
                <label className="enterprise-label">Product Name *</label>
                <input className="enterprise-input w-full" value={form.productName} onChange={e => setForm(p => ({ ...p, productName: e.target.value }))} />
              </div>
              <div>
                <label className="enterprise-label">SKU *</label>
                <input className="enterprise-input w-full" value={form.sku} onChange={e => setForm(p => ({ ...p, sku: e.target.value }))} />
              </div>
              <div>
                <label className="enterprise-label">Category</label>
                <select className="enterprise-input w-full" value={form.category} onChange={e => setForm(p => ({ ...p, category: e.target.value }))}>
                  {categories.filter(c => c !== 'All').map(c => <option key={c} value={c}>{c}</option>)}
                </select>
              </div>
              <div className="col-span-2">
                <label className="enterprise-label">Description</label>
                <textarea className="enterprise-input w-full" value={form.description} onChange={e => setForm(p => ({ ...p, description: e.target.value }))} rows={2} />
              </div>
              <div>
                <label className="enterprise-label">Unit Price ($)</label>
                <input type="number" step="0.01" min="0" className="enterprise-input w-full" value={form.unitPrice} onChange={e => setForm(p => ({ ...p, unitPrice: parseFloat(e.target.value) || 0 }))} />
              </div>
              <div>
                <label className="enterprise-label">Cost Price ($)</label>
                <input type="number" step="0.01" min="0" className="enterprise-input w-full" value={form.costPrice} onChange={e => setForm(p => ({ ...p, costPrice: parseFloat(e.target.value) || 0 }))} />
              </div>
              <div>
                <label className="enterprise-label">Weight (lbs)</label>
                <input type="number" step="0.1" min="0" className="enterprise-input w-full" value={form.weight} onChange={e => setForm(p => ({ ...p, weight: parseFloat(e.target.value) || 0 }))} />
              </div>
            </div>
            <div className="flex justify-end gap-2 mt-6">
              <button className="enterprise-btn-secondary" onClick={() => setShowCreateModal(false)}>Cancel</button>
              <button className="enterprise-btn-primary" onClick={() => saveMutation.mutate()}
                disabled={!form.productName || !form.sku || saveMutation.isPending}>
                {saveMutation.isPending && <Loader2 className="w-4 h-4 animate-spin" />}
                {editingProduct ? 'Update' : 'Create'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
