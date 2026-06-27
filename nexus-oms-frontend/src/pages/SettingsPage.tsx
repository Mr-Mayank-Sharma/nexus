import { useState, useEffect } from 'react'
import { Settings, Building2, Shield, Plus, X, Save } from 'lucide-react'
import { useToast } from '../hooks/useToast'
import * as settingsApi from '../api/settings'

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
        <h1 className="text-2xl font-bold text-gray-900">Settings</h1>
        <p className="text-sm text-gray-500 mt-1">Configure system preferences</p>
      </div>

      <div className="flex gap-1 border-b border-gray-200">
        <button
          onClick={() => setTab('general')}
          className={`px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
            tab === 'general' ? 'border-primary-600 text-primary-600' : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          <Settings className="w-4 h-4 inline mr-1.5" /> General
        </button>
        <button
          onClick={() => setTab('company')}
          className={`px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
            tab === 'company' ? 'border-primary-600 text-primary-600' : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          <Building2 className="w-4 h-4 inline mr-1.5" /> Company
        </button>
        <button
          onClick={() => setTab('security')}
          className={`px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
            tab === 'security' ? 'border-primary-600 text-primary-600' : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          <Shield className="w-4 h-4 inline mr-1.5" /> Security
        </button>
      </div>

      {tab === 'general' && (
        <div className="card p-12 text-center text-gray-500">
          <Settings className="w-12 h-12 mx-auto mb-3 text-gray-300" />
          <p className="font-medium">General settings panel coming soon</p>
          <p className="text-sm mt-1">Tenant configuration, user management, and integrations</p>
        </div>
      )}

      {tab === 'company' && (
        <div className="card p-6 space-y-6">
          {loading ? (
            <div className="flex items-center justify-center h-48">
              <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600" />
            </div>
          ) : (
            <>
              <h3 className="text-lg font-semibold text-gray-900">Company Information</h3>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Company Name</label>
                  <input value={company.companyName} onChange={e => setCompany({ ...company, companyName: e.target.value })} className="input w-full" placeholder="Company name" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Tax ID</label>
                  <input value={company.taxId} onChange={e => setCompany({ ...company, taxId: e.target.value })} className="input w-full" placeholder="Tax ID" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Registration Number</label>
                  <input value={company.registrationNumber} onChange={e => setCompany({ ...company, registrationNumber: e.target.value })} className="input w-full" placeholder="Registration number" />
                </div>
              </div>

              <h4 className="text-sm font-semibold text-gray-700 pt-2">Regional Settings</h4>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Default Currency</label>
                  <select value={company.defaultCurrency} onChange={e => setCompany({ ...company, defaultCurrency: e.target.value })} className="input w-full">
                    {currencies.map(c => <option key={c} value={c}>{c}</option>)}
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Language</label>
                  <select value={company.language} onChange={e => setCompany({ ...company, language: e.target.value })} className="input w-full">
                    {languages.map(l => <option key={l} value={l}>{l}</option>)}
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Timezone</label>
                  <select value={company.timezone} onChange={e => setCompany({ ...company, timezone: e.target.value })} className="input w-full">
                    {timezones.map(t => <option key={t} value={t}>{t}</option>)}
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Date Format</label>
                  <select value={company.dateFormat} onChange={e => setCompany({ ...company, dateFormat: e.target.value })} className="input w-full">
                    {dateFormats.map(d => <option key={d} value={d}>{d}</option>)}
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Time Format</label>
                  <select value={company.timeFormat} onChange={e => setCompany({ ...company, timeFormat: e.target.value })} className="input w-full">
                    {timeFormats.map(t => <option key={t} value={t}>{t}</option>)}
                  </select>
                </div>
              </div>

              <h4 className="text-sm font-semibold text-gray-700 pt-2">Feature Flags</h4>
              <div className="space-y-2">
                {Object.entries(company.featureFlags).map(([key, enabled]) => (
                  <div key={key} className="flex items-center justify-between py-2 px-3 bg-gray-50 rounded-lg">
                    <span className="text-sm font-mono text-gray-700">{key}</span>
                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => toggleFeatureFlag(key)}
                        className={`relative w-10 h-5 rounded-full transition-colors ${enabled ? 'bg-primary-600' : 'bg-gray-300'}`}
                      >
                        <span className={`absolute top-0.5 left-0.5 w-4 h-4 bg-white rounded-full shadow transition-transform ${enabled ? 'translate-x-5' : ''}`} />
                      </button>
                      <button onClick={() => removeFeatureFlag(key)} className="p-1 hover:bg-gray-200 rounded text-gray-400 hover:text-red-500">
                        <X className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </div>
                ))}
                {Object.keys(company.featureFlags).length === 0 && (
                  <p className="text-xs text-gray-500">No feature flags configured.</p>
                )}
              </div>
              <div className="flex items-center gap-2">
                <input
                  value={newFlagName}
                  onChange={e => setNewFlagName(e.target.value)}
                  className="input flex-1"
                  placeholder="New flag name"
                  onKeyDown={e => e.key === 'Enter' && addFeatureFlag()}
                />
                <label className="flex items-center gap-1.5 text-xs text-gray-600 whitespace-nowrap">
                  <input
                    type="checkbox"
                    checked={newFlagEnabled}
                    onChange={e => setNewFlagEnabled(e.target.checked)}
                    className="rounded border-gray-300 text-primary-600 focus:ring-primary-500"
                  />
                  Enabled
                </label>
                <button onClick={addFeatureFlag} className="btn-secondary text-sm">
                  <Plus className="w-3.5 h-3.5" /> Add
                </button>
              </div>

              <div className="pt-4 border-t border-gray-100 flex justify-end">
                <button onClick={handleSaveCompany} disabled={saving} className="btn-primary text-sm">
                  {saving ? <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white" /> : <Save className="w-4 h-4" />}
                  Save Settings
                </button>
              </div>
            </>
          )}
        </div>
      )}

      {tab === 'security' && (
        <div className="card p-6 space-y-4">
          <h3 className="text-lg font-semibold text-gray-900">Security Policy</h3>
          <p className="text-sm text-gray-500">Edit the security policy configuration as JSON.</p>
          <textarea
            value={securityPolicy}
            onChange={e => setSecurityPolicy(e.target.value)}
            className="input w-full font-mono text-xs"
            rows={16}
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
          />
          <div className="flex justify-end">
            <button onClick={handleSaveSecurity} disabled={saving} className="btn-primary text-sm">
              {saving ? <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white" /> : <Save className="w-4 h-4" />}
              Save Security Policy
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
