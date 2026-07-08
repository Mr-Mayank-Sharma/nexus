import { useState, useEffect, useCallback } from 'react'
import {
  Brain, X, CheckCircle, XCircle, ChevronDown, ChevronUp,
  Sparkles, AlertTriangle,
} from 'lucide-react'
import clsx from 'clsx'
import { getRecommendations, approveRecommendation, rejectRecommendation } from '../api/aiAgents'
import type { AiRecommendation } from '../api/aiAgents'

interface Props {
  context?: string
  orderId?: string
  role?: string
  className?: string
}

const PRIORITY_STYLES: Record<string, string> = {
  high: 'bg-red-100 text-red-700 dark:bg-red-900/20 dark:text-red-300 border-red-200 dark:border-red-800',
  medium: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/20 dark:text-yellow-300 border-yellow-200 dark:border-yellow-800',
  low: 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400 border-gray-200 dark:border-gray-600',
}

export default function AiRecommendationPanel({ context, orderId, role, className }: Props) {
  const [open, setOpen] = useState(false)
  const [recommendations, setRecommendations] = useState<AiRecommendation[]>([])
  const [loading, setLoading] = useState(false)
  const [handling, setHandling] = useState<Set<string>>(new Set())

  const fetchRecs = useCallback(async () => {
    setLoading(true)
    try {
      const recs = await getRecommendations(role, 5)
      setRecommendations(recs)
    } catch {
      // silent
    } finally {
      setLoading(false)
    }
  }, [role])

  useEffect(() => {
    if (open) fetchRecs()
  }, [open, fetchRecs])

  const handleApprove = async (id: string) => {
    setHandling(prev => new Set(prev).add(id))
    try {
      await approveRecommendation(id)
      setRecommendations(prev => prev.map(r => r.id === id ? { ...r, status: 'approved' } : r))
    } catch { /* ignore */ }
    finally { setHandling(prev => { const n = new Set(prev); n.delete(id); return n }) }
  }

  const handleReject = async (id: string) => {
    setHandling(prev => new Set(prev).add(id))
    try {
      await rejectRecommendation(id)
      setRecommendations(prev => prev.map(r => r.id === id ? { ...r, status: 'rejected' } : r))
    } catch { /* ignore */ }
    finally { setHandling(prev => { const n = new Set(prev); n.delete(id); return n }) }
  }

  const pendingCount = recommendations.filter(r => r.status === 'pending').length
  const isLoading = loading && recommendations.length === 0

  return (
    <div className={clsx('fixed bottom-4 right-4 z-40', className)}>
      {!open ? (
        <button
          onClick={() => setOpen(true)}
          className="flex items-center gap-2 px-4 py-3 bg-gradient-to-r from-purple-600 to-indigo-600 text-white rounded-2xl shadow-lg hover:shadow-xl hover:from-purple-700 hover:to-indigo-700 transition-all"
        >
          <Sparkles className="w-5 h-5" />
          <span className="text-sm font-semibold">AI Suggestions</span>
          {pendingCount > 0 && (
            <span className="bg-white/20 text-white text-xs font-bold px-1.5 py-0.5 rounded-full min-w-[20px] text-center">
              {pendingCount}
            </span>
          )}
        </button>
      ) : (
        <div className="w-80 bg-white dark:bg-gray-800 rounded-2xl shadow-2xl border border-gray-200 dark:border-gray-700 overflow-hidden">
          <div className="flex items-center justify-between px-4 py-3 bg-gradient-to-r from-purple-600 to-indigo-600 text-white">
            <div className="flex items-center gap-2">
              <Brain className="w-4 h-4" />
              <span className="text-sm font-semibold">AI Recommendations</span>
            </div>
            <button onClick={() => setOpen(false)} className="p-1 rounded-lg hover:bg-white/20 transition-colors">
              <X className="w-4 h-4" />
            </button>
          </div>
          <div className="max-h-96 overflow-y-auto p-3 space-y-2">
            {isLoading ? (
              <div className="text-center py-6 text-gray-400 text-sm">
                <div className="w-6 h-6 border-2 border-purple-500 border-t-transparent rounded-full animate-spin mx-auto mb-2" />
                Loading suggestions...
              </div>
            ) : recommendations.length === 0 ? (
              <div className="text-center py-6 text-gray-400 text-sm">
                <Brain className="w-8 h-8 mx-auto mb-2 text-gray-300" />
                No suggestions right now
              </div>
            ) : (
              recommendations.map(rec => (
                <div key={rec.id} className="p-3 rounded-xl border border-gray-100 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/50">
                  <div className="flex items-center gap-1.5 mb-1 flex-wrap">
                    <span className={clsx('text-[9px] font-semibold uppercase px-1 py-0.5 rounded-full border', PRIORITY_STYLES[rec.impact])}>
                      {rec.impact}
                    </span>
                    {rec.status === 'approved' && <span className="text-[9px] text-green-600 bg-green-50 dark:bg-green-900/20 px-1 py-0.5 rounded-full">✓ Approved</span>}
                    {rec.status === 'rejected' && <span className="text-[9px] text-red-600 bg-red-50 dark:bg-red-900/20 px-1 py-0.5 rounded-full">✕ Rejected</span>}
                  </div>
                  <p className="text-xs font-semibold text-gray-900 dark:text-gray-100">{rec.title}</p>
                  <p className="text-[10px] text-gray-500 dark:text-gray-400 mt-0.5 line-clamp-2">{rec.description}</p>
                  <div className="mt-1.5 flex items-center gap-2">
                    <div className="flex-1 h-1 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
                      <div
                        className={clsx('h-full rounded-full', rec.confidence >= 90 ? 'bg-green-500' : rec.confidence >= 80 ? 'bg-amber-500' : 'bg-red-500')}
                        style={{ width: `${rec.confidence}%` }}
                      />
                    </div>
                    <span className="text-[9px] font-medium text-gray-500">{rec.confidence}%</span>
                  </div>
                  {rec.status === 'pending' && (
                    <div className="flex items-center gap-1.5 mt-2">
                      <button
                        onClick={() => handleApprove(rec.id)}
                        disabled={handling.has(rec.id)}
                        className="flex items-center gap-1 text-[10px] font-medium px-2 py-1 rounded-lg bg-green-50 dark:bg-green-900/20 text-green-700 dark:text-green-300 border border-green-200 dark:border-green-800 hover:bg-green-100 disabled:opacity-50"
                      >
                        <CheckCircle className="w-3 h-3" /> Approve
                      </button>
                      <button
                        onClick={() => handleReject(rec.id)}
                        disabled={handling.has(rec.id)}
                        className="flex items-center gap-1 text-[10px] font-medium px-2 py-1 rounded-lg bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-300 border border-red-200 dark:border-red-800 hover:bg-red-100 disabled:opacity-50"
                      >
                        <XCircle className="w-3 h-3" /> Reject
                      </button>
                    </div>
                  )}
                </div>
              ))
            )}
          </div>
          <div className="px-3 py-2 border-t border-gray-100 dark:border-gray-700 text-center">
            <button
              onClick={fetchRecs}
              className="text-[10px] text-purple-600 dark:text-purple-400 hover:text-purple-700 font-medium"
            >
              Refresh suggestions
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
