import client from './client'
import { ApiResponse, NotificationTemplate, NotificationLog, AlertRule } from '../types'
export type { NotificationTemplate, NotificationLog, AlertRule }

export async function getTemplates(page: number, size: number): Promise<ApiResponse<{ content: NotificationTemplate[]; totalElements: number; totalPages: number }>> {
  try {
    const { data } = await client.get('/notifications/templates', { params: { page, size } })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get templates'
    return { success: false, error: msg } as any
  }
}

export async function getTemplate(id: string): Promise<ApiResponse<NotificationTemplate>> {
  try {
    const { data } = await client.get(`/notifications/templates/${id}`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get template'
    return { success: false, error: msg } as any
  }
}

export async function createTemplate(templateData: Record<string, any>): Promise<ApiResponse<NotificationTemplate>> {
  try {
    const { data } = await client.post('/notifications/templates', templateData)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to create template'
    return { success: false, error: msg } as any
  }
}

export async function updateTemplate(id: string, templateData: Record<string, any>): Promise<ApiResponse<NotificationTemplate>> {
  try {
    const { data } = await client.put(`/notifications/templates/${id}`, templateData)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to update template'
    return { success: false, error: msg } as any
  }
}

export async function sendNotification(channel: string, recipient: string, templateCode: string, variables: Record<string, any>): Promise<ApiResponse<NotificationLog>> {
  try {
    const { data } = await client.post('/notifications/send', { channel, recipient, templateCode, variables })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to send notification'
    return { success: false, error: msg } as any
  }
}

export async function getNotificationLogs(page: number, size: number): Promise<ApiResponse<{ content: NotificationLog[]; totalElements: number; totalPages: number }>> {
  try {
    const { data } = await client.get('/notifications/logs', { params: { page, size } })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get notification logs'
    return { success: false, error: msg } as any
  }
}

export async function getAlertRules(page: number, size: number): Promise<ApiResponse<{ content: AlertRule[]; totalElements: number; totalPages: number }>> {
  try {
    const { data } = await client.get('/notifications/alerts', { params: { page, size } })
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get alert rules'
    return { success: false, error: msg } as any
  }
}

export async function createAlertRule(ruleData: Record<string, any>): Promise<ApiResponse<AlertRule>> {
  try {
    const { data } = await client.post('/notifications/alerts', ruleData)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to create alert rule'
    return { success: false, error: msg } as any
  }
}

export async function toggleAlertRule(id: string): Promise<ApiResponse<AlertRule>> {
  try {
    const { data } = await client.put(`/notifications/alerts/${id}/toggle`)
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to toggle alert rule'
    return { success: false, error: msg } as any
  }
}

export async function getUnreadCount(): Promise<ApiResponse<{ count: number }>> {
  try {
    const { data } = await client.get('/notifications/unread-count')
    return data
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Failed to get unread count'
    return { success: false, error: msg } as any
  }
}
