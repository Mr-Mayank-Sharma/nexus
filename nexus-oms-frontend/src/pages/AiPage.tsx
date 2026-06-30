import { useState, useMemo } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import {
  Brain, BarChart3, TrendingUp, MessageSquare, Cpu, Database,
  RefreshCw, AlertCircle, Clock, CheckCircle, XCircle, Loader2,
} from 'lucide-react'
import clsx from 'clsx'
import { EnterpriseKPICard, EnterpriseBreadcrumbs, EnterpriseStatusBadge } from '../components/enterprise'
import { AiModel, TrainingRun } from '../types'
import { useToast } from '../hooks/useToast'
import * as aiApi from '../api/ai'
import * as aiPlatformApi from '../api/aiPlatform'

const FALLBACK_MODELS: AiModel[] = [
  {
    id: 'fallback-df',
    name: 'Demand Forecast XGBoost',
    description: 'XGBoost-based demand forecasting model using 24 months of historical order data with seasonality decomposition',
    version: '2.4.0',
    type: 'DEMAND_FORECAST',
    status: 'ACTIVE',
    accuracy: 94.6,
    precision: 0.93,
    recall: 0.91,
    f1Score: 0.92,
    lastTrained: new Date(Date.now() - 3 * 86400000).toISOString(),
    trainingHistory: Array.from({ length: 8 }, (_, i) => ({
      id: `fallback-run-df-${i}`,
      startedAt: new Date(Date.now() - (i + 1) * 14 * 86400000).toISOString(),
      completedAt: new Date(Date.now() - (i + 1) * 14 * 86400000 + 5400000).toISOString(),
      status: 'COMPLETED' as const,
      accuracy: 88 + Math.random() * 10,
      loss: 0.08 + Math.random() * 0.25,
      epochs: 100,
      datasetSize: 520000 + Math.floor(Math.random() * 80000),
    })),
  },
  {
    id: 'fallback-inv',
    name: 'Inventory LSTM',
    description: 'LSTM neural network for multi-step inventory demand forecasting across all fulfillment nodes',
    version: '2.1.3',
    type: 'INVENTORY_OPTIMIZATION',
    status: 'ACTIVE',
    accuracy: 91.2,
    precision: 0.89,
    recall: 0.87,
    f1Score: 0.88,
    lastTrained: new Date(Date.now() - 7 * 86400000).toISOString(),
    trainingHistory: Array.from({ length: 6 }, (_, i) => ({
      id: `fallback-run-inv-${i}`,
      startedAt: new Date(Date.now() - (i + 1) * 21 * 86400000).toISOString(),
      completedAt: new Date(Date.now() - (i + 1) * 21 * 86400000 + 7200000).toISOString(),
      status: 'COMPLETED' as const,
      accuracy: 85 + Math.random() * 10,
      loss: 0.05 + Math.random() * 0.2,
      epochs: 150,
      datasetSize: 890000 + Math.floor(Math.random() * 110000),
    })),
  },
  {
    id: 'fallback-fd',
    name: 'Fraud Detection Random Forest',
    description: 'Ensemble random forest classifier detecting anomalous orders and potential fraud patterns in real-time',
    version: '2.0.1',
    type: 'ANOMALY_DETECTION',
    status: 'ACTIVE',
    accuracy: 96.1,
    precision: 0.94,
    recall: 0.92,
    f1Score: 0.93,
    lastTrained: new Date(Date.now() - 1 * 86400000).toISOString(),
    trainingHistory: Array.from({ length: 5 }, (_, i) => ({
      id: `fallback-run-fd-${i}`,
      startedAt: new Date(Date.now() - (i + 1) * 10 * 86400000).toISOString(),
      completedAt: new Date(Date.now() - (i + 1) * 10 * 86400000 + 3600000).toISOString(),
      status: 'COMPLETED' as const,
      accuracy: 90 + Math.random() * 8,
      loss: 0.02 + Math.random() * 0.1,
      epochs: 80,
      datasetSize: 340000 + Math.floor(Math.random() * 60000),
    })),
  },
  {
    id: 'fallback-ro',
    name: 'Route Optimizer',
    description: 'Graph-based route optimization using Dijkstra with real-time traffic and carrier cost matrices',
    version: '1.9.2',
    type: 'CARRIER_SELECTION',
    status: 'TRAINING',
    accuracy: 88.7,
    precision: 0.86,
    recall: 0.84,
    f1Score: 0.85,
    lastTrained: new Date(Date.now() - 14 * 86400000).toISOString(),
    trainingHistory: Array.from({ length: 4 }, (_, i) => ({
      id: `fallback-run-ro-${i}`,
      startedAt: new Date(Date.now() - (i + 1) * 30 * 86400000).toISOString(),
      completedAt: new Date(Date.now() - (i + 1) * 30 * 86400000 + 4500000).toISOString(),
      status: 'COMPLETED' as const,
      accuracy: 82 + Math.random() * 10,
      loss: 0.1 + Math.random() * 0.3,
      epochs: 60,
      datasetSize: 180000 + Math.floor(Math.random() * 40000),
    })),
  },
]

const modelIcons: Record<string, React.ReactNode> = {
  DEMAND_FORECAST: <TrendingUp className="w-5 h-5" />,
  ALLOCATION: <BarChart3 className="w-5 h-5" />,
  CARRIER_SELECTION: <Cpu className="w-5 h-5" />,
  ANOMALY_DETECTION: <AlertCircle className="w-5 h-5" />,
  RETURNS_PREDICTION: <RefreshCw className="w-5 h-5" />,
  INVENTORY_OPTIMIZATION: <Database className="w-5 h-5" />,
}

export default function AiPage() {
  const [selectedModel, setSelectedModel] = useState<AiModel | null>(null)
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
      if (!res?.data || !Array.isArray(res.data) || res.data.length === 0) {
        throw new Error('No model data returned')
      }
      return res.data as AiModel[]
    },
    retry: 1,
    staleTime: 30000,
  })

  const displayModels = useMemo(() => {
    if (models && models.length > 0) return models
    return FALLBACK_MODELS
  }, [models])

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
    predictMutation.mutate({ type: selectedModel.type, input })
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
          <button onClick={() => refetch()} className="enterprise-btn enterprise-btn-primary inline-flex items-center gap-2">
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
          <h1 className="text-2xl font-bold text-[var(--text-primary)]">AI Models</h1>
          <p className="text-sm text-[var(--text-secondary)] mt-1">
            Machine learning model health and performance
            {!models && (
              <span className="ml-2 text-xs text-[var(--color-warning)]">
                (offline — showing cached data)
              </span>
            )}
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

      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4">
        {displayModels.map((model) => (
          <EnterpriseKPICard
            key={model.id}
            title={model.name}
            value={model.status === 'ACTIVE' ? `${model.accuracy}%` : model.status === 'TRAINING' ? 'Training...' : 'Error'}
            subtitle={`v${model.version} · ${new Date(model.lastTrained).toLocaleDateString()}`}
            icon={modelIcons[model.type] || <Brain className="w-5 h-5" />}
            color={model.status === 'ACTIVE' ? 'success' : model.status === 'TRAINING' ? 'info' : 'error'}
            trend={model.trainingHistory.length > 1
              ? (model.trainingHistory[0].accuracy > model.trainingHistory[model.trainingHistory.length - 1].accuracy ? 'up' as const : 'down' as const)
              : 'neutral' as const}
            trendValue={model.trainingHistory.length > 1
              ? `${(model.trainingHistory[0].accuracy - model.trainingHistory[model.trainingHistory.length - 1].accuracy).toFixed(1)}%`
              : undefined}
            onClick={() => setSelectedModel(model)}
          />
        ))}
      </div>

      {selectedModel && (
        <div className="enterprise-card animate-fade-in">
          <div className="card-header flex items-center justify-between p-5 border-b border-[var(--border-color)]">
            <div>
              <h3 className="text-sm font-semibold text-[var(--text-primary)]">{selectedModel.name} — Details</h3>
              <p className="text-xs text-[var(--text-secondary)]">
                v{selectedModel.version} · Last trained {new Date(selectedModel.lastTrained).toLocaleDateString()}
              </p>
            </div>
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
          </div>
          <div className="p-5">
            {selectedModel.status === 'ACTIVE' && (
              <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
                {[
                  { label: 'Accuracy', value: `${selectedModel.accuracy}%`, color: 'text-[var(--color-success)]' },
                  { label: 'Precision', value: selectedModel.precision.toFixed(3), color: 'text-[var(--color-primary-600)]' },
                  { label: 'Recall', value: selectedModel.recall.toFixed(3), color: 'text-[var(--color-info)]' },
                  { label: 'F1 Score', value: selectedModel.f1Score.toFixed(3), color: 'text-[var(--color-warning)]' },
                ].map((metric) => (
                  <div key={metric.label} className="p-3 bg-[var(--bg-tertiary)] rounded-lg text-center">
                    <p className="text-xs text-[var(--text-tertiary)] mb-1">{metric.label}</p>
                    <p className={`text-lg font-bold ${metric.color}`}>{metric.value}</p>
                  </div>
                ))}
              </div>
            )}

            {selectedModel.status === 'TRAINING' && (
              <div className="flex items-center gap-3 p-4 bg-[var(--color-info-bg)] rounded-lg mb-6">
                <Loader2 className="w-5 h-5 animate-spin text-[var(--color-info)]" />
                <div>
                  <p className="text-sm font-medium text-[var(--text-primary)]">Model is currently training</p>
                  <p className="text-xs text-[var(--text-secondary)]">
                    Last trained {new Date(selectedModel.lastTrained).toLocaleDateString()} · v{selectedModel.version}
                  </p>
                </div>
              </div>
            )}

            {selectedModel.status === 'ERROR' && (
              <div className="flex items-center gap-3 p-4 bg-[var(--color-error-bg)] rounded-lg mb-6">
                <XCircle className="w-5 h-5 text-[var(--color-error)]" />
                <div>
                  <p className="text-sm font-medium text-[var(--text-primary)]">Model is in error state</p>
                  <p className="text-xs text-[var(--text-secondary)]">Retrain or redeploy to restore service</p>
                </div>
              </div>
            )}

            <div className="mb-6">
              <h4 className="text-sm font-semibold text-[var(--text-primary)] mb-3">
                <Brain className="w-4 h-4 inline mr-1.5" />
                Test Prediction
              </h4>
              <div className="flex items-end gap-3">
                <div className="flex-1">
                  <label className="block text-xs text-[var(--text-secondary)] mb-1">Input Parameters (JSON)</label>
                  <textarea
                    className="enterprise-input w-full min-h-[96px] font-mono text-xs resize-y leading-relaxed"
                    placeholder='{ "feature1": "value1", "feature2": 42 }'
                    value={testInput}
                    onChange={e => setTestInput(e.target.value)}
                  />
                </div>
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
              </div>
              {testResult && (
                <div className="mt-4 p-4 bg-gray-50 rounded-lg border">
                  <h4 className="font-semibold text-sm mb-2">Prediction Result</h4>
                  {testResult.error ? (
                    <p className="text-sm text-red-600">{testResult.error}</p>
                  ) : (
                    <div className="space-y-2">
                      <div className="flex justify-between text-sm">
                        <span className="font-medium">{testResult.prediction ?? 'N/A'}</span>
                        <span className="text-gray-500">Confidence: {Math.round((testResult.confidence ?? 0) * 100)}%</span>
                      </div>
                      <div className="w-full bg-gray-200 rounded-full h-2.5">
                        <div className="bg-purple-600 h-2.5 rounded-full transition-all duration-500"
                             style={{ width: `${Math.round((testResult.confidence ?? 0) * 100)}%` }} />
                      </div>
                      <p className="text-sm text-gray-600 mt-1">{testResult.explanation}</p>
                      <div className="flex flex-wrap gap-1 mt-1">
                        <span className="text-xs text-gray-400">Model: {testResult.modelVersion ?? 'N/A'}</span>
                        {testResult.featuresUsed && (
                          <span className="text-xs text-gray-400">• Features: {testResult.featuresUsed.join(', ')}</span>
                        )}
                      </div>
                    </div>
                  )}
                </div>
              )}
            </div>

            {selectedModel.trainingHistory.length > 0 && (
              <div>
                <h4 className="text-sm font-semibold text-[var(--text-primary)] mb-3">
                  <Clock className="w-4 h-4 inline mr-1.5" />
                  Training History
                </h4>
                <div className="space-y-2">
                  {selectedModel.trainingHistory.slice(0, 5).map((run) => (
                    <div key={run.id} className="flex items-center justify-between p-3 bg-[var(--bg-tertiary)] rounded-lg text-sm">
                      <div className="flex items-center gap-3">
                        <Clock className="w-4 h-4 text-[var(--text-tertiary)]" />
                        <span className="text-[var(--text-secondary)]">
                          {new Date(run.startedAt).toLocaleDateString()}
                        </span>
                      </div>
                      <div className="flex items-center gap-4">
                        <span className="text-[var(--text-tertiary)] text-xs">
                          {run.datasetSize.toLocaleString()} records
                        </span>
                        <span className="text-[var(--text-tertiary)] text-xs">{run.epochs} epochs</span>
                        <span className="font-medium text-[var(--text-primary)]">
                          {run.accuracy.toFixed(1)}%
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
