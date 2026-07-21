import React, { useState, useEffect, Fragment } from 'react'
import { Building2, FileText, ShoppingCart, ClipboardList, Plus, Search, Eye, X, Check, ChevronDown, ChevronRight, Loader2, Star, PackageCheck, Send } from 'lucide-react'
import { clsx } from 'clsx'
import StatusBadge from '../components/common/StatusBadge'
import PermissionGate from '../components/rbac/PermissionGate'
import Autocomplete from '../components/common/Autocomplete'
import { useToast } from '../hooks/useToast'
import * as procurementApi from '../api/procurement'
import type {
  Supplier, SupplierContact, SupplierContract,
  PurchaseRequest, PurchaseRequestItem,
  Rfq, RfqResponse, RfqItem,
  PurchaseOrder, PurchaseOrderItem,
} from '../api/procurement'

type TabId = 'suppliers' | 'requests' | 'rfqs' | 'purchaseOrders'

const tabs: { id: TabId; label: string; icon: typeof Building2 }[] = [
  { id: 'suppliers', label: 'Suppliers', icon: Building2 },
  { id: 'requests', label: 'Purchase Requests', icon: FileText },
  { id: 'rfqs', label: 'RFQs', icon: ShoppingCart },
  { id: 'purchaseOrders', label: 'Purchase Orders', icon: ClipboardList },
]

const prStatusColors: Record<string, string> = {
  DRAFT: 'bg-[var(--surface-muted)] text-[var(--text-secondary)]',
  PENDING_APPROVAL: 'bg-[var(--nexus-warning-100)] text-[var(--nexus-warning-700)]',
  APPROVED: 'bg-[var(--nexus-success-100)] text-[var(--nexus-success-700)]',
  REJECTED: 'bg-[var(--nexus-error-50)] text-[var(--nexus-error-700)]',
  CONVERTED: 'bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-700)]',
}

const poStatusOrder: Record<string, number> = {
  DRAFT: 0,
  PENDING_APPROVAL: 1,
  APPROVED: 2,
  PARTIALLY_RECEIVED: 3,
  RECEIVED: 4,
  CLOSED: 5,
}

export default function ProcurementPage() {
  const { addToast } = useToast()
  const [activeTab, setActiveTab] = useState<TabId>('suppliers')

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5"><ShoppingCart className="w-7 h-7 text-[var(--nexus-primary-500)]" /> Procurement</h1>
        <p className="text-sm text-[var(--text-secondary)] mt-1">Manage suppliers, purchase requests, RFQs, and purchase orders</p>
      </div>

      <div className="flex gap-1 bg-[var(--surface-muted)] p-1 rounded-lg w-fit">
        {tabs.map((tab) => {
          const Icon = tab.icon
          return (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={clsx(
                'px-4 py-2 text-sm font-medium rounded-md transition-colors flex items-center gap-2',
                activeTab === tab.id
                  ? 'bg-white text-[var(--text-primary)] shadow-sm'
                  : 'text-[var(--text-secondary)] hover:text-[var(--text-secondary)]',
              )}
            >
              <Icon className="w-4 h-4" />
              {tab.label}
            </button>
          )
        })}
      </div>

      {activeTab === 'suppliers' && <SuppliersTab />}
      {activeTab === 'requests' && <RequestsTab />}
      {activeTab === 'rfqs' && <RfqsTab />}
      {activeTab === 'purchaseOrders' && <PurchaseOrdersTab />}
    </div>
  )
}

function SuppliersTab() {
  const { addToast } = useToast()
  const [suppliers, setSuppliers] = useState<Supplier[]>([])
  const [loading, setLoading] = useState(true)
  const [showModal, setShowModal] = useState(false)
  const [saving, setSaving] = useState(false)
  const [expandedId, setExpandedId] = useState<string | null>(null)
  const [contacts, setContacts] = useState<Record<string, SupplierContact[]>>({})
  const [contracts, setContracts] = useState<Record<string, SupplierContract[]>>({})
  const [loadingContacts, setLoadingContacts] = useState<Record<string, boolean>>({})
  const [loadingContracts, setLoadingContracts] = useState<Record<string, boolean>>({})
  const [showContactForm, setShowContactForm] = useState<string | null>(null)
  const [showContractForm, setShowContractForm] = useState<string | null>(null)
  const [savingContact, setSavingContact] = useState(false)
  const [savingContract, setSavingContract] = useState(false)

  const [form, setForm] = useState({
    supplierCode: '',
    companyName: '',
    tradingName: '',
    taxId: '',
    supplierType: 'MANUFACTURER',
    status: 'ACTIVE',
    paymentTerms: 'NET30',
    currency: 'USD',
    creditLimit: '',
    street: '',
    city: '',
    state: '',
    zip: '',
    country: 'US',
    phone: '',
    email: '',
  })

  const [contactForm, setContactForm] = useState({
    name: '',
    jobTitle: '',
    email: '',
    phone: '',
    isPrimary: false,
  })

  const [contractForm, setContractForm] = useState({
    contractNumber: '',
    type: 'SERVICE',
    startDate: '',
    endDate: '',
    status: 'ACTIVE',
    autoRenew: false,
  })

  const supplierTypeOptions = [
    { value: 'MANUFACTURER', label: 'Manufacturer' },
    { value: 'DISTRIBUTOR', label: 'Distributor' },
    { value: 'WHOLESALER', label: 'Wholesaler' },
    { value: 'SERVICE_PROVIDER', label: 'Service Provider' },
    { value: 'CONSULTANT', label: 'Consultant' },
  ]

  const supplierStatusOptions = [
    { value: 'ACTIVE', label: 'Active' },
    { value: 'INACTIVE', label: 'Inactive' },
    { value: 'BLACKLISTED', label: 'Blacklisted' },
  ]

  const paymentTermsOptions = [
    { value: 'NET15', label: 'Net 15' },
    { value: 'NET30', label: 'Net 30' },
    { value: 'NET45', label: 'Net 45' },
    { value: 'NET60', label: 'Net 60' },
    { value: 'COD', label: 'Cash on Delivery' },
  ]

  const currencyOptions = [
    { value: 'USD', label: 'USD' },
    { value: 'EUR', label: 'EUR' },
    { value: 'GBP', label: 'GBP' },
    { value: 'CAD', label: 'CAD' },
    { value: 'AUD', label: 'AUD' },
  ]

  const countryOptions = [
    { value: 'US', label: 'United States' },
    { value: 'CA', label: 'Canada' },
    { value: 'GB', label: 'United Kingdom' },
    { value: 'DE', label: 'Germany' },
    { value: 'FR', label: 'France' },
    { value: 'AU', label: 'Australia' },
  ]

  const contractTypeOptions = [
    { value: 'SERVICE', label: 'Service' },
    { value: 'SUPPLY', label: 'Supply' },
    { value: 'LEASE', label: 'Lease' },
    { value: 'NON_DISCLOSURE', label: 'Non-Disclosure' },
  ]

  const contractStatusOptions = [
    { value: 'ACTIVE', label: 'Active' },
    { value: 'EXPIRED', label: 'Expired' },
    { value: 'TERMINATED', label: 'Terminated' },
  ]

  useEffect(() => { fetchSuppliers() }, [])

  async function fetchSuppliers() {
    try {
      setLoading(true)
      const res = await procurementApi.getSuppliers(0, 100)
      setSuppliers(res.data?.content ?? [])
    } catch {
      addToast({ type: 'error', title: 'Failed to load suppliers' })
    } finally {
      setLoading(false)
    }
  }

  function openCreate() {
    setForm({
      supplierCode: '',
      companyName: '',
      tradingName: '',
      taxId: '',
      supplierType: 'MANUFACTURER',
      status: 'ACTIVE',
      paymentTerms: 'NET30',
      currency: 'USD',
      creditLimit: '',
      street: '',
      city: '',
      state: '',
      zip: '',
      country: 'US',
      phone: '',
      email: '',
    })
    setShowModal(true)
  }

  async function handleSave() {
    if (!form.companyName.trim()) {
      addToast({ type: 'warning', title: 'Company name is required' })
      return
    }
    setSaving(true)
    try {
      await procurementApi.createSupplier({
        code: form.supplierCode,
        name: form.companyName,
        tradingName: form.tradingName,
        taxId: form.taxId,
        category: form.supplierType,
        status: form.status,
        paymentTerms: form.paymentTerms,
        currency: form.currency,
        creditLimit: parseFloat(form.creditLimit) || 0,
        rating: 0,
        isActive: form.status === 'ACTIVE',
        address: {
          street: form.street,
          city: form.city,
          state: form.state,
          zip: form.zip,
          country: form.country,
        },
        primaryContactName: form.companyName,
        primaryContactEmail: form.email,
        primaryContactPhone: form.phone,
      })
      addToast({ type: 'success', title: 'Supplier created' })
      setShowModal(false)
      await fetchSuppliers()
    } catch {
      addToast({ type: 'error', title: 'Failed to create supplier' })
    } finally {
      setSaving(false)
    }
  }

  async function toggleExpand(supplierId: string) {
    if (expandedId === supplierId) {
      setExpandedId(null)
      return
    }
    setExpandedId(supplierId)
    if (!contacts[supplierId]) {
      setLoadingContacts((prev) => ({ ...prev, [supplierId]: true }))
      try {
        const res = await procurementApi.getSupplierContacts(supplierId)
        setContacts((prev) => ({ ...prev, [supplierId]: Array.isArray(res.data) ? res.data : (res.data?.content ?? []) }))
      } catch {
        addToast({ type: 'error', title: 'Failed to load contacts' })
      } finally {
        setLoadingContacts((prev) => ({ ...prev, [supplierId]: false }))
      }
    }
    if (!contracts[supplierId]) {
      setLoadingContracts((prev) => ({ ...prev, [supplierId]: true }))
      try {
        const res = await procurementApi.getSupplierContracts(supplierId)
        setContracts((prev) => ({ ...prev, [supplierId]: Array.isArray(res.data) ? res.data : (res.data?.content ?? []) }))
      } catch {
        addToast({ type: 'error', title: 'Failed to load contracts' })
      } finally {
        setLoadingContracts((prev) => ({ ...prev, [supplierId]: false }))
      }
    }
  }

  async function handleAddContact(supplierId: string) {
    if (!contactForm.name.trim() || !contactForm.email.trim()) {
      addToast({ type: 'warning', title: 'Name and email are required' })
      return
    }
    setSavingContact(true)
    try {
      await procurementApi.addSupplierContact({
        supplierId,
        name: contactForm.name,
        title: contactForm.jobTitle,
        email: contactForm.email,
        phone: contactForm.phone,
        isPrimary: contactForm.isPrimary,
      })
      addToast({ type: 'success', title: 'Contact added' })
      setShowContactForm(null)
      setContactForm({ name: '', jobTitle: '', email: '', phone: '', isPrimary: false })
      const res = await procurementApi.getSupplierContacts(supplierId)
      setContacts((prev) => ({ ...prev, [supplierId]: Array.isArray(res.data) ? res.data : (res.data?.content ?? []) }))
    } catch {
      addToast({ type: 'error', title: 'Failed to add contact' })
    } finally {
      setSavingContact(false)
    }
  }

  async function handleAddContract(supplierId: string) {
    if (!contractForm.contractNumber.trim() || !contractForm.startDate) {
      addToast({ type: 'warning', title: 'Contract number and start date are required' })
      return
    }
    setSavingContract(true)
    try {
      await procurementApi.addSupplierContract({
        supplierId,
        title: contractForm.contractNumber,
        startDate: contractForm.startDate,
        endDate: contractForm.endDate,
        status: contractForm.status,
        terms: contractForm.type,
        value: 0,
      })
      addToast({ type: 'success', title: 'Contract added' })
      setShowContractForm(null)
      setContractForm({ contractNumber: '', type: 'SERVICE', startDate: '', endDate: '', status: 'ACTIVE', autoRenew: false })
      const res = await procurementApi.getSupplierContracts(supplierId)
      setContracts((prev) => ({ ...prev, [supplierId]: Array.isArray(res.data) ? res.data : (res.data?.content ?? []) }))
    } catch {
      addToast({ type: 'error', title: 'Failed to add contract' })
    } finally {
      setSavingContract(false)
    }
  }

  function renderStars(rating: number) {
    return (
      <div className="flex items-center gap-0.5">
        {Array.from({ length: 5 }).map((_, i) => (
          <Star key={i} className={clsx('w-3.5 h-3.5', i < rating ? 'text-[var(--nexus-warning-400)] fill-yellow-400' : 'text-[var(--text-tertiary)]')} />
        ))}
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-sm text-[var(--text-secondary)]">{suppliers.length} suppliers</p>
        <PermissionGate resource="procurement" action="create">
          <button onClick={openCreate} className="btn-primary text-sm">
            <Plus className="w-4 h-4" /> Add Supplier
          </button>
        </PermissionGate>
      </div>

      {loading ? (
        <div className="flex items-center justify-center h-64">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[var(--nexus-primary-600)]" />
        </div>
      ) : suppliers.length === 0 ? (
        <div className="card p-12 text-center text-[var(--text-secondary)]">
          <Building2 className="w-12 h-12 mx-auto mb-3 text-[var(--text-tertiary)]" />
          <p className="font-medium">No suppliers</p>
          <p className="text-sm mt-1">Add your first supplier to get started</p>
        </div>
      ) : (
        <div className="card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-[var(--border-subtle)] bg-[var(--surface-sunken)]/50">
                  <th className="px-4 py-3 w-8" />
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Code</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Company Name</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Type</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Status</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Rating</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Payment Terms</th>
                  <th className="px-4 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {suppliers.map((supplier) => (
                  <Fragment key={supplier.id}>
                    <tr
                      className="hover:bg-[var(--surface-sunken)] cursor-pointer transition-colors"
                      onClick={() => toggleExpand(supplier.id)}
                    >
                      <td className="px-4 py-3">
                        {expandedId === supplier.id
                          ? <ChevronDown className="w-4 h-4 text-[var(--text-tertiary)]" />
                          : <ChevronRight className="w-4 h-4 text-[var(--text-tertiary)]" />}
                      </td>
                      <td className="px-4 py-3 text-sm font-medium text-[var(--text-primary)]">{supplier.code}</td>
                      <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{supplier.name}</td>
                      <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{supplier.category?.replace(/_/g, ' ')}</td>
                      <td className="px-4 py-3"><StatusBadge status={supplier.status} size="sm" /></td>
                      <td className="px-4 py-3">{renderStars(Math.round(supplier.rating))}</td>
                      <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{supplier.paymentTerms}</td>
                      <td className="px-4 py-3 text-right">
                        <button
                          onClick={(e) => { e.stopPropagation(); setExpandedId(expandedId === supplier.id ? null : supplier.id) }}
                          className="p-1.5 hover:bg-[var(--surface-muted)] rounded text-[var(--text-tertiary)] hover:text-[var(--text-secondary)]"
                        >
                          <Eye className="w-4 h-4" />
                        </button>
                      </td>
                    </tr>
                    {expandedId === supplier.id && (
                      <tr>
                        <td colSpan={8} className="px-4 py-4 bg-[var(--surface-sunken)]/50">
                          <div className="space-y-6">
                            <div>
                              <div className="flex items-center justify-between mb-3">
                                <h4 className="text-sm font-semibold text-[var(--text-secondary)]">Contacts</h4>
                                <PermissionGate resource="procurement" action="create">
                                  <button
                                    onClick={() => setShowContactForm(showContactForm === supplier.id ? null : supplier.id)}
                                    className="btn-secondary text-xs"
                                  >
                                    <Plus className="w-3 h-3" /> Add Contact
                                  </button>
                                </PermissionGate>
                              </div>
                              {loadingContacts[supplier.id] ? (
                                <div className="flex items-center gap-2 text-sm text-[var(--text-tertiary)] py-2">
                                  <Loader2 className="w-3 h-3 animate-spin" /> Loading...
                                </div>
                              ) : contacts[supplier.id]?.length === 0 ? (
                                <p className="text-sm text-[var(--text-tertiary)] py-2">No contacts</p>
                              ) : (
                                <table className="w-full mb-3">
                                  <thead>
                                    <tr className="border-b border-[var(--border-default)]">
                                      <th className="px-3 py-2 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Name</th>
                                      <th className="px-3 py-2 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Job Title</th>
                                      <th className="px-3 py-2 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Email</th>
                                      <th className="px-3 py-2 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Phone</th>
                                      <th className="px-3 py-2 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase" />
                                    </tr>
                                  </thead>
                                  <tbody className="divide-y divide-gray-100">
                                    {contacts[supplier.id]?.map((c) => (
                                      <tr key={c.id} className="hover:bg-white">
                                        <td className="px-3 py-2 text-sm text-[var(--text-secondary)]">{c.name}</td>
                                        <td className="px-3 py-2 text-sm text-[var(--text-secondary)]">{c.title}</td>
                                        <td className="px-3 py-2 text-sm text-[var(--text-secondary)]">{c.email}</td>
                                        <td className="px-3 py-2 text-sm text-[var(--text-secondary)]">{c.phone}</td>
                                        <td className="px-3 py-2 text-sm">
                                          {c.isPrimary && (
                                            <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-[var(--nexus-primary-100)] text-[var(--nexus-primary-700)]">
                                              Primary
                                            </span>
                                          )}
                                        </td>
                                      </tr>
                                    ))}
                                  </tbody>
                                </table>
                              )}
                              {showContactForm === supplier.id && (
                                <div className="bg-white rounded-lg border border-[var(--border-default)] p-4 space-y-3">
                                  <div className="grid grid-cols-2 gap-3">
                                    <div>
                                      <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">Name</label>
                                      <Autocomplete
                                        value={contactForm.name}
                                        onChange={(value) => setContactForm({ ...contactForm, name: value })}
                                        inputClassName="input w-full text-sm"
                                        placeholder="Contact name"
                                        minChars={0}
                                        showSearchIcon={false}
                                        clearable={false}
                                      />
                                    </div>
                                    <div>
                                      <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">Job Title</label>
                                      <Autocomplete
                                        value={contactForm.jobTitle}
                                        onChange={(value) => setContactForm({ ...contactForm, jobTitle: value })}
                                        inputClassName="input w-full text-sm"
                                        placeholder="Job title"
                                        minChars={0}
                                        showSearchIcon={false}
                                        clearable={false}
                                      />
                                    </div>
                                    <div>
                                      <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">Email</label>
                                      <Autocomplete
                                        value={contactForm.email}
                                        onChange={(value) => setContactForm({ ...contactForm, email: value })}
                                        inputClassName="input w-full text-sm"
                                        placeholder="Email"
                                        minChars={0}
                                        showSearchIcon={false}
                                        clearable={false}
                                      />
                                    </div>
                                    <div>
                                      <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">Phone</label>
                                      <Autocomplete
                                        value={contactForm.phone}
                                        onChange={(value) => setContactForm({ ...contactForm, phone: value })}
                                        inputClassName="input w-full text-sm"
                                        placeholder="Phone"
                                        minChars={0}
                                        showSearchIcon={false}
                                        clearable={false}
                                      />
                                    </div>
                                  </div>
                                  <label className="flex items-center gap-2 text-sm text-[var(--text-secondary)]">
                                    <input
                                      type="checkbox"
                                      checked={contactForm.isPrimary}
                                      onChange={(e) => setContactForm({ ...contactForm, isPrimary: e.target.checked })}
                                      className="rounded border-[var(--border-default)] text-[var(--text-brand)] focus:ring-[var(--nexus-primary-500)]"
                                    />
                                    Set as primary contact
                                  </label>
                                  <div className="flex justify-end gap-2 pt-2">
                                    <button onClick={() => setShowContactForm(null)} className="btn-secondary text-xs">Cancel</button>
                                    <PermissionGate resource="procurement" action="create">
                                      <button
                                        onClick={() => handleAddContact(supplier.id)}
                                        disabled={savingContact}
                                        className="btn-primary text-xs"
                                      >
                                        {savingContact && <Loader2 className="w-3 h-3 animate-spin" />}
                                        Save
                                      </button>
                                    </PermissionGate>
                                  </div>
                                </div>
                              )}
                            </div>

                            <div>
                              <div className="flex items-center justify-between mb-3">
                                <h4 className="text-sm font-semibold text-[var(--text-secondary)]">Contracts</h4>
                                <PermissionGate resource="procurement" action="create">
                                  <button
                                    onClick={() => setShowContractForm(showContractForm === supplier.id ? null : supplier.id)}
                                    className="btn-secondary text-xs"
                                  >
                                    <Plus className="w-3 h-3" /> Add Contract
                                  </button>
                                </PermissionGate>
                              </div>
                              {loadingContracts[supplier.id] ? (
                                <div className="flex items-center gap-2 text-sm text-[var(--text-tertiary)] py-2">
                                  <Loader2 className="w-3 h-3 animate-spin" /> Loading...
                                </div>
                              ) : contracts[supplier.id]?.length === 0 ? (
                                <p className="text-sm text-[var(--text-tertiary)] py-2">No contracts</p>
                              ) : (
                                <table className="w-full mb-3">
                                  <thead>
                                    <tr className="border-b border-[var(--border-default)]">
                                      <th className="px-3 py-2 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Contract #</th>
                                      <th className="px-3 py-2 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Type</th>
                                      <th className="px-3 py-2 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Start Date</th>
                                      <th className="px-3 py-2 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">End Date</th>
                                      <th className="px-3 py-2 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Status</th>
                                      <th className="px-3 py-2 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase" />
                                    </tr>
                                  </thead>
                                  <tbody className="divide-y divide-gray-100">
                                    {contracts[supplier.id]?.map((c) => (
                                      <tr key={c.id} className="hover:bg-white">
                                        <td className="px-3 py-2 text-sm font-medium text-[var(--text-primary)]">{c.title}</td>
                                        <td className="px-3 py-2 text-sm text-[var(--text-secondary)]">{c.terms}</td>
                                        <td className="px-3 py-2 text-sm text-[var(--text-secondary)]">{new Date(c.startDate).toLocaleDateString()}</td>
                                        <td className="px-3 py-2 text-sm text-[var(--text-secondary)]">{new Date(c.endDate).toLocaleDateString()}</td>
                                        <td className="px-3 py-2"><StatusBadge status={c.status} size="sm" /></td>
                                        <td className="px-3 py-2 text-sm">
                                          {c.status === 'ACTIVE' && (
                                            <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-[var(--nexus-success-100)] text-[var(--nexus-success-700)]">
                                              Auto-Renew
                                            </span>
                                          )}
                                        </td>
                                      </tr>
                                    ))}
                                  </tbody>
                                </table>
                              )}
                              {showContractForm === supplier.id && (
                                <div className="bg-white rounded-lg border border-[var(--border-default)] p-4 space-y-3">
                                  <div className="grid grid-cols-2 gap-3">
                                    <div>
                                      <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">Contract Number</label>
                                      <Autocomplete
                                        value={contractForm.contractNumber}
                                        onChange={(value) => setContractForm({ ...contractForm, contractNumber: value })}
                                        inputClassName="input w-full text-sm"
                                        placeholder="CON-001"
                                        minChars={0}
                                        showSearchIcon={false}
                                        clearable={false}
                                      />
                                    </div>
                                    <div>
                                      <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">Type</label>
                                      <Autocomplete
                                        value={contractForm.type}
                                        onChange={(value) => setContractForm({ ...contractForm, type: value })}
                                        suggestions={contractTypeOptions}
                                        getOptionLabel={o => o.label}
                                        getOptionValue={o => o.value}
                                        inputClassName="input w-full text-sm"
                                        minChars={0}
                                        showSearchIcon={false}
                                        clearable={false}
                                      />
                                    </div>
                                    <div>
                                      <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">Start Date</label>
                                      <Autocomplete
                                        value={contractForm.startDate}
                                        onChange={(value) => setContractForm({ ...contractForm, startDate: value })}
                                        inputClassName="input w-full text-sm"
                                        placeholder=""
                                        minChars={0}
                                        showSearchIcon={false}
                                        clearable={false}
                                      />
                                    </div>
                                    <div>
                                      <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">End Date</label>
                                      <Autocomplete
                                        value={contractForm.endDate}
                                        onChange={(value) => setContractForm({ ...contractForm, endDate: value })}
                                        inputClassName="input w-full text-sm"
                                        placeholder=""
                                        minChars={0}
                                        showSearchIcon={false}
                                        clearable={false}
                                      />
                                    </div>
                                    <div>
                                      <label className="block text-xs font-medium text-[var(--text-secondary)] mb-1">Status</label>
                                      <Autocomplete
                                        value={contractForm.status}
                                        onChange={(value) => setContractForm({ ...contractForm, status: value })}
                                        suggestions={contractStatusOptions}
                                        getOptionLabel={o => o.label}
                                        getOptionValue={o => o.value}
                                        inputClassName="input w-full text-sm"
                                        minChars={0}
                                        showSearchIcon={false}
                                        clearable={false}
                                      />
                                    </div>
                                  </div>
                                  <label className="flex items-center gap-2 text-sm text-[var(--text-secondary)]">
                                    <input
                                      type="checkbox"
                                      checked={contractForm.autoRenew}
                                      onChange={(e) => setContractForm({ ...contractForm, autoRenew: e.target.checked })}
                                      className="rounded border-[var(--border-default)] text-[var(--text-brand)] focus:ring-[var(--nexus-primary-500)]"
                                    />
                                    Auto-renew
                                  </label>
                                  <div className="flex justify-end gap-2 pt-2">
                                    <button onClick={() => setShowContractForm(null)} className="btn-secondary text-xs">Cancel</button>
                                    <PermissionGate resource="procurement" action="create">
                                      <button
                                        onClick={() => handleAddContract(supplier.id)}
                                        disabled={savingContract}
                                        className="btn-primary text-xs"
                                      >
                                        {savingContract && <Loader2 className="w-3 h-3 animate-spin" />}
                                        Save
                                      </button>
                                    </PermissionGate>
                                  </div>
                                </div>
                              )}
                            </div>
                          </div>
                        </td>
                      </tr>
                    )}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {showModal && (
        <div className="enterprise-modal-overlay">
          <div className="enterprise-modal max-w-2xl">
            <div className="flex items-center justify-between p-6 border-b border-[var(--border-subtle)]">
              <h2 className="text-lg font-semibold text-[var(--text-primary)]">Add Supplier</h2>
              <button onClick={() => setShowModal(false)} className="p-1 hover:bg-[var(--surface-muted)] rounded"><X className="w-5 h-5" /></button>
            </div>
            <div className="p-6 space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Supplier Code</label>
                  <Autocomplete value={form.supplierCode} onChange={(value) => setForm({ ...form, supplierCode: value })} inputClassName="input w-full" placeholder="SUP-001" minChars={0} showSearchIcon={false} clearable={false} />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Company Name *</label>
                  <Autocomplete value={form.companyName} onChange={(value) => setForm({ ...form, companyName: value })} inputClassName="input w-full" placeholder="Acme Corp" minChars={0} showSearchIcon={false} clearable={false} />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Trading Name</label>
                  <Autocomplete value={form.tradingName} onChange={(value) => setForm({ ...form, tradingName: value })} inputClassName="input w-full" placeholder="Acme" minChars={0} showSearchIcon={false} clearable={false} />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Tax ID</label>
                  <Autocomplete value={form.taxId} onChange={(value) => setForm({ ...form, taxId: value })} inputClassName="input w-full" placeholder="TAX-12345" minChars={0} showSearchIcon={false} clearable={false} />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Supplier Type</label>
                  <Autocomplete
                    value={form.supplierType}
                    onChange={(value) => setForm({ ...form, supplierType: value })}
                    suggestions={supplierTypeOptions}
                    getOptionLabel={o => o.label}
                    getOptionValue={o => o.value}
                    inputClassName="input w-full"
                    minChars={0}
                    showSearchIcon={false}
                    clearable={false}
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Status</label>
                  <Autocomplete
                    value={form.status}
                    onChange={(value) => setForm({ ...form, status: value })}
                    suggestions={supplierStatusOptions}
                    getOptionLabel={o => o.label}
                    getOptionValue={o => o.value}
                    inputClassName="input w-full"
                    minChars={0}
                    showSearchIcon={false}
                    clearable={false}
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Payment Terms</label>
                  <Autocomplete
                    value={form.paymentTerms}
                    onChange={(value) => setForm({ ...form, paymentTerms: value })}
                    suggestions={paymentTermsOptions}
                    getOptionLabel={o => o.label}
                    getOptionValue={o => o.value}
                    inputClassName="input w-full"
                    minChars={0}
                    showSearchIcon={false}
                    clearable={false}
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Currency</label>
                  <Autocomplete
                    value={form.currency}
                    onChange={(value) => setForm({ ...form, currency: value })}
                    suggestions={currencyOptions}
                    getOptionLabel={o => o.label}
                    getOptionValue={o => o.value}
                    inputClassName="input w-full"
                    minChars={0}
                    showSearchIcon={false}
                    clearable={false}
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Credit Limit</label>
                  <Autocomplete value={form.creditLimit} onChange={(value) => setForm({ ...form, creditLimit: value })} inputClassName="input w-full" placeholder="50000" minChars={0} showSearchIcon={false} clearable={false} />
                </div>
              </div>

              <div>
                <h4 className="text-sm font-semibold text-[var(--text-secondary)] mb-2">Address</h4>
                <div className="grid grid-cols-2 gap-4">
                  <div className="col-span-2">
                    <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Street</label>
                    <Autocomplete value={form.street} onChange={(value) => setForm({ ...form, street: value })} inputClassName="input w-full" placeholder="123 Main St" minChars={0} showSearchIcon={false} clearable={false} />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">City</label>
                    <Autocomplete value={form.city} onChange={(value) => setForm({ ...form, city: value })} inputClassName="input w-full" placeholder="New York" minChars={0} showSearchIcon={false} clearable={false} />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">State</label>
                    <Autocomplete value={form.state} onChange={(value) => setForm({ ...form, state: value })} inputClassName="input w-full" placeholder="NY" minChars={0} showSearchIcon={false} clearable={false} />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">ZIP Code</label>
                    <Autocomplete value={form.zip} onChange={(value) => setForm({ ...form, zip: value })} inputClassName="input w-full" placeholder="10001" minChars={0} showSearchIcon={false} clearable={false} />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Country</label>
                    <Autocomplete
                      value={form.country}
                      onChange={(value) => setForm({ ...form, country: value })}
                      suggestions={countryOptions}
                      getOptionLabel={o => o.label}
                      getOptionValue={o => o.value}
                      inputClassName="input w-full"
                      minChars={0}
                      showSearchIcon={false}
                      clearable={false}
                    />
                  </div>
                </div>
              </div>

              <div>
                <h4 className="text-sm font-semibold text-[var(--text-secondary)] mb-2">Contact</h4>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Phone</label>
                    <Autocomplete value={form.phone} onChange={(value) => setForm({ ...form, phone: value })} inputClassName="input w-full" placeholder="+1 555-1234" minChars={0} showSearchIcon={false} clearable={false} />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Email</label>
                    <Autocomplete value={form.email} onChange={(value) => setForm({ ...form, email: value })} inputClassName="input w-full" placeholder="contact@acme.com" minChars={0} showSearchIcon={false} clearable={false} />
                  </div>
                </div>
              </div>
            </div>
            <div className="p-6 border-t border-[var(--border-subtle)] flex justify-end gap-3">
              <button onClick={() => setShowModal(false)} className="btn-secondary text-sm">Cancel</button>
              <PermissionGate resource="procurement" action="create">
                <button onClick={handleSave} disabled={saving} className="btn-primary text-sm">
                  {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Building2 className="w-4 h-4" />}
                  Create Supplier
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function RequestsTab() {
  const { addToast } = useToast()
  const [requests, setRequests] = useState<PurchaseRequest[]>([])
  const [loading, setLoading] = useState(true)
  const [showModal, setShowModal] = useState(false)
  const [saving, setSaving] = useState(false)
  const [expandedId, setExpandedId] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState<string | null>(null)
  const [approving, setApproving] = useState<string | null>(null)

  const [form, setForm] = useState({
    title: '',
    description: '',
    priority: 'MEDIUM',
    department: '',
    notes: '',
  })

  const [items, setItems] = useState<{ sku: string; productName: string; quantity: number; unitPrice: number }[]>([])

  const priorityOptions = [
    { value: 'LOW', label: 'Low' },
    { value: 'MEDIUM', label: 'Medium' },
    { value: 'HIGH', label: 'High' },
    { value: 'URGENT', label: 'Urgent' },
  ]

  useEffect(() => { fetchRequests() }, [])

  async function fetchRequests() {
    try {
      setLoading(true)
      const res = await procurementApi.getRequests(0, 100)
      setRequests(res.data?.content ?? [])
    } catch {
      addToast({ type: 'error', title: 'Failed to load purchase requests' })
    } finally {
      setLoading(false)
    }
  }

  function openCreate() {
    setForm({ title: '', description: '', priority: 'MEDIUM', department: '', notes: '' })
    setItems([])
    setShowModal(true)
  }

  function addItem() {
    setItems([...items, { sku: '', productName: '', quantity: 1, unitPrice: 0 }])
  }

  function updateItem(index: number, field: string, value: string | number) {
    const updated = items.map((item, i) =>
      i === index ? { ...item, [field]: value } : item,
    )
    setItems(updated)
  }

  function removeItem(index: number) {
    setItems(items.filter((_, i) => i !== index))
  }

  async function handleSave() {
    if (!form.title.trim()) {
      addToast({ type: 'warning', title: 'Title is required' })
      return
    }
    setSaving(true)
    try {
      await procurementApi.createRequest({
        title: form.title,
        description: form.description,
        priority: form.priority,
        department: form.department,
        notes: form.notes,
        items: items.map((item) => ({
          sku: item.sku,
          productName: item.productName,
          quantity: item.quantity,
          unitPrice: item.unitPrice,
          totalPrice: item.quantity * item.unitPrice,
        })),
        totalAmount: items.reduce((sum, item) => sum + item.quantity * item.unitPrice, 0),
      })
      addToast({ type: 'success', title: 'Purchase request created' })
      setShowModal(false)
      await fetchRequests()
    } catch {
      addToast({ type: 'error', title: 'Failed to create request' })
    } finally {
      setSaving(false)
    }
  }

  async function handleSubmitForApproval(id: string) {
    setSubmitting(id)
    try {
      await procurementApi.submitForApproval(id)
      addToast({ type: 'success', title: 'Submitted for approval' })
      await fetchRequests()
    } catch {
      addToast({ type: 'error', title: 'Failed to submit' })
    } finally {
      setSubmitting(null)
    }
  }

  async function handleApprove(id: string) {
    setApproving(id)
    try {
      await procurementApi.approveRequest(id)
      addToast({ type: 'success', title: 'Request approved' })
      await fetchRequests()
    } catch {
      addToast({ type: 'error', title: 'Failed to approve' })
    } finally {
      setApproving(null)
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-sm text-[var(--text-secondary)]">{requests.length} requests</p>
        <PermissionGate resource="procurement" action="create">
          <button onClick={openCreate} className="btn-primary text-sm">
            <Plus className="w-4 h-4" /> New Request
          </button>
        </PermissionGate>
      </div>

      {loading ? (
        <div className="flex items-center justify-center h-64">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[var(--nexus-primary-600)]" />
        </div>
      ) : requests.length === 0 ? (
        <div className="card p-12 text-center text-[var(--text-secondary)]">
          <FileText className="w-12 h-12 mx-auto mb-3 text-[var(--text-tertiary)]" />
          <p className="font-medium">No purchase requests</p>
          <p className="text-sm mt-1">Create a request to start the procurement process</p>
        </div>
      ) : (
        <div className="card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-[var(--border-subtle)] bg-[var(--surface-sunken)]/50">
                  <th className="px-4 py-3 w-8" />
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Request #</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Title</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Status</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Priority</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Created</th>
                  <th className="px-4 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {requests.map((req) => (
                  <Fragment key={req.id}>
                    <tr
                      className="hover:bg-[var(--surface-sunken)] cursor-pointer transition-colors"
                      onClick={() => setExpandedId(expandedId === req.id ? null : req.id)}
                    >
                      <td className="px-4 py-3">
                        {expandedId === req.id
                          ? <ChevronDown className="w-4 h-4 text-[var(--text-tertiary)]" />
                          : <ChevronRight className="w-4 h-4 text-[var(--text-tertiary)]" />}
                      </td>
                      <td className="px-4 py-3 text-sm font-medium text-[var(--text-brand)]">{req.requestNumber}</td>
                      <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">
                        <div className="flex items-center gap-2">
                          {req.title}
                          <span className="inline-flex items-center justify-center w-5 h-5 rounded-full bg-[var(--surface-muted)] text-xs font-medium text-[var(--text-secondary)]">
                            {req.items?.length || 0}
                          </span>
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${prStatusColors[req.status] || 'bg-[var(--surface-muted)] text-[var(--text-secondary)]'}`}>
                          {req.status.replace(/_/g, ' ')}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{req.priority}</td>
                      <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{new Date(req.createdAt).toLocaleDateString()}</td>
                      <td className="px-4 py-3 text-right">
                        <div className="flex items-center justify-end gap-2">
                          {req.status === 'DRAFT' && (
                            <PermissionGate resource="procurement" action="edit">
                              <button
                                onClick={(e) => { e.stopPropagation(); handleSubmitForApproval(req.id) }}
                                disabled={submitting === req.id}
                                className="btn-primary text-xs"
                              >
                                                                {submitting === req.id ? <Loader2 className="w-3 h-3 animate-spin" /> : <Send className="w-3 h-3" />}
                                                                Submit
                              </button>
                            </PermissionGate>
                          )}
                          {req.status === 'PENDING_APPROVAL' && (
                            <PermissionGate resource="procurement" action="edit">
                              <button
                                onClick={(e) => { e.stopPropagation(); handleApprove(req.id) }}
                                disabled={approving === req.id}
                                className="btn-primary text-xs"
                              >
                                {approving === req.id ? <Loader2 className="w-3 h-3 animate-spin" /> : <Check className="w-3 h-3" />}
                                Approve
                              </button>
                            </PermissionGate>
                          )}
                        </div>
                      </td>
                    </tr>
                    {expandedId === req.id && (
                      <tr>
                        <td colSpan={7} className="px-4 py-4 bg-[var(--surface-sunken)]/50">
                          <div>
                            <h4 className="text-sm font-semibold text-[var(--text-secondary)] mb-3">Items</h4>
                            <table className="w-full">
                              <thead>
                                <tr className="border-b border-[var(--border-default)]">
                                  <th className="px-3 py-2 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">SKU</th>
                                  <th className="px-3 py-2 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Product</th>
                                  <th className="px-3 py-2 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Qty</th>
                                  <th className="px-3 py-2 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Unit Price</th>
                                  <th className="px-3 py-2 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Total</th>
                                </tr>
                              </thead>
                              <tbody className="divide-y divide-gray-100">
                                {req.items?.length === 0 ? (
                                  <tr>
                                    <td colSpan={5} className="px-3 py-4 text-sm text-[var(--text-tertiary)] text-center">No items</td>
                                  </tr>
                                ) : (
                                  req.items?.map((item) => (
                                    <tr key={item.id} className="hover:bg-white">
                                      <td className="px-3 py-2 text-sm font-mono text-[var(--text-secondary)]">{item.sku}</td>
                                      <td className="px-3 py-2 text-sm text-[var(--text-secondary)]">{item.productName}</td>
                                      <td className="px-3 py-2 text-sm text-[var(--text-secondary)] text-right">{item.quantity}</td>
                                      <td className="px-3 py-2 text-sm text-[var(--text-secondary)] text-right">${item.unitPrice.toFixed(2)}</td>
                                      <td className="px-3 py-2 text-sm text-[var(--text-secondary)] text-right font-medium">${item.totalPrice.toFixed(2)}</td>
                                    </tr>
                                  ))
                                )}
                              </tbody>
                            </table>
                          </div>
                        </td>
                      </tr>
                    )}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {showModal && (
        <div className="enterprise-modal-overlay">
          <div className="enterprise-modal max-w-2xl">
            <div className="flex items-center justify-between p-6 border-b border-[var(--border-subtle)]">
              <h2 className="text-lg font-semibold text-[var(--text-primary)]">New Purchase Request</h2>
              <button onClick={() => setShowModal(false)} className="p-1 hover:bg-[var(--surface-muted)] rounded"><X className="w-5 h-5" /></button>
            </div>
            <div className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Title *</label>
                <Autocomplete value={form.title} onChange={(value) => setForm({ ...form, title: value })} inputClassName="input w-full" placeholder="Request title" minChars={0} showSearchIcon={false} clearable={false} />
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Description</label>
                <Autocomplete value={form.description} onChange={(value) => setForm({ ...form, description: value })} inputClassName="input w-full" placeholder="Describe the request" minChars={0} showSearchIcon={false} clearable={false} />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Priority</label>
                  <Autocomplete
                    value={form.priority}
                    onChange={(value) => setForm({ ...form, priority: value })}
                    suggestions={priorityOptions}
                    getOptionLabel={o => o.label}
                    getOptionValue={o => o.value}
                    inputClassName="input w-full"
                    minChars={0}
                    showSearchIcon={false}
                    clearable={false}
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Department</label>
                  <Autocomplete value={form.department} onChange={(value) => setForm({ ...form, department: value })} inputClassName="input w-full" placeholder="e.g. Engineering" minChars={0} showSearchIcon={false} clearable={false} />
                </div>
              </div>

              <div>
                <div className="flex items-center justify-between mb-2">
                  <h4 className="text-sm font-semibold text-[var(--text-secondary)]">Items</h4>
                  <PermissionGate resource="procurement" action="create">
                    <button onClick={addItem} className="btn-secondary text-xs"><Plus className="w-3 h-3" /> Add Item</button>
                  </PermissionGate>
                </div>
                {items.length === 0 ? (
                  <p className="text-sm text-[var(--text-tertiary)] py-2 text-center">No items added yet</p>
                ) : (
                  <div className="space-y-2">
                    {items.map((item, index) => (
                      <div key={index} className="flex items-center gap-2 bg-[var(--surface-sunken)] rounded-lg p-3">
                        <div className="flex-1">
                          <Autocomplete
                            value={item.sku}
                            onChange={(value) => updateItem(index, 'sku', value)}
                            inputClassName="input w-full text-sm"
                            placeholder="SKU"
                            minChars={0}
                            showSearchIcon={false}
                            clearable={false}
                          />
                        </div>
                        <div className="flex-[2]">
                          <Autocomplete
                            value={item.productName}
                            onChange={(value) => updateItem(index, 'productName', value)}
                            inputClassName="input w-full text-sm"
                            placeholder="Product name"
                            minChars={0}
                            showSearchIcon={false}
                            clearable={false}
                          />
                        </div>
                        <div className="w-20">
                          <Autocomplete
                            value={String(item.quantity)}
                            onChange={(value) => updateItem(index, 'quantity', parseInt(value) || 0)}
                            inputClassName="input w-full text-sm"
                            placeholder="Qty"
                            minChars={0}
                            showSearchIcon={false}
                            clearable={false}
                          />
                        </div>
                        <div className="w-24">
                          <Autocomplete
                            value={String(item.unitPrice)}
                            onChange={(value) => updateItem(index, 'unitPrice', parseFloat(value) || 0)}
                            inputClassName="input w-full text-sm"
                            placeholder="Price"
                            minChars={0}
                            showSearchIcon={false}
                            clearable={false}
                          />
                        </div>
                        <button onClick={() => removeItem(index)} className="p-1.5 hover:bg-[var(--nexus-error-50)] rounded text-[var(--text-tertiary)] hover:text-[var(--nexus-error-500)]">
                          <X className="w-4 h-4" />
                        </button>
                      </div>
                    ))}
                  </div>
                )}
                {items.length > 0 && (
                  <div className="text-right text-sm font-medium text-[var(--text-secondary)] mt-2">
                    Total: ${items.reduce((sum, item) => sum + item.quantity * item.unitPrice, 0).toFixed(2)}
                  </div>
                )}
              </div>

              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Notes</label>
                <Autocomplete value={form.notes} onChange={(value) => setForm({ ...form, notes: value })} inputClassName="input w-full" placeholder="Additional notes" minChars={0} showSearchIcon={false} clearable={false} />
              </div>
            </div>
            <div className="p-6 border-t border-[var(--border-subtle)] flex justify-end gap-3">
              <button onClick={() => setShowModal(false)} className="btn-secondary text-sm">Cancel</button>
              <PermissionGate resource="procurement" action="create">
                <button onClick={handleSave} disabled={saving} className="btn-primary text-sm">
                  {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <FileText className="w-4 h-4" />}
                  Create Request
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function RfqsTab() {
  const { addToast } = useToast()
  const [rfqs, setRfqs] = useState<Rfq[]>([])
  const [loading, setLoading] = useState(true)
  const [showModal, setShowModal] = useState(false)
  const [saving, setSaving] = useState(false)
  const [expandedId, setExpandedId] = useState<string | null>(null)
  const [responses, setResponses] = useState<Record<string, RfqResponse[]>>({})
  const [loadingResponses, setLoadingResponses] = useState<Record<string, boolean>>({})
  const [submitting, setSubmitting] = useState<string | null>(null)

  const [form, setForm] = useState({
    title: '',
    description: '',
    responseDeadline: '',
    supplierIds: '',
    notes: '',
  })

  const [rfqItems, setRfqItems] = useState<{ sku: string; productName: string; quantity: number }[]>([])

  useEffect(() => { fetchRfqs() }, [])

  async function fetchRfqs() {
    try {
      setLoading(true)
      const res = await procurementApi.getRfqs(0, 100)
      setRfqs(res.data?.content ?? [])
    } catch {
      addToast({ type: 'error', title: 'Failed to load RFQs' })
    } finally {
      setLoading(false)
    }
  }

  function openCreate() {
    setForm({ title: '', description: '', responseDeadline: '', supplierIds: '', notes: '' })
    setRfqItems([])
    setShowModal(true)
  }

  function addRfqItem() {
    setRfqItems([...rfqItems, { sku: '', productName: '', quantity: 1 }])
  }

  async function handleSave() {
    if (!form.title.trim()) {
      addToast({ type: 'warning', title: 'Title is required' })
      return
    }
    setSaving(true)
    try {
      await procurementApi.createRfq({
        title: form.title,
        description: form.description,
        responseDeadline: form.responseDeadline,
        supplierIds: form.supplierIds.split(',').map((s) => s.trim()).filter(Boolean),
        notes: form.notes,
        items: rfqItems,
      })
      addToast({ type: 'success', title: 'RFQ created' })
      setShowModal(false)
      await fetchRfqs()
    } catch {
      addToast({ type: 'error', title: 'Failed to create RFQ' })
    } finally {
      setSaving(false)
    }
  }

  async function toggleRfqExpand(rfqId: string) {
    if (expandedId === rfqId) {
      setExpandedId(null)
      return
    }
    setExpandedId(rfqId)
    if (!responses[rfqId]) {
      setLoadingResponses((prev) => ({ ...prev, [rfqId]: true }))
      try {
        const res = await procurementApi.getRfqResponses(rfqId)
        setResponses((prev) => ({ ...prev, [rfqId]: Array.isArray(res.data) ? res.data : (res.data?.content ?? []) }))
      } catch {
        addToast({ type: 'error', title: 'Failed to load responses' })
      } finally {
        setLoadingResponses((prev) => ({ ...prev, [rfqId]: false }))
      }
    }
  }

  async function handleSubmit(id: string) {
    setSubmitting(id)
    try {
      await procurementApi.submitRfq(id)
      addToast({ type: 'success', title: 'RFQ submitted' })
      await fetchRfqs()
    } catch {
      addToast({ type: 'error', title: 'Failed to submit RFQ' })
    } finally {
      setSubmitting(null)
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-sm text-[var(--text-secondary)]">{rfqs.length} RFQs</p>
        <PermissionGate resource="procurement" action="create">
          <button onClick={openCreate} className="btn-primary text-sm">
            <Plus className="w-4 h-4" /> New RFQ
          </button>
        </PermissionGate>
      </div>

      {loading ? (
        <div className="flex items-center justify-center h-64">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[var(--nexus-primary-600)]" />
        </div>
      ) : rfqs.length === 0 ? (
        <div className="card p-12 text-center text-[var(--text-secondary)]">
          <ShoppingCart className="w-12 h-12 mx-auto mb-3 text-[var(--text-tertiary)]" />
          <p className="font-medium">No RFQs</p>
          <p className="text-sm mt-1">Create an RFQ to request quotes from suppliers</p>
        </div>
      ) : (
        <div className="card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-[var(--border-subtle)] bg-[var(--surface-sunken)]/50">
                  <th className="px-4 py-3 w-8" />
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">RFQ #</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Title</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Status</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Due Date</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Responses</th>
                  <th className="px-4 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {rfqs.map((rfq) => (
                  <Fragment key={rfq.id}>
                    <tr
                      className="hover:bg-[var(--surface-sunken)] cursor-pointer transition-colors"
                      onClick={() => toggleRfqExpand(rfq.id)}
                    >
                      <td className="px-4 py-3">
                        {expandedId === rfq.id
                          ? <ChevronDown className="w-4 h-4 text-[var(--text-tertiary)]" />
                          : <ChevronRight className="w-4 h-4 text-[var(--text-tertiary)]" />}
                      </td>
                      <td className="px-4 py-3 text-sm font-medium text-[var(--text-brand)]">{rfq.rfqNumber}</td>
                      <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{rfq.title}</td>
                      <td className="px-4 py-3"><StatusBadge status={rfq.status} size="sm" /></td>
                      <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{new Date(rfq.responseDeadline).toLocaleDateString()}</td>
                      <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">
                        <span className="inline-flex items-center justify-center w-6 h-6 rounded-full bg-[var(--surface-muted)] text-xs font-medium">
                          {responses[rfq.id]?.length || 0}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-right">
                        {rfq.status === 'DRAFT' && (
                          <PermissionGate resource="procurement" action="edit">
                            <button
                              onClick={(e) => { e.stopPropagation(); handleSubmit(rfq.id) }}
                              disabled={submitting === rfq.id}
                              className="btn-primary text-xs"
                            >
                              {submitting === rfq.id ? <Loader2 className="w-3 h-3 animate-spin" /> : <Send className="w-3 h-3" />}
                              Submit
                            </button>
                          </PermissionGate>
                        )}
                      </td>
                    </tr>
                    {expandedId === rfq.id && (
                      <tr>
                        <td colSpan={7} className="px-4 py-4 bg-[var(--surface-sunken)]/50">
                          <div>
                            <h4 className="text-sm font-semibold text-[var(--text-secondary)] mb-3">Supplier Responses</h4>
                            {loadingResponses[rfq.id] ? (
                              <div className="flex items-center gap-2 text-sm text-[var(--text-tertiary)] py-2">
                                <Loader2 className="w-3 h-3 animate-spin" /> Loading...
                              </div>
                            ) : responses[rfq.id]?.length === 0 ? (
                              <p className="text-sm text-[var(--text-tertiary)] py-2">No responses yet</p>
                            ) : (
                              <table className="w-full">
                                <thead>
                                  <tr className="border-b border-[var(--border-default)]">
                                    <th className="px-3 py-2 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Supplier</th>
                                    <th className="px-3 py-2 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Amount</th>
                                    <th className="px-3 py-2 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Delivery Days</th>
                                    <th className="px-3 py-2 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Valid Until</th>
                                    <th className="px-3 py-2 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Status</th>
                                  </tr>
                                </thead>
                                <tbody className="divide-y divide-gray-100">
                                  {responses[rfq.id]?.map((resp) => {
                                    const deliveryDays = resp.deliveryDate
                                      ? Math.ceil((new Date(resp.deliveryDate).getTime() - Date.now()) / (1000 * 60 * 60 * 24))
                                      : '-'
                                    return (
                                      <tr key={resp.id} className="hover:bg-white">
                                        <td className="px-3 py-2 text-sm text-[var(--text-secondary)]">{resp.supplierName}</td>
                                        <td className="px-3 py-2 text-sm text-[var(--text-secondary)] text-right font-medium">${resp.totalAmount.toFixed(2)}</td>
                                        <td className="px-3 py-2 text-sm text-[var(--text-secondary)] text-right">{deliveryDays === '-' ? '-' : `${deliveryDays} days`}</td>
                                        <td className="px-3 py-2 text-sm text-[var(--text-secondary)]">
                                          {resp.deliveryDate ? new Date(resp.deliveryDate).toLocaleDateString() : '-'}
                                        </td>
                                        <td className="px-3 py-2"><StatusBadge status={resp.status} size="sm" /></td>
                                      </tr>
                                    )
                                  })}
                                </tbody>
                              </table>
                            )}
                          </div>
                        </td>
                      </tr>
                    )}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {showModal && (
        <div className="enterprise-modal-overlay">
          <div className="enterprise-modal max-w-2xl">
            <div className="flex items-center justify-between p-6 border-b border-[var(--border-subtle)]">
              <h2 className="text-lg font-semibold text-[var(--text-primary)]">New RFQ</h2>
              <button onClick={() => setShowModal(false)} className="p-1 hover:bg-[var(--surface-muted)] rounded"><X className="w-5 h-5" /></button>
            </div>
            <div className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Title *</label>
                <Autocomplete value={form.title} onChange={(value) => setForm({ ...form, title: value })} inputClassName="input w-full" placeholder="RFQ title" minChars={0} showSearchIcon={false} clearable={false} />
              </div>
              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Description</label>
                <Autocomplete value={form.description} onChange={(value) => setForm({ ...form, description: value })} inputClassName="input w-full" placeholder="Describe what you're requesting" minChars={0} showSearchIcon={false} clearable={false} />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Response Deadline</label>
                  <Autocomplete value={form.responseDeadline} onChange={(value) => setForm({ ...form, responseDeadline: value })} inputClassName="input w-full" placeholder="" minChars={0} showSearchIcon={false} clearable={false} />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Supplier IDs (comma-separated)</label>
                  <Autocomplete value={form.supplierIds} onChange={(value) => setForm({ ...form, supplierIds: value })} inputClassName="input w-full" placeholder="sup1, sup2" minChars={0} showSearchIcon={false} clearable={false} />
                </div>
              </div>

              <div>
                <div className="flex items-center justify-between mb-2">
                  <h4 className="text-sm font-semibold text-[var(--text-secondary)]">Items</h4>
                  <PermissionGate resource="procurement" action="create">
                    <button onClick={addRfqItem} className="btn-secondary text-xs"><Plus className="w-3 h-3" /> Add Item</button>
                  </PermissionGate>
                </div>
                {rfqItems.length === 0 ? (
                  <p className="text-sm text-[var(--text-tertiary)] py-2 text-center">No items added yet</p>
                ) : (
                  <div className="space-y-2">
                    {rfqItems.map((item, index) => (
                      <div key={index} className="flex items-center gap-2 bg-[var(--surface-sunken)] rounded-lg p-3">
                        <div className="flex-1">
                          <Autocomplete
                            value={item.sku}
                            onChange={(value) => {
                              const updated = [...rfqItems]
                              updated[index] = { ...updated[index], sku: value }
                              setRfqItems(updated)
                            }}
                            inputClassName="input w-full text-sm"
                            placeholder="SKU"
                            minChars={0}
                            showSearchIcon={false}
                            clearable={false}
                          />
                        </div>
                        <div className="flex-[2]">
                          <Autocomplete
                            value={item.productName}
                            onChange={(value) => {
                              const updated = [...rfqItems]
                              updated[index] = { ...updated[index], productName: value }
                              setRfqItems(updated)
                            }}
                            inputClassName="input w-full text-sm"
                            placeholder="Product name"
                            minChars={0}
                            showSearchIcon={false}
                            clearable={false}
                          />
                        </div>
                        <div className="w-20">
                          <Autocomplete
                            value={String(item.quantity)}
                            onChange={(value) => {
                              const updated = [...rfqItems]
                              updated[index] = { ...updated[index], quantity: parseInt(value) || 0 }
                              setRfqItems(updated)
                            }}
                            inputClassName="input w-full text-sm"
                            placeholder="Qty"
                            minChars={0}
                            showSearchIcon={false}
                            clearable={false}
                          />
                        </div>
                        <button onClick={() => setRfqItems(rfqItems.filter((_, i) => i !== index))} className="p-1.5 hover:bg-[var(--nexus-error-50)] rounded text-[var(--text-tertiary)] hover:text-[var(--nexus-error-500)]">
                          <X className="w-4 h-4" />
                        </button>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Notes</label>
                <Autocomplete value={form.notes} onChange={(value) => setForm({ ...form, notes: value })} inputClassName="input w-full" placeholder="Additional notes" minChars={0} showSearchIcon={false} clearable={false} />
              </div>
            </div>
            <div className="p-6 border-t border-[var(--border-subtle)] flex justify-end gap-3">
              <button onClick={() => setShowModal(false)} className="btn-secondary text-sm">Cancel</button>
              <PermissionGate resource="procurement" action="create">
                <button onClick={handleSave} disabled={saving} className="btn-primary text-sm">
                  {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <ShoppingCart className="w-4 h-4" />}
                  Create RFQ
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function PurchaseOrdersTab() {
  const { addToast } = useToast()
  const [orders, setOrders] = useState<PurchaseOrder[]>([])
  const [loading, setLoading] = useState(true)
  const [showModal, setShowModal] = useState(false)
  const [saving, setSaving] = useState(false)
  const [expandedId, setExpandedId] = useState<string | null>(null)
  const [approving, setApproving] = useState<string | null>(null)
  const [showReceiveModal, setShowReceiveModal] = useState(false)
  const [receivePo, setReceivePo] = useState<PurchaseOrder | null>(null)
  const [receiveItems, setReceiveItems] = useState<Record<string, number>>({})
  const [receiving, setReceiving] = useState(false)

  const [form, setForm] = useState({
    supplierId: '',
    supplierName: '',
    requestId: '',
    paymentTerms: 'NET30',
    expectedDeliveryDate: '',
    notes: '',
  })

  const [poItems, setPoItems] = useState<{ sku: string; productName: string; quantity: number; unitPrice: number }[]>([])

  const paymentTermsOptions = [
    { value: 'NET15', label: 'Net 15' },
    { value: 'NET30', label: 'Net 30' },
    { value: 'NET45', label: 'Net 45' },
    { value: 'NET60', label: 'Net 60' },
    { value: 'COD', label: 'Cash on Delivery' },
  ]

  useEffect(() => { fetchOrders() }, [])

  async function fetchOrders() {
    try {
      setLoading(true)
      const res = await procurementApi.getPurchaseOrders(0, 100)
      setOrders(res.data?.content ?? [])
    } catch {
      addToast({ type: 'error', title: 'Failed to load purchase orders' })
    } finally {
      setLoading(false)
    }
  }

  function openCreate() {
    setForm({ supplierId: '', supplierName: '', requestId: '', paymentTerms: 'NET30', expectedDeliveryDate: '', notes: '' })
    setPoItems([])
    setShowModal(true)
  }

  function addPoItem() {
    setPoItems([...poItems, { sku: '', productName: '', quantity: 1, unitPrice: 0 }])
  }

  async function handleSave() {
    if (!form.supplierName.trim()) {
      addToast({ type: 'warning', title: 'Supplier name is required' })
      return
    }
    setSaving(true)
    try {
      const subtotal = poItems.reduce((sum, item) => sum + item.quantity * item.unitPrice, 0)
      await procurementApi.createPurchaseOrder({
        supplierId: form.supplierId || undefined,
        supplierName: form.supplierName,
        requestId: form.requestId || undefined,
        paymentTerms: form.paymentTerms,
        expectedDeliveryDate: form.expectedDeliveryDate || undefined,
        notes: form.notes,
        items: poItems.map((item) => ({
          sku: item.sku,
          productName: item.productName,
          quantity: item.quantity,
          unitPrice: item.unitPrice,
          totalPrice: item.quantity * item.unitPrice,
          quantityReceived: 0,
        })),
        subtotal,
        tax: 0,
        shippingCost: 0,
        totalAmount: subtotal,
      })
      addToast({ type: 'success', title: 'Purchase order created' })
      setShowModal(false)
      await fetchOrders()
    } catch {
      addToast({ type: 'error', title: 'Failed to create purchase order' })
    } finally {
      setSaving(false)
    }
  }

  async function handleApprove(id: string) {
    setApproving(id)
    try {
      await procurementApi.approvePurchaseOrder(id)
      addToast({ type: 'success', title: 'Purchase order approved' })
      await fetchOrders()
    } catch {
      addToast({ type: 'error', title: 'Failed to approve' })
    } finally {
      setApproving(null)
    }
  }

  function openReceive(po: PurchaseOrder) {
    setReceivePo(po)
    const initial: Record<string, number> = {}
    po.items.forEach((item) => {
      initial[item.id] = item.quantity - item.quantityReceived
    })
    setReceiveItems(initial)
    setShowReceiveModal(true)
  }

  async function handleReceive() {
    if (!receivePo) return
    setReceiving(true)
    try {
      const items = Object.entries(receiveItems).map(([itemId, quantityReceived]) => ({
        itemId,
        quantityReceived: Math.max(0, quantityReceived),
      }))
      await procurementApi.receiveItems(receivePo.id, items)
      addToast({ type: 'success', title: 'Items received' })
      setShowReceiveModal(false)
      setReceivePo(null)
      await fetchOrders()
    } catch {
      addToast({ type: 'error', title: 'Failed to receive items' })
    } finally {
      setReceiving(false)
    }
  }

  function getNextStatus(status: string): string | null {
    switch (status) {
      case 'DRAFT': return 'PENDING_APPROVAL'
      case 'APPROVED': return 'SENT'
      case 'SENT': return 'CONFIRMED'
      default: return null
    }
  }

  const receiveable = ['APPROVED', 'SENT', 'CONFIRMED', 'SHIPPED', 'PARTIALLY_RECEIVED']

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-sm text-[var(--text-secondary)]">{orders.length} purchase orders</p>
        <PermissionGate resource="procurement" action="create">
          <button onClick={openCreate} className="btn-primary text-sm">
            <Plus className="w-4 h-4" /> New Purchase Order
          </button>
        </PermissionGate>
      </div>

      {loading ? (
        <div className="flex items-center justify-center h-64">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[var(--nexus-primary-600)]" />
        </div>
      ) : orders.length === 0 ? (
        <div className="card p-12 text-center text-[var(--text-secondary)]">
          <ClipboardList className="w-12 h-12 mx-auto mb-3 text-[var(--text-tertiary)]" />
          <p className="font-medium">No purchase orders</p>
          <p className="text-sm mt-1">Create a PO to start ordering from suppliers</p>
        </div>
      ) : (
        <div className="card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-[var(--border-subtle)] bg-[var(--surface-sunken)]/50">
                  <th className="px-4 py-3 w-8" />
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">PO #</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Supplier</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Status</th>
                  <th className="px-4 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Total Amount</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Expected Delivery</th>
                  <th className="px-4 py-3 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {orders.map((po) => (
                  <Fragment key={po.id}>
                    <tr
                      className="hover:bg-[var(--surface-sunken)] cursor-pointer transition-colors"
                      onClick={() => setExpandedId(expandedId === po.id ? null : po.id)}
                    >
                      <td className="px-4 py-3">
                        {expandedId === po.id
                          ? <ChevronDown className="w-4 h-4 text-[var(--text-tertiary)]" />
                          : <ChevronRight className="w-4 h-4 text-[var(--text-tertiary)]" />}
                      </td>
                      <td className="px-4 py-3 text-sm font-medium text-[var(--text-brand)]">{po.poNumber}</td>
                      <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{po.supplierName}</td>
                      <td className="px-4 py-3"><StatusBadge status={po.status} size="sm" /></td>
                      <td className="px-4 py-3 text-sm text-[var(--text-secondary)] text-right font-medium">${po.totalAmount.toFixed(2)}</td>
                      <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">
                        {po.expectedDeliveryDate ? new Date(po.expectedDeliveryDate).toLocaleDateString() : '-'}
                      </td>
                      <td className="px-4 py-3 text-right">
                        <div className="flex items-center justify-end gap-2">
                          {po.status === 'DRAFT' && (
                            <PermissionGate resource="procurement" action="edit">
                              <button
                                onClick={(e) => { e.stopPropagation(); handleApprove(po.id) }}
                                disabled={approving === po.id}
                                className="btn-primary text-xs"
                              >
                                {approving === po.id ? <Loader2 className="w-3 h-3 animate-spin" /> : <Check className="w-3 h-3" />}
                                Approve
                              </button>
                            </PermissionGate>
                          )}
                          {receiveable.includes(po.status) && (
                            <PermissionGate resource="procurement" action="edit">
                              <button
                                onClick={(e) => { e.stopPropagation(); openReceive(po) }}
                                                                className="btn-secondary text-xs"
                                                              >
                                                                <PackageCheck className="w-3 h-3" /> Receive Items
                              </button>
                            </PermissionGate>
                          )}
                        </div>
                      </td>
                    </tr>
                    {expandedId === po.id && (
                      <tr>
                        <td colSpan={7} className="px-4 py-4 bg-[var(--surface-sunken)]/50">
                          <div>
                            <h4 className="text-sm font-semibold text-[var(--text-secondary)] mb-3">Items</h4>
                            <table className="w-full">
                              <thead>
                                <tr className="border-b border-[var(--border-default)]">
                                  <th className="px-3 py-2 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">SKU</th>
                                  <th className="px-3 py-2 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Product</th>
                                  <th className="px-3 py-2 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Ordered</th>
                                  <th className="px-3 py-2 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Received</th>
                                  <th className="px-3 py-2 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Cancelled</th>
                                  <th className="px-3 py-2 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Unit Price</th>
                                  <th className="px-3 py-2 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Total</th>
                                </tr>
                              </thead>
                              <tbody className="divide-y divide-gray-100">
                                {po.items.map((item) => (
                                  <tr key={item.id} className="hover:bg-white">
                                    <td className="px-3 py-2 text-sm font-mono text-[var(--text-secondary)]">{item.sku}</td>
                                    <td className="px-3 py-2 text-sm text-[var(--text-secondary)]">{item.productName}</td>
                                    <td className="px-3 py-2 text-sm text-[var(--text-secondary)] text-right">{item.quantity}</td>
                                    <td className="px-3 py-2 text-sm text-[var(--text-secondary)] text-right">{item.quantityReceived}</td>
                                    <td className="px-3 py-2 text-sm text-[var(--text-secondary)] text-right">{0}</td>
                                    <td className="px-3 py-2 text-sm text-[var(--text-secondary)] text-right">${item.unitPrice.toFixed(2)}</td>
                                    <td className="px-3 py-2 text-sm text-[var(--text-secondary)] text-right font-medium">${item.totalPrice.toFixed(2)}</td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </div>
                        </td>
                      </tr>
                    )}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {showModal && (
        <div className="enterprise-modal-overlay">
          <div className="enterprise-modal max-w-2xl">
            <div className="flex items-center justify-between p-6 border-b border-[var(--border-subtle)]">
              <h2 className="text-lg font-semibold text-[var(--text-primary)]">New Purchase Order</h2>
              <button onClick={() => setShowModal(false)} className="p-1 hover:bg-[var(--surface-muted)] rounded"><X className="w-5 h-5" /></button>
            </div>
            <div className="p-6 space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Supplier Name *</label>
                  <Autocomplete value={form.supplierName} onChange={(value) => setForm({ ...form, supplierName: value })} inputClassName="input w-full" placeholder="Supplier name" minChars={0} showSearchIcon={false} clearable={false} />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Supplier ID</label>
                  <Autocomplete value={form.supplierId} onChange={(value) => setForm({ ...form, supplierId: value })} inputClassName="input w-full" placeholder="Supplier ID (optional)" minChars={0} showSearchIcon={false} clearable={false} />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Request ID</label>
                  <Autocomplete value={form.requestId} onChange={(value) => setForm({ ...form, requestId: value })} inputClassName="input w-full" placeholder="Related request (optional)" minChars={0} showSearchIcon={false} clearable={false} />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Payment Terms</label>
                  <Autocomplete
                    value={form.paymentTerms}
                    onChange={(value) => setForm({ ...form, paymentTerms: value })}
                    suggestions={paymentTermsOptions}
                    getOptionLabel={o => o.label}
                    getOptionValue={o => o.value}
                    inputClassName="input w-full"
                    minChars={0}
                    showSearchIcon={false}
                    clearable={false}
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Expected Delivery</label>
                  <Autocomplete value={form.expectedDeliveryDate} onChange={(value) => setForm({ ...form, expectedDeliveryDate: value })} inputClassName="input w-full" placeholder="" minChars={0} showSearchIcon={false} clearable={false} />
                </div>
              </div>

              <div>
                <div className="flex items-center justify-between mb-2">
                  <h4 className="text-sm font-semibold text-[var(--text-secondary)]">Items</h4>
                  <PermissionGate resource="procurement" action="create">
                    <button onClick={addPoItem} className="btn-secondary text-xs"><Plus className="w-3 h-3" /> Add Item</button>
                  </PermissionGate>
                </div>
                {poItems.length === 0 ? (
                  <p className="text-sm text-[var(--text-tertiary)] py-2 text-center">No items added yet</p>
                ) : (
                  <div className="space-y-2">
                    {poItems.map((item, index) => (
                      <div key={index} className="flex items-center gap-2 bg-[var(--surface-sunken)] rounded-lg p-3">
                        <div className="flex-1">
                          <Autocomplete
                            value={item.sku}
                            onChange={(value) => {
                              const updated = [...poItems]
                              updated[index] = { ...updated[index], sku: value }
                              setPoItems(updated)
                            }}
                            inputClassName="input w-full text-sm"
                            placeholder="SKU"
                            minChars={0}
                            showSearchIcon={false}
                            clearable={false}
                          />
                        </div>
                        <div className="flex-[2]">
                          <Autocomplete
                            value={item.productName}
                            onChange={(value) => {
                              const updated = [...poItems]
                              updated[index] = { ...updated[index], productName: value }
                              setPoItems(updated)
                            }}
                            inputClassName="input w-full text-sm"
                            placeholder="Product name"
                            minChars={0}
                            showSearchIcon={false}
                            clearable={false}
                          />
                        </div>
                        <div className="w-20">
                          <Autocomplete
                            value={String(item.quantity)}
                            onChange={(value) => {
                              const updated = [...poItems]
                              updated[index] = { ...updated[index], quantity: parseInt(value) || 0 }
                              setPoItems(updated)
                            }}
                            inputClassName="input w-full text-sm"
                            placeholder="Qty"
                            minChars={0}
                            showSearchIcon={false}
                            clearable={false}
                          />
                        </div>
                        <div className="w-24">
                          <Autocomplete
                            value={String(item.unitPrice)}
                            onChange={(value) => {
                              const updated = [...poItems]
                              updated[index] = { ...updated[index], unitPrice: parseFloat(value) || 0 }
                              setPoItems(updated)
                            }}
                            inputClassName="input w-full text-sm"
                            placeholder="Price"
                            minChars={0}
                            showSearchIcon={false}
                            clearable={false}
                          />
                        </div>
                        <button onClick={() => setPoItems(poItems.filter((_, i) => i !== index))} className="p-1.5 hover:bg-[var(--nexus-error-50)] rounded text-[var(--text-tertiary)] hover:text-[var(--nexus-error-500)]">
                          <X className="w-4 h-4" />
                        </button>
                      </div>
                    ))}
                  </div>
                )}
                {poItems.length > 0 && (
                  <div className="text-right text-sm font-medium text-[var(--text-secondary)] mt-2">
                    Total: ${poItems.reduce((sum, item) => sum + item.quantity * item.unitPrice, 0).toFixed(2)}
                  </div>
                )}
              </div>

              <div>
                <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Notes</label>
                <Autocomplete value={form.notes} onChange={(value) => setForm({ ...form, notes: value })} inputClassName="input w-full" placeholder="Additional notes" minChars={0} showSearchIcon={false} clearable={false} />
              </div>
            </div>
            <div className="p-6 border-t border-[var(--border-subtle)] flex justify-end gap-3">
              <button onClick={() => setShowModal(false)} className="btn-secondary text-sm">Cancel</button>
              <PermissionGate resource="procurement" action="create">
                <button onClick={handleSave} disabled={saving} className="btn-primary text-sm">
                  {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <ClipboardList className="w-4 h-4" />}
                  Create Purchase Order
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}

      {showReceiveModal && receivePo && (
        <div className="enterprise-modal-overlay">
          <div className="enterprise-modal max-w-lg">
            <div className="flex items-center justify-between p-6 border-b border-[var(--border-subtle)]">
              <h2 className="text-lg font-semibold text-[var(--text-primary)]">Receive Items - {receivePo.poNumber}</h2>
              <button onClick={() => setShowReceiveModal(false)} className="p-1 hover:bg-[var(--surface-muted)] rounded"><X className="w-5 h-5" /></button>
            </div>
            <div className="p-6 space-y-4">
              <p className="text-sm text-[var(--text-secondary)]">Enter the quantity received for each item</p>
              <table className="w-full">
                <thead>
                  <tr className="border-b border-[var(--border-default)]">
                    <th className="px-3 py-2 text-left text-xs font-semibold text-[var(--text-secondary)] uppercase">Product</th>
                    <th className="px-3 py-2 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Ordered</th>
                    <th className="px-3 py-2 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Received</th>
                    <th className="px-3 py-2 text-right text-xs font-semibold text-[var(--text-secondary)] uppercase">Qty to Receive</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {receivePo.items.map((item) => (
                    <tr key={item.id}>
                      <td className="px-3 py-3 text-sm text-[var(--text-secondary)]">{item.productName}</td>
                      <td className="px-3 py-3 text-sm text-[var(--text-secondary)] text-right">{item.quantity}</td>
                      <td className="px-3 py-3 text-sm text-[var(--text-secondary)] text-right">{item.quantityReceived}</td>
                      <td className="px-3 py-3 text-right">
                        <Autocomplete
                          value={String(receiveItems[item.id] ?? 0)}
                          onChange={(value) => setReceiveItems({ ...receiveItems, [item.id]: parseInt(value) || 0 })}
                          inputClassName="input w-24 text-sm text-right"
                          placeholder=""
                          minChars={0}
                          showSearchIcon={false}
                          clearable={false}
                        />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="p-6 border-t border-[var(--border-subtle)] flex justify-end gap-3">
              <button onClick={() => setShowReceiveModal(false)} className="btn-secondary text-sm">Cancel</button>
              <PermissionGate resource="procurement" action="edit">
                <button onClick={handleReceive} disabled={receiving} className="btn-primary text-sm">
                  {receiving ? <Loader2 className="w-4 h-4 animate-spin" /> : <PackageCheck className="w-4 h-4" />}
                  Receive Items
                </button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}


