import { useState } from 'react'
import { clsx } from 'clsx'
import {
  Search, Truck, Zap, DollarSign, Clock, Star, Loader2,
  Package, MapPin, Scale, ArrowRight,
} from 'lucide-react'
import * as rateShoppingApi from '../api/rateShopping'
import type { RateQuote, RateShoppingResult } from '../types'
import { useToast } from '../hooks/useToast'
import Autocomplete from '../components/common/Autocomplete'
import PermissionGate from '../components/rbac/PermissionGate'

const SERVICE_LEVELS = [
  { value: '', label: 'All Services' },
  { value: 'GROUND', label: 'Ground' },
  { value: 'EXPRESS', label: 'Express 3-Day' },
  { value: 'TWO_DAY', label: 'Express 2-Day' },
  { value: 'OVERNIGHT', label: 'Next Day' },
]

export default function CarrierRateShoppingPage() {
  const [fromZip, setFromZip] = useState('')
  const [toZip, setToZip] = useState('')
  const [weight, setWeight] = useState('')
  const [declaredValue, setDeclaredValue] = useState('')
  const [numPackages, setNumPackages] = useState('1')
  const [residential, setResidential] = useState(false)
  const [serviceLevel, setServiceLevel] = useState('')
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<RateShoppingResult | null>(null)
  const [sortBy, setSortBy] = useState<'cost' | 'speed'>('cost')
  const { addToast } = useToast()

  const handleSearch = async () => {
    if (!fromZip || !toZip || !weight) {
      addToast({ type: 'error', title: 'Please fill in origin, destination, and weight' })
      return
    }
    setLoading(true)
    setResult(null)
    try {
      const res = await rateShoppingApi.shopRates({
        fromZip,
        toZip,
        totalWeightKg: parseFloat(weight),
        declaredValue: declaredValue ? parseFloat(declaredValue) : undefined,
        numPackages: parseInt(numPackages) || 1,
        residential,
        serviceLevels: serviceLevel ? [serviceLevel] : undefined,
      })
      setResult(res.data)
    } catch {
      addToast({ type: 'error', title: 'Failed to fetch rates' })
    } finally {
      setLoading(false)
    }
  }

  const sortedRates: RateQuote[] = result
    ? [...result.rates].sort((a, b) =>
        sortBy === 'cost'
          ? a.totalCost - b.totalCost
          : a.transitDaysMin - b.transitDaysMin
      )
    : []

  const RECOMMENDATION_BADGES: Record<string, string> = {
    Fastest: 'enterprise-badge-ai',
    Cheapest: 'enterprise-badge-success',
    'Best Value': 'enterprise-badge-info',
  }

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="enterprise-page-header">
        <div className="flex items-center gap-4">
          <div>
            <h1 className="flex items-center gap-2.5"><Truck className="w-6 h-6 text-emerald-600" />Carrier Rate Shopping</h1>
            <p>Compare real-time rates across carriers to find the best shipping option</p>
          </div>
        </div>
      </div>

      {/* Search Form */}
      <div className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] p-6">
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          <div className="enterprise-form-group">
            <label className="flex items-center gap-1.5">
              <MapPin className="w-3.5 h-3.5 text-[var(--text-tertiary)]" /> Origin ZIP
            </label>
            <Autocomplete
              className="enterprise-input"
              placeholder="94105"
              value={fromZip}
              onChange={v => setFromZip(v)}
              minChars={0}
            />
          </div>
          <div className="enterprise-form-group">
            <label className="flex items-center gap-1.5">
              <MapPin className="w-3.5 h-3.5 text-[var(--text-tertiary)]" /> Destination ZIP
            </label>
            <Autocomplete
              className="enterprise-input"
              placeholder="10001"
              value={toZip}
              onChange={v => setToZip(v)}
              minChars={0}
            />
          </div>
          <div className="enterprise-form-group">
            <label className="flex items-center gap-1.5">
              <Scale className="w-3.5 h-3.5 text-[var(--text-tertiary)]" /> Weight (kg)
            </label>
            <Autocomplete
              className="enterprise-input"
              placeholder="5.0"
              value={weight}
              onChange={v => setWeight(v)}
              minChars={0}
            />
          </div>
          <div className="enterprise-form-group">
            <label className="flex items-center gap-1.5">
              <DollarSign className="w-3.5 h-3.5 text-[var(--text-tertiary)]" /> Declared Value
            </label>
            <Autocomplete
              className="enterprise-input"
              placeholder="100.00"
              value={declaredValue}
              onChange={v => setDeclaredValue(v)}
              minChars={0}
            />
          </div>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mt-4">
          <div className="enterprise-form-group">
            <label className="flex items-center gap-1.5">
              <Package className="w-3.5 h-3.5 text-[var(--text-tertiary)]" /> Packages
            </label>
            <Autocomplete
              className="enterprise-input"
              placeholder="1"
              value={numPackages}
              onChange={v => setNumPackages(v)}
              minChars={0}
            />
          </div>
          <div className="enterprise-form-group">
            <label className="flex items-center gap-1.5">
              <Zap className="w-3.5 h-3.5 text-[var(--text-tertiary)]" /> Service Level
            </label>
            <Autocomplete className="enterprise-select" value={serviceLevel} onChange={v => setServiceLevel(v)} suggestions={SERVICE_LEVELS} getOptionLabel={o => o.label} getOptionValue={o => o.value} minChars={0} />
          </div>
          <div className="enterprise-form-group flex items-end">
            <label className="flex items-center gap-2 cursor-pointer">
              <input
                type="checkbox"
                checked={residential}
                onChange={e => setResidential(e.target.checked)}
                className="rounded border-[var(--border-default)] text-[var(--text-brand)] focus:ring-[var(--nexus-primary-500)]"
              />
              <span className="text-sm text-[var(--text-secondary)]">Residential Address</span>
            </label>
          </div>
          <div className="enterprise-form-group flex items-end">
            <button
              onClick={handleSearch}
              disabled={loading}
              className="enterprise-btn enterprise-btn-primary w-full"
            >
              {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Search className="w-4 h-4" />}
              {loading ? 'Searching...' : 'Compare Rates'}
            </button>
          </div>
        </div>
      </div>

      {/* Results */}
      {loading && (
        <div className="space-y-3">
          {[1, 2, 3].map(i => (
            <div key={i} className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] p-5">
              <div className="flex items-center gap-4">
                <div className="enterprise-skeleton w-12 h-12 rounded-xl" />
                <div className="flex-1 space-y-2">
                  <div className="enterprise-skeleton h-5 w-48" />
                  <div className="enterprise-skeleton h-4 w-64" />
                </div>
                <div className="enterprise-skeleton h-8 w-24 rounded-lg" />
              </div>
            </div>
          ))}
        </div>
      )}

      {result && result.rates.length === 0 && !loading && (
        <div className="enterprise-empty-state py-16">
          <Truck className="w-12 h-12 text-[var(--text-tertiary)] dark:text-[var(--text-secondary)]" />
          <h3>No rates found</h3>
          <p>Try adjusting the weight or destination</p>
        </div>
      )}

      {result && result.rates.length > 0 && (
        <>
          {/* Summary Cards */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {[
              { label: 'Fastest', quote: result.fastest, icon: Zap, color: 'bg-violet-50 text-violet-600 dark:bg-violet-900/20 dark:text-violet-400' },
              { label: 'Cheapest', quote: result.cheapest, icon: DollarSign, color: 'bg-emerald-50 text-emerald-600 dark:bg-emerald-900/20 dark:text-emerald-400' },
              { label: 'Best Value', quote: result.bestValue, icon: Star, color: 'bg-[var(--nexus-warning-50)] text-[var(--nexus-warning-600)] dark:bg-[var(--nexus-warning-900)]/20 dark:text-[var(--nexus-warning-400)]' },
            ].map(cat => cat.quote && (
              <div key={cat.label} className="bg-[var(--surface-base)] rounded-xl border border-[var(--border-default)] p-5">
                <div className="flex items-center justify-between mb-3">
                  <div className="flex items-center gap-2">
                    <div className={clsx('w-8 h-8 rounded-lg flex items-center justify-center', cat.color)}>
                      <cat.icon className="w-4 h-4" />
                    </div>
                    <span className="text-sm font-semibold text-[var(--text-primary)]">{cat.label}</span>
                  </div>
                  <span className="text-sm font-bold text-[var(--text-primary)]">${cat.quote.totalCost.toFixed(2)}</span>
                </div>
                <div className="flex items-center justify-between text-xs text-[var(--text-secondary)] mt-1 pt-2 border-t border-[var(--border-subtle)]">
                  <span>{cat.quote.carrierName} {cat.quote.serviceName}</span>
                  <span>{cat.quote.transitDaysMin}-{cat.quote.transitDaysMax} days</span>
                </div>
              </div>
            ))}
          </div>

          {/* Sort and count */}
          <div className="flex items-center justify-between">
            <p className="text-sm text-[var(--text-secondary)]">
              {result.rates.length} rate{result.rates.length !== 1 ? 's' : ''} found
              <span className="mx-2">&middot;</span>
              <span className="font-medium text-[var(--text-secondary)]">{result.fromZip}</span>
              <ArrowRight className="w-3 h-3 inline mx-1" />
              <span className="font-medium text-[var(--text-secondary)]">{result.toZip}</span>
              <span className="mx-2">&middot;</span>
              {result.totalWeightKg} kg
            </p>
            <div className="flex items-center gap-2">
              <span className="text-xs text-[var(--text-tertiary)]">Sort:</span>
              <button
                onClick={() => setSortBy('cost')}
                className={clsx('px-3 py-1.5 rounded-lg text-xs font-medium transition-colors',
                  sortBy === 'cost'
                    ? 'bg-[var(--nexus-primary-50)] text-[var(--nexus-primary-700)] dark:bg-[var(--nexus-primary-900)]/20 dark:text-[var(--nexus-primary-300)]'
                    : 'text-[var(--text-secondary)] hover:text-[var(--text-secondary)] hover:bg-[var(--surface-muted)] hover:bg-[var(--surface-base)]')}
              >
                <DollarSign className="w-3 h-3 inline mr-1" />Cost
              </button>
              <button
                onClick={() => setSortBy('speed')}
                className={clsx('px-3 py-1.5 rounded-lg text-xs font-medium transition-colors',
                  sortBy === 'speed'
                    ? 'bg-[var(--nexus-primary-50)] text-[var(--nexus-primary-700)] dark:bg-[var(--nexus-primary-900)]/20 dark:text-[var(--nexus-primary-300)]'
                    : 'text-[var(--text-secondary)] hover:text-[var(--text-secondary)] hover:bg-[var(--surface-muted)] hover:bg-[var(--surface-base)]')}
              >
                <Clock className="w-3 h-3 inline mr-1" />Speed
              </button>
            </div>
          </div>

          {/* Rate Cards */}
          {sortedRates.map((rate, i) => (
            <div
              key={`${rate.carrierCode}-${rate.serviceLevel}`}
              className={clsx(
                'bg-[var(--surface-base)] rounded-xl border p-5 transition-all duration-150 hover:shadow-sm',
                rate === result.cheapest
                  ? 'border-emerald-300 dark:border-emerald-700 ring-1 ring-emerald-200 dark:ring-emerald-800'
                  : 'border-[var(--border-default)]',
              )}
            >
              <div className="flex items-start gap-4">
                <div className={clsx('w-12 h-12 rounded-xl flex items-center justify-center shrink-0',
                  rate.carrierCode === 'FEDEX' && 'bg-[var(--nexus-ai-50)] text-[var(--nexus-ai-600)] dark:bg-[var(--nexus-ai-900)]/20 dark:text-[var(--nexus-ai-400)]',
                  rate.carrierCode === 'UPS' && 'bg-[var(--nexus-warning-50)] text-[var(--nexus-warning-600)] dark:bg-[var(--nexus-warning-900)]/20 dark:text-[var(--nexus-warning-400)]',
                  rate.carrierCode === 'USPS' && 'bg-[var(--nexus-primary-50)] text-[var(--nexus-primary-600)] dark:bg-[var(--nexus-primary-900)]/20 dark:text-[var(--nexus-primary-400)]',
                  !['FEDEX', 'UPS', 'USPS'].includes(rate.carrierCode) && 'bg-[var(--surface-sunken)] text-[var(--text-secondary)] bg-[var(--surface-muted)] dark:text-[var(--text-tertiary)]',
                )}>
                  <Truck className="w-6 h-6" />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-start justify-between">
                    <div>
                      <div className="flex items-center gap-2.5">
                        <h3 className="font-semibold text-[var(--text-primary)]">{rate.carrierName}</h3>
                        <span className="enterprise-badge enterprise-badge-neutral">{rate.serviceName}</span>
                        {rate.recommendation && (
                          <span className={clsx('enterprise-badge', RECOMMENDATION_BADGES[rate.recommendation])}>
                            {rate.recommendation}
                          </span>
                        )}
                        {rate === result.cheapest && (
                          <span className="px-1.5 py-0.5 text-[10px] font-bold bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-300 rounded-md">
                            BEST PRICE
                          </span>
                        )}
                      </div>
                      <div className="flex items-center gap-4 mt-1.5 text-sm text-[var(--text-secondary)]">
                        <span className="font-semibold text-2xl text-[var(--text-primary)]">
                          ${rate.totalCost.toFixed(2)}
                        </span>
                        <span className="flex items-center gap-1">
                          <Clock className="w-3.5 h-3.5" />
                          {rate.transitDaysMin}-{rate.transitDaysMax} business days
                        </span>
                        <span>Est. {rate.estimatedDelivery}</span>
                      </div>
                    </div>
                    <PermissionGate resource="logistics" action="create">
                      <button
                        onClick={() => addToast({ type: 'success', title: `Selected ${rate.carrierName} ${rate.serviceName} — $${rate.totalCost.toFixed(2)}` })}
                        className="enterprise-btn enterprise-btn-primary enterprise-btn-sm shrink-0 ml-4"
                      >
                        Select
                      </button>
                    </PermissionGate>
                  </div>
                  <div className="flex items-center gap-4 mt-3 pt-3 border-t border-[var(--border-subtle)] text-xs text-[var(--text-tertiary)] dark:text-[var(--text-secondary)]">
                    <span>Base: ${rate.baseRate.toFixed(2)}</span>
                    {rate.perKgCharge > 0 && <span>Per kg: ${rate.perKgCharge.toFixed(2)}</span>}
                    {rate.fuelSurcharge > 0 && <span>Fuel: ${rate.fuelSurcharge.toFixed(2)}</span>}
                    {rate.residentialSurcharge > 0 && <span>Residential: ${rate.residentialSurcharge.toFixed(2)}</span>}
                    {rate.zone && <span>Zone: {rate.zone}</span>}
                  </div>
                </div>
              </div>
            </div>
          ))}

          {/* Execution time */}
          <p className="text-xs text-[var(--text-tertiary)] dark:text-[var(--text-secondary)] text-right">
            Queried in {result.executionTimeMs}ms
          </p>
        </>
      )}
    </div>
  )
}
