import client from './client'
import { ApiResponse, NotificationTemplate, NotificationLog, AlertRule } from '../types'
export type { NotificationTemplate, NotificationLog, AlertRule }

export async function getTemplates(page: number, size: number): Promise<ApiResponse<{ content: NotificationTemplate[]; totalElements: number; totalPages: number }>> {
  try {
    const { data } = await client.get('/notifications/templates', { params: { page, size } })
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch templates')
  }
}

export async function getTemplate(id: string): Promise<ApiResponse<NotificationTemplate>> {
  try {
    const { data } = await client.get(`/notifications/templates/${id}`)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch template')
  }
}

export async function createTemplate(templateData: Record<string, any>): Promise<ApiResponse<NotificationTemplate>> {
  try {
    const { data } = await client.post('/notifications/templates', templateData)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to create template')
  }
}

export async function updateTemplate(id: string, templateData: Record<string, any>): Promise<ApiResponse<NotificationTemplate>> {
  try {
    const { data } = await client.put(`/notifications/templates/${id}`, templateData)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to update template')
  }
}

export async function sendNotification(channel: string, recipient: string, templateCode: string, variables: Record<string, any>): Promise<ApiResponse<NotificationLog>> {
  try {
    const { data } = await client.post('/notifications/send', { channel, recipient, templateCode, variables })
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to send notification')
  }
}

export async function getNotificationLogs(page: number, size: number): Promise<ApiResponse<{ content: NotificationLog[]; totalElements: number; totalPages: number }>> {
  try {
    const { data } = await client.get('/notifications/logs', { params: { page, size } })
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch notification logs')
  }
}

export async function getAlertRules(page: number, size: number): Promise<ApiResponse<{ content: AlertRule[]; totalElements: number; totalPages: number }>> {
  try {
    const { data } = await client.get('/notifications/alerts', { params: { page, size } })
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch alert rules')
  }
}

export async function createAlertRule(ruleData: Record<string, any>): Promise<ApiResponse<AlertRule>> {
  try {
    const { data } = await client.post('/notifications/alerts', ruleData)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to create alert rule')
  }
}

export async function toggleAlertRule(id: string): Promise<ApiResponse<AlertRule>> {
  try {
    const { data } = await client.put(`/notifications/alerts/${id}/toggle`)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to toggle alert rule')
  }
}

export async function getUnreadCount(): Promise<ApiResponse<{ count: number }>> {
  try {
    const { data } = await client.get('/notifications/unread-count')
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch unread count')
  }
}
