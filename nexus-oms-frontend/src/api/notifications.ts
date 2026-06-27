import client from './client'
import { ApiResponse } from '../types'

export interface NotificationTemplate {
  id: string
  code: string
  name: string
  description: string
  channel: 'EMAIL' | 'SMS' | 'PUSH' | 'IN_APP' | 'SLACK'
  subject: string
  body: string
  variables: string[]
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface NotificationLog {
  id: string
  templateCode: string
  channel: string
  recipient: string
  subject: string
  body: string
  status: 'SENT' | 'DELIVERED' | 'FAILED' | 'READ'
  errorMessage?: string
  readAt?: string
  sentAt: string
  createdAt: string
}

export interface AlertRule {
  id: string
  name: string
  description: string
  metric: string
  condition: 'GREATER_THAN' | 'LESS_THAN' | 'EQUAL_TO' | 'NOT_EQUAL_TO'
  threshold: number
  channel: string
  recipients: string[]
  isActive: boolean
  cooldownMinutes: number
  lastTriggeredAt?: string
  createdAt: string
  updatedAt: string
}

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
    const { data } = await client.get('/notifications/alert-rules', { params: { page, size } })
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to fetch alert rules')
  }
}

export async function createAlertRule(ruleData: Record<string, any>): Promise<ApiResponse<AlertRule>> {
  try {
    const { data } = await client.post('/notifications/alert-rules', ruleData)
    return data
  } catch (error: any) {
    throw new Error(error.response?.data?.message || error.message || 'Failed to create alert rule')
  }
}

export async function toggleAlertRule(id: string): Promise<ApiResponse<AlertRule>> {
  try {
    const { data } = await client.put(`/notifications/alert-rules/${id}/toggle`)
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
