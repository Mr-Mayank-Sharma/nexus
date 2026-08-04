import { useState } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import {
  Brain, BarChart3, TrendingUp, Cpu, Database,
  RefreshCw, AlertCircle, Clock, Loader2,
} from 'lucide-react'
import { EnterpriseKPICard, EnterpriseBreadcrumbs, EnterpriseStatusBadge } from '../components/enterprise'
import { useToast } from '../hooks/useToast'
import Autocomplete from '../components/common/Autocomplete'
import * as aiApi from '../api/ai'
import * as aiPlatformApi from '../api/aiPlatform'
import PermissionGate from '../components/rbac/PermissionGate'

const modelIcons: Record<string, React.ReactNode> = {
  DEMAND_FORECAST: <TrendingUp className="w-5 h-5" />,
  ALLOCATION: <BarChart3 className="w-5 h-5" />,
  CARRIER_SELECTION: <Cpu className="w-5 h-5" />,
  ANOMALY_DETECTION: <AlertCircle className="w-5 h-5" />,
  RETURNS_PREDICTION: <RefreshCw className="w-5 h-5" />,
  INVENTORY_OPTIMIZATION: <Database className="w-5 h-5" />,
}

function statusColor(status?: string): 'success' | 'info' | 'error' | 'primary' {
  const s = (status || '').toUpperCase()
  if (s === 'ACTIVE' || s === 'DEPLOYED') return 'success'
  if (s === 'TRAINING' || s === 'DRAFT' || s === 'PENDING') return 'info'
  if (s === 'ERROR' || s === 'FAILED') return 'error'
  return 'primary'
}

export default function AiPage() {
  const [selectedModel, setSelectedModel] = useState<any | null>(null)
  const [testInput, setTestInput] = useState('')
  const [testResult, setTestResult] = useState<any>(null)
  const { addToast } = useToast()

  const {
    data: models,
    isLoading,
    isError,
    refetch,
  } = useQuery({
    queryKey: ['ai-models'],
    queryFn: async () => {
      const res = await aiApi.getModelInfo()
      const d = res.data
      return Array.isArray(d) ? d : (d?.content ?? [])
    },
    retry: 1,
    staleTime: 30000,
  })

  const predictMutation = useMutation({
    mutationFn: async (payload: { type: string; input: Record<string, any> }) => {
      switch (payload.type) {
        case 'DEMAND_FORECAST':
          return aiApi.predictDemand(payload.input)
        case 'CARRIER_SELECTION':
          return aiApi.predictCarrier(payload.input)
        case 'INVENTORY_OPTIMIZATION':
          return aiApi.predictInventory(payload.input)
        default:
          return aiApi.predictCarrier(payload.input)
      }
    },
    onSuccess: (res) => {
      setTestResult(res.data)
      addToast({ type: 'success', title: 'Prediction completed' })
    },
    onError: () => {
      setTestResult({ error: 'AI service unavailable' })
      addToast({ type: 'error', title: 'AI service unavailable' })
    },
  })

  function handleRunTest() {
    if (!selectedModel) return
    let input: Record<string, any> = {}
    try { input = testInput ? JSON.parse(testInput) : { sample: true } } catch { input = { raw: testInput } }
    predictMutation.mutate({ type: selectedModel.modelType || selectedModel.type, input })
  }

  if (isError && !models) {
    return (
      <div className="space-y-6">
        <EnterpriseBreadcrumbs crumbs={[{ label: 'Home', path: '/' }, { label: 'AI Models' }]} />
        <div className="enterprise-card p-12 text-center">
          <AlertCircle className="w-14 h-14 mx-auto text-[var(--color-error)] mb-4" />
          <h2 className="text-xl font-semibold text-[var(--text-primary)] mb-2">Failed to load AI models</h2>
          <p className="text-sm text-[var(--text-secondary)] mb-6 max-w-md mx-auto">
            Could not connect to the AI service. Check your connection and try again.
          </p>
          <button type="button" onClick={() => refetch()} className="enterprise-btn enterprise-btn-primary inline-flex items-center gap-2">
            <RefreshCw className="w-4 h-4" />
            Retry
          </button>
        </div>
      </div>
    )
  }

  if (isLoading) {
    return (
      <div className="space-y-6">
        <EnterpriseBreadcrumbs crumbs={[{ label: 'Home', path: '/' }, { label: 'AI Models' }]} />
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <EnterpriseKPICard key={i} title="" value="" loading color="primary" />
          ))}
        </div>
        <div className="enterprise-card p-6">
          <div className="enterprise-skeleton h-5 w-44 mb-4" />
          <div className="enterprise-skeleton h-5 w-64 mb-8" />
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
            {Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="enterprise-skeleton h-20 rounded-lg" />
            ))}
          </div>
          <div className="enterprise-skeleton" style={{ height: 120 }} />
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <EnterpriseBreadcrumbs crumbs={[{ label: 'Home', path: '/' }, { label: 'AI Models' }]} />

      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5"><Brain className="w-5 h-5" />AI Models</h1>
          <p className="text-sm text-[var(--text-secondary)] mt-1">
            Machine learning model health and performance
          </p>
        </div>
        <button
          className="enterprise-btn enterprise-btn-secondary enterprise-btn-sm"
          onClick={() => {
            refetch()
            addToast({ type: 'info', title: 'Refreshing model data' })
          }}
        >
          <RefreshCw className="w-4 h-4" /> Refresh
        </button>
      </div>

      {(!models || models.length === 0) ? (
        <div className="enterprise-card p-12 text-center">
          <Brain className="w-12 h-12 mx-auto mb-3 text-[var(--text-tertiary)]" />
          <p className="font-medium text-[var(--text-secondary)]">No AI models registered yet</p>
          <p className="text-sm text-[var(--text-tertiary)] mt-1">Registered models will appear here.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4">
          {models.map((model: any) => (
            <EnterpriseKPICard
              key={model.id}
              title={model.displayName || model.name}
              value={(model.status || 'UNKNOWN').replace('_', ' ')}
              subtitle={`v${model.currentVersion || model.version || '—'} · ${(model.modelType || model.type || '—').replace('_', ' ')}`}
              icon={modelIcons[model.modelType || model.type] || <Brain className="w-5 h-5" />}
              color={statusColor(model.status)}
              onClick={() => setSelectedModel(model)}
            />
          ))}
        </div>
      )}

      {selectedModel && (
        <div className="enterprise-card animate-fade-in">
          <div className="card-header flex items-center justify-between p-5 border-b border-[var(--border-color)]">
            <div>
              <h3 className="text-sm font-semibold text-[var(--text-primary)]">{selectedModel.displayName || selectedModel.name} — Details</h3>
              <p className="text-xs text-[var(--text-secondary)]">
                v{selectedModel.currentVersion || selectedModel.version || '—'} · {selectedModel.category || (selectedModel.modelType || '—')}
              </p>
            </div>
            <PermissionGate resource="settings" action="create">
              <button
                onClick={async () => {
                  addToast({ type: 'info', title: 'Initiating retrain...' });
                  try {
                    await aiPlatformApi.createTrainingJob(selectedModel.id, {});
                    addToast({ type: 'success', title: 'Retrain job created' });
                  } catch {
                    addToast({ type: 'error', title: 'Failed to initiate retrain' });
                  }
                }}
                className="enterprise-btn enterprise-btn-secondary enterprise-btn-sm"
              >
                <RefreshCw className="w-3 h-3" /> Retrain
              </button>
            </PermissionGate>
          </div>
          <div className="p-5">
            {selectedModel.description && (
              <p className="text-sm text-[var(--text-secondary)] mb-6">{selectedModel.description}</p>
            )}

            <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
              {[
                { label: 'Status', value: (selectedModel.status || '—').replace('_', ' '), color: 'text-[var(--text-primary)]' },
                { label: 'Type', value: (selectedModel.modelType || selectedModel.type || '—').replace('_', ' '), color: 'text-[var(--text-primary)]' },
                { label: 'Version', value: selectedModel.currentVersion || selectedModel.version || '—', color: 'text-[var(--text-primary)]' },
                { label: 'Active', value: selectedModel.isActive == null ? '—' : selectedModel.isActive ? 'Yes' : 'No', color: 'text-[var(--text-primary)]' },
              ].map((metric) => (
                <div key={metric.label} className="p-3 bg-[var(--bg-tertiary)] rounded-lg text-center">
                  <p className="text-xs text-[var(--text-tertiary)] mb-1">{metric.label}</p>
                  <p className={`text-lg font-bold ${metric.color}`}>{metric.value}</p>
                </div>
              ))}
            </div>

            <div className="mb-6">
              <h4 className="text-sm font-semibold text-[var(--text-primary)] mb-3">
                <Brain className="w-4 h-4 inline mr-1.5" />
                Test Prediction
              </h4>
              <div className="flex items-end gap-3">
                <div className="flex-1">
                  <label className="block text-xs text-[var(--text-secondary)] mb-1">Input Parameters (JSON)</label>
                  <Autocomplete
                    value={testInput}
                    onChange={setTestInput}
                    placeholder='{ "feature1": "value1", "feature2": 42 }'
                    minChars={0}
                    inputClassName="enterprise-input w-full min-h-[96px] font-mono text-xs resize-y leading-relaxed"
                  />
                </div>
                <PermissionGate resource="settings" action="create">
                  <button
                    className="enterprise-btn enterprise-btn-primary text-sm"
                    onClick={handleRunTest}
                    disabled={predictMutation.isPending}
                  >
                    {predictMutation.isPending ? (
                      <Loader2 className="w-4 h-4 animate-spin" />
                    ) : (
                      <Brain className="w-4 h-4" />
                    )}
                    {predictMutation.isPending ? 'Running...' : 'Run Test'}
                  </button>
                </PermissionGate>
              </div>
              {testResult && (
                <div className="mt-4 p-4 bg-[var(--surface-sunken)] rounded-lg border">
                  <h4 className="font-semibold text-sm mb-2">Prediction Result</h4>
                  {testResult.error ? (
                    <p className="text-sm text-[var(--nexus-error-600)]">{testResult.error}</p>
                  ) : (
                    <div className="space-y-2">
                      <div className="flex justify-between text-sm">
                        <span className="font-medium">{testResult.prediction ?? 'N/A'}</span>
                        <span className="text-[var(--text-secondary)]">Confidence: {Math.round((testResult.confidence ?? 0) * 100)}%</span>
                      </div>
                      <div className="w-full bg-[var(--surface-muted)] rounded-full h-2.5">
                        <div className="bg-[var(--nexus-ai-600)] h-2.5 rounded-full transition-all duration-500"
                             style={{ width: `${Math.round((testResult.confidence ?? 0) * 100)}%` }} />
                      </div>
                      <p className="text-sm text-[var(--text-secondary)] mt-1">{testResult.explanation}</p>
                      <div className="flex flex-wrap gap-1 mt-1">
                        <span className="text-xs text-[var(--text-tertiary)]">Model: {testResult.modelVersion ?? 'N/A'}</span>
                        {testResult.featuresUsed && (
                          <span className="text-xs text-[var(--text-tertiary)]">• Features: {testResult.featuresUsed.join(', ')}</span>
                        )}
                      </div>
                    </div>
                  )}
                </div>
              )}
            </div>

            {(selectedModel.createdAt || selectedModel.updatedAt) && (
              <div className="flex items-center gap-3 p-3 bg-[var(--bg-tertiary)] rounded-lg text-sm">
                <Clock className="w-4 h-4 text-[var(--text-tertiary)]" />
                <span className="text-[var(--text-secondary)]">
                  Created {selectedModel.createdAt ? new Date(selectedModel.createdAt).toLocaleDateString() : '—'}
                  {selectedModel.updatedAt ? ` · Updated ${new Date(selectedModel.updatedAt).toLocaleDateString()}` : ''}
                </span>
              </div>
            )}

            {selectedModel.trainingHistory && selectedModel.trainingHistory.length > 0 && (
              <div className="mt-6">
                <h4 className="text-sm font-semibold text-[var(--text-primary)] mb-3">
                  <Clock className="w-4 h-4 inline mr-1.5" />
                  Training History
                </h4>
                <div className="space-y-2">
                  {selectedModel.trainingHistory.slice(0, 5).map((run: any) => (
                    <div key={run.id} className="flex items-center justify-between p-3 bg-[var(--bg-tertiary)] rounded-lg text-sm">
                      <div className="flex items-center gap-3">
                        <Clock className="w-4 h-4 text-[var(--text-tertiary)]" />
                        <span className="text-[var(--text-secondary)]">
                          {new Date(run.startedAt || run.createdAt).toLocaleDateString()}
                        </span>
                      </div>
                      <div className="flex items-center gap-4">
                        <span className="text-[var(--text-tertiary)] text-xs">
                          {run.datasetSize != null ? `${run.datasetSize.toLocaleString()} records` : run.epochs != null ? `${run.epochs} epochs` : '—'}
                        </span>
                        <span className="font-medium text-[var(--text-primary)]">
                          {run.accuracy != null ? `${run.accuracy.toFixed(1)}%` : '—'}
                        </span>
                        <EnterpriseStatusBadge status={run.status === 'COMPLETED' ? 'success' : run.status === 'RUNNING' ? 'info' : 'error'} size="sm" />
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
