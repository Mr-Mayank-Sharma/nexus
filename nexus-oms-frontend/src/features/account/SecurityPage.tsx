import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import QRCode from 'qrcode'
import { CheckCircle2, Copy, KeyRound, Lock, ShieldAlert, ShieldCheck, Smartphone } from 'lucide-react'
import { useAuth } from '../../context/AuthContext'
import { activateMfa, beginMfaEnrollment, disableMfa, getMfaStatus, type MfaEnrollment } from '../../api/auth'

const card = 'rounded-2xl border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-6 shadow-sm'
const input = 'w-40 rounded-lg border border-[var(--border-default)] bg-[var(--surface-raised)] px-3 py-2 text-center font-mono text-lg tracking-[0.3em] text-[var(--text-primary)] outline-none focus:border-[var(--nexus-primary-500)] focus:ring-4 focus:ring-[var(--nexus-primary-100)]'
const primaryBtn = 'rounded-lg bg-[var(--nexus-primary-600)] px-4 py-2 text-sm font-semibold text-white transition hover:bg-[var(--nexus-primary-700)] disabled:opacity-50'
const ghostBtn = 'rounded-lg border border-[var(--border-default)] px-4 py-2 text-sm font-semibold text-[var(--text-secondary)] transition hover:bg-[var(--interactive-hover)] disabled:opacity-50'

function CodeInput({ value, onChange, onSubmit, autoFocus }: { value: string; onChange: (v: string) => void; onSubmit: () => void; autoFocus?: boolean }) {
  return (
    <input
      value={value}
      autoFocus={autoFocus}
      inputMode="numeric"
      autoComplete="one-time-code"
      aria-label="Authentication code"
      placeholder="000000"
      maxLength={11}
      onChange={e => onChange(e.target.value.replace(/[^0-9A-Za-z-]/g, '').toUpperCase())}
      onKeyDown={e => { if (e.key === 'Enter') onSubmit() }}
      className={input}
    />
  )
}

function RecoveryCodes({ codes, onDone }: { codes: string[]; onDone: () => void }) {
  const [copied, setCopied] = useState(false)
  const text = codes.join('\n')
  return (
    <div className="space-y-4">
      <div className="flex items-start gap-3 rounded-xl bg-[var(--nexus-warning-50)] p-4 text-sm text-[var(--nexus-warning-700)]">
        <ShieldAlert className="mt-0.5 h-5 w-5 shrink-0" />
        <p>Save these recovery codes somewhere safe, such as a password manager. Each works once if you lose your phone. They will not be shown again.</p>
      </div>
      <div className="grid grid-cols-2 gap-2 rounded-xl bg-[var(--surface-sunken)] p-4 font-mono text-sm text-[var(--text-primary)] sm:grid-cols-5">
        {codes.map(c => <span key={c}>{c}</span>)}
      </div>
      <div className="flex gap-2">
        <button className={ghostBtn} onClick={async () => { await navigator.clipboard.writeText(text); setCopied(true) }}>
          <span className="inline-flex items-center gap-2"><Copy className="h-4 w-4" />{copied ? 'Copied' : 'Copy codes'}</span>
        </button>
        <button className={primaryBtn} onClick={onDone}>I've saved them</button>
      </div>
    </div>
  )
}

export default function SecurityPage() {
  const { user } = useAuth()
  const qc = useQueryClient()
  const status = useQuery({ queryKey: ['mfa-status'], queryFn: getMfaStatus })
  const [enrollment, setEnrollment] = useState<MfaEnrollment | null>(null)
  const [qr, setQr] = useState<string | null>(null)
  const [code, setCode] = useState('')
  const [recovery, setRecovery] = useState<string[] | null>(null)
  const [disabling, setDisabling] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!enrollment) { setQr(null); return }
    QRCode.toDataURL(enrollment.otpauthUri, { margin: 1, width: 192 }).then(setQr).catch(() => setQr(null))
  }, [enrollment])

  const refresh = () => qc.invalidateQueries({ queryKey: ['mfa-status'] })
  const begin = useMutation({
    mutationFn: beginMfaEnrollment,
    onSuccess: e => { setEnrollment(e); setCode(''); setError(null) },
    onError: (e: Error) => setError(e.message),
  })
  const activate = useMutation({
    mutationFn: () => activateMfa(code),
    onSuccess: codes => { setRecovery(codes); setEnrollment(null); setCode(''); setError(null); refresh() },
    onError: (e: Error) => setError(e.message),
  })
  const disable = useMutation({
    mutationFn: () => disableMfa(code),
    onSuccess: () => { setDisabling(false); setCode(''); setError(null); refresh() },
    onError: (e: Error) => setError(e.message),
  })

  const s = status.data

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <header>
        <h1 className="text-2xl font-semibold tracking-tight text-[var(--text-primary)]">Account &amp; security</h1>
        <p className="mt-1 text-sm text-[var(--text-tertiary)]">Your sign-in details and two-step verification.</p>
      </header>

      <section className={card}>
        <div className="flex items-center gap-4">
          <div className="grid h-12 w-12 place-items-center rounded-full bg-[var(--nexus-primary-600)] text-lg font-semibold text-white">
            {(user?.fullName || user?.username || '?').charAt(0).toUpperCase()}
          </div>
          <div className="min-w-0">
            <div className="truncate text-base font-semibold text-[var(--text-primary)]">{user?.fullName || user?.username}</div>
            <div className="truncate text-sm text-[var(--text-tertiary)]">{user?.email || 'No e-mail on file'} · {user?.role}</div>
          </div>
        </div>
      </section>

      <section className={card} aria-labelledby="mfa-heading">
        <div className="mb-4 flex items-start justify-between gap-4">
          <div className="flex items-start gap-3">
            <div className={`grid h-10 w-10 place-items-center rounded-xl ${s?.enabled ? 'bg-[var(--nexus-success-50)] text-[var(--nexus-success-600)]' : 'bg-[var(--surface-sunken)] text-[var(--text-tertiary)]'}`}>
              {s?.enabled ? <ShieldCheck className="h-5 w-5" /> : <Lock className="h-5 w-5" />}
            </div>
            <div>
              <h2 id="mfa-heading" className="text-base font-semibold text-[var(--text-primary)]">Two-step verification</h2>
              <p className="text-sm text-[var(--text-tertiary)]">A 6-digit code from an authenticator app (Google Authenticator, Microsoft Authenticator, 1Password, Authy) at every sign-in.</p>
            </div>
          </div>
          {s && (
            <span className={`shrink-0 rounded-full px-2.5 py-1 text-xs font-semibold ${s.enabled ? 'bg-[var(--nexus-success-50)] text-[var(--nexus-success-700)]' : 'bg-[var(--surface-sunken)] text-[var(--text-secondary)]'}`}>
              {s.enabled ? 'On' : 'Off'}
            </span>
          )}
        </div>

        {status.isLoading && <div className="h-10 animate-pulse rounded-lg bg-[var(--surface-sunken)]" />}
        {status.isError && <p className="text-sm text-[var(--nexus-error-600)]">Couldn't load your security settings. {(status.error as Error).message}</p>}

        {s?.requiredByTenant && !s.enabled && !enrollment && !recovery && (
          <div className="mb-4 flex items-start gap-3 rounded-xl bg-[var(--nexus-warning-50)] p-4 text-sm text-[var(--nexus-warning-700)]">
            <ShieldAlert className="mt-0.5 h-5 w-5 shrink-0" />
            <p>Your organisation requires two-step verification. Set it up now to keep access.</p>
          </div>
        )}

        {recovery && <RecoveryCodes codes={recovery} onDone={() => setRecovery(null)} />}

        {s && !s.enabled && !enrollment && !recovery && (
          <button className={primaryBtn} onClick={() => begin.mutate()} disabled={begin.isPending}>
            <span className="inline-flex items-center gap-2"><Smartphone className="h-4 w-4" />{begin.isPending ? 'Preparing…' : 'Set up authenticator app'}</span>
          </button>
        )}

        {enrollment && (
          <div className="grid gap-6 sm:grid-cols-[auto_1fr]">
            <div className="grid h-[200px] w-[200px] place-items-center rounded-xl border border-[var(--border-subtle)] bg-white p-1">
              {qr ? <img src={qr} alt="QR code for your authenticator app" width={192} height={192} /> : <span className="text-xs text-slate-400">Generating…</span>}
            </div>
            <div className="space-y-4">
              <ol className="list-decimal space-y-1 pl-5 text-sm text-[var(--text-secondary)]">
                <li>Open your authenticator app and scan the code.</li>
                <li>Enter the 6-digit code it shows to finish.</li>
              </ol>
              <details className="text-sm text-[var(--text-tertiary)]">
                <summary className="cursor-pointer select-none">Can't scan? Enter this key manually</summary>
                <code className="mt-2 block break-all rounded-lg bg-[var(--surface-sunken)] px-3 py-2 font-mono text-[var(--text-primary)]">{enrollment.secret}</code>
              </details>
              <div className="flex flex-wrap items-center gap-3">
                <CodeInput value={code} onChange={setCode} onSubmit={() => code.length >= 6 && activate.mutate()} autoFocus />
                <button className={primaryBtn} onClick={() => activate.mutate()} disabled={code.length < 6 || activate.isPending}>
                  {activate.isPending ? 'Verifying…' : 'Turn on'}
                </button>
                <button className={ghostBtn} onClick={() => { setEnrollment(null); setError(null) }}>Cancel</button>
              </div>
            </div>
          </div>
        )}

        {s?.enabled && !recovery && (
          <div className="space-y-4">
            <div className="flex items-center gap-2 text-sm text-[var(--text-secondary)]">
              <CheckCircle2 className="h-4 w-4 text-[var(--nexus-success-600)]" />
              Protected. <KeyRound className="ml-2 h-4 w-4" /> {s.recoveryCodesRemaining} recovery {s.recoveryCodesRemaining === 1 ? 'code' : 'codes'} left.
            </div>
            {!disabling ? (
              <button className={ghostBtn} onClick={() => { setDisabling(true); setCode(''); setError(null) }}>Turn off two-step verification</button>
            ) : (
              <div className="flex flex-wrap items-center gap-3">
                <span className="text-sm text-[var(--text-secondary)]">Enter a current code or a recovery code:</span>
                <CodeInput value={code} onChange={setCode} onSubmit={() => code.length >= 6 && disable.mutate()} autoFocus />
                <button className="rounded-lg bg-[var(--nexus-error-600)] px-4 py-2 text-sm font-semibold text-white hover:bg-[var(--nexus-error-700)] disabled:opacity-50"
                        onClick={() => disable.mutate()} disabled={code.length < 6 || disable.isPending}>
                  {disable.isPending ? 'Turning off…' : 'Turn off'}
                </button>
                <button className={ghostBtn} onClick={() => setDisabling(false)}>Cancel</button>
              </div>
            )}
          </div>
        )}

        {error && <p role="alert" className="mt-4 text-sm text-[var(--nexus-error-600)]">{error}</p>}
      </section>
    </div>
  )
}
