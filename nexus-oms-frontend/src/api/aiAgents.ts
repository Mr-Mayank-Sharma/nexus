import client from './client'
import { ApiResponse } from '../types'

export interface AiModel {
  id: string
  name: string
  description: string
  version: string
  category: string
  status: string
  accuracy: number | null
  latencyMs: number | null
  lastTrained: string | null
  tenantId: string
  isGlobal: boolean
}

export interface AiAgent {
  name: string
  status: string
  accuracy: number
  decisions24h: number
  modelVersion: string
}

export interface AiRecommendation {
  id: string
  title: string
  description: string
  impact: string
  confidence: number
  type: string
  status: string
}

export interface AiDecision {
  id: string
  orderId: string
  agent: string
  decision: string
  confidence: number
  timestamp: string
  overriddenBy: string | null
  processingTime: string
}

export interface AiPackagingPlan {
  boxType: string
  dimensions: string
  weight: number
  fillRate: number
  materials: string[]
  confidence: number
}

export interface AiLoadingPlan {
  id: string
  type: string
  capacity: number
  used: number
  stops: number
  status: string
  departure: string
}

export interface AiPickRoute {
  id: string
  waveId: string
  route: string
  estimatedTime: string
  distance: number
}

export interface AiBriefing {
  kpis: Record<string, string>
  insights: { type: string; title: string; description: string; icon: string }[]
  risks: { title: string; description: string; severity: string; probability: number }[]
  recommendations: AiRecommendation[]
  forecast: { month: string; revenue: number }[]
}

export interface AiForecast {
  month: string
  orders: number
  revenue: number
  growth: number
}

export interface AiSupplierRisk {
  supplier: string
  riskScore: number
  status: string
  leadTime: number
  onTime: number
}

export async function getAgents(): Promise<ApiResponse<AiAgent[]>> {
  try {
    const { data } = await client.get('/ai/routing')
    const agents: AiAgent[] = data?.agents ?? []
    return { success: true, data: agents }
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get AI agents'
    return { success: false, error: msg } as any
  }
}

export async function getAgent(id: string): Promise<ApiResponse<AiAgent | undefined>> {
  try {
    const { data } = await client.get('/ai/routing')
    const agent = (data?.agents ?? []).find((a: AiAgent) => a.name === id || a.name.toLowerCase().replace(/\s+/g, '-') === id)
    return { success: true, data: agent }
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get agent'
    return { success: false, error: msg } as any
  }
}

export async function getRecommendations(role?: string, limit?: number): Promise<ApiResponse<AiRecommendation[]>> {
  try {
    const { data } = await client.get('/ai/briefing')
    const recs: AiRecommendation[] = data?.recommendations ?? []
    const sliced = limit ? recs.slice(0, limit) : recs
    return { success: true, data: sliced }
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get recommendations'
    return { success: false, error: msg } as any
  }
}

export async function approveRecommendation(id: string): Promise<ApiResponse<any>> {
  try {
    const { data } = await client.post(`/ai/recommendations/${id}/respond`, { action: 'approved' })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to approve recommendation'
    return { success: false, error: msg } as any
  }
}

export async function rejectRecommendation(id: string): Promise<ApiResponse<any>> {
  try {
    const { data } = await client.post(`/ai/recommendations/${id}/respond`, { action: 'rejected' })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to reject recommendation'
    return { success: false, error: msg } as any
  }
}

export async function getDecisions(limit?: number): Promise<ApiResponse<AiDecision[]>> {
  try {
    const { data } = await client.get('/ai/audit')
    const decisions: AiDecision[] = data?.decisions ?? []
    const sliced = limit ? decisions.slice(0, limit) : decisions
    return { success: true, data: sliced }
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get decisions'
    return { success: false, error: msg } as any
  }
}

export async function getPackagingPlan(orderId: string): Promise<ApiResponse<AiPackagingPlan | undefined>> {
  try {
    const { data } = await client.get('/ai/packing')
    const orderPlan = (data?.orders ?? []).find((o: any) => o.orderId === orderId)
    return { success: true, data: orderPlan?.aiBoxPlan ?? orderPlan }
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get packaging plan'
    return { success: false, error: msg } as any
  }
}

export async function getLoadingPlan(truckId: string): Promise<ApiResponse<AiLoadingPlan | undefined>> {
  try {
    const { data } = await client.get('/ai/loading')
    const truck = (data?.trucks ?? []).find((t: AiLoadingPlan) => t.id === truckId)
    return { success: true, data: truck }
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get loading plan'
    return { success: false, error: msg } as any
  }
}

export async function getPickRoute(waveId: string): Promise<ApiResponse<AiPickRoute>> {
  try {
    const { data } = await client.get('/ai/routing')
    const queue = data?.queue ?? []
    const first = queue[0]
    const route: AiPickRoute = {
      id: `route-${waveId}`,
      waveId,
      route: first?.orderId ? `Pick for ${first.orderId}` : 'No route data',
      estimatedTime: '45 min',
      distance: 320,
    }
    return { success: true, data: route }
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get pick route'
    return { success: false, error: msg } as any
  }
}

export async function getBriefing(): Promise<ApiResponse<AiBriefing>> {
  try {
    const { data } = await client.get('/ai/briefing')
    return { success: true, data: data as AiBriefing }
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get briefing'
    return { success: false, error: msg } as any
  }
}

export async function getForecasts(): Promise<ApiResponse<AiForecast[]>> {
  try {
    const { data } = await client.get('/ai/forecasting')
    const demand: AiForecast[] = data?.demand ?? []
    return { success: true, data: demand }
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get forecasts'
    return { success: false, error: msg } as any
  }
}

export async function getSupplierRisks(): Promise<ApiResponse<AiSupplierRisk[]>> {
  try {
    const { data } = await client.get('/ai/forecasting')
    const risks: AiSupplierRisk[] = data?.supplierRisk ?? []
    return { success: true, data: risks }
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get supplier risks'
    return { success: false, error: msg } as any
  }
}
