import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Tag, Plus, Percent, DollarSign, Gift, Truck, Trash2, Edit, ToggleLeft, ToggleRight,
  BarChart3, Calendar, Users, Search, X, Check,
} from 'lucide-react'
import clsx from 'clsx'
import { useToast } from '../hooks/useToast'
import promotionsApi from '../api/promotions'
import type { NxPromotion } from '../api/promotions'
import { EnterpriseTabs, EnterpriseStatusBadge, EnterpriseKPICard } from '../components/enterprise'

type PromoTab = 'list' | 'stats'

const PROMOTION_TYPES = [
  { value: 'PERCENTAGE', label: 'Percentage Off', icon: Percent },
  { value: 'FIXED_AMOUNT', label: 'Fixed Amount', icon: DollarSign },
  { value: 'BOGO', label: 'Buy One Get One', icon: Gift },
  { value: 'FREE_SHIPPING', label: 'Free Shipping', icon: Truck },
  { value: 'BUY_X_GET_Y', label: 'Buy X Get Y', icon: Gift },
]

const CHANNELS = ['ALL', 'ONLINE', 'IN_STORE', 'MOBILE']

export default function PromotionsPage() {
  const { addToast } = useToast()
  const queryClient = useQueryClient()
  const [activeTab, setActiveTab] = useState<PromoTab>('list')
  const [searchTerm, setSearchTerm] = useState('')
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [editingPromo, setEditingPromo] = useState<NxPromotion | null>(null)

  const [form, setForm] = useState<Partial<NxPromotion>>({
    name: '',
    description: '',
    promotionType: 'PERCENTAGE',
    discountValue: 10,
    minOrderAmount: 0,
    minQuantity: 1,
    maxUsesTotal: undefined,
    maxUsesPerCustomer: undefined,
    couponCode: '',
    startDate: new Date().toISOString().slice(0, 16),
    endDate: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString().slice(0, 16),
    applicableChannels: 'ALL',
    stackable: false,
    priority: 10,
    active: true,
  })

  // ─── Queries ────────────────────────────────────────────────────────

  const { data: promotions = [], isLoading, refetch } = useQuery({
    queryKey: ['promotions'],
    queryFn: async () => {
      const res = await promotionsApi.getPromotions()
      return res.data as NxPromotion[]
    },
  })

  const { data: stats, isLoading: loadingStats } = useQuery({
    queryKey: ['promotion-stats'],
    queryFn: async () => {
      const results = await Promise.all(
        promotions.map(async (p) => {
          if (!p.id) return null
          try {
            const res = await promotionsApi.getPromotionStats(p.id)
            return res.data
          } catch {
            return null
          }
        })
      )
      return results.filter(Boolean)
    },
    enabled: activeTab === 'stats' && promotions.length > 0,
  })

  // ─── Mutations ──────────────────────────────────────────────────────

  const createMutation = useMutation({
    mutationFn: (data: NxPromotion) => promotionsApi.createPromotion(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['promotions'] })
      addToast({ type: 'success', title: 'Promotion created' })
      setShowCreateModal(false)
      resetForm()
    },
    onError: (err: any) => addToast({ type: 'error', title: 'Failed to create promotion', message: err.message }),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<NxPromotion> }) => promotionsApi.updatePromotion(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['promotions'] })
      addToast({ type: 'success', title: 'Promotion updated' })
      setEditingPromo(null)
      resetForm()
    },
    onError: (err: any) => addToast({ type: 'error', title: 'Failed to update promotion', message: err.message }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => promotionsApi.deletePromotion(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['promotions'] })
      addToast({ type: 'success', title: 'Promotion deleted' })
    },
    onError: (err: any) => addToast({ type: 'error', title: 'Failed to delete promotion', message: err.message }),
  })

  const toggleActiveMutation = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) => promotionsApi.updatePromotion(id, { active }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['promotions'] })
      addToast({ type: 'success', title: 'Promotion status updated' })
    },
  })

  // ─── Helpers ────────────────────────────────────────────────────────

  const resetForm = () => {
    setForm({
      name: '',
      description: '',
      promotionType: 'PERCENTAGE',
      discountValue: 10,
      minOrderAmount: 0,
      minQuantity: 1,
      maxUsesTotal: undefined,
      maxUsesPerCustomer: undefined,
      couponCode: '',
      startDate: new Date().toISOString().slice(0, 16),
      endDate: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString().slice(0, 16),
      applicableChannels: 'ALL',
      stackable: false,
      priority: 10,
      active: true,
    })
  }

  const openEditModal = (promo: NxPromotion) => {
    setEditingPromo(promo)
    setForm({
      ...promo,
      startDate: promo.startDate ? new Date(promo.startDate).toISOString().slice(0, 16) : '',
      endDate: promo.endDate ? new Date(promo.endDate).toISOString().slice(0, 16) : '',
    })
  }

  const handleSubmit = () => {
    if (!form.name || !form.promotionType || !form.discountValue) {
      addToast({ type: 'error', title: 'Please fill required fields' })
      return
    }

    const payload = {
      ...form,
      startDate: form.startDate ? new Date(form.startDate).toISOString() : new Date().toISOString(),
      endDate: form.endDate ? new Date(form.endDate).toISOString() : new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString(),
    }

    if (editingPromo?.id) {
      updateMutation.mutate({ id: editingPromo.id, data: payload })
    } else {
      createMutation.mutate(payload as NxPromotion)
    }
  }

  const getPromoIcon = (type: string) => {
    const found = PROMOTION_TYPES.find(t => t.value === type)
    return found ? found.icon : Tag
  }

  const formatDiscount = (promo: NxPromotion) => {
    switch (promo.promotionType) {
      case 'PERCENTAGE': return `${promo.discountValue}%`
      case 'FIXED_AMOUNT': return `$${promo.discountValue}`
      case 'FREE_SHIPPING': return 'Free'
      default: return `${promo.discountValue}`
    }
  }

  const isExpired = (endDate: string) => new Date(endDate) < new Date()
  const isActive = (promo: NxPromotion) => promo.active && !isExpired(promo.endDate)

  const filteredPromotions = promotions.filter(p =>
    p.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
    (p.couponCode && p.couponCode.toLowerCase().includes(searchTerm.toLowerCase())) ||
    p.promotionType.toLowerCase().includes(searchTerm.toLowerCase())
  )

  // ─── KPI Data ──────────────────────────────────────────────────────

  const totalPromos = promotions.length
  const activePromos = promotions.filter(isActive).length
  const expiredPromos = promotions.filter(p => isExpired(p.endDate)).length
  const totalCouponPromos = promotions.filter(p => p.couponCode).length

  const tabs = [
    { key: 'list', label: 'Promotions', count: totalPromos },
    { key: 'stats', label: 'Usage Stats' },
  ] as const

  // ─── Render ────────────────────────────────────────────────────────

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-white">Promotions</h1>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
            Manage discounts, coupon codes, and promotional campaigns
          </p>
        </div>
        <button
          onClick={() => { resetForm(); setShowCreateModal(true) }}
          className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
        >
          <Plus className="h-4 w-4" /> Create Promotion
        </button>
      </div>

      {/* KPIs */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <EnterpriseKPICard
          title="Total Promotions"
          value={totalPromos}
          icon={Tag}
          trend={{ value: 0, isPositive: true }}
        />
        <EnterpriseKPICard
          title="Active"
          value={activePromos}
          icon={Check}
          trend={{ value: 0, isPositive: true }}
        />
        <EnterpriseKPICard
          title="Expired"
          value={expiredPromos}
          icon={Calendar}
          trend={{ value: 0, isPositive: false }}
        />
        <EnterpriseKPICard
          title="Coupon Codes"
          value={totalCouponPromos}
          icon={Gift}
          trend={{ value: 0, isPositive: true }}
        />
      </div>

      {/* Tabs */}
      <EnterpriseTabs tabs={[...tabs]} activeTab={activeTab} onChange={(t) => setActiveTab(t as PromoTab)} />

      {/* Search */}
      {activeTab === 'list' && (
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
          <input
            type="text"
            placeholder="Search promotions..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full pl-10 pr-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500"
          />
        </div>
      )}

      {/* Promotions List */}
      {activeTab === 'list' && (
        <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 overflow-hidden">
          {isLoading ? (
            <div className="p-8 text-center text-gray-500">Loading promotions...</div>
          ) : filteredPromotions.length === 0 ? (
            <div className="p-8 text-center text-gray-500">
              {searchTerm ? 'No promotions match your search' : 'No promotions created yet'}
            </div>
          ) : (
            <table className="w-full">
              <thead>
                <tr className="border-b border-gray-200 dark:border-gray-700">
                  <th className="text-left px-4 py-3 text-sm font-medium text-gray-500 dark:text-gray-400">Promotion</th>
                  <th className="text-left px-4 py-3 text-sm font-medium text-gray-500 dark:text-gray-400">Type</th>
                  <th className="text-left px-4 py-3 text-sm font-medium text-gray-500 dark:text-gray-400">Discount</th>
                  <th className="text-left px-4 py-3 text-sm font-medium text-gray-500 dark:text-gray-400">Code</th>
                  <th className="text-left px-4 py-3 text-sm font-medium text-gray-500 dark:text-gray-400">Usage</th>
                  <th className="text-left px-4 py-3 text-sm font-medium text-gray-500 dark:text-gray-400">Valid Period</th>
                  <th className="text-left px-4 py-3 text-sm font-medium text-gray-500 dark:text-gray-400">Status</th>
                  <th className="text-right px-4 py-3 text-sm font-medium text-gray-500 dark:text-gray-400">Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredPromotions.map((promo) => {
                  const Icon = getPromoIcon(promo.promotionType)
                  return (
                    <tr key={promo.id} className="border-b border-gray-100 dark:border-gray-700/50 hover:bg-gray-50 dark:hover:bg-gray-700/50">
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-3">
                          <div className={clsx('p-2 rounded-lg', isActive(promo) ? 'bg-green-100 dark:bg-green-900/30' : 'bg-gray-100 dark:bg-gray-700')}>
                            <Icon className={clsx('h-4 w-4', isActive(promo) ? 'text-green-600 dark:text-green-400' : 'text-gray-500')} />
                          </div>
                          <div>
                            <div className="font-medium text-gray-900 dark:text-white">{promo.name}</div>
                            {promo.description && (
                              <div className="text-xs text-gray-500 dark:text-gray-400 truncate max-w-[200px]">{promo.description}</div>
                            )}
                          </div>
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <span className="text-sm text-gray-600 dark:text-gray-300">
                          {PROMOTION_TYPES.find(t => t.value === promo.promotionType)?.label || promo.promotionType}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <span className="text-lg font-bold text-blue-600 dark:text-blue-400">
                          {formatDiscount(promo)}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        {promo.couponCode ? (
                          <span className="px-2 py-1 bg-purple-100 dark:bg-purple-900/30 text-purple-700 dark:text-purple-300 rounded text-sm font-mono">
                            {promo.couponCode}
                          </span>
                        ) : (
                          <span className="text-gray-400">—</span>
                        )}
                      </td>
                      <td className="px-4 py-3">
                        <span className="text-sm text-gray-600 dark:text-gray-300">
                          {promo.currentUses || 0}
                          {promo.maxUsesTotal ? ` / ${promo.maxUsesTotal}` : ''}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <div className="text-sm text-gray-600 dark:text-gray-300">
                          <div>{new Date(promo.startDate).toLocaleDateString()}</div>
                          <div className="text-xs text-gray-400">to {new Date(promo.endDate).toLocaleDateString()}</div>
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <EnterpriseStatusBadge
                          status={isActive(promo) ? 'active' : isExpired(promo.endDate) ? 'error' : 'inactive'}
                          label={isActive(promo) ? 'Active' : isExpired(promo.endDate) ? 'Expired' : 'Inactive'}
                        />
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex items-center justify-end gap-2">
                          <button
                            onClick={() => toggleActiveMutation.mutate({ id: promo.id!, active: !promo.active })}
                            className="p-1 rounded hover:bg-gray-100 dark:hover:bg-gray-700"
                            title={promo.active ? 'Deactivate' : 'Activate'}
                          >
                            {promo.active ? (
                              <ToggleRight className="h-5 w-5 text-green-500" />
                            ) : (
                              <ToggleLeft className="h-5 w-5 text-gray-400" />
                            )}
                          </button>
                          <button
                            onClick={() => openEditModal(promo)}
                            className="p-1 rounded hover:bg-gray-100 dark:hover:bg-gray-700"
                          >
                            <Edit className="h-4 w-4 text-gray-500" />
                          </button>
                          <button
                            onClick={() => {
                              if (confirm('Delete this promotion?')) {
                                deleteMutation.mutate(promo.id!)
                              }
                            }}
                            className="p-1 rounded hover:bg-red-100 dark:hover:bg-red-900/30"
                          >
                            <Trash2 className="h-4 w-4 text-red-500" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          )}
        </div>
      )}

      {/* Stats Tab */}
      {activeTab === 'stats' && (
        <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6">
          {loadingStats ? (
            <div className="text-center text-gray-500 py-8">Loading stats...</div>
          ) : !stats || stats.length === 0 ? (
            <div className="text-center text-gray-500 py-8">No usage data available</div>
          ) : (
            <div className="space-y-4">
              {stats.map((s: any) => (
                <div key={s.promotionId} className="flex items-center justify-between p-4 border border-gray-200 dark:border-gray-700 rounded-lg">
                  <div>
                    <div className="font-medium text-gray-900 dark:text-white">{s.name}</div>
                    <div className="text-sm text-gray-500">
                      {s.totalUses} uses • ${s.totalDiscountGiven?.toFixed(2) || '0.00'} total discount
                    </div>
                  </div>
                  {s.remainingUses !== null && (
                    <div className="text-right">
                      <div className="text-sm text-gray-500">Remaining</div>
                      <div className="font-medium text-gray-900 dark:text-white">{s.remainingUses}</div>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Create/Edit Modal */}
      {(showCreateModal || editingPromo) && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
          <div className="bg-white dark:bg-gray-800 rounded-xl shadow-2xl w-full max-w-2xl max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between p-6 border-b border-gray-200 dark:border-gray-700">
              <h2 className="text-lg font-semibold text-gray-900 dark:text-white">
                {editingPromo ? 'Edit Promotion' : 'Create Promotion'}
              </h2>
              <button onClick={() => { setShowCreateModal(false); setEditingPromo(null) }} className="p-1 rounded hover:bg-gray-100 dark:hover:bg-gray-700">
                <X className="h-5 w-5 text-gray-500" />
              </button>
            </div>

            <div className="p-6 space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Name *</label>
                  <input type="text" value={form.name || ''} onChange={e => setForm({ ...form, name: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Type *</label>
                  <select value={form.promotionType} onChange={e => setForm({ ...form, promotionType: e.target.value as any })}
                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white">
                    {PROMOTION_TYPES.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
                  </select>
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Description</label>
                <textarea value={form.description || ''} onChange={e => setForm({ ...form, description: e.target.value })} rows={2}
                  className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white" />
              </div>

              <div className="grid grid-cols-3 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    {form.promotionType === 'PERCENTAGE' ? 'Percentage (%)' : 'Amount ($)'} *
                  </label>
                  <input type="number" step="0.01" value={form.discountValue || ''} onChange={e => setForm({ ...form, discountValue: parseFloat(e.target.value) })}
                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Min Order ($)</label>
                  <input type="number" step="0.01" value={form.minOrderAmount || ''} onChange={e => setForm({ ...form, minOrderAmount: parseFloat(e.target.value) })}
                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Min Quantity</label>
                  <input type="number" value={form.minQuantity || ''} onChange={e => setForm({ ...form, minQuantity: parseInt(e.target.value) })}
                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white" />
                </div>
              </div>

              <div className="grid grid-cols-3 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Coupon Code</label>
                  <input type="text" value={form.couponCode || ''} onChange={e => setForm({ ...form, couponCode: e.target.value.toUpperCase() })}
                    placeholder="e.g. SUMMER20"
                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white font-mono" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Max Total Uses</label>
                  <input type="number" value={form.maxUsesTotal || ''} onChange={e => setForm({ ...form, maxUsesTotal: parseInt(e.target.value) || undefined })}
                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Max Per Customer</label>
                  <input type="number" value={form.maxUsesPerCustomer || ''} onChange={e => setForm({ ...form, maxUsesPerCustomer: parseInt(e.target.value) || undefined })}
                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white" />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Start Date *</label>
                  <input type="datetime-local" value={form.startDate || ''} onChange={e => setForm({ ...form, startDate: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">End Date *</label>
                  <input type="datetime-local" value={form.endDate || ''} onChange={e => setForm({ ...form, endDate: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white" />
                </div>
              </div>

              <div className="grid grid-cols-3 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Channels</label>
                  <select value={form.applicableChannels || 'ALL'} onChange={e => setForm({ ...form, applicableChannels: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white">
                    {CHANNELS.map(c => <option key={c} value={c}>{c}</option>)}
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Priority</label>
                  <input type="number" value={form.priority || 10} onChange={e => setForm({ ...form, priority: parseInt(e.target.value) })}
                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white" />
                </div>
                <div className="flex items-end">
                  <label className="flex items-center gap-2 cursor-pointer pb-2">
                    <input type="checkbox" checked={form.stackable || false} onChange={e => setForm({ ...form, stackable: e.target.checked })}
                      className="rounded border-gray-300 text-blue-600" />
                    <span className="text-sm text-gray-700 dark:text-gray-300">Stackable</span>
                  </label>
                </div>
              </div>
            </div>

            <div className="flex justify-end gap-3 p-6 border-t border-gray-200 dark:border-gray-700">
              <button onClick={() => { setShowCreateModal(false); setEditingPromo(null) }}
                className="px-4 py-2 text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg">
                Cancel
              </button>
              <button onClick={handleSubmit}
                disabled={createMutation.isPending || updateMutation.isPending}
                className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50">
                {editingPromo ? 'Update' : 'Create'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
