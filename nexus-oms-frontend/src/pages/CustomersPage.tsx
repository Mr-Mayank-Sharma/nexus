import { useState } from 'react'
import { clsx } from 'clsx'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Users, Plus, Search, Mail, Phone, MapPin, Loader2, Trash2, Edit3,
} from 'lucide-react'
import EnterpriseBreadcrumbs from '../components/enterprise/EnterpriseBreadcrumbs'
import EnterpriseToolbar from '../components/enterprise/EnterpriseToolbar'
import EnterpriseKPICard from '../components/enterprise/EnterpriseKPICard'
import EnterpriseStatusBadge from '../components/enterprise/EnterpriseStatusBadge'
import { useToast } from '../hooks/useToast'
import PermissionGate from '../components/rbac/PermissionGate'
import * as customersApi from '../api/customers'
import type { Customer } from '../types'

export default function CustomersPage() {
  const queryClient = useQueryClient()
  const { addToast } = useToast()
  const [searchTerm, setSearchTerm] = useState('')
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [editingCustomer, setEditingCustomer] = useState<Customer | null>(null)
  const [form, setForm] = useState({ name: '', email: '', phone: '', address: '' })

  const { data: customers = [], isLoading } = useQuery({
    queryKey: ['customers'],
    queryFn: async () => {
      const res = await customersApi.getCustomers()
      const d = res.data; return Array.isArray(d) ? d : (d?.content ?? []) as Customer[]
    },
  })

  const filtered = customers.filter(c =>
    !searchTerm || c.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
    c.email?.toLowerCase().includes(searchTerm.toLowerCase())
  )

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ['customers'] })
  }

  function openCreate() {
    setForm({ name: '', email: '', phone: '', address: '' })
    setEditingCustomer(null)
    setShowCreateModal(true)
  }

  function openEdit(c: Customer) {
    setForm({ name: c.name, email: c.email || '', phone: c.phone || '', address: c.address || '' })
    setEditingCustomer(c)
    setShowCreateModal(true)
  }

  const createMutation = useMutation({
    mutationFn: () => customersApi.createCustomer(form),
    onSuccess: () => { invalidate(); setShowCreateModal(false); addToast({ type: 'success', title: 'Customer created' }) },
    onError: () => addToast({ type: 'error', title: 'Failed to create customer' }),
  })

  const updateMutation = useMutation({
    mutationFn: () => customersApi.updateCustomer(editingCustomer!.id, form),
    onSuccess: () => { invalidate(); setShowCreateModal(false); addToast({ type: 'success', title: 'Customer updated' }) },
    onError: () => addToast({ type: 'error', title: 'Failed to update customer' }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => customersApi.deleteCustomer(id),
    onSuccess: () => { invalidate(); addToast({ type: 'success', title: 'Customer deleted' }) },
    onError: () => addToast({ type: 'error', title: 'Failed to delete customer' }),
  })

  return (
    <div className="space-y-6">
      <EnterpriseBreadcrumbs crumbs={[{ label: 'Sales' }, { label: 'Customers' }]} />

      <EnterpriseToolbar
        title="Customers"
        searchPlaceholder="Search by name or email..."
        searchValue={searchTerm}
        onSearch={setSearchTerm}
        autocomplete={{
          fetchSuggestions: async (q) => {
            if (!q) return customers.slice(0, 10)
            const term = q.toLowerCase()
            return customers.filter(c => c.name?.toLowerCase().includes(term) || c.email?.toLowerCase().includes(term)).slice(0, 10)
          },
          onSelect: (item: Customer) => { setSearchTerm(item.name); openEdit(item) },
          getOptionLabel: (item: Customer) => `${item.name} — ${item.email || item.phone || ''}`,
          getOptionValue: (item: Customer) => item.id,
          minChars: 1,
        }}
        actions={[
          { label: 'Add Customer', icon: <Plus className="w-4 h-4" />, onClick: openCreate, variant: 'primary', permission: { resource: 'customers', action: 'create' } },
        ]}
      />

      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <EnterpriseKPICard title="Total Customers" value={customers.length} icon={<Users />} color="primary" />
        <EnterpriseKPICard title="Active" value={customers.filter(c => c.name).length} icon={<Users />} color="success" />
        <EnterpriseKPICard title="With Email" value={customers.filter(c => c.email).length} icon={<Mail />} color="info" />
        <EnterpriseKPICard title="With Phone" value={customers.filter(c => c.phone).length} icon={<Phone />} color="warning" />
      </div>

      {isLoading ? (
        <div className="space-y-3">{[1,2,3].map(i => <div key={i} className="enterprise-skeleton h-16 w-full" />)}</div>
      ) : filtered.length === 0 ? (
        <div className="enterprise-card p-12 text-center">
          <Users className="w-12 h-12 mx-auto mb-3 text-[var(--text-tertiary)]" />
          <p className="font-medium text-[var(--text-secondary)]">No customers found</p>
          <p className="text-sm text-[var(--text-tertiary)] mt-1">Add a customer to get started</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {filtered.map(c => (
            <div key={c.id} className="enterprise-card p-5 hover:shadow-md transition-shadow">
              <div className="flex items-start justify-between">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-full bg-[var(--color-primary)]/10 flex items-center justify-center text-[var(--color-primary)] font-semibold text-sm">
                    {c.name.charAt(0).toUpperCase()}
                  </div>
                  <div>
                    <p className="text-sm font-medium text-[var(--text-primary)]">{c.name}</p>
                    <p className="text-xs text-[var(--text-tertiary)]">{c.externalId ? `ID: ${c.externalId}` : c.id.slice(0, 8)}</p>
                  </div>
                </div>
                <div className="flex gap-1">
                  <PermissionGate resource="customers" action="edit">
                    <button className="enterprise-btn-ghost p-1.5" title="Edit" onClick={() => openEdit(c)}>
                      <Edit3 className="w-3.5 h-3.5" />
                    </button>
                  </PermissionGate>
                  <PermissionGate resource="customers" action="delete">
                    <button className="enterprise-btn-ghost p-1.5 text-[var(--nexus-error-500)]" title="Delete"
                      onClick={() => { if (confirm('Delete this customer?')) deleteMutation.mutate(c.id); }}>
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </PermissionGate>
                </div>
              </div>
              <div className="mt-4 space-y-2 text-xs text-[var(--text-secondary)]">
                {c.email && (
                  <div className="flex items-center gap-2">
                    <Mail className="w-3.5 h-3.5 text-[var(--text-tertiary)]" />
                    <span>{c.email}</span>
                  </div>
                )}
                {c.phone && (
                  <div className="flex items-center gap-2">
                    <Phone className="w-3.5 h-3.5 text-[var(--text-tertiary)]" />
                    <span>{c.phone}</span>
                  </div>
                )}
                {c.address && (
                  <div className="flex items-start gap-2">
                    <MapPin className="w-3.5 h-3.5 text-[var(--text-tertiary)] mt-0.5" />
                    <span>{c.address}</span>
                  </div>
                )}
              </div>
              <div className="mt-3 pt-3 border-t border-[var(--border-subtle)] text-xs text-[var(--text-tertiary)]">
                Created {new Date(c.createdAt).toLocaleDateString()}
              </div>
            </div>
          ))}
        </div>
      )}

      {showCreateModal && (
        <div className="enterprise-modal-overlay" onClick={() => setShowCreateModal(false)}>
          <div className="enterprise-card p-6 w-full max-w-md mx-4" onClick={e => e.stopPropagation()}>
            <h2 className="text-lg font-semibold text-[var(--text-primary)] mb-4">
              {editingCustomer ? 'Edit Customer' : 'Add Customer'}
            </h2>
            <div className="space-y-4">
              <div>
                <label className="enterprise-label">Name *</label>
                <input className="enterprise-input w-full" value={form.name} onChange={e => setForm(p => ({ ...p, name: e.target.value }))} placeholder="Full name" />
              </div>
              <div>
                <label className="enterprise-label">Email</label>
                <input className="enterprise-input w-full" value={form.email} onChange={e => setForm(p => ({ ...p, email: e.target.value }))} placeholder="email@example.com" />
              </div>
              <div>
                <label className="enterprise-label">Phone</label>
                <input className="enterprise-input w-full" value={form.phone} onChange={e => setForm(p => ({ ...p, phone: e.target.value }))} placeholder="+1 (555) 000-0000" />
              </div>
              <div>
                <label className="enterprise-label">Address</label>
                <textarea className="enterprise-input w-full" value={form.address} onChange={e => setForm(p => ({ ...p, address: e.target.value }))} placeholder="Street, City, State, ZIP" rows={3} />
              </div>
            </div>
            <div className="flex justify-end gap-2 mt-6">
              <button className="enterprise-btn-secondary" onClick={() => setShowCreateModal(false)}>Cancel</button>
              <PermissionGate resource="customers" action={editingCustomer ? 'edit' : 'create'}>
                <button className="enterprise-btn-primary"
                  onClick={() => editingCustomer ? updateMutation.mutate() : createMutation.mutate()}
                  disabled={!form.name || (editingCustomer ? updateMutation.isPending : createMutation.isPending)}>
                  {(editingCustomer ? updateMutation.isPending : createMutation.isPending) ? (
                    <Loader2 className="w-4 h-4 animate-spin" />
                  ) : (
                    <Plus className="w-4 h-4" />
                  )}
                  {editingCustomer ? 'Update' : 'Create'}
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
