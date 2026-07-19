import { useState, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Ship,
  Eye,
  EyeOff,
  Mail,
  Lock,
  Shield,
  ChevronDown,
  Search,
  Building2,
  ArrowLeft,
  ArrowRight,
  CheckCircle2,
  Loader2,
  ExternalLink,
  LogIn,
  AlertCircle,
  Users,
} from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import * as authApi from '../api/auth'
import { TenantInfo, UserRole } from '../types'
import Autocomplete from '../components/common/Autocomplete'

type LoginStep = 'tenant' | 'credentials' | 'register' | 'mfa' | 'forgot-password' | 'forgot-sent'

const SSO_PROVIDERS = [
  { id: 'google', name: 'Google', color: 'hover:bg-red-50 hover:border-red-200 hover:text-red-600' },
  { id: 'microsoft', name: 'Microsoft', color: 'hover:bg-blue-50 hover:border-blue-200 hover:text-blue-600' },
  { id: 'okta', name: 'Okta', color: 'hover:bg-orange-50 hover:border-orange-200 hover:text-orange-600' },
  { id: 'auth0', name: 'Auth0', color: 'hover:bg-purple-50 hover:border-purple-200 hover:text-purple-600' },
]

export default function LoginPage() {
  const { login, verifyMfa, ssoLogin, mfaRequired, mfaToken } = useAuth()
  const navigate = useNavigate()

  const [step, setStep] = useState<LoginStep>(
    mfaRequired ? 'mfa' : 'credentials'
  )
  const [tenants, setTenants] = useState<TenantInfo[]>([])
  const [selectedTenant, setSelectedTenant] = useState<TenantInfo | null>(null)
  const [tenantSearch, setTenantSearch] = useState('')
  const [tenantOpen, setTenantOpen] = useState(false)

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [rememberMe, setRememberMe] = useState(false)

  const [totpCode, setTotpCode] = useState(['', '', '', '', '', ''])
  const totpRefs = useRef<(HTMLInputElement | null)[]>([])

  const [forgotEmail, setForgotEmail] = useState('')

  const [registerName, setRegisterName] = useState('')
  const [registerEmail, setRegisterEmail] = useState('')
  const [registerPassword, setRegisterPassword] = useState('')
  const [registerConfirm, setRegisterConfirm] = useState('')

  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [forgotLoading, setForgotLoading] = useState(false)

  useEffect(() => {
    if (mfaRequired) setStep('mfa')
  }, [mfaRequired])

  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const ssoToken = params.get('token')
    const ssoUser = params.get('user')
    const ssoError = params.get('error')

    if (ssoToken && ssoUser) {
      localStorage.setItem('nexus_token', ssoToken)
      localStorage.setItem('nexus_user', JSON.stringify({ username: ssoUser }))
      window.location.href = '/api/v1/'
      return
    }

    if (ssoError) {
      const errorMessages: Record<string, string> = {
        sso_invalid_state: 'SSO session expired. Please try again.',
        sso_state_expired: 'SSO session expired. Please try again.',
        sso_not_configured: 'SSO provider is not configured.',
        sso_token_exchange_failed: 'Failed to complete SSO authentication.',
        sso_userinfo_failed: 'Failed to retrieve user information.',
        sso_no_email: 'Could not retrieve email from SSO provider.',
        sso_callback_failed: 'SSO authentication failed. Please try again.',
      }
      setError(errorMessages[ssoError] || 'SSO authentication failed.')
      window.history.replaceState({}, '', '/login')
    }

    authApi.getTenants().then(res => {
      if (res.success && Array.isArray(res.data) && res.data.length > 0) {
        setTenants(res.data)
        if (res.data.length === 1) {
          setSelectedTenant(res.data[0])
        }
      }
    }).catch(() => {})
  }, [])

  const filteredTenants = tenants.filter(t =>
    t.name.toLowerCase().includes(tenantSearch.toLowerCase())
  )

  function handleTenantSelect(t: TenantInfo) {
    setSelectedTenant(t)
    setTenantOpen(false)
    setTenantSearch('')
  }

  async function handleCredentialsLogin(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    if (!username || !password) {
      setError('Please fill in all fields')
      return
    }
    setLoading(true)
    try {
      await login({
        username,
        password,
        tenantId: selectedTenant?.id,
        rememberMe,
      })
      if (!mfaRequired) {
        navigate('/', { replace: true })
      }
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        setError(axiosErr.response?.data?.message || 'Invalid credentials')
      } else {
        setError('An unexpected error occurred')
      }
    } finally {
      setLoading(false)
    }
  }

  async function handleMfaVerify(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    const code = totpCode.join('')
    if (code.length !== 6) {
      setError('Please enter the complete 6-digit code')
      return
    }
    setLoading(true)
    try {
      await verifyMfa(code)
      navigate('/', { replace: true })
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        setError(axiosErr.response?.data?.message || 'Invalid verification code')
      } else {
        setError('An unexpected error occurred')
      }
    } finally {
      setLoading(false)
    }
  }

  function handleSsoClick(providerId: string) {
    const params = new URLSearchParams()
    if (selectedTenant?.id) params.set('tenantId', selectedTenant.id)
    const qs = params.toString()
    window.location.href = `/api/v1/auth/sso/${providerId}/authorize${qs ? '?' + qs : ''}`
  }

  async function handleForgotPassword(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    if (!forgotEmail) {
      setError('Please enter your email address')
      return
    }
    setForgotLoading(true)
    try {
      await authApi.forgotPassword({ email: forgotEmail })
      setStep('forgot-sent')
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        setError(axiosErr.response?.data?.message || 'Failed to send reset email')
      } else {
        setError('An unexpected error occurred')
      }
    } finally {
      setForgotLoading(false)
    }
  }

  function handleTotpChange(index: number, value: string) {
    if (value && !/^\d$/.test(value)) return
    const newCode = [...totpCode]
    newCode[index] = value
    setTotpCode(newCode)
    if (value && index < 5) {
      totpRefs.current[index + 1]?.focus()
    }
  }

  function handleTotpKeyDown(index: number, e: React.KeyboardEvent) {
    if (e.key === 'Backspace' && !totpCode[index] && index > 0) {
      totpRefs.current[index - 1]?.focus()
    }
  }

  function handleTotpPaste(e: React.ClipboardEvent) {
    e.preventDefault()
    const paste = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, 6)
    const newCode = paste.split('')
    while (newCode.length < 6) newCode.push('')
    setTotpCode(newCode)
    const nextEmpty = newCode.findIndex(c => !c)
    const focusIndex = nextEmpty === -1 ? 5 : nextEmpty
    totpRefs.current[focusIndex]?.focus()
  }

  function resetToCredentials() {
    setStep('credentials')
    setError('')
  }

  async function handleRegister(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    if (!registerName || !registerEmail || !registerPassword) {
      setError('Please fill in all fields')
      return
    }
    if (registerPassword !== registerConfirm) {
      setError('Passwords do not match')
      return
    }
    if (registerPassword.length < 6) {
      setError('Password must be at least 6 characters')
      return
    }
    setLoading(true)
    try {
      const res = await authApi.register({
        username: registerName,
        email: registerEmail,
        password: registerPassword,
        tenantId: selectedTenant?.id,
      })
      if (res.success && res.data?.accessToken) {
        localStorage.setItem('nexus_token', res.data.accessToken)
        const user = { id: '', username: registerEmail, email: registerEmail, fullName: registerName, role: res.data.role as UserRole, permissions: res.data.permissions ?? [], securityGroups: res.data.securityGroups ?? [] }
        localStorage.setItem('nexus_user', JSON.stringify(user))
        window.location.href = '/api/v1/'
      } else {
        setError(res.error || 'Registration failed')
      }
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        setError(axiosErr.response?.data?.message || 'Registration failed')
      } else {
        setError('An unexpected error occurred')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen flex bg-slate-900">
      <div className="hidden lg:flex lg:w-[45%] relative overflow-hidden bg-gradient-to-br from-primary-700 via-primary-600 to-primary-800">
        <div className="absolute inset-0 opacity-10">
          <div className="absolute top-10 left-10 w-72 h-72 bg-white rounded-full blur-3xl" />
          <div className="absolute bottom-20 right-10 w-96 h-96 bg-white rounded-full blur-3xl" />
          <div className="absolute top-1/3 right-1/4 w-48 h-48 bg-white rounded-full blur-2xl" />
        </div>
        <div className="absolute inset-0" style={{
          backgroundImage: `radial-gradient(circle at 25% 50%, rgba(255,255,255,0.05) 0%, transparent 50%),
            radial-gradient(circle at 75% 30%, rgba(255,255,255,0.03) 0%, transparent 50%),
            linear-gradient(135deg, transparent 0%, rgba(255,255,255,0.02) 100%)`,
        }} />
        <div className="absolute inset-0" style={{
          backgroundImage: `url("data:image/svg+xml,%3Csvg width='60' height='60' xmlns='http://www.w3.org/2000/svg'%3E%3Cdefs%3E%3Cpattern id='g' width='20' height='20' patternUnits='userSpaceOnUse'%3E%3Ccircle cx='2' cy='2' r='1' fill='rgba(255,255,255,0.08)'/%3E%3C/pattern%3E%3C/defs%3E%3Crect width='100%25' height='100%25' fill='url(%23g)'/%3E%3C/svg%3E")`,
          opacity: 0.5,
        }} />
        <div className="relative z-10 flex flex-col justify-between p-16 w-full">
          <div className="flex items-center gap-3">
            <div className="flex items-center justify-center w-12 h-12 bg-white/20 backdrop-blur-sm rounded-xl">
              <Ship className="w-7 h-7 text-white" />
            </div>
            <span className="text-2xl font-bold text-white tracking-tight">NexusShip</span>
          </div>

          <div className="space-y-6">
            <h2 className="text-4xl font-bold text-white leading-tight">
              Supply Chain<br />
              <span className="text-primary-200">Command Center</span>
            </h2>
            <p className="text-lg text-primary-100 leading-relaxed max-w-md">
              Unify orders, inventory, warehouse operations, and carrier management
              across your entire enterprise in one intelligent platform.
            </p>
            <div className="grid grid-cols-2 gap-4 pt-4">
              <div className="bg-white/10 backdrop-blur-sm rounded-xl p-4">
                <div className="text-3xl font-bold text-white">50K+</div>
                <div className="text-sm text-primary-200 mt-1">Orders Daily</div>
              </div>
              <div className="bg-white/10 backdrop-blur-sm rounded-xl p-4">
                <div className="text-3xl font-bold text-white">99.9%</div>
                <div className="text-sm text-primary-200 mt-1">Uptime SLA</div>
              </div>
              <div className="bg-white/10 backdrop-blur-sm rounded-xl p-4">
                <div className="text-3xl font-bold text-white">150+</div>
                <div className="text-sm text-primary-200 mt-1">Integrations</div>
              </div>
              <div className="bg-white/10 backdrop-blur-sm rounded-xl p-4">
                <div className="text-3xl font-bold text-white">40%</div>
                <div className="text-sm text-primary-200 mt-1">Cost Reduction</div>
              </div>
            </div>
          </div>

          <div className="flex items-center gap-4 text-primary-200 text-sm">
            <a href="#" className="hover:text-white transition-colors">Documentation</a>
            <div className="w-1 h-1 bg-primary-300 rounded-full" />
            <a href="#" className="hover:text-white transition-colors">API Status</a>
            <div className="w-1 h-1 bg-primary-300 rounded-full" />
            <a href="#" className="hover:text-white transition-colors">Support</a>
            <div className="flex-1 text-right text-primary-300">
              &copy; 2026 Nexus Technologies
            </div>
          </div>
        </div>
      </div>

      <div className="flex-1 flex items-center justify-center p-6 lg:p-12">
        <div className="w-full max-w-md">
          <div className="lg:hidden flex items-center justify-center gap-3 mb-8">
            <div className="flex items-center justify-center w-10 h-10 bg-primary-600 rounded-xl">
              <Ship className="w-6 h-6 text-white" />
            </div>
            <span className="text-xl font-bold text-white">NexusShip</span>
          </div>

          {step === 'tenant' && (
            <div className="bg-white rounded-2xl shadow-2xl p-8">
              <div className="flex items-center gap-3 mb-6">
                <Building2 className="w-6 h-6 text-primary-600" />
                <h2 className="text-xl font-semibold text-gray-900">Select Organization</h2>
              </div>
              <p className="text-sm text-gray-500 mb-4">
                Choose your organization to continue
              </p>
              <Autocomplete
                value={tenantSearch}
                onChange={setTenantSearch}
                onSelect={(t: TenantInfo) => handleTenantSelect(t)}
                suggestions={tenants}
                getOptionLabel={(t: TenantInfo) => t.name}
                getOptionValue={(t: TenantInfo) => t.id}
                placeholder="Search organizations..."
                minChars={0}
                showSearchIcon={false}
                inputClassName="w-full px-4 py-3 border border-gray-300 rounded-xl text-sm focus:border-primary-400"
                className="mb-4"
              />
              <div className="mt-2 max-h-48 overflow-y-auto space-y-1 mb-4">
                {(tenantSearch ? tenants.filter(t => t.name.toLowerCase().includes(tenantSearch.toLowerCase())) : tenants).length === 0 ? (
                  <div className="px-4 py-6 text-center text-sm text-gray-400">No organizations found</div>
                ) : (
                  (tenantSearch ? tenants.filter(t => t.name.toLowerCase().includes(tenantSearch.toLowerCase())) : tenants).map(t => (
                    <button key={t.id}
                      onClick={() => handleTenantSelect(t)}
                      className={`w-full text-left px-4 py-3 text-sm transition-colors hover:bg-primary-50 rounded-lg flex items-center gap-3 ${
                        selectedTenant?.id === t.id ? 'bg-primary-50 text-primary-700 font-medium' : 'text-gray-700'
                      }`}
                    >
                      <div className="w-8 h-8 bg-gray-100 rounded-lg flex items-center justify-center flex-shrink-0">
                        <Building2 className="w-4 h-4 text-gray-500" />
                      </div>
                      <div>
                        <div className="font-medium">{t.name}</div>
                        {t.plan && <div className="text-xs text-gray-400 capitalize">{t.plan} plan</div>}
                      </div>
                    </button>
                  ))
                )}
              </div>
              <button
                onClick={() => selectedTenant && setStep('credentials')}
                disabled={!selectedTenant}
                className="btn-primary w-full py-2.5 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <ArrowRight className="w-4 h-4" /> Continue
              </button>
              <button
                onClick={() => setStep('credentials')}
                className="w-full text-center text-sm text-gray-400 hover:text-gray-600 mt-3 transition-colors"
              >
                Skip &mdash; continue without selecting
              </button>
            </div>
          )}

          {step === 'credentials' && (
            <div className="bg-white rounded-2xl shadow-2xl p-8">
              {selectedTenant && (
                <button
                  onClick={() => setStep('tenant')}
                  className="flex items-center gap-1 text-xs text-gray-400 hover:text-gray-600 mb-4 transition-colors"
                >
                  <ArrowLeft className="w-3 h-3" />
                  {selectedTenant.name}
                </button>
              )}

              <div className="flex items-center gap-3 mb-6">
                <Lock className="w-6 h-6 text-primary-600" />
                <div>
                  <h2 className="text-xl font-semibold text-gray-900">Sign in</h2>
                  {selectedTenant && (
                    <p className="text-sm text-gray-500">{selectedTenant.name}</p>
                  )}
                </div>
              </div>

              <form onSubmit={handleCredentialsLogin} className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Username</label>
                  <input
                    type="text"
                    value={username}
                    onChange={e => setUsername(e.target.value)}
                    className="input"
                    placeholder="Enter your username"
                    autoComplete="username"
                    autoFocus
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Password</label>
                  <div className="relative">
                    <input
                      type={showPassword ? 'text' : 'password'}
                      value={password}
                      onChange={e => setPassword(e.target.value)}
                      className="input pr-10"
                      placeholder="Enter your password"
                      autoComplete="current-password"
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword(!showPassword)}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                    >
                      {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>
                </div>

                <div className="flex items-center justify-between">
                  <label className="flex items-center gap-2 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={rememberMe}
                      onChange={e => setRememberMe(e.target.checked)}
                      className="w-4 h-4 text-primary-600 border-gray-300 rounded focus:ring-primary-500"
                    />
                    <span className="text-sm text-gray-600">Remember me</span>
                  </label>
                  <button
                    type="button"
                    onClick={() => setStep('forgot-password')}
                    className="text-sm text-primary-600 hover:text-primary-700 font-medium"
                  >
                    Forgot password?
                  </button>
                </div>

                {error && (
                  <div className="flex items-start gap-2.5 p-3 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg text-sm text-red-700 dark:text-red-400">
                    <AlertCircle className="w-4 h-4 mt-0.5 shrink-0" />
                    <span>{error}</span>
                  </div>
                )}

                <button
                  type="submit"
                  disabled={loading}
                  className="btn-primary w-full py-2.5"
                >
                  {loading ? (
                    <span className="flex items-center justify-center gap-2">
                      <Loader2 className="w-4 h-4 animate-spin" />
                      Signing in...
                    </span>
                  ) : (
                    <><LogIn className="w-4 h-4" /> Sign in</>
                  )}
                </button>
              </form>

              <div className="relative my-6">
                <div className="absolute inset-0 flex items-center">
                  <div className="w-full border-t border-gray-200" />
                </div>
                <div className="relative flex justify-center text-sm">
                  <span className="px-4 bg-white text-gray-400">Or continue with</span>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                {SSO_PROVIDERS.map(p => (
                  <button
                    key={p.id}
                    type="button"
                    onClick={() => handleSsoClick(p.id)}
                    disabled={loading}
                    className={`flex items-center justify-center gap-2 px-4 py-2.5 border border-gray-300 rounded-xl text-sm font-medium text-gray-700 bg-white ${p.color} transition-colors disabled:opacity-50 disabled:cursor-not-allowed`}
                  >
                    {p.id === 'google' && (
                      <svg className="w-4 h-4" viewBox="0 0 24 24"><path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 0 1-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z"/><path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/><path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/><path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/></svg>
                    )}
                    {p.id === 'microsoft' && (
                      <svg className="w-4 h-4" viewBox="0 0 24 24"><path fill="#F25022" d="M1 1h10.5v10.5H1z"/><path fill="#7FBA00" d="M12.5 1H23v10.5H12.5z"/><path fill="#00A4EF" d="M1 12.5h10.5V23H1z"/><path fill="#FFB900" d="M12.5 12.5H23V23H12.5z"/></svg>
                    )}
                    {p.id === 'okta' && (
                      <svg className="w-4 h-4" viewBox="0 0 24 24"><circle cx="12" cy="12" r="11" fill="#007DC1"/><path fill="#FFF" d="M12 6a6 6 0 1 1 0 12 6 6 0 0 1 0-12zm0 2.4a3.6 3.6 0 1 0 0 7.2 3.6 3.6 0 0 0 0-7.2z"/></svg>
                    )}
                    {p.id === 'auth0' && (
                      <svg className="w-4 h-4" viewBox="0 0 24 24"><path fill="#EB5424" d="M12 1L8.5 8.5 1 9.5l5.5 5-1.5 7.5L12 17.5l7 4.5-1.5-7.5L23 9.5l-7.5-1L12 1z"/></svg>
                    )}
                    {p.name}
                  </button>
                ))}
              </div>

              <div className="mt-4 text-center">
                <button
                  type="button"
                  onClick={() => { setStep('register'); setError('') }}
                  className="text-sm text-primary-600 hover:text-primary-700 font-medium"
                >
                  Don't have an account? Sign up
                </button>
              </div>

              <p className="mt-4 text-xs text-gray-400 text-center">
                NexusShip OMS v5.0 &middot; Enterprise Edition
              </p>
            </div>
          )}

          {step === 'register' && (
            <div className="bg-white rounded-2xl shadow-2xl p-8">
              {selectedTenant && (
                <button
                  onClick={() => setStep('tenant')}
                  className="flex items-center gap-1 text-xs text-gray-400 hover:text-gray-600 mb-4 transition-colors"
                >
                  <ArrowLeft className="w-3 h-3" />
                  {selectedTenant.name}
                </button>
              )}

              <div className="flex items-center gap-3 mb-6">
                <Users className="w-6 h-6 text-primary-600" />
                <div>
                  <h2 className="text-xl font-semibold text-gray-900">Create account</h2>
                  {selectedTenant && (
                    <p className="text-sm text-gray-500">{selectedTenant.name}</p>
                  )}
                </div>
              </div>

              <form onSubmit={handleRegister} className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Full name</label>
                  <input
                    type="text"
                    value={registerName}
                    onChange={e => setRegisterName(e.target.value)}
                    className="input"
                    placeholder="John Doe"
                    autoFocus
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Email</label>
                  <input
                    type="email"
                    value={registerEmail}
                    onChange={e => setRegisterEmail(e.target.value)}
                    className="input"
                    placeholder="you@company.com"
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Password</label>
                  <div className="relative">
                    <input
                      type={showPassword ? 'text' : 'password'}
                      value={registerPassword}
                      onChange={e => setRegisterPassword(e.target.value)}
                      className="input pr-10"
                      placeholder="Min. 6 characters"
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword(!showPassword)}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                    >
                      {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Confirm password</label>
                  <div className="relative">
                    <input
                      type={showPassword ? 'text' : 'password'}
                      value={registerConfirm}
                      onChange={e => setRegisterConfirm(e.target.value)}
                      className="input pr-10"
                      placeholder="Repeat password"
                    />
                  </div>
                </div>

                {error && (
                  <div className="flex items-start gap-2.5 p-3 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg text-sm text-red-700 dark:text-red-400">
                    <AlertCircle className="w-4 h-4 mt-0.5 shrink-0" />
                    <span>{error}</span>
                  </div>
                )}

                <button
                  type="submit"
                  disabled={loading}
                  className="btn-primary w-full py-2.5"
                >
                  {loading ? (
                    <span className="flex items-center justify-center gap-2">
                      <Loader2 className="w-4 h-4 animate-spin" />
                      Creating account...
                    </span>
                  ) : (
                    <><Users className="w-4 h-4" /> Create account</>
                  )}
                </button>
              </form>

              <button
                onClick={() => { setStep('credentials'); setError(''); setRegisterName(''); setRegisterEmail(''); setRegisterPassword(''); setRegisterConfirm('') }}
                className="w-full text-center text-sm text-gray-400 hover:text-gray-600 mt-4 transition-colors"
              >
                <ArrowLeft className="w-3 h-3 inline mr-1" />
                Already have an account? Sign in
              </button>
            </div>
          )}

          {step === 'mfa' && (
            <div className="bg-white rounded-2xl shadow-2xl p-8">
              <div className="flex items-center gap-3 mb-6">
                <Shield className="w-6 h-6 text-primary-600" />
                <div>
                  <h2 className="text-xl font-semibold text-gray-900">Two-Factor Authentication</h2>
                  <p className="text-sm text-gray-500">Enter the verification code from your authenticator app</p>
                </div>
              </div>

              <form onSubmit={handleMfaVerify} className="space-y-6">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-3 text-center">
                    Verification code
                  </label>
                  <div className="flex gap-2 justify-center" onPaste={handleTotpPaste}>
                    {totpCode.map((digit, i) => (
                      <input
                        key={i}
                        ref={el => { totpRefs.current[i] = el }}
                        type="text"
                        inputMode="numeric"
                        maxLength={1}
                        value={digit}
                        onChange={e => handleTotpChange(i, e.target.value)}
                        onKeyDown={e => handleTotpKeyDown(i, e)}
                        className="w-11 h-12 text-center text-lg font-semibold border border-gray-300 rounded-xl focus:border-primary-500 focus:ring-2 focus:ring-primary-200 outline-none transition-all"
                        autoFocus={i === 0}
                      />
                    ))}
                  </div>
                </div>

                {error && (
                  <div className="flex items-start gap-2.5 p-3 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg text-sm text-red-700 dark:text-red-400">
                    <AlertCircle className="w-4 h-4 mt-0.5 shrink-0" />
                    <span>{error}</span>
                  </div>
                )}

                <button
                  type="submit"
                  disabled={loading || totpCode.join('').length !== 6}
                  className="btn-primary w-full py-2.5 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {loading ? (
                    <span className="flex items-center justify-center gap-2">
                      <Loader2 className="w-4 h-4 animate-spin" />
                      Verifying...
                    </span>
                  ) : (
                    'Verify & Sign In'
                  )}
                </button>

                <button
                  type="button"
                  onClick={resetToCredentials}
                  className="w-full text-center text-sm text-gray-400 hover:text-gray-600 transition-colors"
                >
                  <ArrowLeft className="w-3 h-3 inline mr-1" />
                  Back to sign in
                </button>
              </form>
            </div>
          )}

          {(step === 'forgot-password' || step === 'forgot-sent') && (
            <div className="bg-white rounded-2xl shadow-2xl p-8">
              {step === 'forgot-password' && (
                <>
                  <div className="flex items-center gap-3 mb-6">
                    <Mail className="w-6 h-6 text-primary-600" />
                    <div>
                      <h2 className="text-xl font-semibold text-gray-900">Reset password</h2>
                      <p className="text-sm text-gray-500">
                        Enter your email address and we'll send you a reset link
                      </p>
                    </div>
                  </div>

                  <form onSubmit={handleForgotPassword} className="space-y-4">
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">
                        Email address
                      </label>
                      <input
                        type="email"
                        value={forgotEmail}
                        onChange={e => setForgotEmail(e.target.value)}
                        className="input"
                        placeholder="you@company.com"
                        autoFocus
                      />
                    </div>

                    {error && (
                      <div className="flex items-start gap-2.5 p-3 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg text-sm text-red-700 dark:text-red-400">
                        <AlertCircle className="w-4 h-4 mt-0.5 shrink-0" />
                        <span>{error}</span>
                      </div>
                    )}

                    <button
                      type="submit"
                      disabled={forgotLoading}
                      className="btn-primary w-full py-2.5"
                    >
                      {forgotLoading ? (
                        <span className="flex items-center justify-center gap-2">
                          <Loader2 className="w-4 h-4 animate-spin" />
                          Sending...
                        </span>
                      ) : (
                        <><Mail className="w-4 h-4" /> Send Reset Link</>
                      )}
                    </button>

                    <button
                      type="button"
                      onClick={resetToCredentials}
                      className="w-full text-center text-sm text-gray-400 hover:text-gray-600 transition-colors"
                    >
                      <ArrowLeft className="w-3 h-3 inline mr-1" />
                      Back to sign in
                    </button>
                  </form>
                </>
              )}

              {step === 'forgot-sent' && (
                <div className="text-center py-4">
                  <div className="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-4">
                    <CheckCircle2 className="w-8 h-8 text-green-600" />
                  </div>
                  <h2 className="text-xl font-semibold text-gray-900 mb-2">Check your email</h2>
                  <p className="text-sm text-gray-500 mb-6">
                    We've sent a password reset link to <strong className="text-gray-700">{forgotEmail}</strong>
                  </p>
                  <div className="p-4 bg-blue-50 rounded-xl text-xs text-blue-700 text-left mb-6">
                    <strong className="block mb-1">Didn't receive the email?</strong>
                    Check your spam folder or try a different email address. The link expires in 1 hour.
                  </div>
                  <button
                    onClick={resetToCredentials}
                    className="text-sm text-primary-600 hover:text-primary-700 font-medium"
                  >
                    <ArrowLeft className="w-3 h-3 inline mr-1" />
                    Back to sign in
                  </button>
                </div>
              )}
            </div>
          )}

          {!mfaRequired && step !== 'forgot-password' && step !== 'forgot-sent' && (
            <p className="mt-6 text-xs text-gray-500 text-center lg:hidden">
              NexusShip OMS v5.0 &middot; Enterprise Edition &middot; &copy; 2026
            </p>
          )}
        </div>
      </div>
    </div>
  )
}
