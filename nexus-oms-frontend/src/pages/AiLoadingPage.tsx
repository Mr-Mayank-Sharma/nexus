import { useState, useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  Truck, Box, Weight, MapPin, CheckCircle, AlertTriangle,
  ArrowDown, Diamond, Clock, RotateCw, Settings,
} from 'lucide-react'
import clsx from 'clsx'
import { getLoadingPlan } from '../api/aiAgents'
import PermissionGate from '../components/rbac/PermissionGate'
import type { AiLoadingStep } from '../api/aiAgents'

interface MockTruck {
  id: string
  name: string
  dock: string
  loadPercent: number
  status: 'loading' | 'awaiting' | 'waiting' | 'ready' | 'paused'
}

const MOCK_TRUCKS: MockTruck[] = [
  { id: 'TRK-001', name: 'TRK-001', dock: 'Dock 3', loadPercent: 80, status: 'loading' },
  { id: 'TRK-002', name: 'TRK-002', dock: 'Dock 5', loadPercent: 30, status: 'awaiting' },
  { id: 'TRK-003', name: 'TRK-003', dock: 'Dock 7', loadPercent: 0, status: 'waiting' },
  { id: 'TRK-004', name: 'TRK-004', dock: 'Dock 2', loadPercent: 100, status: 'ready' },
  { id: 'TRK-005', name: 'TRK-005', dock: 'Dock 1', loadPercent: 50, status: 'paused' },
]

const MOCK_DELIVERY_STOPS = [
  { order: 1, stop: 'Stop 3 – 425 Oak Ave', distance: '12.4 km' },
  { order: 2, stop: 'Stop 2 – 789 Pine Rd', distance: '8.1 km' },
  { order: 3, stop: 'Stop 1 – 123 Main St', distance: '3.2 km' },
]

const STATUS_STYLES: Record<string, string> = {
  loading: 'border-l-green-500 bg-green-50/30 dark:bg-green-900/5',
  awaiting: 'border-l-blue-500 bg-blue-50/30 dark:bg-blue-900/5',
  waiting: 'border-l-gray-300 dark:border-l-gray-600',
  ready: 'border-l-amber-500 bg-amber-50/30 dark:bg-amber-900/5',
  paused: 'border-l-orange-500 bg-orange-50/30 dark:bg-orange-900/5',
}

const STATUS_ICON_STYLES: Record<string, string> = {
  loading: 'bg-green-100 dark:bg-green-900/20 text-green-600',
  awaiting: 'bg-blue-100 dark:bg-blue-900/20 text-blue-600',
  waiting: 'bg-gray-100 dark:bg-gray-800 text-gray-400',
  ready: 'bg-amber-100 dark:bg-amber-900/20 text-amber-600',
  paused: 'bg-orange-100 dark:bg-orange-900/20 text-orange-600',
}

const STATUS_LABELS: Record<string, string> = {
  loading: 'Loading',
  awaiting: 'Awaiting Plan',
  waiting: 'Waiting',
  ready: 'Ready to Depart',
  paused: 'Paused',
}

const POSITION_GRID: Record<string, { col: number; row: number }> = {
  'front-left': { col: 1, row: 1 },
  'front-right': { col: 1, row: 2 },
  'center': { col: 2, row: 1 },
  'rear-left': { col: 3, row: 1 },
  'rear-right': { col: 3, row: 2 },
}

function LoadingSkeleton() {
  return (
    <div className="space-y-4 animate-pulse">
      <div className="h-8 bg-gray-200 dark:bg-gray-700 rounded w-1/3" />
      <div className="grid grid-cols-5 gap-3">
        {Array.from({ length: 5 }).map((_, i) => (
          <div key={i} className="h-24 bg-gray-200 dark:bg-gray-700 rounded-xl" />
        ))}
      </div>
      <div className="h-64 bg-gray-200 dark:bg-gray-700 rounded-xl" />
      <div className="h-32 bg-gray-200 dark:bg-gray-700 rounded-xl" />
    </div>
  )
}

function PositionLabel({ position }: { position: string }) {
  const labels: Record<string, string> = {
    'front-left': 'Front Left',
    'front-right': 'Front Right',
    'center': 'Center',
    'rear-left': 'Rear Left',
    'rear-right': 'Rear Right',
  }
  return <span className="text-xs font-medium text-gray-600 dark:text-gray-400">{labels[position] || position}</span>
}

export default function AiLoadingPage() {
  const [selectedTruck, setSelectedTruck] = useState<string>('TRK-001')

  const { data: loadingPlan, isLoading, isFetching } = useQuery({
    queryKey: ['loading-plan', selectedTruck],
    queryFn: () => getLoadingPlan(selectedTruck),
    enabled: !!selectedTruck,
  })

  const truck = useMemo(() => MOCK_TRUCKS.find(t => t.id === selectedTruck), [selectedTruck])

  const truckDiagram = useMemo(() => {
    if (!loadingPlan) return null
    const grid: (AiLoadingStep | null)[][] = [
      [null, null],
      [null, null],
      [null, null],
    ]
    for (const step of loadingPlan.sequence) {
      const pos = POSITION_GRID[step.position]
      if (pos && pos.row === 1) grid[pos.col - 1] = [step, grid[pos.col - 1]?.[1] ?? null]
      if (pos && pos.row === 2) grid[pos.col - 1] = [grid[pos.col - 1]?.[0] ?? null, step]
    }
    return grid
  }, [loadingPlan])

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100 flex items-center gap-2.5">
            <Truck className="w-7 h-7 text-orange-500" />
            AI Loading Optimization
          </h1>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">Optimal truck loading sequence</p>
        </div>
        <div className="flex items-center gap-2">
          <PermissionGate resource="settings" action="create">
            <button
              onClick={() => setSelectedTruck(selectedTruck)}
              className="enterprise-btn-ai text-sm flex items-center gap-1.5 px-4 py-2"
            >
              <RotateCw className="w-4 h-4" /> Optimize Route
            </button>
          </PermissionGate>
        </div>
      </div>

      {isLoading && <LoadingSkeleton />}

      {!isLoading && (
        <>
          {/* Truck Selection */}
          <div>
            <h2 className="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-3 flex items-center gap-2">
              <Box className="w-4 h-4" /> Select Truck
            </h2>
            <div className="grid grid-cols-5 gap-3">
              {MOCK_TRUCKS.map(t => (
                <button
                  key={t.id}
                  onClick={() => setSelectedTruck(t.id)}
                  className={clsx(
                    'enterprise-card p-3 border-l-4 text-left transition-all',
                    STATUS_STYLES[t.status],
                    selectedTruck === t.id && 'ring-2 ring-orange-500 shadow-md'
                  )}
                >
                  <div className="flex items-center gap-2 mb-2">
                    <div className={clsx('w-8 h-8 rounded-lg flex items-center justify-center', STATUS_ICON_STYLES[t.status])}>
                      <Truck className="w-4 h-4" />
                    </div>
                    <span className="text-sm font-bold text-gray-900 dark:text-gray-100">{t.name}</span>
                  </div>
                  <div className="text-xs text-gray-500 dark:text-gray-400 flex items-center gap-1 mb-1.5">
                    <MapPin className="w-3 h-3" /> {t.dock}
                  </div>
                  <div className="flex items-center gap-2">
                    <div className="flex-1 h-1.5 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
                      <div
                        className={clsx(
                          'h-full rounded-full transition-all',
                          t.loadPercent === 100 ? 'bg-green-500' :
                          t.loadPercent > 60 ? 'bg-blue-500' :
                          t.loadPercent > 0 ? 'bg-amber-500' : 'bg-gray-300 dark:bg-gray-600'
                        )}
                        style={{ width: `${t.loadPercent}%` }}
                      />
                    </div>
                    <span className="text-[10px] font-semibold text-gray-600 dark:text-gray-400 min-w-[2rem] text-right">
                      {t.loadPercent}%
                    </span>
                  </div>
                  <span className={clsx(
                    'inline-block mt-1.5 text-[10px] font-medium px-1.5 py-0.5 rounded',
                    t.status === 'loading' ? 'text-green-700 bg-green-100 dark:text-green-300 dark:bg-green-900/30' :
                    t.status === 'awaiting' ? 'text-blue-700 bg-blue-100 dark:text-blue-300 dark:bg-blue-900/30' :
                    t.status === 'waiting' ? 'text-gray-500 bg-gray-100 dark:text-gray-400 dark:bg-gray-800' :
                    t.status === 'ready' ? 'text-amber-700 bg-amber-100 dark:text-amber-300 dark:bg-amber-900/30' :
                    'text-orange-700 bg-orange-100 dark:text-orange-300 dark:bg-orange-900/30'
                  )}>
                    {STATUS_LABELS[t.status]}
                  </span>
                </button>
              ))}
            </div>
          </div>

          {loadingPlan && (
            <div className="grid grid-cols-3 gap-6">
              {/* Loading Plan – Timeline */}
              <div className="col-span-2 space-y-6">
                <div className="enterprise-card p-5">
                  <div className="flex items-center justify-between mb-4">
                    <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100 flex items-center gap-1.5">
                      <ArrowDown className="w-4 h-4 text-orange-500" /> Loading Sequence
                    </h3>
                    <span className="text-xs text-gray-500">{loadingPlan.sequence.length} steps</span>
                  </div>
                  <div className="space-y-0">
                    {loadingPlan.sequence.map((step, idx) => (
                      <div key={step.step} className="relative flex gap-4 pb-4 last:pb-0">
                        {idx < loadingPlan.sequence.length - 1 && (
                          <div className="absolute left-4 top-8 bottom-0 w-0.5 bg-gray-200 dark:bg-gray-700" />
                        )}
                        <div className="flex-shrink-0 w-8 h-8 rounded-full bg-orange-100 dark:bg-orange-900/30 text-orange-600 dark:text-orange-400 flex items-center justify-center text-xs font-bold z-10">
                          {step.step}
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center justify-between">
                            <span className="text-sm font-semibold text-gray-900 dark:text-gray-100">{step.boxId}</span>
                            <PositionLabel position={step.position} />
                          </div>
                          <div className="flex items-center gap-3 mt-1 text-xs text-gray-500 dark:text-gray-400">
                            <span className="flex items-center gap-1"><Box className="w-3 h-3" /> {step.itemCount} items</span>
                            <span className="flex items-center gap-1"><Weight className="w-3 h-3" /> {step.weight} kg</span>
                            {step.fragile && (
                              <span className="flex items-center gap-1 text-amber-600 dark:text-amber-400">
                                <Diamond className="w-3 h-3" /> Fragile
                              </span>
                            )}
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>

                {/* Weight Distribution */}
                <div className="enterprise-card p-5">
                  <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100 flex items-center gap-1.5 mb-4">
                    <Weight className="w-4 h-4 text-orange-500" /> Weight Distribution
                  </h3>
                  <div className="w-full h-6 rounded-lg overflow-hidden flex">
                    <div
                      className="bg-blue-500 h-full flex items-center justify-center text-[10px] font-bold text-white"
                      style={{ width: `${loadingPlan.weightDistribution.front}%` }}
                    >
                      F {loadingPlan.weightDistribution.front}%
                    </div>
                    <div
                      className="bg-green-500 h-full flex items-center justify-center text-[10px] font-bold text-white"
                      style={{ width: `${loadingPlan.weightDistribution.center}%` }}
                    >
                      C {loadingPlan.weightDistribution.center}%
                    </div>
                    <div
                      className="bg-amber-500 h-full flex items-center justify-center text-[10px] font-bold text-white"
                      style={{ width: `${loadingPlan.weightDistribution.rear}%` }}
                    >
                      R {loadingPlan.weightDistribution.rear}%
                    </div>
                  </div>
                  <div className="flex justify-between mt-2 text-xs text-gray-500 dark:text-gray-400">
                    <span>Front</span>
                    <span>Center</span>
                    <span>Rear</span>
                  </div>
                </div>

                {/* Delivery Stop Sequence */}
                <div className="enterprise-card p-5">
                  <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100 flex items-center gap-1.5 mb-4">
                    <MapPin className="w-4 h-4 text-orange-500" /> Delivery Stop Sequence
                  </h3>
                  <p className="text-xs text-gray-500 mb-3">Last-in-first-out delivery order</p>
                  <div className="space-y-2">
                    {MOCK_DELIVERY_STOPS.map(stop => (
                      <div key={stop.order} className="flex items-center gap-3 p-2.5 bg-gray-50 dark:bg-gray-800/50 rounded-lg">
                        <div className="w-6 h-6 rounded-full bg-orange-100 dark:bg-orange-900/30 text-orange-600 dark:text-orange-400 flex items-center justify-center text-[10px] font-bold">
                          {stop.order}
                        </div>
                        <span className="flex-1 text-sm text-gray-800 dark:text-gray-200">{stop.stop}</span>
                        <span className="text-xs text-gray-500">{stop.distance}</span>
                      </div>
                    ))}
                  </div>
                </div>

                {/* Validation Checks */}
                <div className="enterprise-card p-5">
                  <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100 flex items-center gap-1.5 mb-4">
                    <AlertTriangle className="w-4 h-4 text-orange-500" /> Validation Checks
                  </h3>
                  <div className="space-y-2">
                    {loadingPlan.checks.map((check, idx) => (
                      <div key={idx} className="flex items-center gap-2.5">
                        {check.ok ? (
                          <CheckCircle className="w-4 h-4 text-green-500 flex-shrink-0" />
                        ) : (
                          <AlertTriangle className="w-4 h-4 text-red-500 flex-shrink-0" />
                        )}
                        <span className={clsx(
                          'text-sm',
                          check.ok ? 'text-gray-700 dark:text-gray-300' : 'text-red-600 dark:text-red-400'
                        )}>
                          {check.label}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>

              {/* Right Column */}
              <div className="space-y-6">
                {/* 3D Truck Visualization */}
                <div className="enterprise-card p-5">
                  <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100 flex items-center gap-1.5 mb-4">
                    <Truck className="w-4 h-4 text-orange-500" /> Truck Layout
                  </h3>
                  <div className="relative bg-gray-100 dark:bg-gray-800 rounded-xl p-3">
                    <div className="text-center mb-2">
                      <span className="text-[10px] font-medium text-gray-400 uppercase tracking-wider">CAB →</span>
                    </div>
                    <div className="grid grid-cols-3 gap-1.5">
                      {truckDiagram && truckDiagram.map((row, rowIdx) => (
                        row.map((cell, cellIdx) => (
                          <div
                            key={`${rowIdx}-${cellIdx}`}
                            className={clsx(
                              'h-20 rounded-lg border-2 flex flex-col items-center justify-center text-center transition-all',
                              cell
                                ? cell.fragile
                                  ? 'border-amber-400 bg-amber-50 dark:bg-amber-900/20'
                                  : 'border-blue-400 bg-blue-50 dark:bg-blue-900/20'
                                : 'border-dashed border-gray-300 dark:border-gray-600 bg-gray-50 dark:bg-gray-800'
                            )}
                          >
                            {cell ? (
                              <>
                                <span className="text-[10px] font-bold text-gray-800 dark:text-gray-200 leading-tight">{cell.boxId}</span>
                                <span className="text-[9px] text-gray-500">{cell.weight}kg</span>
                                {cell.fragile && <Diamond className="w-2.5 h-2.5 text-amber-500 mt-0.5" />}
                              </>
                            ) : (
                              <span className="text-[9px] text-gray-400">Empty</span>
                            )}
                          </div>
                        ))
                      ))}
                    </div>
                    <div className="flex justify-between mt-2 text-[10px] text-gray-400">
                      <span>Front</span>
                      <span>Center</span>
                      <span>Rear</span>
                    </div>
                  </div>
                </div>

                {/* Loading Stats */}
                <div className="enterprise-card p-5">
                  <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100 flex items-center gap-1.5 mb-4">
                    <Clock className="w-4 h-4 text-orange-500" /> Loading Stats
                  </h3>
                  <div className="space-y-3">
                    <div className="flex items-center justify-between">
                      <span className="text-sm text-gray-600 dark:text-gray-400">Est. Load Time</span>
                      <span className="text-sm font-semibold text-gray-900 dark:text-gray-100">~18 min</span>
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="text-sm text-gray-600 dark:text-gray-400">Total Weight</span>
                      <span className="text-sm font-semibold text-gray-900 dark:text-gray-100">{loadingPlan.totalWeight} kg</span>
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="text-sm text-gray-600 dark:text-gray-400">Box Count</span>
                      <span className="text-sm font-semibold text-gray-900 dark:text-gray-100">{loadingPlan.sequence.length}</span>
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="text-sm text-gray-600 dark:text-gray-400">Stop Count</span>
                      <span className="text-sm font-semibold text-gray-900 dark:text-gray-100">{MOCK_DELIVERY_STOPS.length}</span>
                    </div>
                  </div>
                </div>

                {/* Actions */}
                <div className="enterprise-card p-5">
                  <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100 flex items-center gap-1.5 mb-4">
                    <Settings className="w-4 h-4 text-orange-500" /> Actions
                  </h3>
                  <div className="space-y-2">
                    <PermissionGate resource="settings" action="create">
                      <button
                        onClick={() => setSelectedTruck(selectedTruck)}
                        disabled={isFetching}
                        className="w-full enterprise-btn-ai text-sm py-2.5 flex items-center justify-center gap-1.5"
                      >
                        <RotateCw className={clsx('w-4 h-4', isFetching && 'animate-spin')} /> Optimize Route
                      </button>
                    </PermissionGate>
                    <PermissionGate resource="settings" action="edit">
                      <button className="w-full enterprise-btn-primary text-sm py-2.5 flex items-center justify-center gap-1.5">
                        <CheckCircle className="w-4 h-4" /> Confirm Loading Plan
                      </button>
                    </PermissionGate>
                    <PermissionGate resource="settings" action="edit">
                      <button className="w-full enterprise-btn-secondary text-sm py-2.5 flex items-center justify-center gap-1.5">
                        <Settings className="w-4 h-4" /> Manual Adjust
                      </button>
                    </PermissionGate>
                    <PermissionGate resource="settings" action="edit">
                      <button className="w-full enterprise-btn-danger text-sm py-2.5 flex items-center justify-center gap-1.5">
                        <AlertTriangle className="w-4 h-4" /> Override
                      </button>
                    </PermissionGate>
                  </div>
                </div>
              </div>
            </div>
          )}

          {!loadingPlan && !isLoading && (
            <div className="enterprise-card p-12 text-center">
              <Truck className="w-12 h-12 text-gray-300 dark:text-gray-600 mx-auto mb-3" />
              <p className="text-gray-500 dark:text-gray-400 text-sm">Select a truck to view its loading plan</p>
            </div>
          )}
        </>
      )}
    </div>
  )
}
