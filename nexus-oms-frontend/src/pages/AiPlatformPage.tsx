import { useState, useEffect } from 'react'
import {
  Brain,
  Layers,
  Activity,
  Database,
  Play,
  CheckCircle2,
  AlertTriangle,
  XCircle,
  Clock,
  TrendingUp,
  RefreshCw,
  ChevronRight,
  BarChart3,
  Server,
  DollarSign,
  ArrowUp,
  ArrowDown,
  Loader2,
  Shield,
  Cpu,
  FileWarning,
  ThumbsDown,
} from 'lucide-react'
import {
  AiModel, AiModelVersion, AiTrainingJob, AiFeatureDefinition, AiInferenceLog,
  getModels, getModelVersions, getModelSummary, getTrainingJobs,
  getMonitoringDashboard, getTenantDashboard, getFeatureGroups,
  startTrainingJob, deployModel, getModel, getInferenceLogs,
} from '../api/aiPlatform'
import PermissionGate from '../components/rbac/PermissionGate'
import EnterpriseBreadcrumbs from '../components/enterprise/EnterpriseBreadcrumbs'
import EnterpriseTabs from '../components/enterprise/EnterpriseTabs'
import EnterpriseKPICard from '../components/enterprise/EnterpriseKPICard'
import EnterpriseDataGrid from '../components/enterprise/EnterpriseDataGrid'
import EnterpriseStatusBadge from '../components/enterprise/EnterpriseStatusBadge'
import { useToast } from '../hooks/useToast'

type AiTab = 'overview' | 'models' | 'training' | 'features'

const MODEL_TYPE_LABELS: Record<string, string> = {
  DEMAND_FORECAST: 'Demand Forecaster',
  SMART_ALLOCATOR: 'Smart Allocator',
  CARRIER_OPTIMIZER: 'Carrier Optimizer',
  RETURNS_PREDICTOR: 'Returns Predictor',
  INVENTORY_OPTIMIZER: 'Inventory Optimizer',
  ANOMALY_DETECTOR: 'Anomaly Detector',
  AI_ASSISTANT: 'AI Assistant',
  DOCUMENT_AI: 'Document AI',
}

const MODEL_CATEGORY_COLORS: Record<string, string> = {
  GLOBAL: 'info',
  TENANT: 'primary',
  HYBRID: 'warning',
}

const MODEL_STATUS_COLORS: Record<string, string> = {
  ACTIVE: 'success',
  DRAFT: 'info',
  TRAINING: 'warning',
  ERROR: 'error',
  DISABLED: 'error',
  ARCHIVED: 'error',
  WARNING: 'warning',
}

export default function AiPlatformPage() {
  const [activeTab, setActiveTab] = useState<AiTab>('overview')

  return (
    <div className="p-6 space-y-6">
      <EnterpriseBreadcrumbs
        crumbs={[
          { label: 'Home', href: '/' },
          { label: 'AI Platform' },
        ]}
      />
      <h1 className="text-2xl font-bold text-[var(--text-primary)] flex items-center gap-2.5"><Brain className="w-5 h-5" />AI Platform</h1>
      <EnterpriseTabs
        tabs={[
          { id: 'overview', label: 'Overview', icon: <Brain className="w-4 h-4" /> },
          { id: 'models', label: 'Model Registry', icon: <Layers className="w-4 h-4" /> },
          { id: 'training', label: 'Training', icon: <BarChart3 className="w-4 h-4" /> },
          { id: 'features', label: 'Feature Store', icon: <Database className="w-4 h-4" /> },
        ]}
        activeTab={activeTab}
        onChange={(id) => setActiveTab(id as AiTab)}
      />
      {activeTab === 'overview' && <AiOverview />}
      {activeTab === 'models' && <AiModelRegistry />}
      {activeTab === 'training' && <AiTrainingPipeline />}
      {activeTab === 'features' && <AiFeatureStore />}
    </div>
  )
}

function AiOverview() {
  const [dashboard, setDashboard] = useState<Record<string, unknown> | null>(null)
  const [monitoring, setMonitoring] = useState<Record<string, unknown> | null>(null)
  const [inferenceLogs, setInferenceLogs] = useState<AiInferenceLog[]>([])
  const [loading, setLoading] = useState(true)
  const [logsLoading, setLogsLoading] = useState(false)
  const { addToast } = useToast()

  useEffect(() => {
    Promise.all([getTenantDashboard(), getMonitoringDashboard()])
      .then(([dash, mon]) => {
        setDashboard(dash.data)
        setMonitoring(mon.data)
      })
      .catch((e) => { addToast({ type: 'error', title: 'Failed to load dashboard' }) })
      .finally(() => setLoading(false))
  }, [])

  const modelPerformance = (dashboard?.modelPerformance as Array<Record<string, unknown>>) || []
  const healthModels = (monitoring?.models as Array<Record<string, unknown>>) || []

  useEffect(() => {
    if (modelPerformance.length > 0) {
      setLogsLoading(true)
      const firstModelId = modelPerformance[0].id as string
      getInferenceLogs(firstModelId, 0, 20)
        .then((res) => setInferenceLogs(res.data?.content ?? []))
        .catch(() => {})
        .finally(() => setLogsLoading(false))
    }
  }, [dashboard])

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="w-8 h-8 animate-spin text-primary-600" />
      </div>
    )
  }

  const costBreakdown = (dashboard?.costsThisMonth as Record<string, number>) || {}
  const costTotal = Object.values(costBreakdown).reduce((a, b) => a + (b || 0), 0)

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <EnterpriseKPICard
          title="Active Models"
          value={String(monitoring?.activeModels ?? 0)}
          icon={<Brain className="w-5 h-5" />}
          variant="primary"
        />
        <EnterpriseKPICard
          title="Predictions Today"
          value={String(dashboard?.predictionsToday ?? 0)}
          icon={<Activity className="w-5 h-5" />}
          trend={Number(dashboard?.predictionsToday) > 100 ? 'up' : 'neutral'}
          variant="info"
        />
        <EnterpriseKPICard
          title="Models in Training"
          value={String(monitoring?.modelsInTraining ?? 0)}
          icon={<BarChart3 className="w-5 h-5" />}
          variant="warning"
        />
        <EnterpriseKPICard
          title="Monthly Cost"
          value={`$${costTotal.toFixed(2)}`}
          icon={<DollarSign className="w-5 h-5" />}
          variant="success"
        />
      </div>

      {/* Model Health */}
      <div className="bg-white dark:bg-[#162033] rounded-xl border border-gray-200 dark:border-gray-700 p-5">
        <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">Model Health</h3>
        {healthModels.length === 0 ? (
          <p className="text-gray-400 text-sm">No model health data available</p>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            {healthModels.map((m: Record<string, unknown>) => {
              const perf = modelPerformance.find((p: Record<string, unknown>) => p.id === m.id)
              return (
                <div key={m.id as string} className="bg-gray-50 dark:bg-gray-800/50 rounded-lg border border-gray-100 dark:border-gray-700 p-4">
                  <div className="flex items-center justify-between mb-3">
                    <h4 className="font-medium text-sm text-gray-900 dark:text-white">
                      {(m.displayName as string) || (m.name as string) || (m.modelType as string)}
                    </h4>
                    <span className={`w-2.5 h-2.5 rounded-full ${
                      (m.status as string) === 'ACTIVE' ? 'bg-green-500' :
                      (m.status as string) === 'ERROR' ? 'bg-red-500' :
                      (m.status as string) === 'TRAINING' ? 'bg-yellow-500' :
                      'bg-yellow-500'
                    }`} />
                  </div>
                  <div className="space-y-1.5 text-xs text-gray-500 dark:text-gray-400">
                    <div className="flex justify-between">
                      <span>Accuracy</span>
                      <span className="font-medium text-gray-700 dark:text-gray-300">
                        {perf?.accuracy != null ? `${(Number(perf.accuracy) * 100).toFixed(1)}%` : 'N/A'}
                      </span>
                    </div>
                    <div className="flex justify-between">
                      <span>Latency</span>
                      <span className="font-medium text-gray-700 dark:text-gray-300">
                        {perf?.avgLatencyMs != null ? `${Number(perf.avgLatencyMs).toFixed(0)}ms` : 'N/A'}
                      </span>
                    </div>
                    <div className="flex justify-between">
                      <span>Predictions Today</span>
                      <span className="font-medium text-gray-700 dark:text-gray-300">
                        {String(perf?.predictionsToday ?? 0)}
                      </span>
                    </div>
                    <div className="flex justify-between">
                      <span>Drift</span>
                      <span className={`font-medium ${perf?.driftDetected ? 'text-red-500' : 'text-green-500'}`}>
                        {perf?.driftDetected ? 'Detected' : 'Not Detected'}
                      </span>
                    </div>
                    <div className="flex justify-between">
                      <span>Last Training</span>
                      <span className="font-medium text-gray-700 dark:text-gray-300">
                        {perf?.lastTrainingTime
                          ? new Date(perf.lastTrainingTime as string).toLocaleDateString()
                          : (m.lastTrainingTime as string)
                            ? new Date(m.lastTrainingTime as string).toLocaleDateString()
                            : 'N/A'}
                      </span>
                    </div>
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-white dark:bg-[#162033] rounded-xl border border-gray-200 dark:border-gray-700 p-5">
          <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">Model Performance</h3>
          {modelPerformance.length === 0 ? (
            <p className="text-gray-400 text-sm">No model data available</p>
          ) : (
            <div className="space-y-3">
              {modelPerformance.slice(0, 6).map((m: Record<string, unknown>) => (
                <div key={m.id as string} className="flex items-center justify-between p-3 bg-gray-50 dark:bg-gray-800/50 rounded-lg">
                  <div className="flex items-center gap-3">
                    <div className={`w-2 h-2 rounded-full ${
                      (m.status as string) === 'ACTIVE' ? 'bg-green-500' :
                      (m.status as string) === 'TRAINING' ? 'bg-yellow-500' :
                      (m.status as string) === 'ERROR' ? 'bg-red-500' : 'bg-gray-400'
                    }`} />
                    <div>
                      <p className="text-sm font-medium text-gray-900 dark:text-white">
                        {(m.name as string) || (m.modelType as string)}
                      </p>
                      <p className="text-xs text-gray-400">
                        v{m.currentVersion as string} &middot; {Number(m.accuracy as number ?? 0).toFixed(1)}% accuracy
                      </p>
                    </div>
                  </div>
                  <div className="text-right">
                    <p className="text-sm font-medium text-gray-900 dark:text-white">
                      {String(m.predictionsToday ?? 0)} predictions
                    </p>
                    <p className="text-xs text-gray-400">
                      {Number(m.avgLatencyMs as number ?? 0).toFixed(0)}ms latency
                    </p>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="bg-white dark:bg-[#162033] rounded-xl border border-gray-200 dark:border-gray-700 p-5">
          <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">Cost Breakdown (This Month)</h3>
          <div className="space-y-4">
            {Object.entries(costBreakdown).length === 0 ? (
              <p className="text-gray-400 text-sm">No cost data available</p>
            ) : (
              Object.entries(costBreakdown).map(([type, amount]) => {
                const pct = costTotal > 0 ? (Number(amount) / costTotal * 100) : 0
                return (
                  <div key={type}>
                    <div className="flex justify-between text-sm mb-1">
                      <span className="text-gray-600 dark:text-gray-300 capitalize">{type}</span>
                      <span className="font-medium text-gray-900 dark:text-white">${Number(amount).toFixed(2)}</span>
                    </div>
                    <div className="w-full bg-gray-100 dark:bg-gray-700 rounded-full h-2">
                      <div className="bg-primary-500 h-2 rounded-full transition-all" style={{ width: `${pct}%` }} />
                    </div>
                  </div>
                )
              })
            )}
          </div>

          <h3 className="text-lg font-semibold text-gray-900 dark:text-white mt-6 mb-4">Active Deployments</h3>
          <div className="space-y-2">
            {(dashboard?.deployments as Array<Record<string, unknown>>)?.length === 0 ? (
              <p className="text-gray-400 text-sm">No active deployments</p>
            ) : (
              (dashboard?.deployments as Array<Record<string, unknown>>)?.slice(0, 4).map((d: Record<string, unknown>) => (
                <div key={d.id as string} className="flex items-center justify-between p-2 bg-gray-50 dark:bg-gray-800/50 rounded-lg">
                  <div className="flex items-center gap-2">
                    <Server className="w-4 h-4 text-gray-400" />
                    <span className="text-sm text-gray-700 dark:text-gray-300">
                      {d.modelName as string} <span className="text-gray-400">({d.environment as string})</span>
                    </span>
                  </div>
                  <EnterpriseStatusBadge status={d.status as string} />
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      {/* Inference Activity */}
      <div className="bg-white dark:bg-[#162033] rounded-xl border border-gray-200 dark:border-gray-700 p-5">
        <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">Inference Activity</h3>
        {logsLoading ? (
          <div className="flex justify-center py-8">
            <Loader2 className="w-6 h-6 animate-spin text-primary-600" />
          </div>
        ) : inferenceLogs.length === 0 ? (
          <p className="text-gray-400 text-sm">No recent inference activity</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-gray-100 dark:border-gray-700">
                  <th className="text-left py-2 px-3 text-gray-500 font-medium">Timestamp</th>
                  <th className="text-left py-2 px-3 text-gray-500 font-medium">Model</th>
                  <th className="text-right py-2 px-3 text-gray-500 font-medium">Confidence</th>
                  <th className="text-center py-2 px-3 text-gray-500 font-medium">Status</th>
                  <th className="text-right py-2 px-3 text-gray-500 font-medium">Latency</th>
                </tr>
              </thead>
              <tbody>
                {inferenceLogs.slice(0, 10).map((log) => {
                  const modelInfo = modelPerformance.find((p: Record<string, unknown>) => p.id === log.modelId)
                  const logStatus = log.status || (log.fallbackUsed ? 'FALLBACK' : 'SUCCESS')
                  return (
                    <tr key={log.id} className="border-b border-gray-50 dark:border-gray-800 hover:bg-gray-50 dark:hover:bg-gray-800/50">
                      <td className="py-2.5 px-3 text-gray-600 dark:text-gray-400 whitespace-nowrap">
                        {new Date(log.createdAt).toLocaleString()}
                      </td>
                      <td className="py-2.5 px-3 text-gray-900 dark:text-white font-medium">
                        {(modelInfo?.name as string) || log.modelId}
                      </td>
                      <td className="py-2.5 px-3 text-right text-gray-700 dark:text-gray-300">
                        {log.confidence != null ? `${(log.confidence * 100).toFixed(1)}%` : '-'}
                      </td>
                      <td className="py-2.5 px-3 text-center">
                        <span className={`inline-flex items-center gap-1 text-xs font-medium px-2 py-0.5 rounded-full ${
                          logStatus === 'SUCCESS' ? 'bg-green-50 text-green-700 dark:bg-green-900/30 dark:text-green-400' :
                          logStatus === 'FALLBACK' ? 'bg-yellow-50 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400' :
                          'bg-red-50 text-red-700 dark:bg-red-900/30 dark:text-red-400'
                        }`}>
                          {logStatus === 'SUCCESS' ? <CheckCircle2 className="w-3 h-3" /> :
                           logStatus === 'FALLBACK' ? <AlertTriangle className="w-3 h-3" /> :
                           <XCircle className="w-3 h-3" />}
                          {logStatus}
                        </span>
                      </td>
                      <td className="py-2.5 px-3 text-right text-gray-600 dark:text-gray-400">
                        {log.latencyMs != null ? `${log.latencyMs.toFixed(0)}ms` : '-'}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Rule Fallback Activity */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="bg-white dark:bg-[#162033] rounded-xl border border-gray-200 dark:border-gray-700 p-5">
          <div className="flex items-center gap-3 mb-2">
            <div className="w-10 h-10 rounded-lg bg-amber-100 dark:bg-amber-900/30 flex items-center justify-center">
              <ThumbsDown className="w-5 h-5 text-amber-600 dark:text-amber-400" />
            </div>
            <div>
              <p className="text-sm text-gray-500">Fallbacks Today</p>
              <p className="text-2xl font-bold text-gray-900 dark:text-white">
                {String(dashboard?.fallbacksToday ?? 0)}
              </p>
            </div>
          </div>
        </div>
        <div className="bg-white dark:bg-[#162033] rounded-xl border border-gray-200 dark:border-gray-700 p-5">
          <div className="flex items-center gap-3 mb-2">
            <div className="w-10 h-10 rounded-lg bg-blue-100 dark:bg-blue-900/30 flex items-center justify-center">
              <FileWarning className="w-5 h-5 text-blue-600 dark:text-blue-400" />
            </div>
            <div>
              <p className="text-sm text-gray-500">Rule Engine Usage</p>
              <p className="text-2xl font-bold text-gray-900 dark:text-white">
                {dashboard?.ruleEngineUsedToday != null
                  ? `${(Number(dashboard.ruleEngineUsedToday) / Math.max(Number(dashboard?.predictionsToday ?? 1), 1) * 100).toFixed(1)}%`
                  : 'N/A'}
              </p>
            </div>
          </div>
        </div>
        <div className="bg-white dark:bg-[#162033] rounded-xl border border-gray-200 dark:border-gray-700 p-5">
          <div className="flex items-center gap-3 mb-3">
            <div className="w-10 h-10 rounded-lg bg-purple-100 dark:bg-purple-900/30 flex items-center justify-center">
              <Activity className="w-5 h-5 text-purple-600 dark:text-purple-400" />
            </div>
            <div>
              <p className="text-sm text-gray-500">Most Common Reason</p>
              <p className="text-lg font-semibold text-gray-900 dark:text-white truncate">
                {inferenceLogs
                  .filter(l => l.fallbackReason)
                  .reduce<[string, number][]>((acc, l) => {
                    const existing = acc.find(([r]) => r === l.fallbackReason)
                    if (existing) existing[1]++
                    else acc.push([l.fallbackReason!, 1])
                    return acc
                  }, [])
                  .sort((a, b) => b[1] - a[1])[0]?.[0] || 'N/A'}
              </p>
            </div>
          </div>
          {inferenceLogs.filter(l => l.fallbackReason).length > 0 && (
            <div className="space-y-1.5">
              {inferenceLogs
                .filter(l => l.fallbackReason)
                .reduce<[string, number][]>((acc, l) => {
                  const existing = acc.find(([r]) => r === l.fallbackReason)
                  if (existing) existing[1]++
                  else acc.push([l.fallbackReason!, 1])
                  return acc
                }, [])
                .sort((a, b) => b[1] - a[1])
                .slice(0, 3)
                .map(([reason, count]) => {
                  const total = inferenceLogs.filter(l => l.fallbackReason).length
                  const pct = (count / total) * 100
                  return (
                    <div key={reason}>
                      <div className="flex justify-between text-xs text-gray-500 mb-0.5">
                        <span className="truncate">{reason}</span>
                        <span>{pct.toFixed(0)}%</span>
                      </div>
                      <div className="w-full bg-gray-100 dark:bg-gray-700 rounded-full h-1.5">
                        <div className="bg-amber-500 h-1.5 rounded-full" style={{ width: `${pct}%` }} />
                      </div>
                    </div>
                  )
                })}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

function AiModelRegistry() {
  const { addToast } = useToast()
  const [models, setModels] = useState<AiModel[]>([])
  const [summary, setSummary] = useState<Record<string, number> | null>(null)
  const [loading, setLoading] = useState(true)
  const [selectedModel, setSelectedModel] = useState<string | null>(null)
  const [versions, setVersions] = useState<AiModelVersion[]>([])
  const [versionsLoading, setVersionsLoading] = useState(false)
  const [categoryFilter, setCategoryFilter] = useState<string>('')

  useEffect(() => {
    Promise.all([getModels(0, 100, categoryFilter || undefined), getModelSummary()])
      .then(([modelsRes, sumRes]) => {
        setModels(modelsRes.data?.content ?? [])
        setSummary(sumRes.data)
      })
      .catch((e) => { addToast({ type: 'error', title: 'Failed to load models' }) })
      .finally(() => setLoading(false))
  }, [categoryFilter])

  async function handleModelClick(modelId: string) {
    setSelectedModel(modelId === selectedModel ? null : modelId)
    setVersionsLoading(true)
    try {
      const res = await getModelVersions(modelId)
      setVersions(res.data ?? [])
    } catch { setVersions([]) }
    finally { setVersionsLoading(false) }
  }

  async function handleDeploy(modelId: string, versionId: string) {
    try {
      await deployModel(modelId, versionId)
      handleModelClick(modelId)
    } catch { addToast({ type: 'error', title: 'Failed to deploy model' }) }
  }

  if (loading) return <div className="flex justify-center py-12"><Loader2 className="w-8 h-8 animate-spin text-primary-600" /></div>

  return (
    <div className="space-y-6">
      {summary && (
        <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
          <EnterpriseKPICard title="Total Models" value={String(summary.totalModels ?? 0)} icon={<Brain className="w-4 h-4" />} color="primary" />
          <EnterpriseKPICard title="Active" value={String(summary.activeModels ?? 0)} icon={<CheckCircle2 className="w-4 h-4" />} color="success" />
          <EnterpriseKPICard title="Global" value={String(summary.globalModels ?? 0)} icon={<Layers className="w-4 h-4" />} color="primary" />
          <EnterpriseKPICard title="Tenant-Specific" value={String(summary.tenantModels ?? 0)} icon={<Database className="w-4 h-4" />} color="amber" />
          <EnterpriseKPICard title="In Training" value={String(summary.modelsInTraining ?? 0)} icon={<RefreshCw className="w-4 h-4" />} color="amber" />
        </div>
      )}

      <div className="flex gap-2 mb-4">
        {['', 'GLOBAL', 'TENANT', 'HYBRID'].map(c => (
          <button
            key={c}
            onClick={() => setCategoryFilter(c)}
            className={`px-3 py-1.5 text-sm rounded-lg transition-colors ${
              categoryFilter === c
                ? 'bg-primary-600 text-white'
                : 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-300 hover:bg-gray-200'
            }`}
          >
            {c || 'All'}
          </button>
        ))}
      </div>

      <div className="space-y-3">
        {models.length === 0 ? (
          <div className="text-center py-12 text-gray-400">No models found</div>
        ) : (
          models.map((model) => (
            <div key={model.id} className="bg-white dark:bg-[#162033] rounded-xl border border-gray-200 dark:border-gray-700">
              <button
                onClick={() => handleModelClick(model.id)}
                className="w-full flex items-center justify-between p-4 hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors"
              >
                <div className="flex items-center gap-4">
                  <div className={`w-10 h-10 rounded-lg flex items-center justify-center ${
                    model.category === 'GLOBAL' ? 'bg-blue-100 text-blue-600' :
                    model.category === 'TENANT' ? 'bg-purple-100 text-purple-600' :
                    'bg-amber-100 text-amber-600'
                  }`}>
                    {model.modelType === 'DEMAND_FORECAST' ? <TrendingUp className="w-5 h-5" /> :
                     model.modelType === 'ANOMALY_DETECTOR' ? <Shield className="w-5 h-5" /> :
                     <Brain className="w-5 h-5" />}
                  </div>
                  <div className="text-left">
                    <p className="font-medium text-gray-900 dark:text-white">
                      {model.displayName || MODEL_TYPE_LABELS[model.modelType] || model.name}
                    </p>
                    <p className="text-xs text-gray-400 mt-0.5">
                      {model.category} &middot; v{model.currentVersion || '-'} &middot; {model.modelType}
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-3">
                  <EnterpriseStatusBadge status={model.status} />
                  <ChevronRight className={`w-4 h-4 text-gray-400 transition-transform ${selectedModel === model.id ? 'rotate-90' : ''}`} />
                </div>
              </button>
              {selectedModel === model.id && (
                <div className="px-4 pb-4 border-t border-gray-100 dark:border-gray-700">
                  <div className="pt-3">
                    <p className="text-sm text-gray-600 dark:text-gray-400 mb-3">{model.description}</p>
                    {model.inputSchema && (
                      <div className="mb-3">
                        <p className="text-xs font-medium text-gray-500 mb-1">Input Schema</p>
                        <pre className="text-xs bg-gray-50 dark:bg-gray-900 p-2 rounded-lg overflow-x-auto">
                          {JSON.stringify(JSON.parse(model.inputSchema), null, 2)}
                        </pre>
                      </div>
                    )}
                    <h4 className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">Versions</h4>
                    {versionsLoading ? (
                      <Loader2 className="w-5 h-5 animate-spin text-primary-600" />
                    ) : versions.length === 0 ? (
                      <p className="text-sm text-gray-400">No versions</p>
                    ) : (
                      <div className="space-y-2">
                        {versions.map(v => (
                          <div key={v.id} className="flex items-center justify-between p-2 bg-gray-50 dark:bg-gray-800/50 rounded-lg">
                            <div className="flex items-center gap-3">
                              <span className="text-sm font-mono text-gray-700 dark:text-gray-300">{v.version}</span>
                              <EnterpriseStatusBadge status={v.status} />
                              {v.accuracy != null && (
                                <span className="text-xs text-gray-400">{Number(v.accuracy).toFixed(1)}% accuracy</span>
                              )}
                            </div>
                            <div className="flex gap-2">
                              <PermissionGate resource="settings" action="edit">
                                <button
                                  onClick={() => handleDeploy(model.id, v.id)}
                                  className="px-2 py-1 text-xs bg-primary-600 text-white rounded-lg hover:bg-primary-700"
                                >
                                  Deploy
                                </button>
                              </PermissionGate>
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>
          ))
        )}
      </div>
    </div>
  )
}

function AiTrainingPipeline() {
  const { addToast } = useToast()
  const [jobs, setJobs] = useState<any[]>([])
  const [loading, setLoading] = useState(true)
  const [statusFilter, setStatusFilter] = useState<string>('')

  useEffect(() => {
    getTrainingJobs(undefined, statusFilter || undefined, 0)
      .then(res => setJobs(res.data?.content ?? []))
      .catch((e) => { addToast({ type: 'error', title: 'Failed to load training jobs' }) })
      .finally(() => setLoading(false))
  }, [statusFilter])

  async function handleStartJob(jobId: string) {
    try {
      await startTrainingJob(jobId)
      getTrainingJobs(undefined, statusFilter || undefined, 0)
        .then(res => setJobs(res.data?.content ?? []))
    } catch { addToast({ type: 'error', title: 'Failed to start training job' }) }
  }

  const statusCounts: Record<string, number> = {}
  jobs.forEach(j => { statusCounts[j.status] = (statusCounts[j.status] || 0) + 1 })

  if (loading) return <div className="flex justify-center py-12"><Loader2 className="w-8 h-8 animate-spin text-primary-600" /></div>

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
        <EnterpriseKPICard title="Total Jobs" value={String(jobs.length)} icon={<BarChart3 className="w-4 h-4" />} variant="primary" />
        <EnterpriseKPICard title="Running" value={String(statusCounts.RUNNING || 0)} icon={<RefreshCw className="w-4 h-4" />} variant="warning" />
        <EnterpriseKPICard title="Completed" value={String(statusCounts.COMPLETED || 0)} icon={<CheckCircle2 className="w-4 h-4" />} variant="success" />
        <EnterpriseKPICard title="Failed" value={String(statusCounts.FAILED || 0)} icon={<XCircle className="w-4 h-4" />} variant="error" />
        <EnterpriseKPICard title="Pending" value={String(statusCounts.PENDING || 0)} icon={<Clock className="w-4 h-4" />} variant="info" />
      </div>

      <div className="flex gap-2 mb-4">
        {['', 'PENDING', 'RUNNING', 'COMPLETED', 'FAILED'].map(s => (
          <button
            key={s}
            onClick={() => setStatusFilter(s)}
            className={`px-3 py-1.5 text-sm rounded-lg transition-colors ${
              statusFilter === s
                ? 'bg-primary-600 text-white'
                : 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-300 hover:bg-gray-200'
            }`}
          >
            {s || 'All'}
          </button>
        ))}
      </div>

      <EnterpriseDataGrid
        data={jobs}
        loading={loading}
        columns={[
          { header: 'Name', accessor: 'name' as never, render: (j: AiTrainingJob) => (
            <span className="font-medium text-gray-900 dark:text-white">{j.name || 'Training Job'}</span>
          )},
          { header: 'Status', accessor: 'status' as never, render: (j: AiTrainingJob) => (
            <EnterpriseStatusBadge status={j.status} />
          )},
          { header: 'Type', accessor: 'jobType' as never },
          { header: 'Accuracy', accessor: 'accuracy' as never, render: (j: AiTrainingJob) => (
            <span>{j.accuracy != null ? `${Number(j.accuracy).toFixed(1)}%` : '-'}</span>
          )},
          { header: 'Epochs', accessor: 'epochs' as never, render: (j: AiTrainingJob) => j.epochs ?? '-' },
          { header: 'Duration', accessor: 'durationSeconds' as never, render: (j: AiTrainingJob) => (
            <span>{j.durationSeconds ? `${Math.round(j.durationSeconds / 60)}m` : '-'}</span>
          )},
          { header: 'Created', accessor: 'createdAt' as never, render: (j: AiTrainingJob) => (
            <span className="text-sm text-gray-400">{new Date(j.createdAt).toLocaleDateString()}</span>
          )},
          { header: 'Actions', accessor: 'id' as never, render: (j: AiTrainingJob) => (
            j.status === 'PENDING' ? (
              <PermissionGate resource="settings" action="create">
                <button onClick={() => handleStartJob(j.id)} className="p-1.5 bg-primary-600 text-white rounded-lg hover:bg-primary-700">
                  <Play className="w-3.5 h-3.5" />
                </button>
              </PermissionGate>
            ) : null
          )},
        ]}
        keyExtractor={(j: AiTrainingJob) => j.id}
        emptyMessage="No training jobs found"
      />
    </div>
  )
}

function AiFeatureStore() {
  const [groups, setGroups] = useState<Array<{ group: string; count: number }>>([])
  const [features, setFeatures] = useState<AiFeatureDefinition[]>([])
  const [loading, setLoading] = useState(true)
  const { addToast } = useToast()

  useEffect(() => {
    Promise.all([
      getFeatureGroups(),
      getModelSummary(),
    ])
      .then(([grp]) => setGroups(grp.data ?? []))
      .catch((e) => { addToast({ type: 'error', title: 'Failed to load features' }) })
      .finally(() => setLoading(false))
  }, [])

  const FEATURE_ICONS: Record<string, string> = {
    demand: '📈',
    inventory: '📦',
    customer: '👤',
    order: '🛒',
    product: '🏷️',
    shipment: '🚚',
    returns: '↩️',
    warehouse: '🏭',
    time: '⏰',
    weather: '🌤️',
    promotion: '🏷️',
  }

  if (loading) return <div className="flex justify-center py-12"><Loader2 className="w-8 h-8 animate-spin text-primary-600" /></div>

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <EnterpriseKPICard title="Feature Groups" value={String(groups.length)} icon={<Database className="w-4 h-4" />} variant="primary" />
        <EnterpriseKPICard title="Total Features" value={String(groups.reduce((a, g) => a + g.count, 0))} icon={<Layers className="w-4 h-4" />} variant="info" />
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {groups.length === 0 ? (
          <div className="col-span-full text-center py-12 text-gray-400">No feature groups defined</div>
        ) : (
          groups.map(g => (
            <div key={g.group} className="bg-white dark:bg-[#162033] rounded-xl border border-gray-200 dark:border-gray-700 p-4 hover:shadow-md transition-shadow">
              <div className="flex items-center gap-3 mb-3">
                <span className="text-2xl">{FEATURE_ICONS[g.group.toLowerCase()] || '📊'}</span>
                <div>
                  <p className="font-medium text-gray-900 dark:text-white capitalize">{g.group}</p>
                  <p className="text-xs text-gray-400">{g.count} features</p>
                </div>
              </div>
              <div className="w-full bg-gray-100 dark:bg-gray-700 rounded-full h-1.5">
                <div
                  className="bg-primary-500 h-1.5 rounded-full"
                  style={{ width: `${Math.min(100, (g.count / 20) * 100)}%` }}
                />
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  )
}
