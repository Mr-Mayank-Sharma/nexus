import { useState, useEffect } from 'react'
import { Settings, Building2, Shield, Plus, X, Save } from 'lucide-react'
import { useToast } from '../hooks/useToast'
import * as settingsApi from '../api/settings'
import PermissionGate from '../components/rbac/PermissionGate'
import Autocomplete from '../components/common/Autocomplete'

type Tab = 'general' | 'company' | 'security'

const currencies = ['USD', 'EUR', 'GBP', 'INR', 'JPY', 'CAD', 'AUD', 'CNY', 'BRL', 'MXN']
const languages = ['en-US', 'en-GB', 'es-ES', 'fr-FR', 'de-DE', 'zh-CN', 'ja-JP', 'pt-BR']
const timezones = ['UTC', 'America/New_York', 'America/Chicago', 'America/Denver', 'America/Los_Angeles', 'Europe/London', 'Europe/Paris', 'Europe/Berlin', 'Asia/Tokyo', 'Asia/Shanghai', 'Asia/Kolkata', 'Australia/Sydney']
const dateFormats = ['MM/DD/YYYY', 'DD/MM/YYYY', 'YYYY-MM-DD', 'DD.MM.YYYY']
const timeFormats = ['12h', '24h']

export default function SettingsPage() {
  const [tab, setTab] = useState<Tab>('general')
  const { addToast } = useToast()

  const [company, setCompany] = useState({
    companyName: '', taxId: '', registrationNumber: '',
    defaultCurrency: 'USD', language: 'en-US', timezone: 'UTC',
    dateFormat: 'MM/DD/YYYY', timeFormat: '12h',
    featureFlags: {} as Record<string, boolean>,
  })
  const [newFlagName, setNewFlagName] = useState('')
  const [newFlagEnabled, setNewFlagEnabled] = useState(true)
  const [securityPolicy, setSecurityPolicy] = useState('')
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (tab === 'company') fetchCompanySettings()
  }, [tab])

  async function fetchCompanySettings() {
    try {
      setLoading(true)
      const res = await settingsApi.getCompanySettings()
      const d = res.data
      setCompany({
        companyName: d.companyName || '',
        taxId: d.taxId || '',
        registrationNumber: d.registrationNumber || '',
        defaultCurrency: d.currency || 'USD',
        language: d.languages?.[0] || 'en-US',
        timezone: d.timezone || 'UTC',
        dateFormat: d.dateFormat || 'MM/DD/YYYY',
        timeFormat: '12h',
        featureFlags: d.featureFlags || {},
      })
    } catch {
      addToast({ type: 'error', title: 'Failed to load company settings' })
    } finally {
      setLoading(false)
    }
  }

  async function handleSaveCompany() {
    setSaving(true)
    try {
      await settingsApi.updateCompanySettings({
        companyName: company.companyName,
        taxId: company.taxId,
        registrationNumber: company.registrationNumber,
        currency: company.defaultCurrency,
        languages: [company.language],
        timezone: company.timezone,
        dateFormat: company.dateFormat,
      })
      await settingsApi.updateFeatureFlags(company.featureFlags)
      addToast({ type: 'success', title: 'Company settings saved' })
    } catch {
      addToast({ type: 'error', title: 'Failed to save company settings' })
    } finally {
      setSaving(false)
    }
  }

  function addFeatureFlag() {
    if (!newFlagName.trim()) return
    setCompany({
      ...company,
      featureFlags: { ...company.featureFlags, [newFlagName.trim()]: newFlagEnabled },
    })
    setNewFlagName('')
    setNewFlagEnabled(true)
  }

  function removeFeatureFlag(key: string) {
    const { [key]: _, ...rest } = company.featureFlags
    setCompany({ ...company, featureFlags: rest })
  }

  function toggleFeatureFlag(key: string) {
    setCompany({
      ...company,
      featureFlags: { ...company.featureFlags, [key]: !company.featureFlags[key] },
    })
  }

  async function handleSaveSecurity() {
    setSaving(true)
    try {
      let parsed
      try {
        parsed = JSON.parse(securityPolicy)
      } catch {
        addToast({ type: 'warning', title: 'Invalid JSON' })
        setSaving(false)
        return
      }
      await settingsApi.updateSecurityPolicy(parsed)
      addToast({ type: 'success', title: 'Security policy saved' })
    } catch {
      addToast({ type: 'error', title: 'Failed to save security policy' })
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="space-y-6">
      <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5"><Settings className="w-6 h-6" />Settings</h1>
        <p className="text-sm text-[var(--text-secondary)] mt-1">Configure system preferences</p>
      </div>

      <div className="flex gap-1 border-b border-[var(--border-default)]">
        <button
          onClick={() => setTab('general')}
          className={`px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
            tab === 'general' ? 'border-[var(--nexus-primary-600)] text-[var(--text-brand)]' : 'border-transparent text-[var(--text-secondary)] hover:text-[var(--text-secondary)]'
          }`}
        >
          <Settings className="w-4 h-4 inline mr-1.5" /> General
        </button>
        <button
          onClick={() => setTab('company')}
          className={`px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
            tab === 'company' ? 'border-[var(--nexus-primary-600)] text-[var(--text-brand)]' : 'border-transparent text-[var(--text-secondary)] hover:text-[var(--text-secondary)]'
          }`}
        >
          <Building2 className="w-4 h-4 inline mr-1.5" /> Company
        </button>
        <button
          onClick={() => setTab('security')}
          className={`px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
            tab === 'security' ? 'border-[var(--nexus-primary-600)] text-[var(--text-brand)]' : 'border-transparent text-[var(--text-secondary)] hover:text-[var(--text-secondary)]'
          }`}
        >
          <Shield className="w-4 h-4 inline mr-1.5" /> Security
        </button>
      </div>

      {tab === 'general' && (
        <div className="card p-6 space-y-6">
          <h2 className="text-lg font-semibold text-[var(--text-primary)]">General Settings</h2>
          <div className="grid grid-cols-2 gap-6">
            <div>
              <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Company Name</label>
              <p className="text-sm text-[var(--text-primary)]">{company.companyName || '—'}</p>
            </div>
            <div>
              <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Tax ID</label>
              <p className="text-sm text-[var(--text-primary)]">{company.taxId || '—'}</p>
            </div>
            <div>
              <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Registration Number</label>
              <p className="text-sm text-[var(--text-primary)]">{company.registrationNumber || '—'}</p>
            </div>
            <div>
              <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Timezone</label>
              <p className="text-sm text-[var(--text-primary)]">{company.timezone}</p>
            </div>
            <div>
              <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Currency</label>
              <p className="text-sm text-[var(--text-primary)]">{company.defaultCurrency}</p>
            </div>
            <div>
              <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Date Format</label>
              <p className="text-sm text-[var(--text-primary)]">{company.dateFormat}</p>
            </div>
          </div>
          <div className="pt-4 border-t border-[var(--border-subtle)]">
            <button onClick={() => setTab('company')} className="text-sm text-[var(--text-brand)] hover:text-[var(--nexus-primary-700)] font-medium">
              Edit company settings →
            </button>
          </div>
        </div>
      )}

      {tab === 'company' && (
        <div className="card p-6 space-y-6">
          {loading ? (
            <div className="flex items-center justify-center h-48">
              <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[var(--nexus-primary-600)]" />
            </div>
          ) : (
            <>
              <h3 className="text-lg font-semibold text-[var(--text-primary)]">Company Information</h3>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Company Name</label>
                  <Autocomplete value={company.companyName} onChange={(value) => setCompany({ ...company, companyName: value })} inputClassName="input w-full" placeholder="Company name" minChars={0} showSearchIcon={false} clearable={false} />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Tax ID</label>
                  <Autocomplete value={company.taxId} onChange={(value) => setCompany({ ...company, taxId: value })} inputClassName="input w-full" placeholder="Tax ID" minChars={0} showSearchIcon={false} clearable={false} />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Registration Number</label>
                  <Autocomplete value={company.registrationNumber} onChange={(value) => setCompany({ ...company, registrationNumber: value })} inputClassName="input w-full" placeholder="Registration number" minChars={0} showSearchIcon={false} clearable={false} />
                </div>
              </div>

              <h4 className="text-sm font-semibold text-[var(--text-secondary)] pt-2">Regional Settings</h4>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Default Currency</label>
                  <Autocomplete
                    value={company.defaultCurrency}
                    onChange={(value) => setCompany({ ...company, defaultCurrency: value })}
                    suggestions={currencies}
                    inputClassName="input w-full"
                    minChars={0}
                    showSearchIcon={false}
                    clearable={false}
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Language</label>
                  <Autocomplete
                    value={company.language}
                    onChange={(value) => setCompany({ ...company, language: value })}
                    suggestions={languages}
                    inputClassName="input w-full"
                    minChars={0}
                    showSearchIcon={false}
                    clearable={false}
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Timezone</label>
                  <Autocomplete
                    value={company.timezone}
                    onChange={(value) => setCompany({ ...company, timezone: value })}
                    suggestions={timezones}
                    inputClassName="input w-full"
                    minChars={0}
                    showSearchIcon={false}
                    clearable={false}
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Date Format</label>
                  <Autocomplete
                    value={company.dateFormat}
                    onChange={(value) => setCompany({ ...company, dateFormat: value })}
                    suggestions={dateFormats}
                    inputClassName="input w-full"
                    minChars={0}
                    showSearchIcon={false}
                    clearable={false}
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-secondary)] mb-1">Time Format</label>
                  <Autocomplete
                    value={company.timeFormat}
                    onChange={(value) => setCompany({ ...company, timeFormat: value })}
                    suggestions={timeFormats}
                    inputClassName="input w-full"
                    minChars={0}
                    showSearchIcon={false}
                    clearable={false}
                  />
                </div>
              </div>

              <h4 className="text-sm font-semibold text-[var(--text-secondary)] pt-2">Feature Flags</h4>
              <div className="space-y-2">
                {Object.entries(company.featureFlags).map(([key, enabled]) => (
                  <div key={key} className="flex items-center justify-between py-2 px-3 bg-[var(--surface-sunken)] rounded-lg">
                    <span className="text-sm font-mono text-[var(--text-secondary)]">{key}</span>
                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => toggleFeatureFlag(key)}
                        className={`relative w-10 h-5 rounded-full transition-colors ${enabled ? 'bg-[var(--nexus-primary-600)]' : 'bg-[var(--surface-muted)]'}`}
                      >
                        <span className={`absolute top-0.5 left-0.5 w-4 h-4 bg-white rounded-full shadow transition-transform ${enabled ? 'translate-x-5' : ''}`} />
                      </button>
                      <button onClick={() => removeFeatureFlag(key)} className="p-1 hover:bg-[var(--surface-muted)] rounded text-[var(--text-tertiary)] hover:text-[var(--nexus-error-500)]">
                        <X className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </div>
                ))}
                {Object.keys(company.featureFlags).length === 0 && (
                  <p className="text-xs text-[var(--text-secondary)]">No feature flags configured.</p>
                )}
              </div>
              <div className="flex items-center gap-2">
                <Autocomplete
                  value={newFlagName}
                  onChange={(value) => setNewFlagName(value)}
                  inputClassName="input flex-1"
                  placeholder="New flag name"
                  minChars={0}
                  showSearchIcon={false}
                  clearable={false}
                />
                <label className="flex items-center gap-1.5 text-xs text-[var(--text-secondary)] whitespace-nowrap">
                  <input
                    type="checkbox"
                    checked={newFlagEnabled}
                    onChange={e => setNewFlagEnabled(e.target.checked)}
                    className="rounded border-[var(--border-default)] text-[var(--text-brand)] focus:ring-[var(--nexus-primary-500)]"
                  />
                  Enabled
                </label>
                <button onClick={addFeatureFlag} className="btn-secondary text-sm">
                  <Plus className="w-3.5 h-3.5" /> Add
                </button>
              </div>

              <div className="pt-4 border-t border-[var(--border-subtle)] flex justify-end">
                <PermissionGate resource="settings" action="edit">
                  <button onClick={handleSaveCompany} disabled={saving} className="btn-primary text-sm">
                    {saving && <Loader2 className="w-4 h-4 animate-spin" />}
                    Save Settings
                  </button>
                </PermissionGate>
              </div>
            </>
          )}
        </div>
      )}

      {tab === 'security' && (
        <div className="card p-6 space-y-4">
          <h3 className="text-lg font-semibold text-[var(--text-primary)]">Security Policy</h3>
          <p className="text-sm text-[var(--text-secondary)]">Edit the security policy configuration as JSON.</p>
          <Autocomplete
            value={securityPolicy}
            onChange={(value) => setSecurityPolicy(value)}
            inputClassName="input w-full font-mono text-xs"
            placeholder='{
  "passwordMinLength": 8,
  "requireSpecialChars": true,
  "requireNumbers": true,
  "requireUppercase": true,
  "maxLoginAttempts": 5,
  "sessionTimeoutMinutes": 60,
  "twoFactorRequired": false,
  "allowedIpRanges": []
}'
            minChars={0}
            showSearchIcon={false}
            clearable={false}
          />
          <div className="flex justify-end">
            <PermissionGate resource="settings" action="edit">
              <button onClick={handleSaveSecurity} disabled={saving} className="btn-primary text-sm">
                {saving && <Loader2 className="w-4 h-4 animate-spin" />}
                Save Security Policy
              </button>
            </PermissionGate>
          </div>
        </div>
      )}
    </div>
  )
}
