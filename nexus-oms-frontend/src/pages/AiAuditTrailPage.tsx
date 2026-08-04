import { Shield, GitBranch } from 'lucide-react'
import { Link } from 'react-router-dom'

export default function AiAuditTrailPage() {
  return (
    <div className="space-y-6">
      <div className="enterprise-page-header">
        <div>
          <h1 className="flex items-center gap-2.5">
            <Shield className="w-7 h-7 text-[var(--nexus-primary-500)]" /> AI Audit Trail
          </h1>
          <p>Complete record of all AI decisions, overrides & approvals</p>
        </div>
      </div>

      <div className="enterprise-card p-12 text-center">
        <div className="w-16 h-16 mx-auto mb-4 rounded-2xl bg-[var(--surface-muted)] flex items-center justify-center">
          <Shield className="w-8 h-8 text-[var(--text-tertiary)]" />
        </div>
        <h2 className="text-lg font-semibold text-[var(--text-primary)]">Audit trail not yet available</h2>
        <p className="text-sm text-[var(--text-tertiary)] mt-2 max-w-md mx-auto">
          The AI decision log isn't persisted yet, so there are no records to show.
          AI decisions are currently surfaced in real time on the routing page.
        </p>
        <Link
          to="/ai-routing"
          className="enterprise-btn enterprise-btn-primary mt-6 inline-flex items-center gap-2"
        >
          <GitBranch className="w-4 h-4" /> Go to Order Routing
        </Link>
      </div>
    </div>
  )
}
