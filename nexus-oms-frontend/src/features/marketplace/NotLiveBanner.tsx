import { AlertTriangle } from 'lucide-react'

/** Says plainly what this marketplace page can and cannot do, so it never implies a live connection. */
export default function NotLiveBanner({ marketplace, credentialsSaved }: { marketplace: string; credentialsSaved: boolean }) {
  return (
    <div role="status" className="flex items-start gap-3 rounded-xl bg-[var(--nexus-warning-50)] p-4 text-sm text-[var(--nexus-warning-700)]">
      <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0" />
      <div className="space-y-1">
        <p className="font-semibold">Live {marketplace} sync is not available yet</p>
        <p>
          {credentialsSaved
            ? 'Your credentials are saved securely but have not been checked against ' + marketplace + '.'
            : 'No credentials are saved.'}{' '}
          The orders below are orders already in Nexus with the {marketplace} channel; nothing is fetched from {marketplace}.
        </p>
      </div>
    </div>
  )
}
